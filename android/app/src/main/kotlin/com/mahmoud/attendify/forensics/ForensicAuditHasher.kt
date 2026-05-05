package com.mahmoud.attendify.forensics

import java.security.MessageDigest

/**
 * ForensicAuditHasher
 *
 * Chains audit records together using SHA‑256.
 */
object ForensicAuditHasher {

    fun compute(
        record: ForensicAuditRecord
    ): ByteArray {

        val bytes =
            record.index.toString().toByteArray() +
                    record.timestampMillis.toString().toByteArray() +
                    record.snapshotId.toByteArray() +
                    record.decision.toByteArray() +
                    record.previousHash

        return MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
    }
}