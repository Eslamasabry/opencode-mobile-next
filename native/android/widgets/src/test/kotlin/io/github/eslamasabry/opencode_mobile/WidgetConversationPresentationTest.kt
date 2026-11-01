package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.opencode_mobile.WidgetLayoutPolicy.CardNeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConversationPresentationTest {
    @Test fun headerShowsOnlyNonZeroCountsOrACalmPhrase() {
        assertEquals(WidgetLayoutPolicy.Summary.IDLE, WidgetStatusRules.summary(0, 0))
        assertEquals(WidgetLayoutPolicy.Summary.RUNNING, WidgetStatusRules.summary(2, 0))
        assertEquals(WidgetLayoutPolicy.Summary.NEEDS, WidgetStatusRules.summary(0, 1))
        assertEquals(WidgetLayoutPolicy.Summary.BOTH, WidgetStatusRules.summary(1, 1))
    }

    @Test fun acceptedAllowWithRunningTurnAndNoStopProofKeepsNormalOnDemandPresentation() {
        val connection = WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(
            version = 2, connected = false, serviceActive = false,
            onDemandAvailable = true,
            recoveryRequired = WidgetV2Policy.needsBackgroundSettings("accepted", null),
        ))
        assertEquals(WidgetV2Policy.Connection.ON_DEMAND, connection)
        assertFalse(WidgetStatusRules.rowNeedsSettings(connection))
        assertFalse(WidgetV2Policy.actionEnabled(connection, "stop", emptySet(), false))
        assertEquals("lastWorking", WidgetStatusRules.status(false, true, false))
        assertFalse(WidgetStatusRules.timerTicks(true, 1000, false))
    }

    @Test fun settledFollowUpShowsReadyAndDoesNotBecomeRecovery() {
        assertEquals("ready", WidgetStatusRules.status(false, false, false))
        assertFalse(WidgetStatusRules.rowNeedsSettings(WidgetV2Policy.Connection.ON_DEMAND))
        assertTrue(WidgetStatusRules.rowNeedsSettings(WidgetV2Policy.Connection.BACKGROUND_SETTINGS))
    }

    @Test fun attentionTakesPrecedenceAndOnlyLiveWorkingUsesCurrentStatus() {
        assertEquals("needsYou", WidgetStatusRules.status(true, true, false))
        assertEquals("working", WidgetStatusRules.status(false, true, true))
        assertEquals("lastWorking", WidgetStatusRules.status(false, true, false))
    }

    @Test fun measuredUsageNeverUsesNeedsYouAmber() {
        for (remaining in 1..100) assertEquals("running", WidgetUsageStyle.usageRole(remaining))
        assertEquals("neutral", WidgetUsageStyle.usageRole(0))
    }

    @Test fun wallpaperAndSavedAccentsCannotMakeOrdinaryControlsRedOrAmber() {
        for (hue in listOf(0f, 20f, 40f, 60f, 350f)) {
            assertTrue(WidgetUsageStyle.reservedStatusHue(hue, 0.7f))
        }
        for (hue in listOf(120f, 180f, 210f, 270f)) {
            assertFalse(WidgetUsageStyle.reservedStatusHue(hue, 0.7f))
        }
        assertFalse(WidgetUsageStyle.reservedStatusHue(30f, 0.1f))
    }

    @Test fun actualLauncherSizePreservesTwoByTwoHeightInsteadOfShorterPreset() {
        assertEquals(listOf(210 to 224, 388 to 224), WidgetSizePolicy.launcherSizes(
            listOf(210f to 224f, 388f to 224f)))
        assertEquals(emptyList<Pair<Int, Int>>(), WidgetSizePolicy.launcherSizes(emptyList()))
    }

    @Test fun launcherSizesRejectInvalidValuesDeduplicateAndRespectRemoteViewsLimit() {
        val invalid = listOf(Float.NaN to 224f, 210f to Float.POSITIVE_INFINITY,
            -1f to 224f, 210f to 0f, 210f to 2001f)
        assertEquals(emptyList<Pair<Int, Int>>(), WidgetSizePolicy.launcherSizes(invalid))
        assertEquals(listOf(210 to 224), WidgetSizePolicy.launcherSizes(
            listOf(210f to 224f, 210.8f to 224.3f)))
        assertEquals(16, WidgetSizePolicy.launcherSizes((1..100).map { it.toFloat() to 224f }).size)
    }

    @Test fun cardSpendsItsHeightInPriorityOrder() {
        // 4x2 portrait (about 200dp): question, the exact command, the meta line.
        assertEquals(WidgetLayoutPolicy.CardPlan(1, 1, false, true, 0),
            WidgetLayoutPolicy.cardPlan(200, false, CardNeeds(1, 1, false, true, 3)))
        // A two-line question takes its second line before the meta line.
        assertEquals(WidgetLayoutPolicy.CardPlan(2, 0, false, false, 0),
            WidgetLayoutPolicy.cardPlan(190, false, CardNeeds(2, 0, false, true, 0)))
        // A shorter 4x2 keeps the command before the meta line.
        assertEquals(WidgetLayoutPolicy.CardPlan(1, 1, false, false, 0),
            WidgetLayoutPolicy.cardPlan(176, false, CardNeeds(2, 1, false, true, 0)))
        // A command that does not fit whole is not shown at all (and so Allow is not offered).
        assertEquals(0, WidgetLayoutPolicy.cardPlan(150, false, CardNeeds(1, 2, false, true, 0)).command)
        // Working: the live line comes before the meta line.
        assertEquals(WidgetLayoutPolicy.CardPlan(1, 0, true, false, 0),
            WidgetLayoutPolicy.cardPlan(180, false, CardNeeds(1, 0, true, true, 0)))
        // A tall card spends what is left on whole rows of the other conversations, at most five.
        val tall = WidgetLayoutPolicy.cardPlan(400, false, CardNeeds(2, 0, true, true, 9))
        assertEquals(2, tall.title)
        assertEquals((400 - 122 - 27 - 24 - 24 - 27 - 20) / WidgetLayoutPolicy.ROW_DP, tall.rows)
        assertEquals(1, WidgetLayoutPolicy.cardPlan(400, false, CardNeeds(1, 0, false, true, 1)).rows)
        assertEquals(5, WidgetLayoutPolicy.cardPlan(1000, false, CardNeeds(1, 0, false, true, 9)).rows)
        // Larger text (height divided by the font scale) drops lines, never cuts one.
        for (h in 100..600 step 7) {
            val p = WidgetLayoutPolicy.cardPlan(h, h % 2 == 0, CardNeeds(2, 2, true, true, 4))
            assertTrue(p.title in 1..2 && p.command in setOf(0, 2) && p.rows in 0..4)
        }
    }

    @Test fun aShortCommandReadsInTheQuestionAndALongOneUnderIt() {
        assertEquals(WidgetLayoutPolicy.Ask("Run npm install?", ""),
            WidgetLayoutPolicy.ask("npm install", "Run %s?", "Run this command?"))
        val long = "npm run build -- --mode production --watch=false"
        assertEquals(WidgetLayoutPolicy.Ask("Run this command?", long),
            WidgetLayoutPolicy.ask(long, "Run %s?", "Run this command?"))
        // No file or command: the plain question, nothing under it.
        assertEquals(WidgetLayoutPolicy.Ask("Allow webfetch?", ""), WidgetLayoutPolicy.ask("", null, "Allow webfetch?"))
        // A kind without a template keeps its question and shows what it names under it.
        assertEquals(WidgetLayoutPolicy.Ask("Allow this?", "x"), WidgetLayoutPolicy.ask("x", null, "Allow this?"))
    }
    @Test fun workingSinceReadsInMinutesThenHours() {
        assertEquals(null, WidgetElapsedPolicy.sinceUnit(1_000_000, 0))
        assertEquals(null, WidgetElapsedPolicy.sinceUnit(1_000_000, 960_000))
        assertEquals("min" to 3, WidgetElapsedPolicy.sinceUnit(1_000_000, 1_000_000 - 3 * 60_000L - 5_000))
        assertEquals("hour" to 2, WidgetElapsedPolicy.sinceUnit(10_000_000, 10_000_000 - 2 * 3_600_000L - 60_000))
    }

    @Test fun rowsSortNeedsYouThenWorkingThenTheRest() {
        assertEquals(listOf(0, 1, 2), listOf(true to false, false to true, false to false)
            .map { WidgetStatusRules.rank(it.first, it.second) })
        // A conversation that needs you is needs-you even while its turn is still busy.
        assertEquals(0, WidgetStatusRules.rank(true, true))
    }

    @Test fun aTurnWaitingForYouIsNotAlsoCountedAsWorking() {
        assertEquals(1, WidgetStatusRules.workingCount(2, 1))
        assertEquals(0, WidgetStatusRules.workingCount(1, 1))
        assertEquals(0, WidgetStatusRules.workingCount(0, 1))
        assertEquals(3, WidgetStatusRules.workingCount(3, 0))
    }

    @Test fun leadWordsNeverDuplicate() {
        assertEquals(WidgetLayoutPolicy.Lead.NEEDS_YOU, WidgetStatusRules.lead(true, true, true))
        assertEquals(WidgetLayoutPolicy.Lead.OFFLINE, WidgetStatusRules.lead(false, true, true))
        assertEquals(WidgetLayoutPolicy.Lead.WORKING, WidgetStatusRules.lead(false, true, false))
        assertEquals(WidgetLayoutPolicy.Lead.READY, WidgetStatusRules.lead(false, false, false))
        assertEquals("Ready · my-app", WidgetLayoutPolicy.join("Ready", "my-app"))
        assertEquals("my-app", WidgetLayoutPolicy.join("", "my-app", " "))
        assertEquals("", WidgetLayoutPolicy.join("", ""))
    }

    @Test fun estimatedLinesErrHighAndAreCapped() {
        assertEquals(0, WidgetLayoutPolicy.estimateLines("", 150f, 16f, true, 3))
        assertEquals(1, WidgetLayoutPolicy.estimateLines("Fix it", 150f, 16f, true, 3))
        assertEquals(2, WidgetLayoutPolicy.estimateLines("Fix the flaky checkout test", 150f, 16f, true, 3))
        val long = "Create outside-test.txt with a greeting now please"
        assertEquals(3, WidgetLayoutPolicy.estimateLines(long, 150f, 16f, true, 3))
        assertEquals(2, WidgetLayoutPolicy.estimateLines(long, 150f, 16f, true, 2))
        assertEquals(1, WidgetLayoutPolicy.estimateLines("Create outside-test.txt with a greeting", 344f, 16f, true, 2))
    }

    @Test fun elapsedTimeReadsLikeAPhone() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val us = java.util.Locale.US
        val now = 1_760_000_000_000L // 2025-10-09 08:53 UTC (a Thursday)
        fun at(ms: Long) = WidgetElapsedPolicy.elapsed(now, now - ms, utc, us)
        assertEquals(WidgetLayoutPolicy.Elapsed(WidgetLayoutPolicy.Ago.NOW), at(30_000))
        assertEquals(WidgetLayoutPolicy.Elapsed(WidgetLayoutPolicy.Ago.MINUTES, 2), at(2 * 60_000))
        assertEquals(WidgetLayoutPolicy.Elapsed(WidgetLayoutPolicy.Ago.MINUTES, 59), at(59 * 60_000 + 59_000))
        assertEquals(WidgetLayoutPolicy.Elapsed(WidgetLayoutPolicy.Ago.HOURS, 1), at(61 * 60_000))
        assertEquals(WidgetLayoutPolicy.Elapsed(WidgetLayoutPolicy.Ago.YESTERDAY), at(12 * 3_600_000))
        assertEquals(WidgetLayoutPolicy.Elapsed(WidgetLayoutPolicy.Ago.WEEKDAY, label = "Mon"), at(3 * 86_400_000L))
        assertEquals(WidgetLayoutPolicy.Elapsed(WidgetLayoutPolicy.Ago.DATE, label = "29 Sep"), at(10 * 86_400_000L))
        // A clock that ran backwards never shows a negative time.
        assertEquals(
            WidgetLayoutPolicy.Elapsed(WidgetLayoutPolicy.Ago.NOW),
            WidgetElapsedPolicy.elapsed(now, now + 5_000, utc, us),
        )
    }
}
