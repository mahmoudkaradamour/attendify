# 🛡️ Attendify — Military‑Grade Hardening Checklist
## Zero‑Trust, Forensic‑Grade, Hardware‑Anchored Attendance System

---

## 📌 Purpose of This Document

This document defines a **complete execution-level hardening plan** for a high-assurance attendance system.

The system is designed to achieve:

- Tamper-proof biometric attendance
- Forensic-grade evidence integrity
- Hardware-rooted trust guarantees
- Zero-trust interaction boundaries
- Operational resilience under adverse conditions

Each item in this checklist is mandatory and addresses:
- A potential exploit vector
- A failure mode under real-world constraints
- A forensic or legal integrity gap

---

# 🧩 SECTION A — Physical Reality Integrity

## Objective
Ensure that **image, time, and location represent the same physical moment**, not loosely combined values.

---

### ✅ A1 — Reverse Capture Sequencing

**Action**
- Request a **fresh GPS location fix** first (no cached values)
- Wait for an actual location callback
- Inside the callback:
  - Capture camera frame immediately
  - Capture system time immediately
  - Construct snapshot atomically

**Result**
- All components belong to the same physical instant

**Risk Eliminated**
- Time-of-check / Time-of-use mismatch (TOCTOU)
- Location delay exploitation

---

### ✅ A2 — Canonical Serialization of Evidence

**Action**
- Completely eliminate any use of `toString()` for hashing
- Implement a **deterministic serialization format** (CBOR / Protobuf)
- Include:
  - Deterministic hash of image pixel bytes
  - Timestamp
  - Location evidence

**Result**
- Identical inputs always produce identical hashes

**Risk Eliminated**
- Image substitution without invalidating signature
- Non-deterministic hashing behavior

---

### ✅ A3 — Hardware-Backed Signature

**Action**
- Compute SHA‑256 over canonical payload
- Sign hash using Android Keystore
- Use StrongBox if available

**Result**
- Signature is bound to device-secured key

**Risk Eliminated**
- Forged or replayed snapshots

---

# 🔐 SECTION B — Evidence Authenticity

## Objective
Ensure only **verified and authentic evidence** enters the system.

---

### ✅ B1 — Mandatory Signature Verification

**Action**
- Verify signature before any transformation or storage
- Reject entire flow if verification fails
- Log the failed attempt

**Result**
- Only verified data can propagate

**Risk Eliminated**
- Injection of forged evidence

---

### ✅ B2 — Hardware Attestation Verification

**Action**
- Validate certificate chain
- Check hardware-backed indicators (StrongBox / TEE)
- Record security level

**Result**
- Evidence includes its own trust classification

**Risk Eliminated**
- Software-only key spoofing

---

# 🔄 SECTION C — Process Survivability

## Objective
Ensure that no operation can disappear silently due to process termination.

---

### ✅ C1 — Pre-Logging (Intent Recording)

**Action**
- Before any operation:
  - Store event as `INITIATED`
  - Include timestamp and session identifier

**Result**
- Every attempt is recorded

**Risk Eliminated**
- Silent probing attempts

---

### ✅ C2 — Abort Detection

**Action**
- On application startup:
  - Detect incomplete `INITIATED` events
  - Convert them to `SUSPICIOUS_ABORT`

**Result**
- Process kills are traceable

**Risk Eliminated**
- Kill-before-failure bypass

---

### ✅ C3 — ACID-Like Persistence

**Action**
- Implement write-ahead logging
- Ensure operations are idempotent

**Result**
- No inconsistent system state

**Risk Eliminated**
- Partial commits

---

# 🧾 SECTION D — Forensic Ledger Integrity

## Objective
Create an immutable and verifiable audit trail.

---

### ✅ D1 — Persistent Encrypted Storage

**Action**
- Store records in encrypted storage (Room / encrypted file)
- Persist immediately on append

**Result**
- Audit trail survives restarts

**Risk Eliminated**
- Volatile or lost audit state

---

### ✅ D2 — Cryptographic Binding to Evidence

**Action**
- Each record hash must include:
  - Previous hash
  - Hash of normalized evidence

**Result**
- Ledger is cryptographically anchored

**Risk Eliminated**
- Detached or manipulable audit records

---

### ✅ D3 — Server-Anchored Genesis Hash

**Action**
- On initialization:
  - Fetch last known hash from server
- Use it as chain base

**Result**
- Continuity across reinstalls or wipes

**Risk Eliminated**
- Broken hash chains

---

# 🔁 — Persistent Replay Guard# 🔁 SECTION E — Replay Protection

**Action**
- Store used snapshot identifiers securely
- Apply retention and cleanup policy

**Result**
- Snapshots are strictly single-use

**Risk Eliminated**
- Replay attacks after restart

---

# ⚙️ SECTION F — Hardware Safety

## Objective
Prevent corruption or interruption of hardware-backed operations.

---

### ✅ F1 — Execution Phase Separation

**Action**
- Split into:
  - Decision phase (cancellable)
  - Evidence finalization phase (non-cancellable)

---

### ✅ F2 — Non-Cancellable Finalization Block

```kotlin
withContext(NonCancellable) {
    sign()
    persist()
}

## Objective
Prevent reuse of previously valid submissions.

---

