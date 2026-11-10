package io.github.eslamasabry.oc.feature.usage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.ApiKeyAccount
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsUi
import io.github.eslamasabry.oc.feature.usage.presentation.limitsUi
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.usage.ui.LimitsContent
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Usage › Limits (B-159), the real LimitsContent found by the words a person sees. */
@OptIn(ExperimentalTestApi::class)
class LimitsScreenTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() = Locale.setDefault(previousLocale)

    private class Actions : LimitsActions {
        var refreshes = 0

        override fun refresh() {
            refreshes++
        }
    }

    @Composable
    private fun Screen(state: LimitsUi, actions: LimitsActions, locale: String = "en") {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = locale,
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                Box(Modifier.width(360.dp).height(1_400.dp)) { LimitsContent(state, actions) }
            }
        }
    }

    @Test
    fun `B-159 each window reads as percent left with its countdown, several limits in a window sit compactly`() =
        runDesktopComposeUiTest(412, 2600) {
            setContent { Screen(LimitsFixtures.state(), Actions()) }
            onNodeWithText("Claude · Home server", ignoreCase = true).assertExists()
            onNodeWithText("Max plan").assertExists()
            onNodeWithText("60% left").assertExists()
            onNodeWithText("resets in 1h 38m").assertExists()
            onNodeWithText("Within an even pace").assertExists()
            // Two weekly limits: compact cells with their own label, percent and countdown.
            onNodeWithText("Opus").assertExists()
            onNodeWithText("30% left").assertExists()
            onNodeWithText("70% left").assertExists()
            assertEquals(2, onAllNodesWithText("resets in 3d").fetchSemanticsNodes().size)
            // Codex is spending both its windows faster than an even pace.
            assertEquals(2, onAllNodesWithText("Using it faster than an even pace").fetchSemanticsNodes().size)
        }

    @Test
    fun `B-159 a provider without limits says so plainly and no number is invented`() = runDesktopComposeUiTest(412, 2600) {
        setContent { Screen(LimitsFixtures.state(), Actions()) }
        onNodeWithText("This provider doesn't report limits.").assertExists()
        // A key row says where the key lives first (the kit row prefixes its server), then why it has no numbers.
        onNodeWithText("This phone · API key. Keys have no plan limits to show.").assertExists()
    }

    @Test
    fun `B-159 old numbers show their age`() = runDesktopComposeUiTest(412, 2600) {
        setContent { Screen(LimitsFixtures.state(), Actions()) }
        onNodeWithText("Read 2h ago").assertExists()
    }

    @Test
    fun `B-159 pulling down, or the accessibility action, reads the limits again`() = runDesktopComposeUiTest(412, 2600) {
        val actions = Actions()
        setContent { Screen(LimitsFixtures.state(), actions) }
        val refresh =
            onNodeWithTag("usage-limits").fetchSemanticsNode().config[SemanticsActions.CustomActions].first {
                it.label == "Refresh"
            }
        runOnIdle { refresh.action() }
        assertEquals(1, actions.refreshes)
    }

    @Test
    fun `B-159 exact reset times wait under Details`() = runDesktopComposeUiTest(412, 2600) {
        setContent { Screen(LimitsFixtures.state(), Actions()) }
        // Codex comes first (least left); its five hours reset at 15:00 on the device's clock.
        onNodeWithText("5-hour window resets at 2026-11-07 15:00").assertDoesNotExist()
        onAllNodesWithText("Details")[0].performClick()
        onNodeWithText("5-hour window resets at 2026-11-07 15:00").assertExists()
    }

    private fun withAccount(ref: String?): LimitsUi {
        val agy =
            UsageSubscription(
                key = "antigravity|${ref ?: "conn:phone"}",
                providerId = "antigravity",
                displayName = "Antigravity",
                state = SubscriptionState.Ok,
                planLabel = "Google AI Pro",
                accountRef = ref,
                windows = listOf(UsageWindow("5h", 20.0, 18_000, null)),
                fetchedAt = Instant.fromEpochMilliseconds(LimitsFixtures.now - 60_000),
            )
        return limitsUi(UsageSnapshot(subscriptions = listOf(agy), loaded = true), false, LimitsFixtures.now, "UTC")
    }

    @Test
    fun `B-183 Details names the signed-in account email and copies it`() = runDesktopComposeUiTest(412, 2600) {
        setContent { Screen(withAccount("altaf.alu@gmail.com"), Actions()) }
        onNodeWithText("altaf.alu@gmail.com", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Details").performClick()
        onNodeWithText("Account", useUnmergedTree = true).assertExists()
        onNodeWithText("altaf.alu@gmail.com", useUnmergedTree = true).assertExists()
        onNodeWithTag("kit-details-copy-0", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-183 an opaque account hash still reads as Yes, and no account as No`() = runDesktopComposeUiTest(412, 2600) {
        var state by mutableStateOf(withAccount("a".repeat(64)))
        setContent { Screen(state, Actions()) }
        onNodeWithText("Details").performClick()
        onNodeWithText("Yes, an account is known", useUnmergedTree = true).assertExists()
        onNodeWithText("a".repeat(64), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("kit-details-copy-0", useUnmergedTree = true).assertDoesNotExist()
        // No account means another plan key, so the fold starts closed again.
        state = withAccount(null)
        waitForIdle()
        onNodeWithText("Details").performClick()
        onNodeWithText("No account known", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-L10N-010 the limits read in Arabic`() = runDesktopComposeUiTest(412, 2600) {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Screen(LimitsFixtures.state(), Actions(), "ar") }
        onNodeWithText("متبقٍّ 60%").assertExists()
    }
}

/** One snapshot with every kind of account the Limits view shows, read at a fixed moment. */
internal object LimitsFixtures {
    val now: Long = Instant.parse("2026-11-07T12:00:00Z").toEpochMilliseconds()
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private val server = UsageConnection("srv", "Home server", UsageConnectionKind.OpenCodeServer)
    private val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)

    private fun window(id: String, used: Double, resetsIn: Long, seconds: Long, label: String = "") =
        UsageWindow(id, used, seconds, Instant.fromEpochMilliseconds(now + resetsIn), label)

    private fun at(ago: Long) = Instant.fromEpochMilliseconds(now - ago)

    val snapshot: UsageSnapshot =
        UsageSnapshot(
            subscriptions =
                listOf(
                    UsageSubscription(
                        key = "claude",
                        providerId = "claude",
                        displayName = "Claude",
                        state = SubscriptionState.Ok,
                        planLabel = "Max",
                        windows =
                            listOf(
                                window("five_hour", 40.0, HOUR + 38 * MINUTE, 18_000),
                                window("seven_day", 70.0, 3 * 24 * HOUR, 604_800),
                                window("seven_day_opus", 30.0, 3 * 24 * HOUR, 604_800, label = "Opus"),
                            ),
                        fetchedAt = at(MINUTE),
                        readFrom = server,
                        showConnectionName = true,
                    ),
                    UsageSubscription(
                        key = "codex",
                        providerId = "codex",
                        displayName = "Codex",
                        state = SubscriptionState.NearLimit,
                        windows =
                            listOf(
                                window("5h", 85.0, 3 * HOUR, 18_000),
                                window("weekly", 50.0, 5 * 24 * HOUR, 604_800),
                            ),
                        fetchedAt = at(2 * HOUR),
                        readFrom = phone,
                    ),
                    UsageSubscription(
                        key = "glm",
                        providerId = "glm",
                        displayName = "GLM",
                        state = SubscriptionState.NoLimits,
                        fetchedAt = at(MINUTE),
                    ),
                ),
            apiKeyAccounts = listOf(ApiKeyAccount("anthropic", "Anthropic", phone)),
            loaded = true,
        )

    fun state(): LimitsUi = limitsUi(snapshot, refreshing = false, nowMs = now, timezone = "UTC")
}
