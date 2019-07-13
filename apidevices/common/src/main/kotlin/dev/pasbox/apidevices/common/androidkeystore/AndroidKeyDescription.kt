package dev.pasbox.apidevices.common.androidkeystore

import com.squareup.moshi.JsonClass
import org.bouncycastle.asn1.*
import java.time.Instant

@JsonClass(generateAdapter = true)
data class AndroidKeyDescription(
  val attestationVersion: Int,
  val attestationSecurityLevel: Int,
  val keymasterVersion: Int,
  val keymasterSecurityLevel: Int,
  val attestationChallenge: ByteArray,
  val uniqueId: ByteArray,
  val softwareEnforced: AuthorizationList,
  val teeEnforced: AuthorizationList
) {

  companion object {
    const val KEYMASTER_V2 = 2
    const val KEYMASTER_V3 = 3
    const val KEYMASTER_V4 = 4

    const val SECURITY_LEVEL_TRUSTED_ENVIRONMENT = 1
    const val SECURITY_LEVEL_STRONGBOX = 2

    fun parseASN1(sequence: ASN1Sequence) =
      AndroidKeyDescription(
        attestationVersion = (sequence.getObjectAt(0) as ASN1Integer).value.toInt(),
        attestationSecurityLevel = (sequence.getObjectAt(1) as ASN1Enumerated).value.toInt(),
        keymasterVersion = (sequence.getObjectAt(2) as ASN1Integer).value.toInt(),
        keymasterSecurityLevel = (sequence.getObjectAt(3) as ASN1Enumerated).value.toInt(),
        attestationChallenge = (sequence.getObjectAt(4) as ASN1OctetString).octets,
        uniqueId = (sequence.getObjectAt(5) as ASN1OctetString).octets,
        softwareEnforced = AuthorizationList.parseASN1(
          sequence.getObjectAt(6) as ASN1Sequence
        ),
        teeEnforced = AuthorizationList.parseASN1(
          sequence.getObjectAt(7) as ASN1Sequence
        )
      )
  }

  @JsonClass(generateAdapter = true)
  data class AuthorizationList(
    val purpose: List<Int>?,
    val algorithm: Int?,
    val keySize: Int?,
    val digest: List<Int>?,
    val padding: List<Int>?,
    val ecCurve: Int?,
    val activeDateTime: Instant?,
    val originationExpireDateTime: Instant?,
    val usageExpireDateTime: Instant?,
    val noAuthRequired: Boolean?,
    val userAuthType: Int?,
    val authTimeout: Int?,
    val allowWhileOnBody: Boolean?,
    val trustedUserPresenceRequired: Boolean?,
    val trustedConfirmationRequired: Boolean?,
    val unlockedDeviceRequired: Boolean?,
    val allApplications: Boolean?,
    val applicationId: ByteArray?,
    val creationDateTime: Instant?,
    val origin: Int?,
    val rootOfTrust: RootOfTrust?,
    val osVersion: Int?,
    val osPatchLevel: Int?,
    val attestationApplicationId: ByteArray?,
    val attestationIdBrand: ByteArray?,
    val attestationIdDevice: ByteArray?,
    val attestationIdProduct: ByteArray?,
    val attestationIdSerial: ByteArray?,
    val attestationIdImei: ByteArray?,
    val attestationIdMeid: ByteArray?,
    val attestationIdManufacturer: ByteArray?,
    val attestationIdModel: ByteArray?
  ) {
    companion object {
      const val PURPOSE_SIGN = 2
      const val PURPOSE_VERIFY = 3

      const val EC_CURVE_P_256 = 1

      const val ORIGIN_GENERATED = 0

      const val DIGEST_SHA2_256 = 4

      val TAG_ARRAY = intArrayOf(
        1, 2, 3, 5, 6, 10,
        200,
        303,
        400, 401, 402,
        503, 504, 506, 507, 508, 509,
        600,
        701, 702, 704, 705, 706, 709, 710, 711, 712, 713, 714, 715, 716, 717, 718, 719
      )

      fun parseASN1(sequence: ASN1Sequence): AuthorizationList {
        val tags: Array<ASN1Primitive?> = Array<ASN1Primitive?>(36) { null }
          .also { array ->
            sequence.forEach {
              when (it) {
                is ASN1TaggedObject -> {
                  if (it.isExplicit) {
                    TAG_ARRAY.binarySearch(it.tagNo).let { tagIdx ->
                      if (tagIdx > -1) {
                        array[tagIdx] = it.`object`
                      }
                    }
                  }
                }
              }
            }
          }

        fun get(tag: Int): ASN1Primitive? = TAG_ARRAY.binarySearch(tag).let { tagIdx ->
          if (tagIdx > -1) {
            tags[tagIdx]
          } else {
            null
          }
        }

        fun getInt(tag: Int): Int? = get(tag)?.let { (it as ASN1Integer).value.toInt() }

        fun getIntSet(tag: Int): List<Int>? =
          get(tag)?.let { (it as ASN1Set).map { (it as ASN1Integer).value.toInt() } }

        fun getInstant(tag: Int): Instant? = get(tag)?.let { Instant.ofEpochMilli((it as ASN1Integer).value.toLong()) }

        fun getBytes(tag: Int): ByteArray? = get(tag)?.let { (it as ASN1OctetString).octets }

        fun getRoT(tag: Int): RootOfTrust? = get(tag)?.let {
          RootOfTrust.parseASN1(
            it as ASN1Sequence
          )
        }

        fun getNull(tag: Int): Boolean? = get(tag)?.let { it as ASN1Null; true }

        return AuthorizationList(
          purpose = getIntSet(1),
          algorithm = getInt(2),
          keySize = getInt(3),
          digest = getIntSet(5),
          padding = getIntSet(6),
          ecCurve = getInt(10),
          activeDateTime = getInstant(400),
          originationExpireDateTime = getInstant(401),
          usageExpireDateTime = getInstant(402),
          noAuthRequired = getNull(503),
          userAuthType = getInt(504),
          authTimeout = getInt(505),
          allowWhileOnBody = getNull(506),
          trustedUserPresenceRequired = getNull(507),
          trustedConfirmationRequired = getNull(508),
          unlockedDeviceRequired = getNull(509),
          allApplications = getNull(600),
          applicationId = getBytes(601),
          creationDateTime = getInstant(701),
          origin = getInt(702),
          rootOfTrust = getRoT(704),
          osVersion = getInt(705),
          osPatchLevel = getInt(706),
          attestationApplicationId = getBytes(709),
          attestationIdBrand = getBytes(710),
          attestationIdDevice = getBytes(711),
          attestationIdProduct = getBytes(712),
          attestationIdSerial = getBytes(713),
          attestationIdImei = getBytes(714),
          attestationIdMeid = getBytes(715),
          attestationIdManufacturer = getBytes(716),
          attestationIdModel = getBytes(717)
        )
      }

    }
  }

  @JsonClass(generateAdapter = true)
  data class RootOfTrust(
    val verifiedBootKey: ByteArray?,
    val deviceLocked: Boolean,
    val verifiedBootState: Int,
    val verifiedBootHash: ByteArray?
  ) {
    companion object {
      fun parseASN1(sequence: ASN1Sequence): RootOfTrust =
        RootOfTrust(
          verifiedBootKey = (sequence.getObjectAt(0) as ASN1OctetString).octets,
          deviceLocked = (sequence.getObjectAt(1) as ASN1Boolean).isTrue,
          verifiedBootState = (sequence.getObjectAt(2) as ASN1Enumerated).value.toInt(),
          verifiedBootHash = (sequence.getObjectAt(3) as ASN1OctetString).octets
        )
    }
  }
}
