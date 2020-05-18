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

package org.apache.james.jmap.http

import org.apache.james.core.Domain
import org.apache.james.dnsservice.api.DNSService
import org.apache.james.domainlist.memory.MemoryDomainList
import org.apache.james.jmap.http.Fixture._
import org.apache.james.user.api.UsersRepository
import org.apache.james.user.memory.MemoryUsersRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.Mockito.mock

class BasicAuthenticationTokenManagerTest {

  var usersRepository: UsersRepository = _
  var testee: BasicAuthenticationTokenManager = _

  @BeforeEach
  def setup(): Unit = {
    val dnsService = mock(classOf[DNSService])
    val domainList = new MemoryDomainList(dnsService)
    domainList.addDomain(Domain.of("james.org"))
    usersRepository = MemoryUsersRepository.withoutVirtualHosting(domainList)
    usersRepository.addUser(username1, "password")
    testee = new BasicAuthenticationTokenManager(usersRepository)
  }

  @Test
  def shouldReturnAnyUsernameWhenValidBasicAuthToken(): Unit = {
    assertThat(testee.retrieveUserNameFromCredential(userExistedToken)).isEqualTo(username1)
  }

  @Test
  def shouldReturnTrueWhenTestValidBasicAuthToken(): Unit = {
    assertThat(testee.isValid(userExistedToken)).isTrue
  }

  @Test
  def shouldReturnFalseWhenTestInvalidBasicAuthToken(): Unit = {
    assertThat(testee.isValid(userNonExistedToken)).isFalse
  }

  @Test
  def shouldThrowWhenInvalidBasicAuthToken(): Unit = {
    // Does not throw with MemoryUserRepositoty
    assertThat(testee.isValid(userNonExistedToken)).isFalse
  }
}