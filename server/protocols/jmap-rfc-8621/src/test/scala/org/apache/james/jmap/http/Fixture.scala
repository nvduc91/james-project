package org.apache.james.jmap.http

import java.nio.charset.StandardCharsets
import java.util.Base64

import org.apache.james.core.Username

object Fixture {
  val user1: String = "user1:password"
  val username1: Username = Username.of("user1")
  val userExistedToken: String = Base64.getEncoder.encodeToString(user1.getBytes(StandardCharsets.UTF_8))
  val userNotFound: String = "usernotfound:password"
  val usernameNotFound: Username = Username.of("usernotfound")
  val userNonExistedToken: String = Base64.getEncoder.encodeToString(userNotFound.getBytes(StandardCharsets.UTF_8))
}
