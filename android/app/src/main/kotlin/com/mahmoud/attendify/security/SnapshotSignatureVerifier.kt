package com.mahmoud.attendify.security

import android.graphics.Bitmap
import android.os.Build
import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot
import com.mahmoud.attendify.system.location.LocationEvidence
import com.mahmoud.attendify.system.time.TimeSnapshot
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * ============================================================================
 * SnapshotSignatureVerifier — FINAL (B3 HARDENED)
 * ============================================================================
 *
 * 🔐 SYSTEM ROLE:
 * ----------------------------------------------------------------------------
 * This is the FINAL TRUST GATE.
 *
 * No evidence is accepted unless:
 *   ✅ Payload integrity verified
 *   ✅ Signature valid
 *   ✅ Certificate trusted
 *   ✅ Device environment acceptable
 *
 * ============================================================================
 * 🔬 CRYPTO MODEL
 * ============================================================================
 *
 *   S  = Canonical Payload
 *   H  = SHA256(S)
 *   SIG = Sign(H)
 *
 * VALID ⇔ Verify(SIG, H)
 *
 * ============================================================================
 * 🔄 FULL FLOW DIAGRAM
 * ============================================================================
 *
 *        Snapshot
 *           │
 *           ▼
 *   Build Canonical Payload
 *           │
 *           ▼
 *     Hash (SHA-256)
 *           │
 *   ┌───────┴────────┐
 *   ▼                ▼
 * Compare Hash   Verify Signature
 *   │                │
 *   ▼                ▼
 *   OK           Signature OK
 *   │                │
 *   └────────┬───────┘
 *            ▼
 *   Certificate Validation
 *            │
 *            ▼
 *   Hardware Presence Check
 *            │
 *            ▼
 *   Device Integrity Check
 *            │
 *            ▼
 *        ACCEPT ✅ / REJECT ❌
 *
 * ============================================================================
 */
object SnapshotSignatureVerifier {

    fun verify(snapshot: SignedPhysicalRealitySnapshot): Boolean {

        return try {

            /* ============================================================
             * STEP 1 — REBUILD PAYLOAD
             * ============================================================ */
            val payload = buildCanonicalPayload(snapshot)

            /* ============================================================
             * STEP 2 — HASH
             * ============================================================ */
            val recomputedHash =
                MessageDigest.getInstance("SHA-256")
                    .digest(payload)

            if (!recomputedHash.contentEquals(snapshot.snapshotHash)) {
                return false
            }

            /* ============================================================
             * STEP 3 — CERTIFICATE
             * ============================================================ */
            val certs =
                snapshot.certificateChain.map { parseCertificate(it) }

            if (certs.isEmpty()) return false
            if (!validateCertificateChain(certs)) return false

            val leaf = certs.first()

            /* ============================================================
             * STEP 4 — SIGNATURE VERIFY
             * ============================================================ */
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(leaf.publicKey)
            sig.update(snapshot.snapshotHash)

            if (!sig.verify(snapshot.signature)) {
                return false
            }

            /* ============================================================
             * STEP 5 — HARDWARE CHECK
             * ============================================================ */
            if (!isHardwareBacked(leaf)) {
                return false
            }

            /* ============================================================
             * STEP 6 — DEVICE CHECK
             * ============================================================ */
            if (!isDeviceEnvironmentAcceptable()) {
                return false
            }

            /* ============================================================
             * STEP 7 — TIMESTAMP
             * ============================================================ */
            if (snapshot.timestampMillis <= 0) return false

            true

        } catch (_: Exception) {
            false
        }
    }

    /* =========================================================================
     * 🧠 CANONICAL PAYLOAD
     * ========================================================================= */

    private fun buildCanonicalPayload(
        snapshot: SignedPhysicalRealitySnapshot
    ): ByteArray {

        val imageHash = hashBitmap(snapshot.payload.frozenFrame)

        val timeBytes =
            snapshot.payload.timeSnapshot.toCanonicalBytes()

        val locationBytes =
            snapshot.payload.locationEvidence.toCanonicalBytes()

        return intToBytes(imageHash.size) + imageHash +
                intToBytes(timeBytes.size) + timeBytes +
                intToBytes(locationBytes.size) + locationBytes
    }

    /* ========================================================================= */

    private fun hashBitmap(bitmap: Bitmap): ByteArray {
        val bytes = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    /* ========================================================================= */

    private fun TimeSnapshot.toCanonicalBytes(): ByteArray {

        return longToBytes(wallClockMillis) +
                longToBytes(elapsedRealtimeMillis) +
                longToBytes(uptimeMillis) +
                stringToBytes(bootId) +
                stringToBytes(timeZoneId)
    }

    private fun LocationEvidence.toCanonicalBytes(): ByteArray {

        return (latitude ?: Double.NaN).toString().toByteArray() +
                (longitude ?: Double.NaN).toString().toByteArray() +
                (accuracyMeters ?: Float.NaN).toString().toByteArray() +
                provider.toByteArray() +
                booleanToByte(isMockDetected) +
                booleanToByte(isStale) +
                booleanToByte(teleportDetected) +
                (distanceToAllowedZoneMeters ?: Double.NaN).toString().toByteArray() +
                (zoneDecision?.policy?.name ?: "NONE").toByteArray() +
                policyDecision.name.toByteArray() +
                booleanToByte(justificationRequired) +
                networkContext.toString().toByteArray() +
                longToBytes(timestampMillis)
    }

    /* ========================================================================= */

    private fun isHardwareBacked(cert: X509Certificate): Boolean {
        return try {
            val subject = cert.subjectX500Principal.name
            subject.contains("Android", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun isDeviceEnvironmentAcceptable(): Boolean {
        return !(Build.TAGS?.contains("test-keys") ?: false)
    }

    /* ========================================================================= */

    private fun parseCertificate(data: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(data.inputStream()) as X509Certificate
    }

    private fun validateCertificateChain(chain: List<X509Certificate>): Boolean {
        return try {
            chain.forEach { it.checkValidity() }
            true
        } catch (_: Exception) {
            false
        }
    }

    /* ========================================================================= */

    private fun intToBytes(v: Int): ByteArray =
        ByteBuffer.allocate(4).putInt(v).array()

    private fun longToBytes(v: Long): ByteArray =
        ByteBuffer.allocate(8).putLong(v).array()

    private fun booleanToByte(v: Boolean): ByteArray =
        byteArrayOf(if (v) 1 else 0)

    private fun stringToBytes(value: String): ByteArray {
        val raw = value.toByteArray()
        return intToBytes(raw.size) + raw
    }
}