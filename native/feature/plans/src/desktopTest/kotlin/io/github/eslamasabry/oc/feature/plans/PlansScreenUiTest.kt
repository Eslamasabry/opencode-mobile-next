package io.github.eslamasabry.oc.feature.plans

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.ApiKeyAccount
import io.github.eslamasabry.oc.engine.integrations.usage.SignInMethod
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageProblem
import io.github.eslamasabry.oc.engine.integrations.usage.UsageReading
import io.github.eslamasabry.oc.engine.integrations.usage.UsageReadingStatus
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.engine.integrations.usage.mergeUsageSubscriptions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPhase
import io.github.eslamasabry.oc.feature.plans.presentation.PlansUi
import io.github.eslamasabry.oc.feature.plans.presentation.plansUi
import io.github.eslamasabry.oc.feature.plans.ui.PlansScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The real Plans screen over a real merge, found by visible text. */
@OptIn(ExperimentalTestApi::class)
class PlansScreenUiTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)
    private val pc = UsageConnection("pc", "Workstation", UsageConnectionKind.OpenCodeServer)

    private class Recorder : PlansActions {
        val calls = mutableListOf<String>()

        override fun back() {
            calls += "back"
        }

        override fun refresh() {
            calls += "refresh"
        }
    }

    @Composable
    private fun Themed(locale: String = "en", content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) { content() }
            }
        }
    }

    private fun reading(provider: String, connection: String, five: Double = 20.0, problem: UsageProblem? = null) =
        UsageReading(
            providerId = provider,
            displayName = if (provider == "claude") "Claude" else "Codex",
            connectionId = connection,
            status = UsageReadingStatus.Ok,
            fetchedAt = now - 1.minutes,
            windows = listOf(UsageWindow("five_hour", five, durationSeconds = 18_000, resetsAt = now + 2.hours)),
            accountRef = null,
            plan = "plus",
            method = SignInMethod.Plan,
            problem = problem,
        )

    private fun state(): PlansUi =
        plansUi(
            mergeUsageSubscriptions(
                now = now,
                connections = listOf(phone, pc),
                readings = listOf(reading("claude", UsageConnection.PHONE_ID, five = 40.0), reading("claude", "pc", five = 90.0)),
            ),
            refreshing = false,
            nowMs = now.toEpochMilliseconds(),
        )

    @Test
    fun `B-NAV-080 usage rows from the phone source are labelled This phone`() =
        runComposeUiTest {
            val ui = state()
            assertEquals(PlansPhase.List, ui.phase)
            setContent { Themed { PlansScreen(ui, Recorder()) } }
            onNodeWithText("Claude · This phone").assertExists()
            onNodeWithText("Claude · Workstation").assertExists()
            onNodeWithText("This phone: numbers read here").assertExists()
        }

    @Test
    fun `B-STATE-154 each plan says how close it is and what is left in plain words`() =
        runComposeUiTest {
            val actions = Recorder()
            setContent { Themed { PlansScreen(state(), actions) } }
            onNodeWithText("Getting close to the limit").assertExists()
            onNodeWithText("Plenty left").assertExists()
            onNodeWithText("60% left · resets in 2 h").assertExists()
            onNodeWithText("Refresh").performClick()
            assertEquals(listOf("refresh"), actions.calls)
        }

    @Test
    fun `B-STATE-154 a broken reader offers Try again as a visible row`() =
        runComposeUiTest {
            val ui =
                plansUi(
                    mergeUsageSubscriptions(now = now, connections = listOf(pc), readings = listOf(reading("codex", "pc", problem = UsageProblem.Broken))),
                    refreshing = false,
                    nowMs = now.toEpochMilliseconds(),
                )
            val actions = Recorder()
            setContent { Themed { PlansScreen(ui, actions) } }
            onNodeWithText("The numbers couldn't be read this time").assertExists()
            onNodeWithText("Try again").performClick()
            assertEquals(listOf("refresh"), actions.calls)
        }

    @Test
    fun `B-STATE-153 nothing signed in shows the add list in plain words`() =
        runComposeUiTest {
            val ui =
                plansUi(
                    mergeUsageSubscriptions(now = now, connections = listOf(pc), readings = emptyList()),
                    refreshing = false,
                    nowMs = now.toEpochMilliseconds(),
                )
            assertEquals(PlansPhase.Empty, ui.phase)
            setContent { Themed { PlansScreen(ui, Recorder()) } }
            onNodeWithText("No plans signed in yet").assertExists()
        }

    @Test
    fun `B-STATE-154 an API key row says it has no plan limits`() =
        runComposeUiTest {
            val snapshot =
                mergeUsageSubscriptions(now = now, connections = listOf(pc), readings = emptyList()).copy(
                    apiKeyAccounts = listOf(ApiKeyAccount("claude", "Claude", pc, viaOpenCode = true)),
                    loaded = true,
                )
            setContent { Themed { PlansScreen(plansUi(snapshot, false, now.toEpochMilliseconds()), Recorder()) } }
            onNodeWithText("Claude's plan limits can't be read through OpenCode").assertExists()
        }

    @Test
    fun `B-NAV-080 the Arabic screen labels the phone source in Arabic`() =
        runComposeUiTest {
            setContent { Themed(locale = "ar") { PlansScreen(state(), Recorder()) } }
            onNodeWithText("Claude · هذا الهاتف").assertExists()
            onNodeWithText("الخطط والحدود").assertExists()
        }
}
