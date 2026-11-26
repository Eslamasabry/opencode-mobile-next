package io.github.eslamasabry.oc.feature.usage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsUi
import io.github.eslamasabry.oc.feature.usage.presentation.UsageActions
import io.github.eslamasabry.oc.feature.usage.presentation.UsageDay
import io.github.eslamasabry.oc.feature.usage.presentation.UsageModel
import io.github.eslamasabry.oc.feature.usage.presentation.UsagePhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageProject
import io.github.eslamasabry.oc.feature.usage.presentation.UsageRange
import io.github.eslamasabry.oc.feature.usage.presentation.UsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageScope
import io.github.eslamasabry.oc.feature.usage.presentation.UsageServer
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTokens
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTools
import io.github.eslamasabry.oc.feature.usage.presentation.UsageUi
import io.github.eslamasabry.oc.feature.usage.presentation.limitsUi
import io.github.eslamasabry.oc.feature.usage.ui.LimitsContent
import io.github.eslamasabry.oc.feature.usage.ui.UsageScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/** Polish sweep, Usage: #16 the half-wide spend card (B-559) and #29 the doubled "Weekly" (B-572). */
@OptIn(ExperimentalTestApi::class)
class UsagePolishTest {
    private var previousLocale: Locale = Locale.getDefault()

    private val home = ProfileId("home")

    private val server = UsageConnection("srv", "Home server", UsageConnectionKind.OpenCodeServer)

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Numbers.pinned = null
        Locale.setDefault(previousLocale)
    }

    private class Actions : UsageActions {
        override fun back() = Unit

        override fun refresh() = Unit

        override fun chooseRange(range: UsageRange) = Unit

        override fun chooseScope(scope: UsageScope) = Unit

        override fun chooseServer(id: ProfileId) = Unit

        override fun openServers() = Unit

        override fun editBudget(unit: UsageBudgetUnit) = Unit

        override fun budgetChanged(value: String) = Unit

        override fun saveBudget() = Unit

        override fun removeBudget() = Unit

        override fun closeBudget() = Unit

        override fun clearBudgets() = Unit
    }

    private val report =
        UsageReport(
            fromMs = 1_761_955_200_000,
            toMs = 1_762_084_800_001,
            sessions = 12,
            subagents = 2,
            prompts = 40,
            steps = 120,
            activeDays = 3,
            streak = 2,
            cost = 4.5,
            tokens = UsageTokens(input = 12_000, output = 3_000),
            tools = UsageTools(calls = 10, succeeded = 6, failed = 2, unfinished = 2),
            models = persistentListOf(UsageModel("anthropic", "claude-x", null, 100, UsageTokens(input = 12_000), 4.5)),
            activity = persistentListOf(UsageDay("2026-11-01", 80), UsageDay("2026-11-02", 40)),
        )

    private fun ready() =
        UsageUi(
            servers = persistentListOf(UsageServer(home, "Home")),
            server = home,
            phase = UsagePhase.Ready,
            report = report,
            timezone = "UTC",
            projects = persistentListOf(UsageProject("prj_app", "app")),
        )

    /** [inside] runs under the theme, where a test may read the kit's tokens. */
    @Composable
    private fun Screen(
        state: UsageUi,
        actions: UsageActions,
        locale: String = "en",
        inside: @Composable () -> Unit = {},
    ) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = locale,
            stillFrame = true,
        ) {
            inside()
            Box(Modifier.width(360.dp)) { UsageScreen(state, actions) }
        }
    }

    @Composable
    private fun LimitsPage(state: LimitsUi, locale: String = "en") {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = locale,
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                Box(Modifier.width(360.dp)) { LimitsContent(state, NoRefresh) }
            }
        }
    }

    /** One account whose only group is a week shared by the limits named [labels] (blank: the plan's own week). */
    private fun weekly(vararg labels: String): LimitsUi {
        val now = LimitsFixtures.now
        val resetsAt = Instant.fromEpochMilliseconds(now + 3L * 24 * 3_600_000)
        val windows =
            labels.mapIndexed { i, label ->
                UsageWindow("seven_day_$i", 30.0 * (i + 1), 604_800L, resetsAt, label)
            }
        val claude =
            UsageSubscription(
                key = "claude",
                providerId = "claude",
                displayName = "Claude",
                state = SubscriptionState.Ok,
                planLabel = "Max",
                windows = windows,
                fetchedAt = Instant.fromEpochMilliseconds(now - 60_000L),
                readFrom = server,
                showConnectionName = true,
            )
        return limitsUi(UsageSnapshot(subscriptions = listOf(claude), loaded = true), false, now, "UTC")
    }

    private object NoRefresh : LimitsActions {
        override fun refresh() = Unit
    }

    @Test
    fun `B-USE-559 the spend panel spans the content width`() = runDesktopComposeUiTest(360, 2600) {
        var gutter = 0
        setContent {
            Screen(ready(), Actions()) { gutter = LocalKitTokens.current.gutter }
        }
        val panel = onNodeWithTag("usage-total-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(panel.left - gutter) < 0.5f, "the panel starts a gutter in: left ${panel.left}, gutter $gutter")
        assertTrue(abs(panel.right - (360 - gutter)) < 0.5f, "the panel ends a gutter in: right ${panel.right}")
    }

    @Test
    fun `B-USE-572 a compact group does not repeat its own name`() = runDesktopComposeUiTest(360, 2600) {
        setContent { LimitsPage(weekly("", "Opus")) }
        onAllNodesWithText("Weekly").assertCountEquals(1)
        onNodeWithText("Opus").assertExists()
    }

    @Test
    fun `B-USE-572 a compact group of named limits keeps its heading`() = runDesktopComposeUiTest(360, 2600) {
        setContent { LimitsPage(weekly("Sonnet", "Opus")) }
        onAllNodesWithText("Weekly").assertCountEquals(1)
        onNodeWithText("Sonnet").assertExists()
        onNodeWithText("Opus").assertExists()
    }

    @Test
    fun `B-USE-559 usage cost has one time-range selector offering every range`() = runDesktopComposeUiTest(360, 2600) {
        setContent { Screen(ready(), Actions()) }
        onNodeWithTag("usage-segment").assertDoesNotExist()
        onNodeWithTag("usage-range").performClick()
        onNodeWithText("Today").assertExists()
        onNodeWithText("All time").assertExists()
    }

    @Test
    fun `B-USE-559 Arabic usage cost uses one numeral script`() = runDesktopComposeUiTest(360, 2600) {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        Numbers.pinned = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫', minus = '-')
        setContent { Screen(ready(), Actions(), locale = "ar") }
        onNodeWithText("إنفاق آخر ٣٠ يومًا").assertExists()
        onNodeWithText("إنفاق آخر 30 يومًا").assertDoesNotExist()
    }

    @Test
    fun `B-USE-572 Arabic limits uses one numeral script and says قبل ساعتين`() = runDesktopComposeUiTest(360, 2600) {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        Numbers.pinned = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫', minus = '-')
        setContent { LimitsPage(LimitsFixtures.state(), locale = "ar") }
        onNodeWithText("متبقٍّ ٦٠%").assertExists()
        onNodeWithText("متبقٍّ 60%").assertDoesNotExist()
        onNodeWithText("قُرئت قبل ساعتين").assertExists()
        onNodeWithText("قُرئت قبل ساعتان").assertDoesNotExist()
    }
}
