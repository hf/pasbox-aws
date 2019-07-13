package dev.pasbox.apidevices.common.androidkeystore

import dev.pasbox.apidevices.common.exception.ExceptionWithReasons

class AndroidKeystoreAttestationException(override val reasons: List<String> = emptyList(), cause: Throwable? = null) :
  Exception("Android KeyStore Attestation verification failure, reasons: ${reasons.joinToString(", ")}", cause),
  ExceptionWithReasons
