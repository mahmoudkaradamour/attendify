package com.mahmoud.attendify.system.time.working

import java.time.LocalDate

/**
 * Holiday
 *
 * Represents a non‑working calendar date.
 *
 * Can be:
 * - public holiday
 * - administrative holiday
 * - organization‑specific event
 */
data class Holiday(

    /** Calendar date of the holiday */
    val date: LocalDate,

    /** Optional description (e.g. "Eid Al‑Fitr") */
    val name: String? = null
)