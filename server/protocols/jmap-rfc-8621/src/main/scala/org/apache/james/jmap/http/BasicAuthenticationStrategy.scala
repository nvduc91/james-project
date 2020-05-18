package org.apache.james.jmap.http

import javax.inject.Inject
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SFlux
import reactor.netty.http.server.HttpServerRequest
import scala.compat.java8.StreamConverters._
class BasicAuthenticationStrategy @Inject() (val basicAuthTokenManager: BasicAuthenticationTokenManager,
                                             val mailboxManager: MailboxManager) extends AuthenticationStrategy {

  override def createMailboxSession(httpRequest: HttpServerRequest): Mono[MailboxSession] = {
    SFlux.fromStream(() => authHeaders(httpRequest).toScala[Stream])
      .map(basicAuthToken => basicAuthTokenManager.removeBasicAuthenticationPrefix(basicAuthToken))
      .filter(basicAuthToken => basicAuthTokenManager.isValid(basicAuthToken))
      .map(basicAuthToken => basicAuthTokenManager.retrieveUserNameFromCredential(basicAuthToken))
      .map(username => mailboxManager.createSystemSession(username))
      .singleOrEmpty()
      .asJava()
  }
}
