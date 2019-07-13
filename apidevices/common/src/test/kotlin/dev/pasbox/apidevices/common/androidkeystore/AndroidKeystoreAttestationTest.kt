package dev.pasbox.apidevices.common.androidkeystore

import com.squareup.moshi.Moshi
import dev.pasbox.apidevices.common.androidkeystore.AndroidKeyDescription
import dev.pasbox.apidevices.common.data.Base64Adapter
import dev.pasbox.apidevices.common.data.TimeAdapter
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.util.*

class AndroidKeystoreAttestationTest {

  @Test
  fun sampleTest() {

    val key = AndroidKeystoreAttestation().verify(
      CertificateFactory.getInstance("X.509", "BC")!!
        .run {
          listOf(
            "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"
          )
            .map { Base64.getDecoder().decode(it) }
            .map { generateCertificate(ByteArrayInputStream(it)) }
        }
    )

    System.out.println(
      Moshi.Builder()
        .add(Base64Adapter())
        .add(TimeAdapter())
        .build().adapter(AndroidKeyDescription::class.java).toJson(key)
    )
  }

}