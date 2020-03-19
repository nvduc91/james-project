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

import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.Size
import eu.timepit.refined.numeric.{Greater, Interval, LessEqual}
import eu.timepit.refined.string.MatchesRegex
import org.apache.james.jmap.rfc.model.CreatedIds.{ClientId, ServerId}
import org.apache.james.jmap.rfc.model.CustomType.ID
import play.api.libs.json._

case class CreatedIds(value: Map[ClientId, ServerId])

object CustomType {
  type ID = String Refined And[
    Size[Interval.Closed[Greater[W.`0`.T], LessEqual[W.`255`.T]]],
    MatchesRegex["^[a-zA-Z0-9-_]*$"]]
}

object CreatedIds {

  implicit val idTypeFormat = new Format[ID] {
    override def reads(json: JsValue) = json.validate[String] map[ID]  (s => Refined.unsafeApply(s))
    override def writes(handle: ID) = JsString(handle.value)
  }

    case class Id(value: ID)

    implicit val idFormat: Format[Id] = Json.valueFormat[Id]

    case class ClientId(value: Id) extends AnyVal

    implicit val clientIdFormat: Format[ClientId] = Json.valueFormat[ClientId]

    case class ServerId(value: Id) extends AnyVal

    implicit val serverIdFormat: Format[ServerId] = Json.valueFormat[ServerId]

    implicit val createdIdsFormat: Format[CreatedIds] = Json.valueFormat[CreatedIds]

    implicit def createdIdsIdWrites(implicit serverIdWriter: Writes[ServerId]): Writes[Map[ClientId, ServerId]] =
      (map: Map[ClientId, ServerId]) => {
        JsObject(map.map { case (k, v) => (k.value.value.value, serverIdWriter.writes(v)) }.toSeq)
      }

    implicit def createdIdsIdRead(implicit serverIdReader: Reads[ServerId]): Reads[Map[ClientId, ServerId]] =
      Reads.mapReads[ClientId, ServerId] { str =>
        Json.fromJson[ClientId](JsString(str))
      }
  }


