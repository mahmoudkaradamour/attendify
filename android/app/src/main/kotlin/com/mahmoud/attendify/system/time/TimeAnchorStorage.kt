package com.mahmoud.attendify.system.time

/**
 * TimeAnchorStorage
 *
 * Contract responsible for persisting and retrieving
 * the immutable time anchor (Genesis Record).
 *
 * Implementations MUST guarantee:
 * - Secure persistence (Keystore-backed or equivalent)
 * - Tamper resistance
 */
interface TimeAnchorStorage {

    /**
     * @return true if a valid anchor record exists.
     */
    fun hasAnchor(): Boolean

    /**
     * Loads the stored anchor snapshot.
     *
     * @throws IllegalStateException if no anchor exists.
     */
    fun loadAnchor(): TimeSnapshot

    /**
     * Persists a new anchor.
     *
     * This MUST be called only after a successful
     * online handshake with the backend.
     */
    fun saveAnchor(snapshot: TimeSnapshot)

    /**
     * Removes the existing anchor.
     *
     * This is equivalent to returning the system
     * to an uninitialized (cold start) state.
     */
    fun clearAnchor()
}
