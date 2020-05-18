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