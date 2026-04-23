# Attendify – Secure Face-Based Attendance System

Attendify is an **enterprise-grade, security-aware face attendance system**
built with **Android (Kotlin)** and **TensorFlow Lite**.

It is designed to replace traditional attendance methods
(manual signatures, cards, PINs) with a **defensive, auditable,
and production-ready biometric pipeline**.

> ⚠️ Attendify is designed for sensitive environments.
> Security, determinism, and auditability are treated as first‑class concerns.

---

## ✨ Key Capabilities

- ✅ **Face Anti‑Spoofing (FAS)**
  - MiniFASNet V1SE (Lightweight & fast)
  - MiniFASNet V2 (Default, balanced accuracy)
  - FastFASNet V3 (High‑security mode)

- ✅ **Clean Architecture**
  - Strict separation between hardware, logic, and policy
  - Template Method Pattern enforcement
  - Policy‑driven model selection

- ✅ **Security‑First Design**
  - Fail‑Secure behavior (no silent downgrade)
  - Native Core as the single source of truth
  - Deterministic, versioned model behavior

- ✅ **Real‑Time Performance**
  - Zero heap allocation during camera streaming
  - Stable FPS under continuous operation
  - Controlled thermal behavior on low‑end devices

---

## 🧠 High‑Level Architecture

Camera / Hardware Layer
↓
Image Quality Checks
↓
Face Detection
↓
Expanded Face Crop
↓
Active / Passive Liveness
↓
Face Anti‑Spoofing (FAS)
↓
Face Matching
↓
Signed Attendance Decision

### Architectural Principles

- **Native Core decides – UI displays**
- **Models do not decide – Base classes decide**
- **Policies select models – models own thresholds**

All ML decision logic is centralized and protected.

---

## 🏛️ Core Design Patterns

### ✅ Template Method Pattern
- Core inference pipeline is `final`
- Subclasses can only implement preprocessing
- Prevents security and logic drift

### ✅ Single Source of Truth
- Softmax
- Thresholding
- Decision logic
- Interpreter lifecycle

All centralized inside the Native Core.

---

## 🛡️ Security Model

### Fail‑Secure by Design
- If a required security model is unavailable → attendance is **blocked**
- No fallback to weaker models

### Anti‑Spoofing Hardening
- Expanded face crop preserves background context
- Strong resistance to:
  - Printed photo attacks
  - Screen replay attacks

### Numerical Safety
- Stable Softmax (overflow / underflow safe)
- L2 Normalization
- NaN / Infinity guarded

> The Native Core is intentionally designed as a **defensive stronghold**.

---

## 📦 Model Management Policy

- ML models are **bundled with the application binary**
- No automatic OTA model updates

### Rationale
- Attendance has legal and administrative implications
- Reproducibility and auditability are mandatory
- Model version = behavioral version

> OTA model updates are intentionally **deferred by design**
> and treated as a future architectural decision if the operational context changes.

---

## 🚀 Runtime Performance Characteristics

- ✔ Zero heap allocation during inference
- ✔ Reusable buffers (ByteBuffer & pixel arrays)
- ✔ Native resources released deterministically
- ✔ Graceful slowdown (thermal throttling) under long sessions
- ✔ No Out‑Of‑Memory crashes under sustained usage

---

## 🔄 Flutter ↔ Native Communication

- Native Core streams state via **EventChannels**
- Flutter is **display‑only**
- No business or security logic in Flutter

Example state stream:
- `CAMERA_READY`
- `FACE_TOO_FAR`
- `HOLD_STILL`
- `BLINK_NOW`
- `PROCESSING`
- `SUCCESS`
- `FAILED / SPOOF`

> Zero‑Trust rule: **Flutter is never trusted with decisions**.

---

## 🔐 Backend Trust Model (Recommended)

- Native Core produces attendance result
- Result is **cryptographically signed**
- Backend accepts **only signed native payloads**
- Prevents replay and UI‑level tampering

---

## 🧪 Testing & Validation

- Static image test harness for FAS models
- Deterministic inference results
- Field‑testing oriented design

### Recommended Pre‑Deployment Tests
- Low‑light environments
- Continuous camera operation (≥ 30 minutes)
- Users wearing glasses or masks
- Strong backlight scenarios

---

## ✅ Production Readiness Status

- ✔ Architecture review: **PASSED**
- ✔ Security audit: **PASSED**
- ✔ Memory & performance review: **PASSED**
- ✔ Silent downgrade risks: **ELIMINATED**

**Current Status:** ✅ **Production‑Ready**

---

## 📌 Guidelines for Future Contributors

- Do not modify core inference logic without review
- All new FAS models must:
  - Extend `BaseFASModel`
  - Implement preprocessing only
- Any weakening of security guarantees
  is considered a **breaking change**

---

## 📄 License & Usage

This repository is intended for controlled,
enterprise, or internal deployment.

Licensing and redistribution rules
are defined by the owning organization.

