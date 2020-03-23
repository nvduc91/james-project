package org.apache.james.jmap.rfc.api.routes

import org.apache.james.jmap.rfc.api.method.EchoMethod
import org.apache.james.jmap.rfc.model.{RequestObject, ResponseObject}
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.publisher.Mono

object JMAPApiRoutes {
  private val LOGGER = LoggerFactory.getLogger(JMAPApiRoutes.getClass)
  private val ECHO_METHOD = new EchoMethod();
}

class JMAPApiRoutes {
  val logger: Logger = JMAPApiRoutes.LOGGER
  val echoMethod: EchoMethod = JMAPApiRoutes.ECHO_METHOD

  def post(httpRequest: HttpServerRequest, httpServerResponse: HttpServerResponse): Mono[Void] = {
    httpRequest
      .receive()
      .asInputStream()
      .flatMap(inputStream => {
        echoMethod.toRequestObject(inputStream)
      })
      .flatMap((requestObject: RequestObject) => {
        echoMethod.toResponseObject(requestObject)
          .map((responseObject: ResponseObject) => {
            httpServerResponse.status(HttpResponseStatus.OK).sendObject(responseObject)
          })
      })
      .`then`()
  }
}

