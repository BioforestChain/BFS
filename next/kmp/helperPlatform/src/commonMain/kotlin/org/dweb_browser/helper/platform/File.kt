package org.dweb_browser.helper.platform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MultiPartFileEncode {
  @SerialName("utf8")
  UTF8,

  @SerialName("base64")
  BASE64
}

@Serializable
data class MultiPartFile(
  val name: String,
  val size: Long,
  val type: String,
  val data: String,
  val encode: MultiPartFileEncode = MultiPartFileEncode.UTF8
)