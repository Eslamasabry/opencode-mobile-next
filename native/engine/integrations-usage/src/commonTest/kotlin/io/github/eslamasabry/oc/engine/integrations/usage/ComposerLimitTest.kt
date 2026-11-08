package io.github.eslamasabry.oc.engine.integrations.usage

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

// The composer's "Approaching limit" strip (owner 2026-11-08). NOW (shared with the merge test) is Tuesday
// 2026-10-13 12:00 UTC.

private val CLAUDE_ON_PC = ComposerLimitTarget("anthropic", "claude-sonnet-4-5", connectionId = "pc")

private fun windows(five: Double, week: Double, fiveIn: Duration = 2.hours + 10.minutes, weekIn: Duration = 3.days) =
    listOf(
        UsageWindow("five_hour", five, durationSeconds = 18_000, resetsAt = NOW + fiveIn),
        UsageWindow("seven_day", week, durationSeconds = 604_800, resetsAt = NOW + weekIn),
    )

private fun snapshot(vararg readings: UsageReading, connections: List<UsageConnection> = listOf(PC, LAPTOP)) =
    mergeUsageSubscriptions(now = NOW, connections = connections, readings = readings.toList())

private fun claude(five: Double, week: Double = 10.0, connection: String = "pc", account: String? = "acct-1") =
    reading("claude", connection, account = account, windows = windows(five, week))

class ComposerLimitTest {
    private fun notice(snap: UsageSnapshot, target: ComposerLimitTarget? = CLAUDE_ON_PC, zone: String? = "UTC", now: Instant = NOW) =
        composerLimitNotice(snap, target, now, zone)

    @Test
    fun `S24-LIMIT-001 the strip starts at 80 percent used and says Limit reached at 100`() {
        assertNull(notice(snapshot(claude(79.9))))
        assertEquals(ComposerLimitLevel.Approaching, notice(snapshot(claude(80.0)))?.level)
        assertEquals(ComposerLimitLevel.Approaching, notice(snapshot(claude(99.9)))?.level)
        assertEquals(ComposerLimitLevel.Reached, notice(snapshot(claude(100.0)))?.level)
    }

    @Test
    fun `S24-LIMIT-002 the tightest window decides, not the first one`() {
        val n = assertNotNull(notice(snapshot(claude(five = 40.0, week = 86.0))))
        assertEquals("seven_day", n.windowId)
        assertEquals(86.0, n.usedPercent)
        assertEquals(ComposerLimitReset.OnDay(isoWeekday = 5, hour = 12, minute = 0), n.reset) // Friday 12:00
    }

    @Test
    fun `S24-LIMIT-003 under a day the reset is a countdown, rounded up to the minute`() {
        assertEquals(ComposerLimitReset.Within(2, 10), notice(snapshot(claude(90.0)))?.reset)
        assertEquals(ComposerLimitReset.Within(2, 10), composerLimitReset(NOW + 2.hours + 9.minutes + 1.seconds, NOW, "UTC"))
        assertEquals(ComposerLimitReset.Within(0, 1), composerLimitReset(NOW + 20.seconds, NOW, "UTC"))
        assertEquals(ComposerLimitReset.Within(23, 59), composerLimitReset(NOW + 1.days - 1.minutes, NOW, null))
    }

    @Test
    fun `S24-LIMIT-004 a later reset names the weekday and time on the phone's own clock`() {
        val friday5am = Instant.parse("2026-10-16T05:00:00Z")
        assertEquals(ComposerLimitReset.OnDay(5, 5, 0), composerLimitReset(friday5am, NOW, "UTC"))
        // The same instant is Friday 14:00 in Tokyo (UTC+9).
        assertEquals(ComposerLimitReset.OnDay(5, 14, 0), composerLimitReset(friday5am, NOW, "Asia/Tokyo"))
        // A monthly window weeks away keeps its date.
        val later = NOW + 20.days
        assertEquals(ComposerLimitReset.OnDate(later.toEpochMilliseconds()), composerLimitReset(later, NOW, "UTC"))
        // No readable zone: no made-up clock.
        assertNull(composerLimitReset(friday5am, NOW, null))
        assertNull(composerLimitReset(friday5am, NOW, "Not/AZone"))
    }

    @Test
    fun `S24-LIMIT-005 at the limit the reset is when every full window is clear again`() {
        val r = reading("claude", "pc", account = "acct-1", windows = windows(five = 100.0, week = 100.0))
        val n = assertNotNull(notice(snapshot(r)))
        assertEquals(ComposerLimitLevel.Reached, n.level)
        assertEquals(NOW + 3.days, n.resetsAt)
    }

    @Test
    fun `S24-LIMIT-006 no reading means no strip`() {
        assertNull(notice(UsageSnapshot()))
        assertNull(notice(snapshot(claude(95.0)), target = null))
        // Another provider's numbers never stand in.
        assertNull(notice(snapshot(reading("codex", "pc", windows = windows(95.0, 95.0)))))
        // Signed out, or a reader that failed: nothing read, nothing shown.
        assertNull(notice(snapshot(reading("claude", "pc", status = UsageReadingStatus.NotSignedIn))))
        assertNull(notice(snapshot(reading("claude", "pc", windows = windows(95.0, 95.0), problem = UsageProblem.Broken))))
        // A window whose reset has passed holds old numbers.
        assertNull(notice(snapshot(claude(95.0, week = 10.0)), now = NOW + 3.hours))
    }

    @Test
    fun `S24-LIMIT-007 with several accounts only the one signed in on this connection counts`() {
        val mine = claude(five = 20.0, connection = "pc", account = "acct-1")
        val other = claude(five = 97.0, connection = "laptop", account = "acct-2")
        assertNull(notice(snapshot(mine, other)))
        val onLaptop = assertNotNull(notice(snapshot(mine, other), CLAUDE_ON_PC.copy(connectionId = "laptop")))
        assertEquals(97.0, onLaptop.usedPercent)
        // Two accounts, and the conversation's connection has neither: unknown, so nothing.
        assertNull(notice(snapshot(mine, other), CLAUDE_ON_PC.copy(connectionId = "elsewhere")))
        // One account read elsewhere is still this provider's plan.
        assertNotNull(notice(snapshot(other), CLAUDE_ON_PC))
    }

    @Test
    fun `S24-LIMIT-008 a window kept for one model family applies only to that model`() {
        val opusWeek = UsageWindow("seven_day_opus", 95.0, durationSeconds = 604_800, resetsAt = NOW + 3.days, label = "Opus weekly")
        val r = reading("claude", "pc", account = "acct-1", windows = windows(10.0, 10.0) + opusWeek)
        assertNull(notice(snapshot(r), CLAUDE_ON_PC))
        assertEquals("seven_day_opus", notice(snapshot(r), CLAUDE_ON_PC.copy(modelId = "claude-opus-4-1"))?.windowId)
        assertNull(notice(snapshot(r), CLAUDE_ON_PC.copy(modelId = null)))
    }

    @Test
    fun `S24-LIMIT-009 the conversation's provider id maps onto Usage's names`() {
        assertEquals("claude", composerLimitProvider("anthropic"))
        assertEquals("codex", composerLimitProvider("openai"))
        assertEquals("gemini", composerLimitProvider("google"))
        assertEquals("antigravity", composerLimitProvider("antigravity"))
        val codex = reading("codex", "pc", windows = windows(85.0, 10.0))
        assertEquals("codex", notice(snapshot(codex), ComposerLimitTarget("openai", "gpt-5", "pc"))?.providerId)
        val gemini = reading("gemini", "pc", windows = windows(85.0, 10.0))
        assertEquals("gemini", notice(snapshot(gemini), ComposerLimitTarget("google", "gemini-2.5-pro", "pc"))?.providerId)
    }

    @Test
    fun `S24-LIMIT-010 dismiss hides a window until its next threshold or its reset`() {
        val d = ComposerLimitDismissals()
        val approaching = assertNotNull(notice(snapshot(claude(85.0))))
        d.dismiss(approaching)
        assertEquals(true, d.hides(approaching))
        assertEquals(true, d.hides(assertNotNull(notice(snapshot(claude(92.0))))))
        // Next threshold: Limit reached shows again.
        assertEquals(false, d.hides(assertNotNull(notice(snapshot(claude(100.0))))))
        // The window reset and filled up again: a new reset time shows again.
        val next = reading("claude", "pc", account = "acct-1", windows = windows(85.0, 10.0, fiveIn = 7.hours))
        assertEquals(false, d.hides(assertNotNull(notice(snapshot(next)))))
        // Another window is not covered.
        assertEquals(false, d.hides(assertNotNull(notice(snapshot(claude(10.0, week = 85.0))))))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `S24-LIMIT-011 the presenter follows readings, dismissal and the clock`() = runTest {
        val snap = MutableStateFlow(snapshot(claude(85.0)))
        val presenter = ComposerLimitPresenter(snap, virtualClock(), { "UTC" }, tick = 1.minutes)
        presenter.notice(MutableStateFlow(CLAUDE_ON_PC)).test {
            val first = assertNotNull(awaitItem())
            assertEquals(ComposerLimitReset.Within(2, 10), first.reset)
            advanceTimeBy(1.minutes + 1.milliseconds)
            val second = assertNotNull(awaitItem())
            assertEquals(ComposerLimitReset.Within(2, 9), second.reset)
            presenter.dismiss(second)
            assertNull(awaitItem())
            snap.value = snapshot(claude(100.0))
            assertEquals(ComposerLimitLevel.Reached, awaitItem()?.level)
            snap.value = UsageSnapshot()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.virtualClock(): Clock =
        object : Clock {
            override fun now(): Instant = NOW + currentTime.milliseconds
        }
}
