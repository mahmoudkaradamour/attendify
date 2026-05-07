package com.mahmoud.attendify.forensics.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forensic_audit")
data class ForensicAuditEntity(

    @PrimaryKey
    val index: Long,

    val timestampMillis: Long,
    val snapshotId: String,
    val decision: String,

    val snapshotHash: ByteArray,
    val resultHash: ByteArray,
    val previousHash: ByteArray

) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ForensicAuditEntity) return false

        return index == other.index &&
                timestampMillis == other.timestampMillis &&
                snapshotId == other.snapshotId &&
                decision == other.decision &&
                snapshotHash.contentEquals(other.snapshotHash) &&
                resultHash.contentEquals(other.resultHash) &&
                previousHash.contentEquals(other.previousHash)
    }

    override fun hashCode(): Int {
        var result = index.hashCode()
        result = 31 * result + timestampMillis.hashCode()
        result = 31 * result + snapshotId.hashCode()
        result = 31 * result + decision.hashCode()
        result = 31 * result + snapshotHash.contentHashCode()
        result = 31 * result + resultHash.contentHashCode()
        result = 31 * result + previousHash.contentHashCode()
        return result
    }
}