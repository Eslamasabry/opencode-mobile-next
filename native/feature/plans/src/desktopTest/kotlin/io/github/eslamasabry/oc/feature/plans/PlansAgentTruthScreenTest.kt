package io.github.eslamasabry.oc.feature.plans

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.usage.SignInMethod
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SignedInOn
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.feature.plans.presentation.PlanAgentTruth
import io.github.eslamasabry.oc.feature.plans.presentation.PlansActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansUi
import io.github.eslamasabry.oc.feature.plans.presentation.plansUi
import io.github.eslamasabry.oc.feature.plans.ui.PlansScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Usage › Plans when Claude Code's reader finds no account: the Plans tab says what the phone's agent says about
 * itself, matching Usage › Limits, the Agents tile and the agent chooser.
 */
@OptIn(ExperimentalTestApi::class)
class PlansAgentTruthScreenTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun localeAfter() = Locale.setDefault(previousLocale)

    private val now = Instant.parse("2026-11-16T12:00:00Z").toEpochMilliseconds()
    private val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)

    private class Actions : PlansActions {
        var checks = 0

        override fun back(): Unit = Unit

        override fun refresh(): Unit = Unit

        override fun setUpCollector(): Unit = Unit

        override fun turnOffCollector(): Unit = Unit

        override fun editThreshold(key: String?): Unit = Unit

        override fun thresholdChanged(value: String): Unit = Unit

        override fun saveThreshold(): Unit = Unit

        override fun useDefaultThreshold(): Unit = Unit

        override fun toggleResetNotice(): Unit = Unit

        override fun dismissEditor(): Unit = Unit

        override fun setAlertsOn(enabled: Boolean): Unit = Unit

        override fun askCollector(profile: ProfileId): Unit = Unit

        override fun fix(key: String): Unit = Unit

        override fun checkAgent() {
            checks++
        }
    }

    @Composable
    private fun Screen(state: PlansUi, actions: PlansActions) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp).height(1000.dp)) { PlansScreen(state, actions) }
            }
        }
    }

    private fun state(truth: PlanAgentTruth?): PlansUi {
        val claude =
            UsageSubscription(
                key = "claude|conn:phone",
                providerId = "claude",
                displayName = "Claude",
                state = SubscriptionState.NotSignedIn,
                signedInOn = listOf(SignedInOn(phone, SignInState.NotSignedIn, SignInMethod.Plan)),
                fix = UsageFix.SignIn,
                fixConnection = phone,
            )
        return plansUi(
            UsageSnapshot(subscriptions = listOf(claude), connections = listOf(phone), loaded = true),
            refreshing = false,
            nowMs = now,
            agents = if (truth == null) emptyMap() else mapOf("claude" to truth),
        )
    }

    @Test
    fun `SIGNIN-P-1 unconfirmed check never says Not signed in and offers Check Claude Code again`() =
        runComposeUiTest {
            val actions = Actions()
            setContent { Screen(state(PlanAgentTruth.NotConfirmed("Claude Code")), actions) }
            onNodeWithText("Sign-in not confirmed").assertExists()
            onNodeWithText("Not signed in", substring = true, ignoreCase = true).assertDoesNotExist()
            onNodeWithText("Claude").performClick()
            onNodeWithText("Check Claude Code again").performClick()
            assertEquals(1, actions.checks)
        }

    @Test
    fun `SIGNIN-P-2 signed out agent keeps Not signed in`() =
        runComposeUiTest {
            setContent { Screen(state(PlanAgentTruth.SignedOut), Actions()) }
            onNodeWithText("Not signed in").assertExists()
        }

    @Test
    fun `SIGNIN-P-3 signed in agent with no numbers says signed in, never Not signed in`() =
        runComposeUiTest {
            setContent { Screen(state(PlanAgentTruth.SignedIn), Actions()) }
            onNodeWithText("Signed in, but its numbers can't be read yet").assertExists()
            onNodeWithText("Not signed in", substring = true, ignoreCase = true).assertDoesNotExist()
        }

    @Test
    fun `SIGNIN-P-4 weekly limit says the limit and reset, never Not signed in`() =
        runComposeUiTest {
            setContent { Screen(state(PlanAgentTruth.Limit("Nov 20, 1:00 AM")), Actions()) }
            onNodeWithText("Plan limit reached · resets Nov 20, 1:00 AM").assertExists()
            onNodeWithText("Not signed in", substring = true, ignoreCase = true).assertDoesNotExist()
        }
}
