package dev.pasbox.apidevices.common.safetynet

import dev.pasbox.apidevices.common.exception.ExceptionWithReasons

class SafetyNetAttestationException(override val reasons: List<String> = emptyList(), cause: Throwable? = null) :
  Exception("Android KeyStore Attestation verification failure, reasons: ${reasons.joinToString(", ")}", cause),
  ExceptionWithReasons
