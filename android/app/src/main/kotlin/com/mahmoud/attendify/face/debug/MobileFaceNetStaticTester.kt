package com.mahmoud.attendify.face.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.mahmoud.attendify.face.FaceCropper
import com.mahmoud.attendify.face.FaceDetector
import com.mahmoud.attendify.face.MobileFaceNet
import com.mahmoud.attendify.matching.EmbeddingDistance
import java.io.InputStream
import kotlin.math.sqrt

/**
 * MobileFaceNetStaticTester
 *
 * Arabic:
 * اختبار ثابت لـ Face Recognition
 * - بدون كاميرا
 * - بدون Flutter
 * - يستخدم صور من assets
 *
 * English:
 * Static tester for face embeddings
 * - No camera
 * - No Flutter
 * - Uses asset images
 */
object MobileFaceNetStaticTester {

    private const val TAG = "MFNET_TEST"

    /**
     * run3ImageSuite
     *
     * Arabic:
     * تشغيل اختبار كامل على 3 صور:
     * - real face
     * - printed photo
     * - screen attack
     *
     * English:
     * Run full static test on 3 images
     */
    fun run3ImageSuite(
        context: Context,
        imageReal: String,
        imagePrinted: String,
        imageScreen: String
    ) {
        Log.d(TAG, "--------------------------------------------------")
        Log.d(TAG, "Suite: $imageReal | $imagePrinted | $imageScreen")

        val detector = FaceDetector(context)
        val faceNet = MobileFaceNet(context)

        val embReal = extractEmbedding(context, detector, faceNet, imageReal)
        val embPrinted = extractEmbedding(context, detector, faceNet, imagePrinted)
        val embScreen = extractEmbedding(context, detector, faceNet, imageScreen)

        if (embReal == null || embPrinted == null || embScreen == null) {
            Log.e(TAG, "❌ One or more embeddings failed – suite aborted")
            return
        }

        // ✅ Determinism test (same image twice)
        val embReal2 = extractEmbedding(context, detector, faceNet, imageReal)
        if (embReal2 != null) {
            val selfDist = EmbeddingDistance.l2(embReal, embReal2)
            Log.d(TAG, "Self distance (real vs real again) = $selfDist")
        }

        // ✅ Cross comparisons
        Log.d(TAG, "L2(real, printed) = ${EmbeddingDistance.l2(embReal, embPrinted)}")
        Log.d(TAG, "L2(real, screen)  = ${EmbeddingDistance.l2(embReal, embScreen)}")
        Log.d(TAG, "L2(printed, screen) = ${EmbeddingDistance.l2(embPrinted, embScreen)}")

        // ✅ Norms (diagnostic only – no normalization applied)
        Log.d(TAG, "Norm(real)    = ${l2Norm(embReal)}")
        Log.d(TAG, "Norm(printed) = ${l2Norm(embPrinted)}")
        Log.d(TAG, "Norm(screen)  = ${l2Norm(embScreen)}")

        Log.d(TAG, "--------------------------------------------------")
    }

    /**
     * extractEmbedding
     *
     * Arabic:
     * - تحميل الصورة من assets
     * - كشف الوجه
     * - قص الوجه
     * - توليد embedding
     *
     * English:
     * - Load bitmap
     * - Detect face
     * - Crop face
     * - Generate embedding
     */
    private fun extractEmbedding(
        context: Context,
        detector: FaceDetector,
        faceNet: MobileFaceNet,
        assetPath: String
    ): FloatArray? {

        val bitmap = loadBitmapFromAssets(context, assetPath)
            ?: return null

        val detection = detector.detectBestFace(bitmap)
        if (detection == null) {
            Log.e(TAG, "No face detected in $assetPath")
            return null
        }

        val faceBitmap = FaceCropper.cropFace(bitmap, detection.box)
        if (faceBitmap == null) {
            Log.e(TAG, "Face crop failed in $assetPath")
            return null
        }

        return try {
            faceNet.getEmbedding(faceBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Embedding extraction failed in $assetPath", e)
            null
        }
    }

    /**
     * loadBitmapFromAssets
     */
    private fun loadBitmapFromAssets(
        context: Context,
        path: String
    ): Bitmap? {
        return try {
            val inputStream: InputStream = context.assets.open(path)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load asset image: $path", e)
            null
        }
    }

    /**
     * l2Norm
     *
     * Arabic:
     * حساب طول المتجه (للتشخيص فقط)
     *
     * English:
     * Compute vector L2 norm (diagnostic only)
     */
    private fun l2Norm(v: FloatArray): Double {
        var sum = 0.0
        for (x in v) {
            sum += (x * x)
        }
        return sqrt(sum)
    }
}
