package com.mahmoud.attendify.system.location

/**
 * Persists last known VALID location to detect teleportation across sessions.
 */
interface LocationAnchorStorage {

    fun hasLastLocation(): Boolean

    fun loadLastLocation(): LocationSnapshot

    fun saveLastLocation(snapshot: LocationSnapshot)

    fun clear()
}