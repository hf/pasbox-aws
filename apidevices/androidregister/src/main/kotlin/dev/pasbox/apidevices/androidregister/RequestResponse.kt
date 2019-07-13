package dev.pasbox.apidevices.androidregister

import com.squareup.moshi.JsonClass
import dev.pasbox.apidevices.common.androidkeystore.AndroidKeyDescription
import dev.pasbox.apidevices.common.data.JSONWebSignature
import java.security.cert.Certificate

@JsonClass(generateAdapter = true)
data class Request(
  val deviceCertificate: List<Certificate>,
  val identityAgreement: ByteArray,
  val safetyNet: JSONWebSignature,
  val token: String,
  val signature: ByteArray,
  val hashcash20: Int
)

@JsonClass(generateAdapter = true)
data class Response(val result: Result? = null, val error: Error? = null) {

  @JsonClass(generateAdapter = true)
  data class Error(val code: Int, val reasons: List<String> = emptyList(), val track: String? = null)

  @JsonClass(generateAdapter = true)
  data class Result(val identityKey: ByteArray, val certificateDescription: AndroidKeyDescription)
}

class ResponseException(
  val code: Int = 400,
  val reasons: List<String> = emptyList(),
  val track: String? = null,
  cause: Throwable? = null
) : RuntimeException("ResponseException ${code} reasons: ${reasons.joinToString(", ")}", cause) {

  val response: Response
    get() = Response(
      error = Response.Error(code = code, reasons = reasons, track = track)
    )
}