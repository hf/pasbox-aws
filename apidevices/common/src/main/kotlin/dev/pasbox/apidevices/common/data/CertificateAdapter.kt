package dev.pasbox.apidevices.common.data

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.*

class CertificateAdapter {
  companion object {
    init {
      if (null == Security.getProvider("BC")) {
        Security.addProvider(BouncyCastleProvider())
      }
    }
  }

  @FromJson
  fun fromJson(value: String): Certificate =
    if (Base64Adapter.isBase64(value) && value.startsWith("MII")) {
      CertificateFactory.getInstance("X.509", "BC")!!
        .run { generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(value))) }
    } else {
      throw JsonDataException("value for certificate must be a base64 string and start with MII")
    }

  @FromJson
  fun fromJson(value: List<String>): List<Certificate> = value.map { fromJson(it) }

  @ToJson
  fun toJson(value: Certificate): String = Base64.getEncoder().encodeToString(value.encoded)

  @ToJson
  fun toJson(value: List<Certificate>): List<String> = value.map { toJson(it) }
}