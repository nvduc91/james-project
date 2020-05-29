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

package org.apache.james.jmap.rfc8621.contract

import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.Header
import org.apache.http.HttpStatus.{SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{Tag, Test}

trait AuthenticationsContract extends AuthenticationContract {
  override def doSetup(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(USER.asString(), USER_PASSWORD)
      .addDomain(BOB.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .build
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def shouldOKWhenBasicAuthValidAndJWTInvalid() = {
    `given`()
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .header(new Header(AUTHORIZATION_HEADER, s"Bearer $UNKNOWN_USER_TOKEN"))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_OK)
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def shouldOKWhenJWTAuthValidAndBasicAuthInvalid() = {
    `given`()
          .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"this-thing-wrong")}")))
          .header(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN"))
          .body(ECHO_REQUEST_OBJECT)
        .when()
          .post()
        .then
          .statusCode(SC_OK)
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def shouldOKWhenBothAuthenticationValid() = {
    `given`()
          .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
          .header(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN"))
          .body(ECHO_REQUEST_OBJECT)
        .when()
          .post()
        .then
          .statusCode(SC_OK)
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def should401WhenNoneAuthenticationValid() = {
    `given`()
          .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"this-one-wrong")}")))
          .header(new Header(AUTHORIZATION_HEADER, s"Bearer $UNKNOWN_USER_TOKEN"))
          .body(ECHO_REQUEST_OBJECT)
        .when()
          .post()
        .then
          .statusCode(SC_UNAUTHORIZED)
  }
}
