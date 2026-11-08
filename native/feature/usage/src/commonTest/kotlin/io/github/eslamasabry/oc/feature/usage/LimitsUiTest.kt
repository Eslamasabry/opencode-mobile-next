package io.github.eslamasabry.oc.feature.usage

import io.github.eslamasabry.oc.engine.integrations.usage.ApiKeyAccount
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.feature.usage.presentation.LimitAccountRef
import io.github.eslamasabry.oc.feature.usage.presentation.LimitMath
import io.github.eslamasabry.oc.feature.usage.presentation.LimitPace
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsPhase
import io.github.eslamasabry.oc.feature.usage.presentation.ResetIn
import io.github.eslamasabry.oc.feature.usage.presentation.limitsUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Usage › Limits (B-159): the pace marker, the reset countdown and the snapshot → screen mapping. */
class LimitsUiTest {
    private val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilliseconds()
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `B-159 the pace marker is elapsed over length, so even spending leaves one minus that`() {
        // A five-hour window that resets in 1 h 15 m has run for 3 h 45 m: 75 % elapsed, even pace leaves 25 %.
        val resets = now + hour + 15 * minute
        assertEquals(0.75, LimitMath.elapsed(now, resets, LimitMath.FIVE_HOURS_MS)!!, 1e-9)
        assertEquals(0.25, LimitMath.evenLeft(now, resets, LimitMath.FIVE_HOURS_MS)!!, 1e-9)
        // A week that resets in 3.5 days is half gone.
        assertEquals(0.5, LimitMath.elapsed(now, now + 84 * hour, LimitMath.WEEK_MS)!!, 1e-9)
        // A reset further away than the window is long has just started: nothing elapsed, never negative.
        assertEquals(0.0, LimitMath.elapsed(now, now + 6 * hour, LimitMath.FIVE_HOURS_MS)!!, 1e-9)
    }

    @Test
    fun `B-159 no pace is stated without a reset time, without a length, or after the reset passed`() {
        assertNull(LimitMath.elapsed(now, null, LimitMath.WEEK_MS))
        assertNull(LimitMath.elapsed(now, now + hour, null))
        assertNull(LimitMath.elapsed(now, now + hour, 0))
        assertNull(LimitMath.elapsed(now, now, LimitMath.WEEK_MS))
        assertNull(LimitMath.elapsed(now, now - minute, LimitMath.WEEK_MS))
        assertEquals(LimitPace.Unknown, LimitMath.pace(0.5, null))
    }

    @Test
    fun `B-159 less left than an even pace is ahead of pace, on pace is not`() {
        assertEquals(LimitPace.Ahead, LimitMath.pace(leftShare = 0.20, evenLeft = 0.25))
        assertEquals(LimitPace.WithinPace, LimitMath.pace(0.25, 0.25))
        // Half a percent of slack: a reading exactly on pace does not flip to Ahead on rounding.
        assertEquals(LimitPace.WithinPace, LimitMath.pace(0.246, 0.25))
        assertEquals(LimitPace.WithinPace, LimitMath.pace(0.9, 0.25))
    }

    @Test
    fun `B-159 the window length is what the source reported, else what its kind fixes, never guessed`() {
        assertEquals(3_600_000L, LimitMath.lengthMs(UsageWindow("tokens", 10.0, durationSeconds = 3_600)))
        assertEquals(LimitMath.FIVE_HOURS_MS, LimitMath.lengthMs(UsageWindow("five_hour", 10.0)))
        assertEquals(LimitMath.WEEK_MS, LimitMath.lengthMs(UsageWindow("seven_day", 10.0)))
        // A month has no fixed length; an unknown window has none either.
        assertNull(LimitMath.lengthMs(UsageWindow("monthly", 10.0)))
        assertNull(LimitMath.lengthMs(UsageWindow("mcp", 10.0)))
    }

    @Test
    fun `B-159 the reset countdown splits whole minutes into days, hours and minutes`() {
        assertEquals(ResetIn.Left(0, 1, 38), LimitMath.resetIn(now, now + 98 * minute + 59_000))
        assertEquals(ResetIn.Left(2, 4, 0), LimitMath.resetIn(now, now + 52 * hour))
        assertEquals(ResetIn.Left(0, 0, 12), LimitMath.resetIn(now, now + 12 * minute))
        assertEquals(ResetIn.UnderMinute, LimitMath.resetIn(now, now + 59_000))
        assertEquals(ResetIn.Passed, LimitMath.resetIn(now, now))
        assertEquals(ResetIn.Passed, LimitMath.resetIn(now, now - hour))
    }

    private val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)
    private val server = UsageConnection("srv", "Home server", UsageConnectionKind.OpenCodeServer)

    private fun window(id: String, used: Double, resetsIn: Long?, seconds: Long? = null, label: String = "") =
        UsageWindow(
            id,
            used,
            durationSeconds = seconds,
            resetsAt = resetsIn?.let { Instant.fromEpochMilliseconds(now + it) },
            label = label,
        )

    private fun sub(
        key: String,
        windows: List<UsageWindow>,
        state: SubscriptionState = SubscriptionState.Ok,
        fetchedAgo: Long = minute,
    ) =
        UsageSubscription(
            key = key,
            providerId = key,
            displayName = key.replaceFirstChar { it.uppercase() },
            state = state,
            windows = windows,
            fetchedAt = Instant.fromEpochMilliseconds(now - fetchedAgo),
            readFrom = server,
        )

    @Test
    fun `B-159 one window per length is a headline, several in one length are listed compactly`() {
        val claude =
            sub(
                "claude",
                listOf(
                    window("five_hour", 40.0, hour + 38 * minute, 18_000),
                    window("seven_day", 30.0, 3 * 24 * hour, 604_800),
                    window("seven_day_opus", 70.0, 3 * 24 * hour, 604_800, label = "Opus"),
                ),
            )
        val ui = limitsUi(UsageSnapshot(subscriptions = listOf(claude), loaded = true), false, now, "UTC")
        assertEquals(LimitsPhase.List, ui.phase)
        val groups = ui.accounts.single().groups
        assertEquals(listOf(UsageWindowKind.FiveHour, UsageWindowKind.Weekly), groups.map { it.kind })
        assertFalse(groups[0].compact)
        assertTrue(groups[1].compact)
        assertEquals(listOf(70, 30), groups[1].windows.map { it.leftPercent })
        val five = groups[0].windows.single()
        assertEquals(60, five.leftPercent)
        assertEquals(ResetIn.Left(0, 1, 38), five.resetIn)
        // 3 h 22 m of 5 h gone: even pace leaves 32.7 %; 60 % is left, so within pace.
        assertEquals(1 - (3 * 60 + 22) / 300.0, five.evenLeft!!, 1e-9)
        assertEquals(LimitPace.WithinPace, five.pace)
        // Absolute times are dated on the given zone for Details.
        assertEquals("13:38", five.resetsAt!!.clock)
        assertEquals("2026-11-07", five.resetsAt!!.day)
    }

    @Test
    fun `B-159 a provider that reports nothing says so and shows no numbers`() {
        val glm = sub("glm", emptyList(), SubscriptionState.NoLimits)
        val ui =
            limitsUi(
                UsageSnapshot(
                    subscriptions = listOf(glm),
                    apiKeyAccounts = listOf(ApiKeyAccount("anthropic", "Anthropic", phone)),
                    loaded = true,
                ),
                false,
                now,
            )
        assertTrue(ui.accounts.single().reportsNothing)
        assertEquals("Anthropic", ui.keys.single().name)
        assertTrue(ui.keys.single().connectionIsPhone)
    }

    @Test
    fun `B-159 old, offline or past-reset numbers are marked stale so their age shows`() {
        val fresh = sub("codex", listOf(window("5h", 10.0, hour)), fetchedAgo = 2 * minute)
        val old = sub("claude", listOf(window("5h", 10.0, hour)), fetchedAgo = LimitMath.STALE_AFTER_MS + minute)
        val offline = sub("glm", listOf(window("5h", 10.0, hour)), SubscriptionState.OfflineStale)
        val pastReset = sub("minimax", listOf(window("5h", 10.0, -minute)))
        val ui =
            limitsUi(UsageSnapshot(subscriptions = listOf(fresh, old, offline, pastReset), loaded = true), false, now)
        val stale = ui.accounts.associate { it.key to it.stale }
        assertEquals(mapOf("codex" to false, "claude" to true, "glm" to true, "minimax" to true), stale)
        // A passed reset gives no countdown and no pace, only "it has reset".
        val passed = ui.accounts.first { it.key == "minimax" }.groups.single().windows.single()
        assertEquals(ResetIn.Passed, passed.resetIn)
        assertNull(passed.evenLeft)
    }

    @Test
    fun `B-159 before the first answer it is loading, after an empty answer it is empty`() {
        assertEquals(LimitsPhase.Loading, limitsUi(UsageSnapshot(), true, now).phase)
        assertEquals(LimitsPhase.Empty, limitsUi(UsageSnapshot(loaded = true), false, now).phase)
    }

    @Test
    fun `B-159 an unreadable time zone leaves the absolute times out instead of writing UTC`() {
        val codex = sub("codex", listOf(window("5h", 10.0, hour)))
        val ui = limitsUi(UsageSnapshot(subscriptions = listOf(codex), loaded = true), false, now, "Not/AZone")
        val account = ui.accounts.single()
        assertNull(account.fetchedAt)
        assertNull(account.groups.single().windows.single().resetsAt)
    }

    private fun accountOf(ref: String?, read: Boolean = true) =
        limitsUi(
            UsageSnapshot(
                subscriptions =
                    listOf(
                        UsageSubscription(
                            key = "antigravity|x",
                            providerId = "antigravity",
                            displayName = "Antigravity",
                            state = SubscriptionState.Ok,
                            accountRef = ref,
                            fetchedAt = if (read) Instant.fromEpochMilliseconds(now - minute) else null,
                        )
                    ),
                loaded = true,
            ),
            refreshing = false,
            nowMs = now,
        ).accounts.single().account

    @Test
    fun `B-183 a readable account is named, an opaque hash reads as known, none reads as not known`() {
        assertEquals(LimitAccountRef.Named("altaf.alu@gmail.com"), accountOf("altaf.alu@gmail.com"))
        assertEquals(LimitAccountRef.Named("work-account"), accountOf("work-account"))
        assertEquals(LimitAccountRef.Known, accountOf("0123456789abcdef".repeat(4)))
        assertEquals(LimitAccountRef.Known, accountOf("deadbeef"), "a bare hex reference is not shown")
        assertEquals(LimitAccountRef.Known, accountOf("ab"), "too short to mean anything")
        assertEquals(LimitAccountRef.Unknown, accountOf(null))
        assertNull(accountOf(null, read = false), "nothing read and nothing known: no row")
        assertEquals(LimitAccountRef.Named("a@b.co"), accountOf("a@b.co", read = false), "a known account shows unread")
    }

    @Test
    fun `B-183 the shown account is cleaned of control characters and capped`() {
        assertEquals(LimitAccountRef.Named("me @corp.io"), accountOf("me\u202e@corp.io"))
        val long = accountOf("x".repeat(100) + "@corp.io") as LimitAccountRef.Named
        assertEquals(64, long.text.length)
    }
}
