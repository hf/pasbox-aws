package dev.pasbox.apidevices.common.data

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant

class TimeAdapter {
  @FromJson
  fun fromJson(value: Double): Instant = Instant.ofEpochMilli(value.toLong())

  @ToJson
  fun toJson(value: Instant): Double = value.toEpochMilli().toDouble()
}