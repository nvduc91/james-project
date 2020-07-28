package org.apache.james.jmap.model

import java.net.URL

object JmapRfcConfiguration {
  private val DEFAULT_BASE_PATH_STRING = "http://this-url-is-hardcoded.org"
  private val DEFAULT_BASE_PATH = new URL(DEFAULT_BASE_PATH_STRING)
  val DEFAULT_JMAP_RFC_CONFIGURATION = new JmapRfcConfiguration(DEFAULT_BASE_PATH)
}

case class JmapRfcConfiguration(basePath: URL)
