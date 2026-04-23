# 🛡️ Attendify - Enterprise-Grade Biometric Attendance Engine

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-Clean_Architecture-blue)
![ML](https://img.shields.io/badge/ML-TensorFlow_Lite-FF6F00?logo=tensorflow)

Attendify is a highly secure, real-time face recognition and anti-spoofing (FAS) engine built natively for Android. Designed for enterprise and mission-critical environments, it ensures deterministic performance, zero-allocation memory management, and military-grade resistance to presentation attacks (spoofing).

---

## ✨ Engineering Highlights & Core Features

### 🔒 Zero-Tolerance Anti-Spoofing (Liveness Detection)
* **Passive FAS (Neural Networks):** Integrates multiple TFLite models (e.g., `MiniFASNet`, `FastFASNetV3`) with dynamic **Scale Expansion** to detect printed photos, screen replays, and cut-out masks by analyzing background context and Moiré patterns.
* **Active FAS (Metrics Engine):** Real-time tracking of facial metrics (Blink, Smile, Head Yaw/Pitch) to ensure the physical presence of a live subject.
* **Fail-Secure Architecture:** Strict policy enforcement that explicitly blocks attendance if a high-security model is unavailable, strictly preventing silent security downgrades.

### ⚡ High-Performance Native Core
* **Zero-Allocation Streaming:** Utilizes pre-allocated memory buffers (`IntArray`, `ByteBuffer`) for continuous frame processing. Ensures **30 FPS inference** without triggering Garbage Collection (GC) churn or thermal throttling on low-end devices.
* **Smart Hardware Acceleration:** Dynamic resolution of GPU vs. CPU (XNNPACK) delegation based on model quantization (INT8 vs Float32) to prevent hardware crashes on legacy chipsets.
* **Numerical Integrity:** Custom implementation of **Stable Softmax** and **L2 Normalization** to prevent math overflows (NaN/Infinity) and guarantee deterministic cosine similarity.

---

## 🏗️ System Architecture (The Pipeline)

Attendify is built strictly on **Clean Architecture** and **Template Method Patterns**, featuring a unidirectional, asynchronous data pipeline that keeps the UI thread entirely free:

1. **CameraX Input** ➔ Low-overhead YUV frame capturing with early-release memory management.
2. **Quality Gate** ➔ Discards blurry or poorly lit frames to conserve CPU power.
3. **BlazeFace Detection** ➔ High-speed face localization.
4. **Safe Scale Cropper** ➔ Expands the bounding box (e.g., 2.5x) to capture vital physical spoofing context.
5. **Liveness Orchestrator** ➔ Validates Real vs. Spoof using adaptive, policy-driven FAS models.
6. **MobileFaceNet Extractor** ➔ Dequantizes INT8 tensors safely into 128-D normalized embeddings.
7. **Matching Engine** ➔ Compares embeddings using deterministic Cosine Similarity thresholds.

---

## 🛠️ Tech Stack & Patterns
* **Core:** Kotlin, Coroutines, StateFlow (Concurrency)
* **Camera:** Android CameraX (Hardware Abstraction)
* **AI / ML:** TensorFlow Lite (C++ Native backend), Google ML Kit
* **Design Patterns:** Clean Architecture, Template Method Pattern, Orchestrator Pattern, Single Source of Truth.

---

## 📜 Audit & Compliance
Features a robust `AttendanceAuditLog` system to track precise inference times, exact hardware delegates used, and detailed rejection reasons. This ensures complete traceability and accountability for enterprise auditing and compliance.

---

*Built with strict engineering discipline. Ready for Production.*
