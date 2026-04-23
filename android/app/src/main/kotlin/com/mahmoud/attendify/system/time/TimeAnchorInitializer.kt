package com.mahmoud.attendify.system.time

/**
 * TimeAnchorInitializer
 *
 * Creates the Genesis Anchor using server‑trusted time.
 * Must be called once online before any offline attendance.
 */
class TimeAnchorInitializer(
    private val serverTimeClient: ServerTimeClient,
    private val anchorStorage: TimeAnchorStorage
) {

    /**
     * Performs mandatory initial handshake.
     */
    fun initializeAnchor() {

        if (anchorStorage.hasAnchor()) {
            // Already initialized
            return
        }

        // Fetch trusted server time (latency‑compensated)
        val trustedUtc = serverTimeClient.fetchAnchoredUtcTime()

        // Capture local monotonic snapshot immediately
        val localSnapshot = TimeSource.snapshot()

        val anchorSnapshot = TimeSnapshot(
            wallClockMillis = trustedUtc,
            elapsedRealtimeMillis = localSnapshot.elapsedRealtimeMillis,
            uptimeMillis = localSnapshot.uptimeMillis,
            bootId = localSnapshot.bootId,
            timeZoneId = localSnapshot.timeZoneId
        )

        anchorStorage.saveAnchor(anchorSnapshot)
    }
}