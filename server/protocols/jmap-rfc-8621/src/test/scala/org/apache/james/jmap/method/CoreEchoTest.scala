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
package org.apache.james.jmap.method

import java.io.InputStream
import java.nio.charset.StandardCharsets

import org.apache.james.jmap.json.Fixture.invocation1
import org.apache.james.jmap.model.ResponseObject
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono
import reactor.netty.ByteBufFlux
import reactor.netty.http.server.HttpServerRequest

class CoreEchoTest extends AnyWordSpec with Matchers {
  private val echoMethod: CoreEcho = new CoreEcho()

  private val REQUEST_OBJECT: String =
    """
      |{
      |  "using": [ "urn:ietf:params:jmap:core"],
      |  "methodCalls": [
      |    [ "Core/echo1", {
      |      "arg1": "arg1data",
      |      "arg2": "arg2data"
      |    }, "c1" ]
      |  ]
      |}
      |""".stripMargin

  private val REQUEST_OBJECT_MISSING_FILED: String =
    """
      |{
      |  "using": [ "urn:ietf:params:jmap:core"]
      |}
      |""".stripMargin

  private val RESPONSE_OBJECT: ResponseObject = ResponseObject(
    sessionState = ResponseObject.SESSION_STATE,
    methodResponses = Seq(invocation1))

  private val INVALID_REQUEST_OBJECT: String =
    """
      |{
      |  "field1": [ "just example"],
      |  "field2": ["value of field"]
      |}
      |""".stripMargin

  "CoreEcho" should {
    "Process" should {
      "should success and return ResponseObject with valid RequestObject" in {
        val inputStreamData: Flux[InputStream] = ByteBufFlux.fromString(SMono.just(REQUEST_OBJECT)).asInputStream()
        val expectedResponse: ResponseObject = RESPONSE_OBJECT

        val dataResponse = SMono.fromPublisher(echoMethod.process(inputStreamData)).block()
        dataResponse shouldEqual expectedResponse
      }

      "should error and return error with valid RequestObject but missing required field" in {
        val inputStreamData: Flux[InputStream] = ByteBufFlux.fromString(SMono.just(REQUEST_OBJECT_MISSING_FILED)).asInputStream()
        assertThrows[RuntimeException]{
          SMono.fromPublisher(echoMethod.process(inputStreamData)).block()
        }
      }

      "should error and return error with invalid RequestObject" in {
        val inputStreamData: Flux[InputStream] = ByteBufFlux.fromString(SMono.just(INVALID_REQUEST_OBJECT)).asInputStream()
        assertThrows[RuntimeException]{
          SMono.fromPublisher(echoMethod.process(inputStreamData)).block()
        }
      }
    }
  }
}
