plugins {
    id("com.android.application")
    id("kotlin-android")

    // ✅ Required for Room (annotation processing)
    id("kotlin-kapt")

    // Flutter Gradle Plugin must be applied last
    id("dev.flutter.flutter-gradle-plugin")
}

android {

    /*
     * Namespace:
     * Must match project package
     */
    namespace = "com.mahmoud.attendify"

    /*
     * Compile SDK
     */
    compileSdk = flutter.compileSdkVersion

    ndkVersion = flutter.ndkVersion

    /*
     * Java / Kotlin
     */
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {

        applicationId = "com.mahmoud.attendify"

        minSdk = 28
        targetSdk = flutter.targetSdkVersion

        versionCode = flutter.versionCode
        versionName = flutter.versionName

        multiDexEnabled = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

/* ========================================================================
 * ✅ KAPT CONFIG (Stable version)
 * ====================================================================== */
kapt {
    arguments {
        arg("correctErrorTypes", "true")
    }
}

flutter {
    source = "../.."
}

dependencies {

    /* =========================================================
     * ✅ Core Android
     * ========================================================= */
    implementation("androidx.appcompat:appcompat:1.7.1")

    /* =========================================================
     * ✅ Room Database (D1 — Persistent Ledger)
     * ========================================================= */
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    // Annotation processor (stable)
    kapt("androidx.room:room-compiler:2.8.4")

    /* =========================================================
     * ✅ TensorFlow Lite (FIXED ALIGNMENT ISSUE)
     * ========================================================= */
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")

    /* =========================================================
     * ✅ CameraX (STABLE — DO NOT UPDATE NOW)
     * ========================================================= */
    implementation("androidx.camera:camera-core:1.3.2")
    implementation("androidx.camera:camera-camera2:1.3.2")
    implementation("androidx.camera:camera-lifecycle:1.3.2")
    implementation("androidx.camera:camera-view:1.3.2")

    /* =========================================================
     * ✅ Security (Stable Release)
     * ========================================================= */
    implementation("androidx.security:security-crypto:1.1.0")

    /* =========================================================
     * ✅ Networking (STABLE VERSION)
     * ========================================================= */
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    /* =========================================================
     * ✅ JSON
     * ========================================================= */
    implementation("org.json:json:20231013")
}