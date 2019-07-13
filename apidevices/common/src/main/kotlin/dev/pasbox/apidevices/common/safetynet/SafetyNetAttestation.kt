package dev.pasbox.apidevices.common.safetynet

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.pasbox.apidevices.common.data.Base64Adapter
import dev.pasbox.apidevices.common.data.CertificateAdapter
import dev.pasbox.apidevices.common.data.JSONWebSignature
import dev.pasbox.apidevices.common.data.TimeAdapter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.GeneralSecurityException
import java.security.Security
import java.security.Signature
import java.security.cert.*
import java.time.Instant
import java.util.*


class SafetyNetAttestation {
  companion object {
    init {
      if (null == Security.getProvider("BC")) {
        Security.addProvider(BouncyCastleProvider())
      }
    }

    val JSON = Moshi.Builder()
      .add(Base64Adapter())
      .add(CertificateAdapter())
      .add(JSONWebSignature.Adapter())
      .add(TimeAdapter())
      .build()

    val GOOGLE_CAS = setOf(TrustAnchor(
      CertificateFactory.getInstance("X.509", "BC")!!
        .run {
          generateCertificate(
            ByteArrayInputStream(
              Base64.getDecoder().decode(
                "" +
                  "MIIDujCCAqKgAwIBAgILBAAAAAABD4Ym5g0wDQYJKoZIhvcNAQEFBQAwTDEgMB4G" +
                  "A1UECxMXR2xvYmFsU2lnbiBSb290IENBIC0gUjIxEzARBgNVBAoTCkdsb2JhbFNp" +
                  "Z24xEzARBgNVBAMTCkdsb2JhbFNpZ24wHhcNMDYxMjE1MDgwMDAwWhcNMjExMjE1" +
                  "MDgwMDAwWjBMMSAwHgYDVQQLExdHbG9iYWxTaWduIFJvb3QgQ0EgLSBSMjETMBEG" +
                  "A1UEChMKR2xvYmFsU2lnbjETMBEGA1UEAxMKR2xvYmFsU2lnbjCCASIwDQYJKoZI" +
                  "hvcNAQEBBQADggEPADCCAQoCggEBAKbPJA6+Lm8omUVCxKs+IVSbC9N/hHD6ErPL" +
                  "v4dfxn+G07IwXNb9rfF73OX4YJYJkhD10FPe+3t+c4isUoh7SqbKSaZeqKeMWhG8" +
                  "eoLrvozps6yWJQeXSpkqBy+0Hne/ig+1AnwblrjFuTosvNYSuetZfeLQBoZfXklq" +
                  "tTleiDTsvHgMCJiEbKjNS7SgfQx5TfC4LcshytVsW33hoCmEofnTlEnLJGKRILzd" +
                  "C9XZzPnqJworc5HGnRusyMvo4KD0L5CLTfuwNhv2GXqF4G3yYROIXJ/gkwpRl4pa" +
                  "zq+r1feqCapgvdzZX99yqWATXgAByUr6P6TqBwMhAo6CygPCm48CAwEAAaOBnDCB" +
                  "mTAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUm+IH" +
                  "V2ccHsBqBt5ZtJot39wZhi4wNgYDVR0fBC8wLTAroCmgJ4YlaHR0cDovL2NybC5n" +
                  "bG9iYWxzaWduLm5ldC9yb290LXIyLmNybDAfBgNVHSMEGDAWgBSb4gdXZxwewGoG" +
                  "3lm0mi3f3BmGLjANBgkqhkiG9w0BAQUFAAOCAQEAmYFThxxol4aR7OBKuEQLq4Gs" +
                  "J0/WwbgcQ3izDJr86iw8bmEbTUsp9Z8FHSbBuOmDAGJFtqkIk7mpM0sYmsL4h4hO" +
                  "291xNBrBVNpGP+DTKqttVCL1OmLNIG+6KYnX3ZHu01yiPqFbQfXf5WRDLenVOavS" +
                  "ot+3i9DAgBkcRcAtjOj4LaR0VknFBbVPFd5uRHg5h6h+u/N5GJG79G+dwfCMNYxd" +
                  "AfvDbbnvRG15RjF+Cv6pgsH/76tuIMRQyV+dTZsXjAzlAcmgQWpzU/qlULRuJQ/7" +
                  "TBj0/VLZjmmx6BEP3ojY+x1J96relc8geMJgEtslQIxq/H5COEBkEveegeGTLg=="
              )
            )
          )
        }
        as X509Certificate,
      null))
  }

  @JsonClass(generateAdapter = true)
  data class Header(val alg: String, val x5c: List<Certificate>)

  @JsonClass(generateAdapter = true)
  data class Payload(
    val timestampMs: Instant,
    val nonce: ByteArray,
    val apkPackageName: String,
    val apkDigestSha256: String,
    val apkCertificateDigestSha256: List<String>,
    val ctsProfileMatch: Boolean,
    val basicIntegrity: Boolean,
    val error: String?,
    val advice: String?
  )

  fun verify(safetyNet: JSONWebSignature): Pair<Header, Payload> {
    val header = JSON.adapter(Header::class.java)
      .failOnUnknown()
      .nonNull()
      .fromJson(safetyNet.header)!!

    val payload = JSON.adapter(Payload::class.java)
      .failOnUnknown()
      .nonNull()
      .fromJson(safetyNet.payload)!!

    if ("RS256" != header.alg) {
      throw SafetyNetAttestationException(
        reasons = listOf(
          "bad-jws",
          "jws-header-alg"
        )
      )
    }

    if (header.x5c.size < 2) {
      throw SafetyNetAttestationException(
        reasons = listOf(
          "bad-jws",
          "jws-header-x5c"
        )
      )
    }

    val androidAttest = header.x5c.first() as X509Certificate

    if (androidAttest.subjectAlternativeNames.size != 1 || androidAttest.subjectAlternativeNames.first().size != 2 || "attest.android.com" != androidAttest.subjectAlternativeNames.first().last()) {
      throw SafetyNetAttestationException(
        reasons = listOf(
          "bad-jws",
          "jws-header-x5c-san"
        )
      )
    }

    try {
      val validSignature = Signature.getInstance(androidAttest.sigAlgOID, "BC")!!
        .run {
          initVerify(androidAttest)
          update(safetyNet.signatureOver)
          verify(safetyNet.signature)
        }

      if (!validSignature) {
        throw SafetyNetAttestationException(
          reasons = listOf(
            "bad-jws",
            "jws-signature"
          )
        )
      }
    } catch (e: GeneralSecurityException) {
      throw SafetyNetAttestationException(
        reasons = listOf(
          "bad-jws",
          "jws-signature"
        ), cause = e
      )
    }

    try {
      val certPath = CertificateFactory.getInstance("X.509", "BC")!!
        .run {
          generateCertPath(header.x5c)
        }

      CertPathValidator.getInstance("PKIX", "BC")!!
        .apply {
          validate(certPath, PKIXParameters(GOOGLE_CAS)
            .apply {
              isRevocationEnabled = false
              date = Date.from(payload.timestampMs)
            }
          )
        }
    } catch (e: GeneralSecurityException) {
      throw SafetyNetAttestationException(
        reasons = listOf(
          "bad-jws",
          "jws-header-no-trust"
        ), cause = e
      )
    }

    return Pair(header, payload)
  }
}
