package dev.pasbox.apidevices.common.hashcash

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security
import kotlin.experimental.and

class Hashcash(val difficultyBytes: Int = 2, val lastBitmask: Byte = 0) {
  companion object {
    init {
      if (null == Security.getProvider("BC")) {
        Security.addProvider(BouncyCastleProvider())
      }
    }
  }

  val arrays = arrayListOf<ByteArray>()

  fun add(array: ByteArray) {
    arrays.add(array)
  }

  fun verify(counter: Int): Boolean =
    MessageDigest.getInstance("SHA-256", "BC")!!.run {
      arrays.forEach { update(it) }
      update(
        byteArrayOf(
          ((counter ushr (3 * 8)) and 0xFF).toByte(),
          ((counter ushr (2 * 8)) and 0xFF).toByte(),
          ((counter ushr (1 * 8)) and 0xFF).toByte(),
          ((counter ushr (0 * 8)) and 0xFF).toByte()
        )
      )

      digest().let { out ->
        var zeroCount = 0
        for (i in 0 until difficultyBytes) {
          if (0.toByte() != out[i]) {
            break
          }
          zeroCount += 1
        }

        difficultyBytes == zeroCount && 0.toByte() == (out[difficultyBytes] and lastBitmask)
      }
    }
}