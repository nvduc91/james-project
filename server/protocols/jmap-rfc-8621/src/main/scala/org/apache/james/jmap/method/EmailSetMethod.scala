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

import com.google.common.collect.ImmutableList
import eu.timepit.refined.auto._
import javax.inject.Inject
import javax.mail.Flags
import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.json.{EmailSetSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.EmailSet.UnparsedMessageId
import org.apache.james.jmap.mail.{DestroyIds, EmailSet, EmailSetRequest, EmailSetResponse, EmailSetUpdate, MailboxIds, ValidatedEmailSetUpdate}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.DefaultCapabilities.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.SetError.SetErrorDescription
import org.apache.james.jmap.model.{Capabilities, Invocation, SetError, State}
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.{ComposedMessageIdWithMetaData, DeleteResult, MessageId}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

case class MessageNotFoundExeception(messageId: MessageId) extends Exception

class EmailSetMethod @Inject()(serializer: EmailSetSerializer,
                               messageIdManager: MessageIdManager,
                               messageIdFactory: MessageId.Factory,
                               val metricFactory: MetricFactory,
                               val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[EmailSetRequest] {


  case class DestroyResults(results: Seq[DestroyResult]) {
    def destroyed: Option[DestroyIds] = {
      Option(results.flatMap({
        result => result match {
          case result: DestroySuccess => Some(result.messageId)
          case _ => None
        }
      }).map(EmailSet.asUnparsed))
        .filter(_.nonEmpty)
        .map(DestroyIds)
    }

    def notDestroyed: Option[Map[UnparsedMessageId, SetError]] = {
      Option(results.flatMap({
        result => result match {
          case failure: DestroyFailure => Some(failure)
          case _ => None
        }
      })
        .map(failure => (failure.unparsedMessageId, failure.asMessageSetError))
        .toMap)
        .filter(_.nonEmpty)
    }
  }

  object DestroyResult {
    def from(deleteResult: DeleteResult): DestroyResult = {
      val notFound = deleteResult.getNotFound.asScala

      deleteResult.getDestroyed.asScala
        .headOption
        .map(DestroySuccess)
        .getOrElse(DestroyFailure(EmailSet.asUnparsed(notFound.head), MessageNotFoundExeception(notFound.head)))
    }
  }

  trait DestroyResult
  case class DestroySuccess(messageId: MessageId) extends DestroyResult
  case class DestroyFailure(unparsedMessageId: UnparsedMessageId, e: Throwable) extends DestroyResult {
    def asMessageSetError: SetError = e match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"$unparsedMessageId is not a messageId: ${e.getMessage}"))
      case e: MessageNotFoundExeception => SetError.notFound(SetErrorDescription(s"Cannot find message with messageId: ${e.messageId.serialize()}"))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }

  trait UpdateResult
  case class UpdateSuccess(messageId: MessageId) extends UpdateResult
  case class UpdateFailure(unparsedMessageId: UnparsedMessageId, e: Throwable) extends UpdateResult {
    def asMessageSetError: SetError = e match {
      case e: IllegalArgumentException => SetError.invalidPatch(SetErrorDescription(s"Message $unparsedMessageId update is invalid: ${e.getMessage}"))
      case _: MailboxNotFoundException => SetError.notFound(SetErrorDescription(s"Mailbox not found"))
      case e: MessageNotFoundExeception => SetError.notFound(SetErrorDescription(s"Cannot find message with messageId: ${e.messageId.serialize()}"))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }
  case class UpdateResults(results: Seq[UpdateResult]) {
    def updated: Option[Map[MessageId, Unit]] = {
      Option(results.flatMap({
        result => result match {
          case result: UpdateSuccess => Some(result.messageId, ())
          case _ => None
        }
      }).toMap).filter(_.nonEmpty)
    }

    def notUpdated: Option[Map[UnparsedMessageId, SetError]] = {
      Option(results.flatMap({
        result => result match {
          case failure: UpdateFailure => Some(failure)
          case _ => None
        }
      })
        .map(failure => (failure.unparsedMessageId, failure.asMessageSetError))
        .toMap)
        .filter(_.nonEmpty)
    }
  }

  override val methodName: MethodName = MethodName("Email/set")
  override val requiredCapabilities: Capabilities = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailSetRequest): SMono[InvocationWithContext] = {
    for {
      destroyResults <- destroy(request, mailboxSession)
      updateResults <- update(request, mailboxSession)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = invocation.invocation.methodName,
        arguments = Arguments(serializer.serialize(EmailSetResponse(
          accountId = request.accountId,
          newState = State.INSTANCE,
          updated = updateResults.updated,
          notUpdated = updateResults.notUpdated,
          destroyed = destroyResults.destroyed,
          notDestroyed = destroyResults.notDestroyed))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = invocation.processingContext)
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[EmailSetRequest] = asEmailSetRequest(invocation.arguments)

  private def asEmailSetRequest(arguments: Arguments): SMono[EmailSetRequest] =
    serializer.deserialize(arguments.value) match {
      case JsSuccess(emailSetRequest, _) => SMono.just(emailSetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def destroy(emailSetRequest: EmailSetRequest, mailboxSession: MailboxSession): SMono[DestroyResults] =
    SFlux.fromIterable(emailSetRequest.destroy.getOrElse(DestroyIds(Seq())).value)
      .flatMap(id => deleteMessage(id, mailboxSession))
      .collectSeq()
      .map(DestroyResults)

  private def update(emailSetRequest: EmailSetRequest, mailboxSession: MailboxSession): SMono[UpdateResults] = {
    emailSetRequest.update
      .filter(_.nonEmpty)
      .map(update(_, mailboxSession))
      .getOrElse(SMono.just(UpdateResults(Seq())))
  }

  private def update(updates: Map[UnparsedMessageId, JsObject], session: MailboxSession): SMono[UpdateResults] = {
    val validatedUpdates: List[Either[UpdateFailure, (MessageId, EmailSetUpdate)]] = updates
      .map({
        case (unparsedMessageId, json) => EmailSet.parse(messageIdFactory)(unparsedMessageId)
          .toEither
          .left.map(e => UpdateFailure(unparsedMessageId, e))
          .flatMap(id => serializer.deserializeEmailSetUpdate(json)
            .asEither
            .fold(e => Left(UpdateFailure(unparsedMessageId, new IllegalArgumentException(e.toString()))),
              (emailSetUpdate: EmailSetUpdate) => Right((id, emailSetUpdate))))
      })
      .toList
    val failures: List[UpdateFailure] = validatedUpdates.flatMap({
      case Left(e) => Some(e)
      case _ => None
    })
    val validUpdates: List[(MessageId, EmailSetUpdate)] = validatedUpdates.flatMap({
      case Right(pair) => Some(pair)
      case _ => None
    })

    for {
      updates <- SFlux.fromPublisher(messageIdManager.messagesMetadata(validUpdates.map(_._1).asJavaCollection, session))
        .collectMultimap(metaData => metaData.getComposedMessageId.getMessageId)
        .flatMap(metaData => {
          SFlux.fromIterable(validUpdates)
            .concatMap[UpdateResult]({
              case (messageId, updatePatch) =>
                doUpdate(messageId, updatePatch, metaData.get(messageId).toList.flatten, session)
            })
            .collectSeq()
        })
    } yield {
      UpdateResults(updates ++ failures)
    }
  }

  private def doUpdate(messageId: MessageId, update: EmailSetUpdate, storedMetaData: List[ComposedMessageIdWithMetaData], session: MailboxSession): SMono[UpdateResult] = {
    val mailboxIds: MailboxIds = MailboxIds(storedMetaData.map(metaData => metaData.getComposedMessageId.getMailboxId))
    val originFlags: Flags = storedMetaData
      .foldLeft[Flags](new Flags())((flags: Flags, m: ComposedMessageIdWithMetaData) => {
        flags.add(m.getFlags)
        flags
      })

    if (mailboxIds.value.isEmpty) {
      SMono.just[UpdateResult](UpdateFailure(EmailSet.asUnparsed(messageId), MessageNotFoundExeception(messageId)))
    } else {
      update.validate
        .fold(
          e => SMono.just(UpdateFailure(EmailSet.asUnparsed(messageId), e)),
          validatedUpdate =>
            resetFlags(messageId, validatedUpdate, mailboxIds, originFlags, session)
              .flatMap {
                case failure: UpdateFailure => SMono.just[UpdateResult](failure)
                case _: UpdateSuccess => updateMailboxIds(messageId, validatedUpdate, mailboxIds, session)
              }
              .onErrorResume(e => SMono.just[UpdateResult](UpdateFailure(EmailSet.asUnparsed(messageId), e)))
              .switchIfEmpty(SMono.just[UpdateResult](UpdateSuccess(messageId))))
    }
  }

  private def updateMailboxIds(messageId: MessageId, update: ValidatedEmailSetUpdate, mailboxIds: MailboxIds, session: MailboxSession): SMono[UpdateResult] = {
    val targetIds = update.mailboxIdsTransformation.apply(mailboxIds)
    if (targetIds.equals(mailboxIds)) {
      SMono.just[UpdateResult](UpdateSuccess(messageId))
    } else {
      SMono.fromCallable(() => messageIdManager.setInMailboxes(messageId, targetIds.value.asJava, session))
        .subscribeOn(Schedulers.elastic())
        .`then`(SMono.just[UpdateResult](UpdateSuccess(messageId)))
        .onErrorResume(e => SMono.just[UpdateResult](UpdateFailure(EmailSet.asUnparsed(messageId), e)))
        .switchIfEmpty(SMono.just[UpdateResult](UpdateSuccess(messageId)))
    }
  }

  private def resetFlags(messageId: MessageId, update: ValidatedEmailSetUpdate, mailboxIds: MailboxIds, originalFlags: Flags, session: MailboxSession): SMono[UpdateResult] =
    update.keywords
      .map(keywords => keywords.asFlagsWithRecentAndDeletedFrom(originalFlags))
      .map(flags => SMono.fromCallable(() =>
        messageIdManager.setFlags(flags, FlagsUpdateMode.REPLACE, messageId, ImmutableList.copyOf(mailboxIds.value.asJavaCollection), session))
        .subscribeOn(Schedulers.elastic())
        .`then`(SMono.just[UpdateResult](UpdateSuccess(messageId))))
      .getOrElse(SMono.just[UpdateResult](UpdateSuccess(messageId)))

  private def deleteMessage(destroyId: UnparsedMessageId, mailboxSession: MailboxSession): SMono[DestroyResult] =
    EmailSet.parse(messageIdFactory)(destroyId)
      .fold(e => SMono.just(DestroyFailure(destroyId, e)),
        parsedId => SMono.fromCallable(() => DestroyResult.from(messageIdManager.delete(parsedId, mailboxSession)))
          .subscribeOn(Schedulers.elastic)
          .onErrorRecover(e => DestroyFailure(EmailSet.asUnparsed(parsedId), e)))
}