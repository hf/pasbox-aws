package dev.pasbox.apidevices.common.data

val HEX = charArrayOf(
  '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
)

fun ByteArray.toHex() = StringBuilder(size * 2)
  .also { builder ->
    this.forEach {
      builder.append(HEX[(it.toInt() and 0xF0) ushr 4])
      builder.append(HEX[it.toInt() and 0x0F])
    }
  }
  .toString()