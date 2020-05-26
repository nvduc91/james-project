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

package org.apache.james.jmap.rfc8621.contract

import java.nio.charset.StandardCharsets

object Fixture {
  def baseRequestSpecBuilder(server: GuiceJamesServer) = new RequestSpecBuilder()
    .setContentType(ContentType.JSON)
    .setAccept(ContentType.JSON)
    .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
    .setPort(server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue)
    .setBasePath(JMAP)

  def authScheme(userCredential: UserCredential): PreemptiveBasicAuthScheme = {
    val authScheme: PreemptiveBasicAuthScheme = new PreemptiveBasicAuthScheme
    authScheme.setUserName(userCredential.username.asString())
    authScheme.setPassword(userCredential.password)

    authScheme
  }

  val DOMAIN: Domain = Domain.of("domain.tld")
  val DOMAIN_WITH_SPACE: Domain = Domain.of("dom ain.tld")
  val _2_DOT_DOMAIN: Domain = Domain.of("do.main.tld")
  val BOB: Username = Username.fromLocalPartWithDomain("bob", DOMAIN)
  val ALICE: Username = Username.fromLocalPartWithDomain("alice", _2_DOT_DOMAIN)
  val BOB_PASSWORD: String = "bobpassword"
  val ALICE_PASSWORD: String = "alicepassword"

  val ECHO_REQUEST_OBJECT: String =
    """{
      |  "using": [
      |    "urn:ietf:params:jmap:core"
      |  ],
      |  "methodCalls": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}""".stripMargin

  val ECHO_RESPONSE_OBJECT: String =
    """{
      |  "sessionState": "75128aab4b1b",
      |  "methodResponses": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}""".stripMargin

  val ACCEPT_RFC8621_VERSION_HEADER: String = "application/json; jmapVersion=rfc-8621"
}
