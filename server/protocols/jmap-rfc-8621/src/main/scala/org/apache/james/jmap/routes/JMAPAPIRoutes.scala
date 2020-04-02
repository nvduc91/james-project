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
      .onErrorResume(throwable => Mono.from(errorHandling(throwable, httpServerResponse)))
      .subscribeOn(Schedulers.elastic)

  private def requestAsJsonStream(httpServerRequest: HttpServerRequest): Mono[RequestObject] = {
    SMono.fromPublisher(httpServerRequest
      .receive()
      .aggregate()
      .asInputStream())
      .flatMap(this.parseRequestObject)
      .asJava()
  }

  private def parseRequestObject(inputStream: InputStream): SMono[RequestObject] =
    new Serializer().deserializeRequestObject(inputStream) match {
      case JsSuccess(requestObject, _) => SMono.just(requestObject)
      case JsError(errors) => SMono.raiseError(new RuntimeException(errors.toString()))
    }

  private def process(requestObject: RequestObject, httpServerResponse: HttpServerResponse): Mono[Void] = {
    Mono.just(requestObject.methodCalls
      .map(this.methodNameMatcher)
      .foldLeft(Seq[Invocation]()) { (invocations: Seq[Invocation], lazyList: LazyList[Invocation]) =>
        invocations.appended(lazyList.head)
      })
      .flatMap((invocations: Seq[Invocation]) => httpServerResponse.status(OK)
        .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
        .sendString(Mono.just(new Serializer().serialize(new ResponseObject(ResponseObject.SESSION_STATE, invocations)).toString()))
        .`then`()
      )
  }

  private def methodNameMatcher(invocation: Invocation): LazyList[Invocation] = invocation.methodName match {
    case coreEcho.methodName => coreEcho.process(invocation)
    case _ => LazyList[Invocation](new Invocation(
      MethodName("error"),
      Arguments(Json.obj("type" -> "Not implemented")),
      invocation.methodCallId))
  }

  private def errorHandling(throwable: Throwable, response: HttpServerResponse): Mono[Void] =
    throwable match {
      case _: Throwable => handleInternalError(response, throwable)
    }
}
