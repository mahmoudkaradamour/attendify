package com.mahmoud.attendify.matching

/**
 * MatchDecision
 *
 * Arabic:
 * قرار نهائي مفهوم لواجهة المستخدم.
 */
sealed class MatchDecision {

    object MatchSuccess : MatchDecision()

    object NoMatch : MatchDecision()

    object ReferenceImageNotApproved : MatchDecision()

    object PolicyBlockedAttendance : MatchDecision()
}