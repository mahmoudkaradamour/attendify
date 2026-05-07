package com.mahmoud.attendify.security.legal

import java.security.MessageDigest

/**
 * =============================================================================
 * 🔐 EvidenceHasher — Cryptographic Hash Generator
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 MODEL
 * -----------------------------------------------------------------------------
 *
 * H(data) → SHA-256 hash
 *
 * -----------------------------------------------------------------------------
 * 🔐 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * ✅ Detect tampering
 * ✅ Create immutable fingerprint
 *
 */
object EvidenceHasher {

    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())

        return bytes.joinToString("") { "%02x".format(it) }
    }
}