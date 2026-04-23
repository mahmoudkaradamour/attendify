package com.mahmoud.attendify.attendance.usecase

import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.system.time.*

/**
 * AttendanceUseCase
 *
 * Orchestrates attendance operations (Check‑In / Check‑Out)
 * with full time integrity enforcement.
 */
class AttendanceUseCase(
    private val timeIntegrityGuard: TimeIntegrityGuard,
    private val timeProofFactory: AttendanceTimeProofFactory,
    private val anchorStorage: TimeAnchorStorage
) {

    /**
     * Executes an attendance action.
     */
    fun execute(action: AttendanceAction): AttendanceResult {

        // Load last snapshot if any
        val previousSnapshot: TimeSnapshot? =
            if (anchorStorage.hasAnchor()) anchorStorage.loadAnchor() else null

        // Validate time integrity
        val integrityResult = timeIntegrityGuard.validate(previousSnapshot)

        if (integrityResult is TimeIntegrityResult.Blocked ||
            integrityResult is TimeIntegrityResult.Tampered
        ) {
            return AttendanceResult.Blocked(integrityResult)
        }

        // Time is valid → create snapshot
        val currentSnapshot = TimeSource.snapshot()

        // Generate forensic time proof
        val proof = timeProofFactory.create(
            currentSnapshot = currentSnapshot,
            integrityResult = integrityResult
        )

        // Update snapshot reference (for next delta comparison)
        anchorStorage.saveAnchor(currentSnapshot)

        return AttendanceResult.Success(
            action = action,
            proof = proof
        )
    }
}
