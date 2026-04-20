# 📘 Attendify – Face Anti‑Spoofing (FAS) Models

This directory contains all **Passive Face Anti‑Spoofing (FAS)** models used by the Attendify platform.

These models are **on‑device**, **offline**, and designed to protect the attendance system against **presentation attacks** such as:

*   Screen replay attacks
*   Video replays
*   Printed photos
*   Basic deepfake attempts

All models in this directory are **Passive (Silent) Liveness Detection** models.  
They do **not** rely on user interaction (blink / smile / head movement).

***

## 🧱 Design Principles

The FAS subsystem in Attendify is built around **four non‑negotiable principles**:

1.  **Edge‑Only Execution**  
    All inference runs locally on the device. No cloud dependency.

2.  **Multi‑Model Architecture**  
    The system supports multiple FAS models, each serving a different security tier.

3.  **Policy‑Driven Activation**  
    Whether FAS is enabled, which model is used, the confidence threshold, and GPU usage  
    are all decisions made by **administrative policy**, not by code.

4.  **Fail‑Safe by Design**  
    Any failure (device, camera, lighting, model, runtime) is:
    *   detected
    *   logged
    *   handled without crashing the application

***

## 📂 Directory Structure

    models/
    └── fas/
        ├── minifasnet_v2_80x80_default.tflite
        ├── minifasnet_v1se_80x80_light.tflite
        ├── fastfasnet_v3_128x128_highsec.tflite
        └── README.md

Each model is **explicitly named** to indicate:

*   its architecture
*   input resolution
*   intended security tier

***

## 🛡️ Available Models

### ✅ 1. MiniFASNet V2 – Default Secure Model

**File**

    minifasnet_v2_80x80_default.tflite

**Purpose**

*   Default **production** FAS model
*   Secure‑by‑default for all new organizations

**Characteristics**

*   Input: `80 × 80 RGB`
*   Strong resistance to:
    *   screen replay attacks
    *   video replays
    *   printed photos
*   Balanced performance and accuracy
*   Suitable for the majority of users

**Recommended Usage**

*   Enabled by default
*   Organization‑level or group‑level policy
*   Medium to high confidence threshold (e.g. 0.85)

***

### ⚙️ 2. MiniFASNet V1SE – Lightweight Model

**File**

    minifasnet_v1se_80x80_light.tflite

**Purpose**

*   Lightweight FAS option for constrained environments

**Characteristics**

*   Input: `80 × 80 RGB`
*   Lower computational cost
*   Faster inference on low‑end devices
*   Slightly lower resistance compared to V2

**Recommended Usage**

*   Factories, warehouses, large attendance queues
*   Low‑end or older Android devices
*   Used when speed is prioritized over maximum security

***

### 🔐 3. FastFASNet V3 – High‑Security Model

**File**

    fastfasnet_v3_128x128_highsec.tflite

**Purpose**

*   High‑precision FAS model for sensitive roles
*   Escalation or high‑risk verification

**Architecture**

*   MobileNetV3‑Large backbone
*   Optimized for ARM processors (Hard‑Swish activation)

**Characteristics**

*   Input: `128 × 128 RGB`
*   Sees more facial context and background edges
*   Superior detection of:
    *   screen borders
    *   reflections
    *   moiré patterns
*   Higher computational cost than MiniFASNet models

**Recommended Usage**

*   Finance, administration, executives
*   High‑risk users
*   Situations where spoof attempts are suspected
*   Can be paired with GPU or NNAPI acceleration per policy

***

## ⚙️ Hardware Acceleration (GPU / NNAPI)

FAS models **do NOT require GPU by default**, but the system supports hardware acceleration **per model**.

*   GPU / NNAPI can be enabled **only for specific models**
*   Other models can continue running on CPU
*   Hardware acceleration is:
    *   optional
    *   policy‑controlled
    *   fails safely with CPU fallback

There is **no global GPU switch** for the application.

***

## 🎚️ Confidence Thresholds

Each FAS decision produces a **confidence score** (real vs spoof).

Thresholds are **not hardcoded** and can be defined at multiple levels:

*   Per employee
*   Per group
*   Per organization
*   Per model default

Example:

*   Default model: `0.85`
*   Finance department: `0.90`
*   Factory workers: `0.78`

***

## 🚨 Error Handling & Observability

The FAS pipeline is designed to handle failures gracefully.

### Possible failure sources:

*   Device limitations (memory, temperature)
*   Camera issues
*   Poor lighting or motion blur
*   Model initialization failure
*   Inference errors
*   Unexpected runtime exceptions

### System behavior:

*   All errors are logged with context
*   Critical failures prevent attendance safely
*   Non‑critical issues provide user‑friendly feedback
*   No crashes, no undefined states

***

## 🔍 Important Notes

*   Not all employees must have FAS enabled.
*   Disabling FAS for any user or group should be auditable.
*   This directory **must only contain FAS models**.
*   Face recognition models belong elsewhere.

***

## ✅ Summary

This directory is the foundation of Attendify’s **Passive Liveness Defense Layer**.

It enables:

*   Strong anti‑spoofing protection
*   Flexible security policies
*   Device‑adaptive performance
*   Enterprise‑grade reliability

No model in this directory is hard‑wired into logic.  
**Everything is policy‑driven.**
