package com.mahmoud.attendify.forensics

/**
 * ForensicAuditRecord
 *
 * Immutable audit ledger entry.
 * Each record links cryptographically to the previous one.
 */
data class ForensicAuditRecord(
    val index: Long,
    val timestampMillis: Long,
    val snapshotId: String,
    val decision: String,
    val resultHash: ByteArray,
    val previousHash: ByteArray
)