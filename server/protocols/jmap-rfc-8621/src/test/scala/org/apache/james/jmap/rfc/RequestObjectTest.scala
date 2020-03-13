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

package org.apache.james.jmap.rfc

import org.apache.james.jmap.rfc.model.RequestObject
import org.apache.james.jmap.rfc.model.RequestObject.{Capability, ClientId, Invocation, ServerId}
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._

class RequestObjectTest extends PlaySpec {

  "Deserialize Capability" must {
    "succeed with 1 value" in {
      val capabilityJsValue: JsValue = JsString("org.com.test.1")
      Json.fromJson[Capability](capabilityJsValue) must be(JsSuccess(Capability("org.com.test.1")))
    }

    "failed with wrong value type" in {
      val capabilityJsValue: JsValue = JsBoolean(true)
      Json.fromJson[Capability](capabilityJsValue) must not be (JsSuccess(Capability("org.com.test.1")))
    }

    "failed with wrong class type" in {
      val capabilityJsValue: JsValue = JsBoolean(true)
      Json.fromJson[ClientId](capabilityJsValue) must not be (JsSuccess(Capability("org.com.test.1")))
    }
  }

  "Serialize Capability" must {
    "succeed " in {
      val capability: Capability = Capability("org.com.test.1")
      val expectedCapability: JsValue = JsString("org.com.test.1")
      Json.toJson[Capability](capability) must be(expectedCapability)
    }
  }

  "Deserialize ClientId" must {
    "succeed " in {
      val clientIdJsValue: JsValue = JsString("Core/echo")
      Json.fromJson[ClientId](clientIdJsValue) must be(JsSuccess(ClientId("Core/echo")))
    }

    "failed with wrong class type" in {
      val clientIdJsValue: JsValue = JsBoolean(true)
      Json.fromJson[Capability](clientIdJsValue) must not be (JsSuccess(ClientId("Core/echo")))
    }

    "failed with wrong value type" in {
      val clientIdJsValue: JsValue = JsBoolean(true)
      Json.fromJson[ClientId](clientIdJsValue) must not be (JsSuccess(ClientId("Core/echo")))
    }
  }

  "Deserialize ServerId" must {
    "succeed " in {
      val serverIdJsValue: JsValue = JsString("Server 1 for test")
      Json.fromJson[ServerId](serverIdJsValue) must be(JsSuccess(ServerId("Server 1 for test")))
    }

    "failed with wrong class type" in {
      val serverIdJsValue: JsValue = JsBoolean(true)
      Json.fromJson[Capability](serverIdJsValue) must not be (JsSuccess(ServerId("Server 1 for test")))
    }

    "failed with wrong value type" in {
      val serverIdJsValue: JsValue = JsBoolean(true)
      Json.fromJson[ServerId](serverIdJsValue) must not be (JsSuccess(ServerId("Server 1 for test")))
    }
  }

  "Deserialize Invocation" must {
    "succeed values" in {
      val invocationJsValue: JsArray = Json.arr({
        Json.parse(
          """[ "Core/echo", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |""".stripMargin)},
        {Json.parse(
          """[ "Core/echo2", {
            |      "arg3": "arg3data",
            |      "arg4": "arg4data"
            |    }, "c2" ]
            |""".stripMargin)
        })
      Json.fromJson[Invocation](invocationJsValue) must be(JsSuccess(Invocation(invocationJsValue)))
    }

    "succeed value" in {
      val invocationJsValue: JsArray = Json.arr({
        Json.parse(
          """[ "Core/echo", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |""".stripMargin)})
      Json.fromJson[Invocation](invocationJsValue) must be(JsSuccess(Invocation(invocationJsValue)))
    }

    "failed with wrong value type" in {
      val invocationJsValue: JsValue = JsBoolean(true)
      Json.fromJson[Invocation](invocationJsValue) must not be (JsSuccess(Invocation(Json.arr({
        Json.parse(
          """[ "Core/echo", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |""".stripMargin)}))))
    }
  }

  "Serialize Invocation" must {
    "succeed " in {
      val invocation: Invocation = Invocation(Json.arr({
        Json.parse(
          """[ "Core/echo", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |""".stripMargin)}))
      val expectedInvocationJsArray: JsArray = Json.arr({
        Json.parse(
          """[ "Core/echo", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |""".stripMargin)})

      Json.toJson[Invocation](invocation) must be(expectedInvocationJsArray)
    }
  }

  "Deserialize RequestObject" must {
    "succeed " in {
      RequestObject.deserialize(
        """
          |{
          |  "using": [ "urn:ietf:params:jmap:core"],
          |  "methodCalls": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ]
          |  ]
          |}
          |""".stripMargin) must be(
        RequestObject.RequestObject(
          using = Seq(RequestObject.Capability("urn:ietf:params:jmap:core")),
          methodCalls = Seq(Invocation(Json.parse(
            """[ "Core/echo", {
              |      "arg1": "arg1data",
              |      "arg2": "arg2data"
              |    }, "c1" ]
              |""".stripMargin).as[JsArray]))))
    }

    "succeed with many Capability, methodCalls" in {
      RequestObject.deserialize(
        """
          |{
          |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:core2"],
          |  "methodCalls": [
          |    [ "Core/echo", {
          |      "arg1": "arg1data",
          |      "arg2": "arg2data"
          |    }, "c1" ],
          |    [ "Core/echo2", {
          |      "arg3": "arg3data",
          |      "arg4": "arg4data"
          |    }, "c2" ]
          |  ]
          |}
          |""".stripMargin) must be(
        RequestObject.RequestObject(
          using = Seq(RequestObject.Capability("urn:ietf:params:jmap:core"), RequestObject.Capability("urn:ietf:params:jmap:core2")),
          methodCalls = Seq(Invocation(Json.parse(
            """[ "Core/echo", {
              |      "arg1": "arg1data",
              |      "arg2": "arg2data"
              |    }, "c1" ]
              |""".stripMargin).as[JsArray]), Invocation(Json.parse(
            """[ "Core/echo2", {
              |      "arg3": "arg3data",
              |      "arg4": "arg4data"
              |    }, "c2" ]
              |""".stripMargin).as[JsArray]))))
    }
  }

  "Serialize RequestObject" must {
    "succeed " in {
      val requestObject: RequestObject.RequestObject = RequestObject.RequestObject(
        using = Seq(RequestObject.Capability("urn:ietf:params:jmap:core"), RequestObject.Capability("urn:ietf:params:jmap:core2")),
        methodCalls = Seq(Invocation(Json.parse(
          """[ "Core/echo", {
            |      "arg1": "arg1data",
            |      "arg2": "arg2data"
            |    }, "c1" ]
            |""".stripMargin).as[JsArray])))
      Json.stringify(Json.toJson(requestObject)) must be(
        """{"using":["urn:ietf:params:jmap:core","urn:ietf:params:jmap:core2"],"methodCalls":[["Core/echo",{"arg1":"arg1data","arg2":"arg2data"},"c1"]]}""")
    }
  }
}
