package com.mahmoud.attendify.system.time.working

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * WorkingTimeEvaluator
 *
 * PURPOSE:
 * --------
 * Evaluates whether an attendance attempt occurs
 * within or outside administratively defined
 * working time boundaries.
 *
 * IMPORTANT ARCHITECTURAL RULES:
 * ------------------------------
 * 1) This class does NOT validate time correctness.
 *    - It assumes TimeIntegrity has already passed.
 *
 * 2) This class does NOT enforce attendance.
 *    - It only evaluates policy and returns a decision.
 *
 * 3) This class operates purely on ADMINISTRATIVE POLICY.
 *
 * In other words:
 * TimeIntegrity answers: "Is the time trustworthy?"
 * WorkingTimeEvaluator answers: "Is attendance allowed now?"
 */
class WorkingTimeEvaluator(
    private val policy: WorkingTimePolicy
) {

    /**
     * Evaluates a trusted time instant against
     * the configured WorkingTimePolicy.
     *
     * @param instant
     *   A trusted UTC instant obtained AFTER
     *   successful TimeIntegrity validation.
     *
     * @return WorkingTimeDecision
     *   An immutable description of:
     *   - whether the attempt is within working time
     *   - whether today is a holiday
     *   - what administrative action applies
     */
    fun evaluate(instant: Instant): WorkingTimeDecision {

        /* ==================================================
         * 1️⃣ CONVERT INSTANT TO POLICY TIME ZONE
         * ================================================== */

        /**
         * The device time zone MUST NEVER be trusted.
         *
         * All calculations are done using the
         * time zone explicitly defined by policy.
         */
        val zonedDateTime = instant.atZone(policy.timeZone)

        val localDate: LocalDate = zonedDateTime.toLocalDate()
        val localTime: LocalTime = zonedDateTime.toLocalTime()

        /* ==================================================
         * 2️⃣ DETERMINE DAY OF WEEK
         * ================================================== */

        /**
         * We explicitly map the ISO day-of-week
         * to our administrative WorkingDay enum.
         *
         * This avoids any locale-based assumptions.
         */
        val workingDay =
            WorkingDay.valueOf(zonedDateTime.dayOfWeek.name)

        /* ==================================================
         * 3️⃣ CHECK HOLIDAYS
         * ================================================== */

        /**
         * Holidays override everything else.
         *
         * A day may be:
         * - a working day
         * - but still a holiday
         */
        val isHoliday =
            policy.holidays.any { it.date == localDate }

        /* ==================================================
         * 4️⃣ CHECK WHETHER TODAY IS A WORKING DAY
         * ================================================== */

        val isWorkingDay =
            policy.workingDays.contains(workingDay)

        /* ==================================================
         * 5️⃣ CHECK WORKING HOURS
         * ================================================== */

        /**
         * Working hours are evaluated using LocalTime
         * because they represent a daily window,
         * not an absolute timestamp.
         */
        val isWithinWorkingHours =
            localTime >= policy.workingHours.start &&
                    localTime <= policy.workingHours.end

        /* ==================================================
         * 6️⃣ FINAL "WITHIN WORKING TIME" DETERMINATION
         * ================================================== */

        /**
         * An attendance attempt is considered
         * WITHIN working time only if:
         *
         * - Today is a configured working day
         * - Today is NOT a holiday
         * - Current time is within working hours
         */
        val isWithinWorkingTime =
            isWorkingDay && !isHoliday && isWithinWorkingHours

        /* ==================================================
         * 7️⃣ DETERMINE ADMINISTRATIVE ACTION
         * ================================================== */

        /**
         * Inside working time:
         *   - Attendance is always allowed.
         *
         * Outside working time:
         *   - Behavior is defined EXCLUSIVELY
         *     by administrative policy.
         */
        val action =
            if (isWithinWorkingTime)
                OutOfWorkingTimeAction.ALLOW
            else
                policy.outOfWorkingTimeAction

        /* ==================================================
         * 8️⃣ BUILD FINAL DECISION
         * ================================================== */

        return WorkingTimeDecision(
            policy = policy,
            isWithinWorkingTime = isWithinWorkingTime,
            isHoliday = isHoliday,
            action = action
        )
    }
}