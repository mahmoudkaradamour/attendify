<div align="center">
  <h1>🚀 Attendify</h1>
  <p><b>Enterprise-Grade, Offline-First Attendance & Liveness Tracking Engine</b></p>
</div>

<div align="center">
  <img src="https://img.shields.io/badge/Flutter-%2302569B.svg?style=for-the-badge&logo=Flutter&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/TensorFlow%20Lite-%23FF6F00.svg?style=for-the-badge&logo=TensorFlow&logoColor=white" />
  <img src="https://img.shields.io/badge/Architecture-Clean%20%26%20DDD-success?style=for-the-badge" />
</div>

## 📌 Overview
Attendify is not just a UI for attendance; it is a strict **Decision Engine** built with Clean Architecture. Designed for enterprise environments requiring robust security, it combats GPS spoofing, time manipulation, and biometric fraud completely **On-Device**, with zero dependency on constant network connectivity.

## 🏗️ Core Architectural Highlights

### 1. 🛡️ Time & Location Integrity Guards
Bypasses OS-level manipulation and fake GPS apps:
- **`TimeIntegrityGuard`:** Cross-references secure server time with GPS satellite time, sealed locally using `TimeProofSigner`.
- **`LocationIntegrityGuard`:** Evaluates exact coordinates against strict zones while actively probing for mocked location behaviors and storing data via `SecureLocationAnchorStorage`.

### 2. 👤 Face Anti-Spoofing (FAS) & Liveness
Prevents Deepfakes, printed photos, and screen attacks without sending images to a server:
- **Multi-Layered TF-Lite Models:** Dynamically loads `FastFASNetV3` (High-Sec) or `MiniFASNetV1SE` (Light) depending on the device's hardware capabilities via `DeviceCapabilityProfiler`.
- **`FacialMetricsEngine`:** Enforces real-time human interaction through randomized active liveness checks (Blink, Yaw/Pitch, Smile, Photometric checks).
- **`MobileFaceNet`:** Executes high-precision facial embedding extraction and distance matching locally.

### 3. 📡 Offline-First & ERP Ready
Engineered for real-world logistical challenges (e.g., remote warehouses with poor network coverage):
- **Local Encrypted Storage:** Faces and records are stored securely via `LocalEncryptedEmployeeReferenceRepository`.
- **Audit Logging:** Every attendance attempt is logged (`LocalAttendanceAuditLogger`) and queued for sync using a persistent Android `ForegroundService`.
- **Middleware Ready:** Clean separation of `AttendanceDecision` and `EmployeeReference` entities makes the system highly adaptable for seamless integration with enterprise ERPs (Odoo, SAP, Dynamics).

## 🧩 Project Structure (Domain-Driven)
The codebase enforces rigid separation of concerns:
- `attendance/`: Core orchestration (`AttendanceRuntimeOrchestrator`).
- `system/`: Hardware abstraction (Time, Location, Device Profiling).
- `fas/ & liveness/`: Computer vision and ML evaluation logic.
- `matching/`: Mathematical embedding similarities.
- `channel/`: Kotlin native bridge `AttendanceMethodChannel` for heavy ML tasks.

## 👨‍💻 Developer
Built by **[Mahmoud Karah Damour](https://github.com/mahmoudkaradamour)** - Architecting order from complexity.
