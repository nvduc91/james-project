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

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.Header
import org.apache.http.HttpStatus.{SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ALICE, ALICE_PASSWORD, AUTHORIZATION_HEADER, BOB, BOB_BASIC_AUTH_HEADER, BOB_PASSWORD, DOMAIN, DOMAIN_WITH_SPACE, ECHO_REQUEST_OBJECT, INVALID_JWT_TOKEN, UNKNOWN_USER_TOKEN, USER_TOKEN, getHeadersWith, toBase64, _}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Nested, Tag, Test}


trait AuthenticationContract {
  @BeforeEach
  def setup(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain(_2_DOT_DOMAIN.asString)
      .addUser(USER.asString, USER_PASSWORD)
      .addUser(ALICE.asString, ALICE_PASSWORD)
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .build
  }

  @Nested
  class BothAuthenticationMechanisms {
    @Tag(CategoryTags.BASIC_FEATURE)
    @Test
    def shouldOKWhenBasicAuthValidAndJWTInvalid(): Unit = {
      `given`
        .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
        .header(new Header(AUTHORIZATION_HEADER, s"Bearer $UNKNOWN_USER_TOKEN"))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
    }

    @Tag(CategoryTags.BASIC_FEATURE)
    @Test
    def shouldOKWhenJWTAuthValidAndBasicAuthInvalid(): Unit = {
      `given`
        .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"this-thing-wrong")}")))
        .header(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN"))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
    }

    @Tag(CategoryTags.BASIC_FEATURE)
    @Test
    def shouldOKWhenBothAuthenticationValid(): Unit = {
      `given`
        .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
        .header(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN"))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
    }
  }

  @Nested
  class BasicAuth {
    @Tag(CategoryTags.BASIC_FEATURE)
    @Test
    def should401WhenNoneAuthenticationValid(): Unit = {
      `given`
        .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"this-one-wrong")}")))
        .header(new Header(AUTHORIZATION_HEADER, s"Bearer $UNKNOWN_USER_TOKEN"))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_UNAUTHORIZED)
    }

    @Test
    def postShouldRespondUnauthorizedWhenNoAuthorizationHeader(): Unit = {
      given()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_UNAUTHORIZED)
    }

    @Test
    @Tag(CategoryTags.BASIC_FEATURE)
    def postShouldRespond200WhenHasCredentials(): Unit = {
      `given`
        .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
    }

    @Test
    def postShouldRespond401WhenCredentialsWithInvalidUser(): Unit = {
      val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${BOB.getLocalPart}@@$DOMAIN:$BOB_PASSWORD")}")
      `given`
        .headers(getHeadersWith(authHeader))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_UNAUTHORIZED)
    }

    @Test
    def postShouldRespondOKWhenCredentialsWith2DotDomain(): Unit = {
      val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${ALICE.asString}:$ALICE_PASSWORD")}")
      `given`
        .headers(getHeadersWith(authHeader))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
    }

    @Test
    def postShouldRespond401WhenCredentialsWithSpaceDomain(): Unit = {
      val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${BOB.getLocalPart}@$DOMAIN_WITH_SPACE:$BOB_PASSWORD")}")
      `given`
        .headers(getHeadersWith(authHeader))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_UNAUTHORIZED)
    }

    @Test
    def postShouldRespond401WhenUserNotFound(): Unit = {
      val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"usernotfound@$DOMAIN:$BOB_PASSWORD")}")
      `given`
        .headers(getHeadersWith(authHeader))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_UNAUTHORIZED)
    }

    @Test
    @Tag(CategoryTags.BASIC_FEATURE)
    def postShouldRespond401WhenWrongPassword(): Unit = {
      val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${BOB.asString}:WRONG_PASSWORD")}")
      `given`
        .headers(getHeadersWith(authHeader))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_UNAUTHORIZED)
    }
  }

  @Nested
  class JWTAuth {
    @Tag(CategoryTags.BASIC_FEATURE)
    @Test
    def getMustReturn200WhenValidJwtAuthorizationHeader(): Unit = {
      `given`
        .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN")))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
    }

    @Test
    @Tag(CategoryTags.BASIC_FEATURE)
    def getMustReturn401WhenValidUnknownUserJwtAuthorizationHeader(): Unit = {
      val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Bearer $UNKNOWN_USER_TOKEN")
      `given`
        .headers(getHeadersWith(authHeader))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_UNAUTHORIZED)
    }

    @Test
    @Tag(CategoryTags.BASIC_FEATURE)
    def getMustReturn401WhenInvalidJwtAuthorizationHeader(): Unit = {
      `given`
        .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Bearer $INVALID_JWT_TOKEN")))
        .body(ECHO_REQUEST_OBJECT)
      .when
        .post
      .`then`
        .statusCode(SC_UNAUTHORIZED)
    }
  }
}

