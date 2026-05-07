package com.mahmoud.attendify.security.legal

import com.mahmoud.attendify.attendance.domain.AttendanceResult

/**
 * =============================================================================
 * 🧾 LegalProofGenerator — Non-Repudiation Builder
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 PIPELINE
 * -----------------------------------------------------------------------------
 *
 * Input Data
 *    │
 *    ▼
 * Canonical String
 *    │
 *    ▼
 * SHA-256 Hash
 *    │
 *    ▼
 * Evidence Bundle
 *
 */
object LegalProofGenerator {

    fun generate(
        employeeId: String,
        timestamp: Long,
        result: AttendanceResult
    ): LegalEvidenceBundle {

        val canonical = "$employeeId|$timestamp|${result.javaClass.simpleName}"

        val hash = EvidenceHasher.hash(canonical)

        return LegalEvidenceBundle(
            employeeId = employeeId,
            timestamp = timestamp,
            result = result,
            evidenceHash = hash
        )
    }
}