package dev.pasbox.apidevices.common.data

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import java.util.*

data class JSONWebSignature(
  val original: String,
  val header: String,
  val payload: String,
  val signature: ByteArray,
  val signatureOver: ByteArray
) {
  class Adapter {
    @FromJson
    fun fromJson(value: String) =
      if (value.matches(Regex("^[a-z0-9_-]+[.][a-z0-9_-]+[.][a-z0-9_-]+$", RegexOption.IGNORE_CASE))) {
        value.split('.')
          .map {
            if (!Base64Adapter.isBase64URL(it)) {
              throw JsonDataException("bad base64-url in JWS part")
            }

            Base64.getUrlDecoder().decode(it)
          }.let { results ->
            JSONWebSignature(
              original = value,
              header = String(results[0]),
              payload = String(results[1]),
              signature = results[2],
              signatureOver = value.substring(0, value.lastIndexOf('.')).toByteArray()
            )
          }
      } else {
        throw JsonDataException("JWS not in proper format")
      }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as JSONWebSignature

    if (original != other.original) return false

    return true
  }

  override fun hashCode(): Int {
    return original.hashCode()
  }
}