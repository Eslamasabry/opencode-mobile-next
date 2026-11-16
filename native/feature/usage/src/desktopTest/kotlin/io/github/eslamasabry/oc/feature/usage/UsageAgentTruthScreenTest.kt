package io.github.eslamasabry.oc.feature.usage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SignedInOn
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsUi
import io.github.eslamasabry.oc.feature.usage.presentation.UsageAgentTruth
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
 * Usage › Limits when Claude Code's reader finds no account (signin-followups): the page says what the phone's agent
 * says about itself. "Not signed in" only for an agent that is signed out; a limit says the limit and its reset; an
 * unconfirmed check says so with "Check Claude Code again".
 */
@OptIn(ExperimentalTestApi::class)
class UsageAgentTruthScreenTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun localeAfter() = Locale.setDefault(previousLocale)

    private val now = Instant.parse("2026-11-16T12:00:00Z").toEpochMilliseconds()
    private val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)

    private fun state(agent: UsageAgentTruth?): LimitsUi {
        val claude =
            UsageSubscription(
                key = "claude|conn:phone",
                providerId = "claude",
                displayName = "Claude",
                state = SubscriptionState.NotSignedIn,
                readFrom = phone,
            )
        return limitsUi(
            UsageSnapshot(subscriptions = listOf(claude), connections = listOf(phone), loaded = true),
            refreshing = false,
            nowMs = now,
            timezone = "UTC",
            agents = if (agent == null) emptyMap() else mapOf("claude" to agent),
        )
    }

    private class Actions : LimitsActions {
        var checks = 0

        override fun refresh(): Unit = Unit

        override fun checkAgent() {
            checks++
        }
    }

    @Composable
    private fun Screen(state: LimitsUi, actions: LimitsActions) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                Box(Modifier.width(360.dp).height(900.dp)) { LimitsContent(state, actions) }
            }
        }
    }

    @Test
    fun `SIGNIN-U-1 an agent at its limit shows the limit and its reset, never Not signed in`() =
        runDesktopComposeUiTest(412, 1000) {
            setContent { Screen(state(UsageAgentTruth.Limit("Nov 20, 1:00 AM")), Actions()) }
            onNodeWithText("Plan limit reached · resets Nov 20, 1:00 AM").assertExists()
            onNodeWithText("Not signed in", substring = true).assertDoesNotExist()
        }

    @Test
    fun `SIGNIN-U-2 a limit without a reset time still says limit`() = runDesktopComposeUiTest(412, 1000) {
        setContent { Screen(state(UsageAgentTruth.Limit(null)), Actions()) }
        onNodeWithText("Plan limit reached").assertExists()
        onNodeWithText("Not signed in", substring = true).assertDoesNotExist()
    }

    @Test
    fun `SIGNIN-U-3 a signed out agent says Not signed in`() = runDesktopComposeUiTest(412, 1000) {
        setContent { Screen(state(UsageAgentTruth.SignedOut), Actions()) }
        onNodeWithText("Not signed in", substring = true).assertExists()
    }

    @Test
    fun `SIGNIN-U-4 a reader with no agent row keeps Not signed in`() = runDesktopComposeUiTest(412, 1000) {
        setContent { Screen(state(null), Actions()) }
        onNodeWithText("Not signed in", substring = true).assertExists()
    }

    @Test
    fun `SIGNIN-U-5 an unconfirmed check says so and offers to check again`() = runDesktopComposeUiTest(412, 1000) {
        val actions = Actions()
        setContent { Screen(state(UsageAgentTruth.NotConfirmed("Claude Code")), actions) }
        onNodeWithText("Sign-in not confirmed").assertExists()
        onNodeWithText("Not signed in", substring = true).assertDoesNotExist()
        onNodeWithText("Check Claude Code again").performClick()
        assertEquals(1, actions.checks)
    }

    @Test
    fun `SIGNIN-U-6 a signed in agent whose numbers cannot be read is not called signed out`() =
        runDesktopComposeUiTest(412, 1000) {
            setContent { Screen(state(UsageAgentTruth.SignedIn), Actions()) }
            onNodeWithText("Signed in, but its numbers can't be read yet.").assertExists()
            onNodeWithText("Not signed in", substring = true).assertDoesNotExist()
        }

    @Test
    fun `SIGNIN-U-7 a collapsed subscription with another connection ahead of phone still uses phone truth`() =
        runDesktopComposeUiTest(412, 1000) {
            val actions = Actions()
            val other = UsageConnection("pc", "Workstation", UsageConnectionKind.PaseoServer)
            val claude =
                UsageSubscription(
                    key = "claude|conn:pc",
                    providerId = "claude",
                    displayName = "Claude",
                    state = SubscriptionState.NotSignedIn,
                    readFrom = null,
                    signedInOn =
                        listOf(
                            SignedInOn(other, SignInState.NotSignedIn),
                            SignedInOn(phone, SignInState.NotSignedIn),
                        ),
                )
            val ui =
                limitsUi(
                    UsageSnapshot(subscriptions = listOf(claude), connections = listOf(other, phone), loaded = true),
                    refreshing = false,
                    nowMs = now,
                    timezone = "UTC",
                    agents = mapOf("claude" to UsageAgentTruth.NotConfirmed("Claude Code")),
                )
            setContent { Screen(ui, actions) }
            onNodeWithText("Sign-in not confirmed").assertExists()
            onNodeWithText("Not signed in", substring = true).assertDoesNotExist()
            onNodeWithText("Check Claude Code again").performClick()
            assertEquals(1, actions.checks)
        }

    @Test
    fun `SIGNIN-U-8 a limit stands even when the reader reports no limits`() =
        runDesktopComposeUiTest(412, 1000) {
            val claude =
                UsageSubscription(
                    key = "claude|conn:phone",
                    providerId = "claude",
                    displayName = "Claude",
                    state = SubscriptionState.NoLimits,
                    readFrom = null,
                    signedInOn = listOf(SignedInOn(phone, SignInState.SignedIn)),
                )
            val ui =
                limitsUi(
                    UsageSnapshot(subscriptions = listOf(claude), connections = listOf(phone), loaded = true),
                    refreshing = false,
                    nowMs = now,
                    timezone = "UTC",
                    agents = mapOf("claude" to UsageAgentTruth.Limit("Nov 20, 1:00 AM")),
                )
            setContent { Screen(ui, Actions()) }
            onNodeWithText("Plan limit reached · resets Nov 20, 1:00 AM").assertExists()
            onNodeWithText("Not signed in", substring = true).assertDoesNotExist()
        }
}
