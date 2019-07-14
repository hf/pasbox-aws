package dev.pasbox.apidevices.androidregister

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dev.pasbox.apidevices.androidregister.dynamodb.dynamo
import dev.pasbox.apidevices.androidregister.dynamodb.updateItem
import dev.pasbox.apidevices.common.androidkeystore.AndroidKeyDescription
import dev.pasbox.apidevices.common.androidkeystore.AndroidKeystoreAttestation
import dev.pasbox.apidevices.common.androidkeystore.AndroidKeystoreAttestationException
import dev.pasbox.apidevices.common.data.*
import dev.pasbox.apidevices.common.hashcash.Hashcash
import dev.pasbox.apidevices.common.safetynet.SafetyNetAttestation
import dev.pasbox.apidevices.common.safetynet.SafetyNetAttestationException
import org.whispersystems.curve25519.Curve25519
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.CreatePlatformEndpointRequest
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.SubscribeRequest
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.Mac

class AndroidRegister(
  val clock: Clock = Clock.systemUTC(),
  val dynamoDB: DynamoDbAsyncClient = DynamoDbAsyncClient.create(),
  val sns: SnsAsyncClient = SnsAsyncClient.create()
) :
  RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  companion object {
    val JSON = Moshi.Builder()
      .add(Base64Adapter())
      .add(TimeAdapter())
      .add(CertificateAdapter())
      .add(JSONWebSignature.Adapter())
      .build()
  }

  override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context) =
    JSON.adapter(Response::class.java).let { responseAdapter ->
      APIGatewayProxyResponseEvent()
        .apply {
          try {
            withBody(
              responseAdapter.toJson(
                handle(input, context)
              )
            )
            withStatusCode(200)
          } catch (e: ResponseException) {
            withBody(
              responseAdapter.toJson(
                e.response
              )
            )
            withStatusCode(e.code)
          } catch (e: Throwable) {
            withBody(
              responseAdapter.toJson(
                Response(
                  error = Response.Error(
                    code = 500,
                    reasons = listOf("not-your-fault"),
                    track = input.requestContext.requestId
                  )
                )
              )
            )
            withStatusCode(500)
          }
        }
    }

  private fun handle(event: APIGatewayProxyRequestEvent, context: Context): Response {
    val now = clock.instant()

    if (event.isBase64Encoded) {
      throw ResponseException(reasons = listOf("body-is-octet-stream"))
    }

    if ("application/json" != event.headers["content-type"]) {
      throw ResponseException(reasons = listOf("body-not-json"))
    }

    val request = try {
      JSON.adapter(Request::class.java)
        .nonNull()
        .failOnUnknown()
        .fromJson(event.body)!!
    } catch (e: JsonDataException) {
      throw ResponseException(
        reasons = listOf("body-bad-json"),
        cause = e,
        track = event.requestContext.requestId
      )
    }

    validateRequest(request)

    verifyHashcash(request)
    verifyDeviceSignature(request)
    verifySafetyNet(request, now)
    verifyDeviceCertificate(request)
      .let { keyDescription ->

        val identityKey = KeyGenerator.getInstance("HmacSHA512", "BC")!!
          .run {
            generateKey()
          }

        val identity = Curve25519.getInstance(Curve25519.BEST)!!
          .run {
            generateKeyPair()
              .let {
                Pair(it.publicKey,
                  Mac.getInstance("HmacSHA512", "BC")!!
                    .run {
                      init(identityKey)
                      update(calculateAgreement(request.identityAgreement, it.privateKey))
                      doFinal()
                    })
              }
          }

        val platformEndpoint = sns.createPlatformEndpoint(
          CreatePlatformEndpointRequest.builder()
            .token(request.token)
            .platformApplicationArn(Config.SNS_PLATFORM_APPLICATION_ANDROID_ARN)
            .build()
        )
          .get()

        val subscription = sns.subscribe(
          SubscribeRequest.builder()
            .endpoint(platformEndpoint.endpointArn())
            .protocol("application")
            .topicArn(Config.SNS_TOPIC_ANDROID_ARN)
            .returnSubscriptionArn(true)
            .build()
        )
          .get()

        listOf(
          sns.publish(
            PublishRequest.builder()
              .messageStructure("json")
              .message(
                JSON.adapter(DeviceRegistrationMessage::class.java)!!
                  .toJson(
                    DeviceRegistrationMessage(
                      FCM = DeviceRegistrationMessage.MessageForFCM(
                        identityAgreement = identity.first,
                        timestamp = now
                      )
                    )
                  )
              )
              .targetArn(platformEndpoint.endpointArn())
              .build()
          ),

          dynamoDB.updateItem("Devices") {
            key("key" dynamo "registration/android/${identity.second.toHex()}")

            set {
              assign(
                "identity" dynamo identity.second,
                "safetyNet" dynamo request.safetyNet.original,
                "deviceCertificate" dynamo request.deviceCertificate,
                "token" dynamo request.token,
                "endpointArn" dynamo platformEndpoint.endpointArn(),
                "subscriptionArn" dynamo subscription.subscriptionArn(),
                "hashcash20" dynamo request.hashcash20,
                "updatedAt" dynamo now
              )

              ifNotExists("createdAt", "createdAt" dynamo now)
            }

            returnAllNew()
          },

          dynamoDB.updateItem("Devices") {
            key("key" dynamo "token/fcm/${request.token}")

            set {
              assign(
                "identity" dynamo identity.second,
                "safetyNet" dynamo request.safetyNet.original,
                "deviceCertificate" dynamo request.deviceCertificate,
                "token" dynamo request.token,
                "endpointArn" dynamo platformEndpoint.endpointArn(),
                "subscriptionArn" dynamo subscription.subscriptionArn(),
                "hashcash20" dynamo request.hashcash20,
                "updatedAt" dynamo now
              )

              ifNotExists("createdAt", "createdAt" dynamo now)
            }

            returnAllNew()
          })
          .forEach { future ->
            future.get()
          }

        return Response(
          result = Response.Result(
            identityKey = identityKey.encoded,
            certificateDescription = keyDescription
          )
        )
      }
  }

  internal fun verifyDeviceSignature(request: Request) {
    val deviceCertificate = request.deviceCertificate.first() as X509Certificate

    if (!Signature.getInstance(deviceCertificate.sigAlgOID, "BC")!!
        .run {
          initVerify(deviceCertificate)

          request.deviceCertificate.forEach { update(it.encoded) }
          update(request.identityAgreement)
          update(request.safetyNet.original.toByteArray())
          update(request.token.toByteArray())

          verify(request.signature)
        }
    ) {
      throw ResponseException(reasons = listOf("bad-signature"))
    }
  }

  internal fun validateRequest(request: Request) {
    if (request.token.length < 16) {
      throw ResponseException(
        reasons = listOf(
          "bad-payload",
          "token-too-short"
        )
      )
    }

    if (request.deviceCertificate.size < 2) {
      throw ResponseException(
        reasons = listOf("bad-payload", "not-enough-certificates")
      )
    }

    if (request.signature.size != 256 / 8) {
      throw ResponseException(
        reasons = listOf("bad-signature", "incorrect-size")
      )
    }

    if (request.identityAgreement.size != 256 / 8) {
      throw ResponseException(
        reasons = listOf("bad-identity-agreement", "incorrect-size")
      )
    }

    if (request.hashcash20 < 0) {
      throw ResponseException(
        reasons = listOf("bad-pow", "negative-counter")
      )
    }
  }

  internal fun verifyHashcash(request: Request) {
    if (!Hashcash(2, 0b1111_0000.toByte())
        .apply {
          request.deviceCertificate.forEach { add(it.encoded) }
          add(request.identityAgreement)
          add(request.safetyNet.original.toByteArray())
          add(request.token.toByteArray())
          add(request.signature)
        }
        .verify(request.hashcash20)
    ) {
      throw ResponseException(reasons = listOf("bad-pow"))
    }
  }

  internal fun verifyDeviceCertificate(request: Request): AndroidKeyDescription {
    return try {
      AndroidKeystoreAttestation().verify(request.deviceCertificate)
        .also { keyDescription ->

          when (keyDescription.keymasterVersion) {
            AndroidKeyDescription.KEYMASTER_V2,
            AndroidKeyDescription.KEYMASTER_V3,
            AndroidKeyDescription.KEYMASTER_V4 -> true
            else -> throw ResponseException(
              reasons = listOf(
                "bad-device-certificate",
                "old-keymaster"
              )
            )
          }

          when (keyDescription.attestationSecurityLevel) {
            AndroidKeyDescription.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
            AndroidKeyDescription.SECURITY_LEVEL_STRONGBOX -> true
            else -> throw ResponseException(
              reasons = listOf(
                "bad-device-certificate",
                "attestation-not-tee",
                "attestation-not-strongbox"
              )
            )
          }

          when (keyDescription.keymasterSecurityLevel) {
            AndroidKeyDescription.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
            AndroidKeyDescription.SECURITY_LEVEL_STRONGBOX -> true
            else -> throw ResponseException(
              reasons = listOf(
                "bad-device-certificate",
                "keymaster-not-tee",
                "keymaster-not-strongbox"
              )
            )
          }

          if (null == keyDescription.teeEnforced.purpose || keyDescription.teeEnforced.purpose!!.size != 1) {
            throw ResponseException(
              reasons = listOf(
                "bad-device-certificate",
                "bad-purpose"
              )
            )
          }

          if (!keyDescription.teeEnforced.purpose!!.contains(AndroidKeyDescription.AuthorizationList.PURPOSE_SIGN)) {
            throw ResponseException(
              reasons = listOf(
                "bad-device-certificate",
                "bad-purpose"
              )
            )
          }

          when (keyDescription.teeEnforced.ecCurve) {
            AndroidKeyDescription.AuthorizationList.EC_CURVE_P_256 -> true
            else -> throw ResponseException(
              reasons = listOf(
                "bad-device-certificate",
                "bad-ec-curve"
              )
            )
          }

          when (keyDescription.teeEnforced.origin) {
            AndroidKeyDescription.AuthorizationList.ORIGIN_GENERATED -> true
            else -> throw ResponseException(
              reasons = listOf(
                "bad-device-certificate",
                "origin-not-generated"
              )
            )
          }

          if (!Arrays.equals(keyDescription.attestationChallenge,
              MessageDigest.getInstance("SHA-256", "BC")!!.run {
                update(request.token.toByteArray())
                digest()
              })
          ) {
            throw ResponseException(
              reasons = listOf(
                "bad-device-certificate",
                "challenge-mismatch"
              )
            )
          }
        }
    } catch (e: AndroidKeystoreAttestationException) {
      throw ResponseException(reasons = e.reasons, cause = e)
    }
  }

  internal fun verifySafetyNet(request: Request, now: Instant) {
    try {
      SafetyNetAttestation().verify(request.safetyNet)
        .let { (_, payload) ->
          if (!payload.basicIntegrity || !payload.ctsProfileMatch) {
            throw ResponseException(
              reasons = listOf(
                "bad-safetynet",
                "incompatible-device"
              )
            )
          }

          if ("me.stojan.pasbox" != payload.apkPackageName) {
            throw ResponseException(
              reasons = listOf(
                "bad-safetynet",
                "unknown-package-name"
              )
            )
          }

          if (String(payload.nonce) != request.token) {
            throw ResponseException(
              reasons = listOf(
                "bad-safetynet",
                "nonce-not-token"
              )
            )
          }

          if (payload.timestampMs.isBefore(now.minus(Duration.ofMinutes(2)))) {
            throw ResponseException(
              reasons = listOf(
                "bad-safetynet",
                "stale-attestation"
              )
            )
          }
        }
    } catch (e: SafetyNetAttestationException) {
      throw ResponseException(reasons = e.reasons, cause = e)
    }
  }
}
