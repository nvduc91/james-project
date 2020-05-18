package org.apache.james.jmap.http

import java.util.Base64

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.http.BasicAuthenticationTokenManager.PREFIX
import org.apache.james.user.api.UsersRepository

object BasicAuthenticationTokenManager {
  val PREFIX: String = "Basic "
}

class BasicAuthenticationTokenManager @Inject() (val usersRepository: UsersRepository) {
  class UserRequest(val username: String, val password: String)

  private def userRequestExtraction(token: String): UserRequest = {
    val arr: Array[String] = token.split(":")
    if (arr.length != 2) {
      throw new IllegalArgumentException("BasicAuthentication invalid")
    }

    new UserRequest(arr(0), arr(1))
  }

  private def validUsername(username: String): Boolean = usersRepository.contains(Username.of(username))

  private def validPassword(password: String, username: String): Boolean = usersRepository.test(Username.of(username), password)

  private def tokenDecoder(token: String): UserRequest = userRequestExtraction(new String(Base64.getDecoder.decode(token)))

  def isValid(token: String): Boolean = {
    val userRequest: UserRequest = tokenDecoder(token)
    validUsername(userRequest.username) && validPassword(userRequest.password, userRequest.username)
  }

  def retrieveUserNameFromCredential(token: String): Username = Username.of(tokenDecoder(token).username)

  def removeBasicAuthenticationPrefix(token: String): String = token.replace(PREFIX, "")
}
