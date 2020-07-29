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

package org.apache.james.jmap.json

import eu.timepit.refined.auto._
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.JmapRfc8621Configuration.LOCALHOST_URL_PREFIX
import org.apache.james.jmap.model.{ClientId, CreatedIds, Invocation, ResponseObject, ServerId}
import play.api.libs.json.Json

object Fixture {
  val id: Id = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"
  val createdIds: CreatedIds = CreatedIds(Map(ClientId(id) -> ServerId(id)))
  val coreIdentifier: CapabilityIdentifier = "urn:ietf:params:jmap:core"
  val mailIdentifier: CapabilityIdentifier = "urn:ietf:params:jmap:mail"
  val invocation1: Invocation = Invocation(
    methodName = MethodName("Core/echo"),
    arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data")),
    methodCallId = MethodCallId("c1"))
  val invocation2: Invocation = Invocation(
    methodName = MethodName("Core/echo"),
    arguments = Arguments(Json.obj("arg3" -> "arg3data", "arg4" -> "arg4data")),
    methodCallId = MethodCallId("c2")
  )
  val unsupportedInvocation: Invocation = Invocation(
    methodName = MethodName("error"),
    arguments = Arguments(Json.obj("type" -> "Not implemented")),
    methodCallId = MethodCallId("notsupport"))
  val responseObject1: ResponseObject = ResponseObject(ResponseObject.SESSION_STATE, Seq(invocation1))
  val responseObject2: ResponseObject = ResponseObject(ResponseObject.SESSION_STATE, Seq(invocation2))
  val responseObjectWithUnsupportedMethod: ResponseObject = ResponseObject(ResponseObject.SESSION_STATE, Seq(invocation1, unsupportedInvocation))

  val expected_session_object: String = s"""{
                         |  "capabilities" : {
                         |    "urn:ietf:params:jmap:core" : {
                         |      "maxSizeUpload" : 10000000,
                         |      "maxConcurrentUpload" : 4,
                         |      "maxSizeRequest" : 10000000,
                         |      "maxConcurrentRequests" : 4,
                         |      "maxCallsInRequest" : 16,
                         |      "maxObjectsInGet" : 500,
                         |      "maxObjectsInSet" : 500,
                         |      "collationAlgorithms" : [ "i;unicode-casemap" ]
                         |    },
                         |    "urn:ietf:params:jmap:mail" : {
                         |      "maxMailboxesPerEmail" : 10000000,
                         |      "maxMailboxDepth" : null,
                         |      "maxSizeMailboxName" : 200,
                         |      "maxSizeAttachmentsPerEmail" : 20000000,
                         |      "emailQuerySortOptions" : [ "receivedAt", "cc", "from", "to", "subject", "size", "sentAt", "hasKeyword", "uid", "Id" ],
                         |      "mayCreateTopLevelMailbox" : true
                         |    }
                         |  },
                         |  "accounts" : {
                         |    "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401" : {
                         |      "name" : "bob@james.org",
                         |      "isPersonal" : true,
                         |      "isReadOnly" : false,
                         |      "accountCapabilities" : {
                         |        "urn:ietf:params:jmap:core" : {
                         |          "maxSizeUpload" : 10000000,
                         |          "maxConcurrentUpload" : 4,
                         |          "maxSizeRequest" : 10000000,
                         |          "maxConcurrentRequests" : 4,
                         |          "maxCallsInRequest" : 16,
                         |          "maxObjectsInGet" : 500,
                         |          "maxObjectsInSet" : 500,
                         |          "collationAlgorithms" : [ "i;unicode-casemap" ]
                         |        },
                         |        "urn:ietf:params:jmap:mail" : {
                         |          "maxMailboxesPerEmail" : 10000000,
                         |          "maxMailboxDepth" : null,
                         |          "maxSizeMailboxName" : 200,
                         |          "maxSizeAttachmentsPerEmail" : 20000000,
                         |          "emailQuerySortOptions" : [ "receivedAt", "cc", "from", "to", "subject", "size", "sentAt", "hasKeyword", "uid", "Id" ],
                         |          "mayCreateTopLevelMailbox" : true
                         |        }
                         |      }
                         |    }
                         |  },
                         |  "primaryAccounts" : {
                         |    "urn:ietf:params:jmap:core" : "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401",
                         |    "urn:ietf:params:jmap:mail" : "0fe275bf13ff761407c17f64b1dfae2f4b3186feea223d7267b79f873a105401"
                         |  },
                         |  "username" : "bob@james.org",
                         |  "apiUrl" : "${LOCALHOST_URL_PREFIX}/jmap",
                         |  "downloadUrl" : "${LOCALHOST_URL_PREFIX}/download",
                         |  "uploadUrl" : "${LOCALHOST_URL_PREFIX}/upload",
                         |  "eventSourceUrl" : "${LOCALHOST_URL_PREFIX}/eventSource",
                         |  "state" : "000001"
                         |}""".stripMargin
}
