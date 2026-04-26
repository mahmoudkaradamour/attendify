package com.mahmoud.attendify.system.location

sealed class LocationIntegrityResult {

    data class Allowed(
        val evidence: LocationEvidence
    ) : LocationIntegrityResult()

    data class Blocked(
        val reason: String,
        val evidence: LocationEvidence? = null
    ) : LocationIntegrityResult()
}