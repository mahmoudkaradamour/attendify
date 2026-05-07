package com.mahmoud.attendify.orchestration.security

import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot

object CanonicalSnapshotSerializer {

    fun serialize(
        imageHash: ByteArray,
        time: Long,
        latitude: Double,
        longitude: Double
    ): ByteArray {

        // ترتيب ثابت
        val payload =
            imageHash +
                    time.toString().toByteArray() +
                    latitude.toString().toByteArray() +
                    longitude.toString().toByteArray()

        return payload
    }
}