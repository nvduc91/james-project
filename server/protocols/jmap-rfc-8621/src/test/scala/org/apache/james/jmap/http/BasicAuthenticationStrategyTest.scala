package org.apache.james.jmap.http

import com.google.common.collect.{ImmutableList, ImmutableSet}
import io.netty.handler.codec.http.HttpHeaders
import org.apache.james.core.Domain
import org.apache.james.dnsservice.api.DNSService
import org.apache.james.domainlist.memory.MemoryDomainList
import org.apache.james.jmap.http.BasicAuthenticationStrategyTest._
import org.apache.james.jmap.http.Fixture.{userExistedToken, userNonExistedToken, username1}
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.extension.PreDeletionHook
import org.apache.james.mailbox.inmemory.MemoryMailboxManagerProvider
import org.apache.james.user.api.UsersRepositoryException
import org.apache.james.user.memory.MemoryUsersRepository
import org.junit.jupiter.api.{Assertions, BeforeEach, Test}
import org.mockito.Mockito
import org.mockito.Mockito.{mock, when}
import reactor.netty.http.server.HttpServerRequest

object BasicAuthenticationStrategyTest {
  private val empty_set: ImmutableSet[PreDeletionHook] = ImmutableSet.of()
  val mockedRequest: HttpServerRequest = mock(classOf[HttpServerRequest])
  val mockedHeaders: HttpHeaders = mock(classOf[HttpHeaders])
  val AUTHORIZATION_HEADERS: String = "Authorization"
  private val dnsService = mock(classOf[DNSService])
  private val domainList = new MemoryDomainList(dnsService)
  domainList.addDomain(Domain.of("james.org"))

  private val usersRepository = MemoryUsersRepository.withoutVirtualHosting(domainList)
  usersRepository.addUser(username1, "password")

  val basicAuthenticationTokenManager: BasicAuthenticationTokenManager = new BasicAuthenticationTokenManager(usersRepository)
  val mailboxManager: MailboxManager = MemoryMailboxManagerProvider.provideMailboxManager(empty_set)
}

class BasicAuthenticationStrategyTest {
  var testee: BasicAuthenticationStrategy = _

  @BeforeEach
  def setup(): Unit = {
    when(mockedRequest.requestHeaders).thenReturn(mockedHeaders)
    testee = new BasicAuthenticationStrategy(basicAuthenticationTokenManager, mailboxManager)
  }

  @Test
  def shouldReturnAnyUsernameWhenValidBasicAuthToken(): Unit = {
    Mockito.when(mockedRequest.requestHeaders().getAll(AUTHORIZATION_HEADERS)).thenReturn(ImmutableList.of(userExistedToken))
    Assertions.assertEquals(testee.createMailboxSession(mockedRequest).block.getUser, username1)
  }

  @Test
  def shouldThrowWhenInvalidBasicAuthToken(): Unit = {
    Mockito.when(mockedHeaders.getAll(AUTHORIZATION_HEADERS)).thenReturn(ImmutableList.of("invalid"))
    Assertions.assertThrows(classOf[IllegalArgumentException], () => testee.createMailboxSession(mockedRequest).block())
  }

  @Test
  def shouldThrowWhenUsernameNotFound(): Unit = {
    Mockito.when(mockedHeaders.getAll(AUTHORIZATION_HEADERS)).thenReturn(ImmutableList.of(userNonExistedToken))
    assert(testee.createMailboxSession(mockedRequest).blockOptional().isEmpty)
  }

  @Test
  def shouldThrowWhenNullHttpServletRequest(): Unit = {
    Assertions.assertThrows(classOf[IllegalArgumentException], () => testee.createMailboxSession(null).block())
  }
}
