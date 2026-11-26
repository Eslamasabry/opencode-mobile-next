package io.github.eslamasabry.oc.feature.usage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsUi
import io.github.eslamasabry.oc.feature.usage.presentation.limitsUi
import io.github.eslamasabry.oc.feature.usage.ui.LimitsContent
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Usage › Limits pace bend chart and run-out forecast (B-508, no-history option A).
 */
@OptIn(ExperimentalTestApi::class)
class UsagePaceBendTest {
    // Saturday 2026-11-07 12:00 UTC.
    private val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilliseconds()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)

    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() = Locale.setDefault(previousLocale)

    private object NoActions : LimitsActions {
        override fun refresh() = Unit
    }

    private fun sub(key: String, name: String, vararg windows: UsageWindow): UsageSubscription =
        UsageSubscription(
            key = key,
            providerId = key,
            displayName = name,
            state = SubscriptionState.Ok,
            windows = windows.toList(),
            fetchedAt = Instant.fromEpochMilliseconds(now - minute),
            readFrom = phone,
        )

    private fun state(vararg subs: UsageSubscription, zone: String? = "UTC"): LimitsUi =
        limitsUi(
            snapshot = UsageSnapshot(subscriptions = subs.toList(), loaded = true),
            refreshing = false,
            nowMs = now,
            timezone = zone,
        )

    @Composable
    private fun Screen(ui: LimitsUi) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                Box(Modifier.width(360.dp).height(1_800.dp)) { LimitsContent(ui, NoActions) }
            }
        }
    }

    @Test
    fun `B-508 on course to last shows plain lasts-until-reset sentence and bend chart`() =
        runDesktopComposeUiTest(360, 1_800) {
            // 7-day window, 3.5d elapsed (50%), 30% used (70% left): projects 40% left at reset.
            val weekly = UsageWindow("seven_day", 30.0, 604_800, Instant.fromEpochMilliseconds(now + 84 * hour))
            setContent { Screen(state(sub("claude", "Claude", weekly))) }
            onNodeWithText("At this rate it lasts until the reset.").assertExists()
            onNodeWithTag("pace-bend-chart", useUnmergedTree = true).assertExists()
            onNodeWithText("runs out", substring = true).assertDoesNotExist()
        }

    @Test
    fun `B-508 ahead of pace shows run-out time, gap, needed-to-last rate, hint and details note`() =
        runDesktopComposeUiTest(360, 1_800) {
            // Codex weekly: 2d elapsed of 7d, 50% used (50% left), 5d to reset.
            // Average since reset = 25%/day; needed to last = 50%/5d = 10%/day (NOT flat 14%/day).
            // Runs out in 2d = Monday 2026-11-09 12:00 UTC, 3d before reset.
            val codexWeek = UsageWindow("weekly", 50.0, 604_800, Instant.fromEpochMilliseconds(now + 5 * day))
            // Claude weekly has plenty of room: 3d elapsed of 7d, 20% used (80% left).
            val claudeWeek = UsageWindow("seven_day", 20.0, 604_800, Instant.fromEpochMilliseconds(now + 4 * day))
            setContent {
                Screen(state(sub("codex", "Codex", codexWeek), sub("claude", "Claude", claudeWeek)))
            }
            onNodeWithText("At this rate it runs out about Monday at 12:00, 3d before the reset.").assertExists()
            onNodeWithText("Since the reset 25% a day · to last, about 10% a day").assertExists()
            // Must compare against needed-to-last (10%), never whole-window flat average (14%).
            onNodeWithText("14% a day", substring = true).assertDoesNotExist()
            // Next-step hint suggests slowing down or switching to Claude which has room.
            onNodeWithText("Slow down on this one, or switch to Claude (80% left).").assertExists()
            onNodeWithTag("pace-bend-chart", useUnmergedTree = true).assertExists()
            // Details explains no-history forecast source.
            onAllNodesWithText("Details")[0].performClick()
            onNodeWithText("Forecast from the average since the reset; not enough readings stored yet.").assertExists()
        }

    @Test
    fun `B-508 five-hour window ahead of pace uses hourly comparison against needed-to-last rate`() =
        runDesktopComposeUiTest(360, 1_800) {
            // 5h window: 2h elapsed, 85% used (15% left), 3h to reset.
            // Since reset = 42.5% -> 43% an hour; needed to last = 15%/3h = 5% an hour (NOT flat 20%/h).
            // Runs out in 21m = today at 12:21, 2h 39m before reset.
            val fiveHour = UsageWindow("five_hour", 85.0, 18_000, Instant.fromEpochMilliseconds(now + 3 * hour))
            setContent { Screen(state(sub("codex", "Codex", fiveHour))) }
            onNodeWithText("At this rate it runs out about today at 12:21, 2h 39m before the reset.").assertExists()
            onNodeWithText("Since the reset 43% an hour · to last, about 5% an hour").assertExists()
            onNodeWithText("20% an hour", substring = true).assertDoesNotExist()
            onNodeWithText("Slow down to make it last until the reset, or switch to another agent with room.")
                .assertExists()
        }

    @Test
    fun `B-508 too little elapsed time or too little use says not enough use yet to tell`() =
        runDesktopComposeUiTest(360, 1_800) {
            // 2% elapsed (< 5%) even with 10% used: do not make a wild run-out guess.
            val earlyWindow =
                UsageWindow("weekly", 10.0, 604_800, Instant.fromEpochMilliseconds(now + 7 * day - 3 * hour))
            // 50% elapsed, only 1% used (<= 1%): too little use to tell.
            val tinyUse = UsageWindow("five_hour", 1.0, 18_000, Instant.fromEpochMilliseconds(now + 150 * minute))
            setContent { Screen(state(sub("codex", "Codex", earlyWindow, tinyUse))) }
            assertEquals(2, onAllNodesWithText("Not enough use yet to tell.").fetchSemanticsNodes().size)
            onNodeWithText("runs out", substring = true).assertDoesNotExist()
        }

    @Test
    fun `B-508 window without reset time shows no pace chart and no forecast`() =
        runDesktopComposeUiTest(360, 1_800) {
            val noReset = UsageWindow("five_hour", 40.0, 18_000, resetsAt = null)
            setContent { Screen(state(sub("claude", "Claude", noReset))) }
            onNodeWithText("60% left").assertExists()
            onNodeWithText("No reset time reported").assertExists()
            onNodeWithTag("pace-bend-chart", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("At this rate", substring = true).assertDoesNotExist()
            onNodeWithText("Not enough use yet to tell", substring = true).assertDoesNotExist()
        }
}
