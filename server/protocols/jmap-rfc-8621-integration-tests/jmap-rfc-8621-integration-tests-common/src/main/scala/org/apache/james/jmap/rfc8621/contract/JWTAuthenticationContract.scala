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

import io.restassured.RestAssured.{`given`, `with`, requestSpecification}
import io.restassured.http.ContentType
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.hamcrest.Matchers.{both, equalTo, isA, notNullValue}
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import org.apache.james.jmap.rfc8621.contract.JWTAuthenticationContract._
import org.apache.james.utils.DataProbeImpl

object JWTAuthenticationContract {
  private def fromGoodContinuationTokenRequest = `with`
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .body(s"""{
          | "username": "${BOB.getLocalPart}",
          | "clientName": "Mozilla Thunderbird",
          | "clientVersion": "42.0",
          | "deviceName": "Joe Blogg’s iPhone"
          | }""".stripMargin)
      .post("/authentication")
        .body
      .path("continuationToken")
        .toString

  private def fromGoodAccessTokenRequest(continuationToken: String) = `with`
    .contentType(ContentType.JSON).accept(ContentType.JSON)
    .body(
      s"""{
         |  "token": "${continuationToken}",
         |  "method": "password",
         |  "password": "$BOB_PASSWORD}"
         | }""".stripMargin)
    .post("/authentication").path("accessToken")
    .toString

  private def goodDeleteAccessTokenRequest(accessToken: String): Unit = {
    `with`.header("Authorization", accessToken).delete("/authentication")
  }
}

trait JWTAuthenticationContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server).build
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def getMustReturnEndpointsWhenValidAuthorizationHeader(): Unit = {
    val continuationToken = fromGoodContinuationTokenRequest
    val token = fromGoodAccessTokenRequest(continuationToken)
    `given`
        .header("Authorization", token)
      .when
        .get("/authentication").`then`
      .statusCode(200)
        .body("api", equalTo("/jmap"))
        .body("eventSource", both(isA(classOf[String])).and(notNullValue))
        .body("upload", equalTo("/upload"))
        .body("download", equalTo("/download"))
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def getMustReturnEndpointsWhenValidJwtAuthorizationHeader(): Unit = {
    val token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGRvbWFpbi50bGQifQ.U-dUPv6OU6KO5N7CooHUfMkCd" +
      "FJHx2F3H4fm7Q79g1BPfBSkifPj5xyVlZ0JwEGXypC4zBw9ay3l4DxzX7D_6p1Hx_ihXsoLx1Ca-WUo44x-XRSpPfgxiZjHCJkGBLMV3RZlA" +
      "jip-d18mxkcX3JGplX_sCQkFisduAOAHuKSUg9wI6VBgUQi_0B35FYv6tP_bD6eFtvaAUN9QyXXh8UQjEp8CO12lRz6enfLx_V6BG_fEMkee" +
      "6vRqdEqx_F9OF3eWTe1giMp_JhQ7_l1OXXtbd4TndVvTeuVy4irPbsRc-M8x_-qTDpFp6saRRsyOcFspxPp5n3yIhEK7B3UZiseXw"
    `given`
        .header("Authorization", "Bearer " + token)
      .when
        .get("/authentication")
      .`then`
        .statusCode(200)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMustReturnEndpointsWhenValidUnkwnonUserJwtAuthorizationHeader(): Unit = {
    val token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1bmtub3duQGRvbWFpbi50bGQifQ.hr8AhlNIpiA3Mv_A5ZLyL" +
      "f1BHeBSaRDfdR_GLV_hlPdIrWv1xtwjBH86E1YnTPx2tTpr_NWTbHcR1OCkuVCpgloEnUNbE3U2l0WrGOX2Eh9dWCXOCtrNvCeSHQuvx5_8W" +
      "nSVENYidk7o2icE8_gz_Giwf0Z3bHJJYXfAxupv__tCkmhqt3E888VZPjs26AsqxQ29YyX0Fjx8UwKbPrH5-tnyftX-kLjjZNtahVIVtbW4v" +
      "b8rEEZ4nzqxHqtI2co6yCXjgyoFMdDAKCOU-Bq35Gdo-Qiu8l7a0kQGhuhkjoaVWvw4bcSvunxAnh_0W5g3Lw-rwljNu2JrJS0gAH6NDA"
    `given`
        .header("Authorization", "Bearer " + token)
      .when
        .get("/authentication")
      .`then`
        .statusCode(200)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMustReturnBadCredentialsWhenInvalidJwtAuthorizationHeader(): Unit = {
    val token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
      "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZ" +
      "EN_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf" +
      "49t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2" +
      "qNOR8Q31ydinyqzXvCSzVJOf6T60-w"
    `given`
        .header("Authorization", "Bearer " + token)
      .when
        .get("/authentication")
      .`then`
        .statusCode(401)
  }
}
