package dev.pasbox.apidevices.androidregister

import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class DeviceRegistrationMessage(
  val FCM: MessageForFCM
) {

  @JsonClass(generateAdapter = true)
  data class MessageForFCM(val identityAgreement: ByteArray, val timestamp: Instant)
}