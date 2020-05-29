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

import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.Header
import org.apache.http.HttpStatus._
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{Tag, Test}

trait JWTAuthenticationContract extends AuthenticationContract {
  override def doSetup(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(USER.asString(), USER_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .build
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def getMustReturn200WhenValidJwtAuthorizationHeader(): Unit = {
    `given`
      .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN")))
      .body(ECHO_REQUEST_OBJECT)
    .when
      .post()
    .`then`
      .statusCode(SC_OK)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMustReturn401WhenValidUnknownUserJwtAuthorizationHeader(): Unit = {
    val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Bearer $UNKNOWN_USER_TOKEN")
    `given`()
      .headers(getHeadersWith(authHeader))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMustReturn401WhenInvalidJwtAuthorizationHeader(): Unit = {
    `given`
      .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Bearer $INVALID_JWT_TOKEN")))
      .body(ECHO_REQUEST_OBJECT)
    .when
      .post()
    .`then`
      .statusCode(SC_UNAUTHORIZED)
  }
}
