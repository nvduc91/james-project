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

package org.apache.james.jmap.mail

import com.google.common.hash.Hashing
import org.apache.james.jmap.model.AccountId
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause
import org.apache.james.mailbox.model.{MailboxId, MessageId, SearchQuery}

case class EmailQueryRequest(accountId: AccountId, inMailbox: Option[MailboxId], inMailboxOtherThan: Option[Seq[MailboxId]], comparator: Option[Set[Comparator]])

case class Position(value: Int) extends AnyVal
object Position{
  val zero: Position = Position(0)
}
case class Limit(value: Long) extends AnyVal
object Limit {
  val default: Limit = Limit(256L)
}
case class QueryState(value: String) extends AnyVal

sealed trait SortProperty {
  def toSortClause: SortClause
}
case object ReceivedAtSortProperty extends SortProperty {
  override def toSortClause: SortClause = SortClause.Arrival
}
case object SentAtSortProperty extends SortProperty {
  override def toSortClause: SortClause = SortClause.SentDate
}

object IsAscending {
  val DESCENDING: IsAscending = IsAscending(false)
}
case class IsAscending(sortByASC: Boolean) extends AnyVal {
  def toSortOrder: SearchQuery.Sort.Order = if (sortByASC) SearchQuery.Sort.Order.NATURAL else SearchQuery.Sort.Order.REVERSE
}

object Comparator {
  val default: Comparator = Comparator(ReceivedAtSortProperty, Some(IsAscending.DESCENDING), None)
}

case class Collation(value: String) extends AnyVal

case class Comparator(property: SortProperty,
                      isAscending: Option[IsAscending],
                      collation: Option[Collation]) {
  def toSort: SearchQuery.Sort = new SearchQuery.Sort(property.toSortClause, isAscending.getOrElse(IsAscending.DESCENDING).toSortOrder)
}

object QueryState {
  def forIds(ids: Seq[MessageId]): QueryState = QueryState(
    Hashing.murmur3_32()
      .hashUnencodedChars(ids.map(_.serialize()).mkString(" "))
      .toString)
}

object IsCalculateChanges {
  val CANT: IsCalculateChanges = IsCalculateChanges(false)
}

case class IsCalculateChanges(value: Boolean) extends AnyVal

case class EmailQueryResponse(accountId: AccountId,
                              queryState: QueryState,
                              canCalculateChanges: IsCalculateChanges,
                              ids: Seq[MessageId],
                              position: Position,
                              sort: Option[List[Comparator]],
                              limit: Option[Limit])
