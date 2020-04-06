/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/
package org.apache.james.jmap.routes

import java.io.InputStream
import java.util.stream
import java.util.stream.Stream

import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus.OK
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.method.CoreEcho
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.{Invocation, RequestObject, ResponseObject}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsSuccess, Json}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

class JMAPAPIRoutes extends JMAPRoutes {
  override def logger(): Logger = LoggerFactory.getLogger(getClass)

  private val coreEcho = new CoreEcho

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.POST, JMAP))
      .action(this.post)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, JMAP))
      .action(JMAPRoutes.CORS_CONTROL)
      .corsHeaders())

  private def post(httpServerRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): Mono[Void] =
    this.requestAsJsonStream(httpServerRequest)
      .flatMap(requestObject => this.process(requestObject, httpServerResponse))
      .onErrorResume(throwable => SMono.fromPublisher(handleInternalError(httpServerResponse, throwable)))
      .subscribeOn(Schedulers.elastic)
      .asJava()

  private def requestAsJsonStream(httpServerRequest: HttpServerRequest): SMono[RequestObject] = {
    SMono.fromPublisher(httpServerRequest
      .receive()
      .aggregate()
      .asInputStream())
      .flatMap(this.parseRequestObject)
  }

  private def parseRequestObject(inputStream: InputStream): SMono[RequestObject] =
    new Serializer().deserializeRequestObject(inputStream) match {
      case JsSuccess(requestObject, _) => SMono.just(requestObject)
      case JsError(errors) => SMono.raiseError(new RuntimeException(errors.toString()))
    }

  private def process(requestObject: RequestObject, httpServerResponse: HttpServerResponse): SMono[Void] =
    SMono.fromPublisher(
      Mono.just(requestObject.methodCalls.flatMap(this.processMethodWithMatchName))
        .flatMap((invocations: Seq[Invocation]) => httpServerResponse.status(OK)
          .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
          .sendString(SMono.just(new Serializer().serialize(ResponseObject(ResponseObject.SESSION_STATE, invocations)).toString()))
          .`then`()
        ))

  private def processMethodWithMatchName(invocation: Invocation): LazyList[Invocation] = invocation.methodName match {
    case coreEcho.methodName => coreEcho.process(invocation)
    case _ => LazyList[Invocation](new Invocation(
      MethodName("error"),
      Arguments(Json.obj("type" -> "Not implemented")),
      invocation.methodCallId))
  }
}
