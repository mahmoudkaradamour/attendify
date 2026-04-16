plugins {
    id("com.android.application")
    id("kotlin-android")
    // Flutter Gradle Plugin must be applied last
    id("dev.flutter.flutter-gradle-plugin")
}

android {

    /*
     * Namespace:
     * - Used internally by Android for R, BuildConfig, MainActivity
     * - Must match your Kotlin package structure
     * - Do NOT change later
     */
    namespace = "com.mahmoud.attendify"

    /*
     * Compile SDK:
     * - Taken from Flutter
     * - Usually the latest stable Android SDK
     */
    compileSdk = flutter.compileSdkVersion

    ndkVersion = flutter.ndkVersion

    /*
     * Java / Kotlin compatibility
     * Java 17 is recommended for modern Android
     */
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {

        /*
         * Application ID:
         * - This is the permanent identity of the app on Android
         * - Used internally by the OS and Play Store
         * - Suitable for GitHub and personal open‑source projects
         */
        applicationId = "com.mahmoud.attendify"

        /*
         * Minimum supported Android version
         * Android 9 (Pie) = API 28
         * Good balance between stability and device coverage
         */
        minSdk = 28

        /*
         * Target SDK:
         * - Latest behavior expected by Android
         */
        targetSdk = flutter.targetSdkVersion

        /*
         * Versioning comes from Flutter
         */
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        /*
         * Important for large / enterprise apps
         * (biometric, services, crypto, networking, etc.)
         */
        multiDexEnabled = true
    }

    buildTypes {
        release {
            /*
             * Debug signing is acceptable during development
             * Replace with a release keystore later when publishing
             */
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    // ✅ TensorFlow Lite Core
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    // ✅ Optimized CPU kernels (مهم جدًا للأجهزة الضعيفة)
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.14.0")

    // ✅ Optional: GPU delegate (لن نستخدمه افتراضيًا)
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // Camera
    implementation("androidx.camera:camera-core:1.3.2")
    implementation("androidx.camera:camera-camera2:1.3.2")
    implementation("androidx.camera:camera-lifecycle:1.3.2")

    // CameraX Preview (للمستقبل)
    implementation("androidx.camera:camera-view:1.3.2")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")








}