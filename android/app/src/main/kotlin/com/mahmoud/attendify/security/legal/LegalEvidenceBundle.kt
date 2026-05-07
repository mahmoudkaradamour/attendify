package com.mahmoud.attendify.security.legal

import com.mahmoud.attendify.attendance.domain.AttendanceResult

/**
 * =============================================================================
 * 🧾 LegalEvidenceBundle — Immutable Legal Evidence Container
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Represents a **forensic-grade immutable record** of an attendance attempt.
 *
 * This bundle encapsulates:
 *
 *   - identity
 *   - time
 *   - decision
 *   - cryptographic linkage
 *
 * -----------------------------------------------------------------------------
 * 📊 STRUCTURE
 * -----------------------------------------------------------------------------
 *
 *   EvidenceBundle:
 *     ├── employeeId
 *     ├── timestamp
 *     ├── result
 *     ├── hash
 *
 * -----------------------------------------------------------------------------
 * 🔐 LEGAL PROPERTY
 * -----------------------------------------------------------------------------
 *
 * ✅ Immutable after creation
 * ✅ Cryptographically verifiable
 * ✅ Suitable for audit and legal validation
 *
 */
data class LegalEvidenceBundle(
    val employeeId: String,
    val timestamp: Long,
    val result: AttendanceResult,
    val evidenceHash: String
)