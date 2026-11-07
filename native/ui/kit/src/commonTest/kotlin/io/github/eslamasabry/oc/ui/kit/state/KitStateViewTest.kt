package io.github.eslamasabry.oc.ui.kit.state

import io.github.eslamasabry.oc.core.common.Failure
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rules order 08 added to KitStateView (lib/ui/kit/kit_state_view.dart at 8302de537). */
class KitStateViewTest {
    @Test
    fun `B-KIT-034 details given apart from the failure still offer Copy details`() {
        // Dart KitStateView.error(details:) adds Copy details whenever details are set, whatever the error carries.
        val other = Failure.UnreadableReply()
        assertEquals(
            listOf(ErrorWay.TryAgain, ErrorWay.CopyDetails, ErrorWay.ReportProblem),
            StateViewRules.errorWays(other, canRetry = true, canSwitch = true, canReport = true, hasDetails = true),
        )
        assertEquals(
            listOf(ErrorWay.TryAgain, ErrorWay.SwitchServer, ErrorWay.CopyDetails),
            StateViewRules.errorWays(Failure.TimedOut(), canRetry = true, canSwitch = true, canReport = true, hasDetails = true),
        )
        assertEquals(listOf(ErrorWay.TryAgain), StateViewRules.errorWays(other, canRetry = true, canSwitch = false, canReport = false, hasDetails = false))
    }

    @Test
    fun `B-KIT-034 the slow line names the escalation's own 8 s, not the running wait`() {
        // Dart KitSince.slowLabel: kitSinceStillWaiting(KitMotion.escalateAfter.inSeconds).
        assertEquals(8L, StateViewRules.slowSeconds())
    }

    @Test
    fun `B-KIT-034 a state is a page unless it says inline`() {
        assertEquals(listOf(KitStateSize.Page, KitStateSize.Inline), KitStateSize.entries.toList())
    }
}
