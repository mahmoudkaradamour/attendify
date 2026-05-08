package com.mahmoud.attendify.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import com.mahmoud.attendify.MainActivity
import com.mahmoud.attendify.R

/**
 * =============================================================================
 * 🧠 DisclosureActivity — Privacy & Consent Gate
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * PURPOSE
 * -----------------------------------------------------------------------------
 *
 * This Activity enforces the "informed consent" principle:
 *
 * User MUST understand:
 *
 *   - What data is used
 *   - Why it is used
 *   - Where processing occurs
 *
 * BEFORE any sensitive permission request is made.
 *
 * -----------------------------------------------------------------------------
 * FLOW
 * -----------------------------------------------------------------------------
 *
 *   Launch App →
 *   Show Disclosure →
 *   User Accepts →
 *   Request Permissions →
 *   Continue
 *
 * -----------------------------------------------------------------------------
 * SECURITY MODEL
 * -----------------------------------------------------------------------------
 *
 * Prevents:
 *   ❌ Silent biometric collection
 *   ❌ Unexpected permission prompts
 *
 * Ensures:
 *   ✅ User awareness
 *   ✅ Legal compliance (Google Play + GDPR principles)
 *
 * =============================================================================
 */
class DisclosureActivity : AppCompatActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val cameraGranted =
                result[Manifest.permission.CAMERA] == true

            val locationGranted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (cameraGranted && locationGranted) {
                proceedToMain()
            } else {
                // You can show a retry or explanation dialog here
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_disclosure)

        val continueBtn = findViewById<Button>(R.id.btn_continue)
        val text = findViewById<TextView>(R.id.txt_disclosure)

        text.text = """
Secure attendance verification requires:
• Camera access for facial analysis
• Location access for presence validation

All processing happens ON DEVICE.
No images are stored or transmitted externally.

Tap continue to proceed.
        """.trimIndent()

        continueBtn.setOnClickListener {
            requestPermissions()
        }
    }

    private fun requestPermissions() {

        val permissionsNeeded = mutableListOf<String>()

        if (!hasPermission(Manifest.permission.CAMERA)) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsNeeded.isEmpty()) {
            proceedToMain()
        } else {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED

    private fun proceedToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}