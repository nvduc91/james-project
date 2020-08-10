/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail._
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.State.INSTANCE
import org.apache.james.jmap.model.{CapabilityIdentifier, ErrorCode, Invocation, MailboxFactory}
import org.apache.james.jmap.utils.quotas.{QuotaLoader, QuotaLoaderWithPreloadedDefaultFactory}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{MailboxId, MailboxMetaData}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

class MailboxGetMethod @Inject() (serializer: Serializer,
                                  mailboxManager: MailboxManager,
                                  quotaFactory : QuotaLoaderWithPreloadedDefaultFactory,
                                  mailboxFactory: MailboxFactory,
                                  metricFactory: MetricFactory) extends Method {
  override val methodName: MethodName = MethodName("Mailbox/get")

  object MailboxGetResults {
    def found(mailbox: Mailbox): MailboxGetResults = MailboxGetResults(Set(mailbox), NotFound(Set.empty))
    def notFound(mailboxId: MailboxId): MailboxGetResults = MailboxGetResults(Set.empty, NotFound(Set(mailboxId)))
  }

  case class MailboxGetResults(mailboxes: Set[Mailbox], notFound: NotFound) {
    def merge(other: MailboxGetResults): MailboxGetResults = MailboxGetResults(this.mailboxes ++ other.mailboxes, this.notFound.merge(other.notFound))
  }

  override def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession): Publisher[Invocation] = {
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      asMailboxGetRequest(invocation.arguments)
        .flatMap(mailboxGetRequest => {
          mailboxGetRequest.properties match {
            case Some(properties) if !properties.asSetOfString.subsetOf(Mailbox.allProperties) =>
              SMono.just(Invocation.error(errorCode = ErrorCode.InvalidArguments,
                description = Some(s"The following properties [${properties.asSetOfString.diff(Mailbox.allProperties).mkString(", ")}] do not exist."),
                methodCallId = invocation.methodCallId))
            case _ => getMailboxes(capabilities, mailboxGetRequest, mailboxSession)
              .reduce(MailboxGetResults(Set.empty, NotFound(Set.empty)), (result1: MailboxGetResults, result2: MailboxGetResults) => result1.merge(result2))
              .map(mailboxes => MailboxGetResponse(
                accountId = mailboxGetRequest.accountId,
                state = INSTANCE,
                list = mailboxes.mailboxes.toList.sortBy(_.sortOrder),
                notFound = mailboxes.notFound))
              .map(mailboxGetResponse => Invocation(
                methodName = methodName,
                arguments = Arguments(serializer.serialize(mailboxGetResponse, mailboxGetRequest.properties, capabilities).as[JsObject]),
                methodCallId = invocation.methodCallId))
          }
        }
        ))
  }

  private def asMailboxGetRequest(arguments: Arguments): SMono[MailboxGetRequest] = {
    serializer.deserializeMailboxGetRequest(arguments.value) match {
      case JsSuccess(mailboxGetRequest, _) => SMono.just(mailboxGetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString))
    }
  }

  private def getMailboxes(capabilities: Set[CapabilityIdentifier],
                           mailboxGetRequest: MailboxGetRequest,
                           mailboxSession: MailboxSession): SFlux[MailboxGetResults] =
    mailboxGetRequest.ids match {
      case None => getAllMailboxes(capabilities, mailboxSession)
        .map(MailboxGetResults.found)
      case Some(ids) => SFlux.fromIterable(ids.value)
        .flatMap(id => getMailboxResultById(capabilities, id, mailboxSession))
    }

  private def getMailboxResultById(capabilities: Set[CapabilityIdentifier],
                                   mailboxId: MailboxId,
                                   mailboxSession: MailboxSession): SMono[MailboxGetResults] =
    quotaFactory.loadFor(mailboxSession)
      .flatMap(quotaLoader => mailboxFactory.create(mailboxId, mailboxSession, quotaLoader)
        .map(mailbox => filterShared(capabilities, mailbox))
        .onErrorResume {
          case _: MailboxNotFoundException => SMono.just(MailboxGetResults.notFound(mailboxId))
          case error => SMono.raiseError(error)
        })
      .subscribeOn(Schedulers.elastic)

  private def filterShared(capabilities: Set[CapabilityIdentifier], mailbox: Mailbox): MailboxGetResults = {
    if (capabilities.contains(CapabilityIdentifier.JAMES_SHARES)) {
      MailboxGetResults.found(mailbox)
    } else {
      mailbox.namespace match {
        case _: PersonalNamespace => MailboxGetResults.found(mailbox)
        case _ => MailboxGetResults.notFound(mailbox.id)
      }
    }
  }

  private def getAllMailboxes(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SFlux[Mailbox] = {
    quotaFactory.loadFor(mailboxSession)
      .subscribeOn(Schedulers.elastic)
      .flatMapMany(quotaLoader =>
        getAllMailboxesMetaData(capabilities, mailboxSession)
          .flatMapMany(mailboxesMetaData =>
            SFlux.fromIterable(mailboxesMetaData)
              .flatMap(mailboxMetaData =>
                getMailboxResult(mailboxMetaData = mailboxMetaData,
                  mailboxSession = mailboxSession,
                  allMailboxesMetadata = mailboxesMetaData,
                  quotaLoader = quotaLoader))))
  }

  private def getAllMailboxesMetaData(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SMono[Seq[MailboxMetaData]] =
      SFlux.fromPublisher(mailboxManager.search(
          mailboxQuery(capabilities, mailboxSession),
          mailboxSession))
        .collectSeq()

  private def mailboxQuery(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession) =
    if (capabilities.contains(CapabilityIdentifier.JAMES_SHARES)) {
      MailboxQuery.builder
        .matchesAllMailboxNames
        .build
    } else {
      MailboxQuery.builder
        .privateNamespace()
        .user(mailboxSession.getUser)
        .build
    }

  private def getMailboxResult(mailboxSession: MailboxSession,
                               allMailboxesMetadata: Seq[MailboxMetaData],
                               mailboxMetaData: MailboxMetaData,
                               quotaLoader: QuotaLoader): SMono[Mailbox] =
    mailboxFactory.create(mailboxMetaData = mailboxMetaData,
      mailboxSession = mailboxSession,
      allMailboxesMetadata = allMailboxesMetadata,
      quotaLoader = quotaLoader)
}
