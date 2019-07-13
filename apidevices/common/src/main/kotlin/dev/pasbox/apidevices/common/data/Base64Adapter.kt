package dev.pasbox.apidevices.common.data

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import java.util.*

class Base64Adapter {
  companion object {
    fun isBase64(value: String) =
      value.matches(Regex("^([a-z0-9/+]{4})*(\$|[a-z0-9/+]{3}=\$|[a-z0-9/+]{2}==\$)\$", RegexOption.IGNORE_CASE))

    fun isBase64URL(value: String) = value.matches(
      Regex("^([a-z0-9_-]{4})*(\$|[a-z0-9_-]{3}=?\$|[a-z0-9_-]{2}(==)?\$)\$", RegexOption.IGNORE_CASE)
    )
  }

  @ToJson
  fun toJson(value: ByteArray?): String? = value?.let { Base64.getEncoder().encodeToString(it) }

  @FromJson
  fun fromJson(value: String?): ByteArray? = value?.let {
    if (isBase64(it)) {
      Base64.getDecoder().decode(it)
    } else {
      throw JsonDataException("value is not a base64 string")
    }
  }
}