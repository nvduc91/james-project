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

import eu.timepit.refined.api._
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.Size
import eu.timepit.refined.numeric.{Greater, Interval, LessEqual}
import eu.timepit.refined.string.MatchesRegex
import org.apache.james.jmap.rfc.model.CreatedIds.{ClientId, ServerId}
import play.api.libs.json._
import de.cbley.refined.play.json._

case class CreatedIds(value: Map[ClientId, ServerId])

object CreatedIds {
  type ID = String Refined And[
    Size[Interval.Closed[1, 255]],
    MatchesRegex["^[a-zA-Z0-9-_]*$"]]

  final case class ClientId(value: ID)

  implicit val clientIdFormat: Format[ClientId] = Json.valueFormat[ClientId]

  final case class ServerId(value: ID)

  implicit val serverIdFormat: Format[ServerId] = Json.valueFormat[ServerId]

  implicit val createdIdsFormat: Format[CreatedIds] = Json.valueFormat[CreatedIds]

  implicit def createdIdsIdWrites(implicit serverIdWriter: Writes[ServerId]): Writes[Map[ClientId, ServerId]] =
    (map: Map[ClientId, ServerId]) => {
      JsObject(map.map { case (k, v) => (k.value.value, serverIdWriter.writes(v)) }.toSeq)
    }

  implicit def createdIdsIdRead(implicit serverIdReader: Reads[ServerId]): Reads[Map[ClientId, ServerId]] =
    Reads.mapReads[ClientId, ServerId] { str =>
      Json.fromJson[ClientId](JsString(str))
    }
}


