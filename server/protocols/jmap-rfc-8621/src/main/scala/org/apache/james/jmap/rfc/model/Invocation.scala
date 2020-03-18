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
package org.apache.james.jmap.rfc.model

import org.apache.james.jmap.rfc.model.Invocation.{Arguments, MethodCallId, MethodName}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

case class Invocation(methodName: MethodName, arguments: Arguments, methodCallId: MethodCallId)

object Invocation {

  case class MethodName(value: String) extends AnyVal

  case class Arguments(value: JsObject) extends AnyVal

  case class MethodCallId(value: String) extends AnyVal

  implicit val methodNameFormat: Format[MethodName] = Json.valueFormat[MethodName]
  implicit val argumentFormat: Format[Arguments] = Json.valueFormat[Arguments]
  implicit val methodCallIdFormat: Format[MethodCallId] = Json.valueFormat[MethodCallId]
  val invocationRead: Reads[Invocation] = (
    (JsPath \ 0).read[MethodName] and
      (JsPath \ 1).read[Arguments] and
      (JsPath \ 2).read[MethodCallId]
    ) (Invocation.apply _)

  val invocationWrite: Writes[Invocation] = (invocation: Invocation) =>
    Json.arr(invocation.methodName, invocation.arguments, invocation.methodCallId)
  implicit val invocationFormat: Format[Invocation] = Format(invocationRead, invocationWrite)
}
