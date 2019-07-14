package dev.pasbox.apidevices.androidregister

object Config {
  val SNS_TOPIC_ANDROID_ARN = System.getenv("SNS_TOPIC_ANDROID_ARN")!!
  val SNS_PLATFORM_APPLICATION_ANDROID_ARN = System.getenv("SNS_PLATFORM_APPLICATION_ANDROID_ARN")!!
}
