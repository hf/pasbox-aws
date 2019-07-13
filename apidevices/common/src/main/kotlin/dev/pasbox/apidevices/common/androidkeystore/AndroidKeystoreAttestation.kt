package dev.pasbox.apidevices.common.androidkeystore

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.Security
import java.security.cert.*
import java.time.Clock
import java.util.*

class AndroidKeystoreAttestation(val clock: Clock = Clock.systemUTC()) {
  companion object {
    init {
      if (null == Security.getProvider("BC")) {
        Security.addProvider(BouncyCastleProvider())
      }
    }

    val GOOGLE_PLAY_CAS = setOf(TrustAnchor(
      CertificateFactory.getInstance("X.509", "BC")!!
        .run {
          generateCertificate(
            ByteArrayInputStream(
              Base64.getDecoder().decode(
                "" +
                  "MIIFYDCCA0igAwIBAgIJAOj6GWMU0voYMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV" +
                  "BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTYwNTI2MTYyODUyWhcNMjYwNTI0MTYy" +
                  "ODUyWjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B" +
                  "AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS" +
                  "Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7" +
                  "tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj" +
                  "nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq" +
                  "C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ" +
                  "oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O" +
                  "JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg" +
                  "sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi" +
                  "igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M" +
                  "RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E" +
                  "aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um" +
                  "AGMCAwEAAaOBpjCBozAdBgNVHQ4EFgQUNmHhAHyIBQlRi0RsR/8aTMnqTxIwHwYD" +
                  "VR0jBBgwFoAUNmHhAHyIBQlRi0RsR/8aTMnqTxIwDwYDVR0TAQH/BAUwAwEB/zAO" +
                  "BgNVHQ8BAf8EBAMCAYYwQAYDVR0fBDkwNzA1oDOgMYYvaHR0cHM6Ly9hbmRyb2lk" +
                  "Lmdvb2dsZWFwaXMuY29tL2F0dGVzdGF0aW9uL2NybC8wDQYJKoZIhvcNAQELBQAD" +
                  "ggIBACDIw41L3KlXG0aMiS//cqrG+EShHUGo8HNsw30W1kJtjn6UBwRM6jnmiwfB" +
                  "Pb8VA91chb2vssAtX2zbTvqBJ9+LBPGCdw/E53Rbf86qhxKaiAHOjpvAy5Y3m00m" +
                  "qC0w/Zwvju1twb4vhLaJ5NkUJYsUS7rmJKHHBnETLi8GFqiEsqTWpG/6ibYCv7rY" +
                  "DBJDcR9W62BW9jfIoBQcxUCUJouMPH25lLNcDc1ssqvC2v7iUgI9LeoM1sNovqPm" +
                  "QUiG9rHli1vXxzCyaMTjwftkJLkf6724DFhuKug2jITV0QkXvaJWF4nUaHOTNA4u" +
                  "JU9WDvZLI1j83A+/xnAJUucIv/zGJ1AMH2boHqF8CY16LpsYgBt6tKxxWH00XcyD" +
                  "CdW2KlBCeqbQPcsFmWyWugxdcekhYsAWyoSf818NUsZdBWBaR/OukXrNLfkQ79Iy" +
                  "ZohZbvabO/X+MVT3rriAoKc8oE2Uws6DF+60PV7/WIPjNvXySdqspImSN78mflxD" +
                  "qwLqRBYkA3I75qppLGG9rp7UCdRjxMl8ZDBld+7yvHVgt1cVzJx9xnyGCC23Uaic" +
                  "MDSXYrB4I4WHXPGjxhZuCuPBLTdOLU8YRvMYdEvYebWHMpvwGCF6bAx3JBpIeOQ1" +
                  "wDB5y0USicV3YgYGmi+NZfhA4URSh77Yd6uuJOJENRaNVTzk"
              )
            )
          )
        } as X509Certificate,
      null
    ))
  }

  fun verify(certificates: List<Certificate>): AndroidKeyDescription {
    val cert = certificates.first() as X509Certificate

    if (!cert.nonCriticalExtensionOIDs.contains("1.3.6.1.4.1.11129.2.1.17")) {
      throw AndroidKeystoreAttestationException(
        reasons = listOf(
          "bad-keyattestation",
          "not-attestation"
        )
      )
    }

    try {
      val certPath = CertificateFactory.getInstance("X.509", "BC")!!
        .run {
          generateCertPath(certificates)
        }

      CertPathValidator.getInstance("PKIX", "BC")!!
        .run {
          validate(certPath, PKIXParameters(GOOGLE_PLAY_CAS)
            .apply {
              isRevocationEnabled = false
              date = Date.from(clock.instant())
            }
          )
        }
    } catch (e: GeneralSecurityException) {
      throw AndroidKeystoreAttestationException(
        reasons = listOf(
          "bad-keyattestation",
          "not-trusted"
        ), cause = e
      )
    }

    try {
      return ASN1InputStream(cert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")).readObject()
        .let { it ->
          when (it) {
            is DEROctetString -> ASN1InputStream(it.octetStream).readObject()
              .let {
                when (it) {
                  is ASN1Sequence -> AndroidKeyDescription.parseASN1(
                    it
                  )
                  else -> throw AndroidKeystoreAttestationException(
                    reasons = listOf(
                      "bad-ksattestation",
                      "bad-ext"
                    )
                  )
                }
              }

            else -> throw AndroidKeystoreAttestationException(
              reasons = listOf(
                "bad-ksattestation",
                "bad-ext"
              )
            )
          }
        }
    } catch (e: IOException) {
      throw AndroidKeystoreAttestationException(
        reasons = listOf(
          "bad-keyattestation",
          "bad-ext"
        ), cause = e
      )
    }

  }
}
