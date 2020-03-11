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

import org.apache.james.jmap.rfc.model.Id.Id
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsError, JsString, JsSuccess, Json}
import eu.timepit.refined.auto._
import be.venneborg.refined.play.RefinedJsonFormats._
import com.fasterxml.jackson.databind.exc.MismatchedInputException

class IdTest extends WordSpec with Matchers {

  "apply" when {
    "in Runtime" should {
      "be JsError when Serialize null Id" in {
        assertThrows[NullPointerException] {
          val nullId: Option[String] = null
          Json.fromJson[Id](Json.parse(nullId.get)) shouldBe a[JsError]
        }
      }

      "be JsError when Serialize empty Id" in {
        assertThrows[MismatchedInputException] {
          val nullId: Option[String] = Option("")
          Json.fromJson[Id](Json.parse(nullId.get))
        }
      }

      "be JsError when Serialize invalid Id" in {
        val invalidString: JsString = JsString("===")
        Json.fromJson[Id](invalidString) shouldBe a[JsError]
      }
    }

    "in Compiletime" should {
      "be succeed when Serialize valid Id" in {
        val validString: JsString = JsString("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8")
        val expectedValue: Id = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"
        Json.fromJson[Id](validString) shouldBe a[JsSuccess[_]]
        Json.fromJson[Id](validString) should be(JsSuccess(expectedValue))
      }

      "be succeed when Deserialize valid Id" in {
        val expectedValue: String = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"
        val id: Id = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"
        Json.toJson[Id](id) shouldBe a[JsString]
        Json.toJson[Id](id) should be(JsString(expectedValue))
      }
    }
  }
}
