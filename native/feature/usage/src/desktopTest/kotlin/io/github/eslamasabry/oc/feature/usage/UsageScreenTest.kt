package io.github.eslamasabry.oc.feature.usage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
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
import io.github.eslamasabry.oc.feature.usage.ui.UsageScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** The real UsageScreen, found by the words a person sees. */
@OptIn(ExperimentalTestApi::class)
class UsageScreenTest {
    private var previousLocale: Locale = Locale.getDefault()
    private val home = ProfileId("home")

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class Actions : UsageActions {
        val calls = mutableListOf<String>()

        override fun back() {
            calls += "back"
        }

        override fun refresh() {
            calls += "refresh"
        }

        override fun chooseRange(range: UsageRange) {
            calls += "range:$range"
        }

        override fun chooseScope(scope: UsageScope) {
            calls += "scope:" + ((scope as? UsageScope.Project)?.id ?: "all")
        }

        override fun chooseServer(id: ProfileId) {
            calls += "server:${id.raw}"
        }

        override fun openServers() {
            calls += "servers"
        }

        override fun editBudget(unit: UsageBudgetUnit) {
            calls += "editBudget"
        }

        override fun budgetChanged(value: String) {
            calls += "budgetChanged"
        }

        override fun saveBudget() {
            calls += "saveBudget"
        }

        override fun removeBudget() {
            calls += "removeBudget"
        }

        override fun closeBudget() {
            calls += "closeBudget"
        }

        override fun clearBudgets() {
            calls += "clearBudgets"
        }
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

    @Composable
    private fun Screen(state: UsageUi, actions: UsageActions, locale: String = "en") {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = locale,
            stillFrame = true,
        ) {
            Box(Modifier.width(412.dp)) { UsageScreen(state, actions) }
        }
    }

    private fun ready() =
        UsageUi(
            servers = persistentListOf(UsageServer(home, "Home")),
            server = home,
            phase = UsagePhase.Ready,
            report = report,
            timezone = "UTC",
            projects = persistentListOf(UsageProject("prj_app", "app")),
        )

    @Test
    fun `SCREENS-NAV-96 the total comes first, then the calendar and project filters, then the breakdowns`() =
        runComposeUiTest {
            val actions = Actions()
            setContent { Screen(ready(), actions) }
            onNodeWithText("Usage and cost").assertExists()
            // The total is labelled with the range it covers (Flutter usageSpentThirtyDays; 30 days is the default).
            onNodeWithText("Spent in the last 30 days").assertExists()
            assertTrue(onAllNodesWithText("$4.50").fetchSemanticsNodes().isNotEmpty())
            onNodeWithText("Timezone: UTC").assertExists()
            onNodeWithText("All projects").assertExists()
            // Range and project are picker rows (Flutter KitPickerRow): open each, then choose.
            onNodeWithTag("usage-range").performClick()
            onNodeWithText("Today").performClick()
            onNodeWithTag("usage-scope").performClick()
            onNodeWithText("app").performClick()
            assertEquals(listOf("range:Today", "scope:prj_app"), actions.calls)
        }

    @Test
    fun `B-NAV-078 a failed refresh that kept the previous result says so`() = runComposeUiTest {
        setContent { Screen(ready().copy(phase = UsagePhase.Failed, previous = true), Actions()) }
        onNodeWithText("Showing the previous result for these filters.").assertExists()
        onNodeWithText("Couldn't load usage.").assertExists()
    }

    @Test
    fun `B-NAV-078 a server without usage is explained in place, not as an error`() = runComposeUiTest {
        setContent { Screen(ready().copy(phase = UsagePhase.Unsupported, report = null), Actions()) }
        // F: lib/ui/screens/usage_screen.dart:266-275: the whole page is the OpenCode 2 explainer; no Refresh, no
        // filters and no budgets for a server that reports no usage.
        onNodeWithText("OpenCode 2 server").assertExists()
        onNodeWithText("Needs a server running OpenCode 2.").assertExists()
        onNodeWithText("Couldn't load usage.").assertDoesNotExist()
        onNodeWithText("Set USD budget").assertDoesNotExist()
        onNodeWithContentDescription("Refresh").assertDoesNotExist()
    }

    @Test
    fun `B-NAV-078 authorization problems read plainly with Try again`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(ready().copy(phase = UsagePhase.Authorization, report = null), actions) }
        onNodeWithText("Check this server's password and permissions, then refresh.").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("refresh"), actions.calls)
    }

    @Test
    fun `B-L10N-010 the usage screen reads in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Screen(ready(), Actions(), "ar") }
        onNodeWithText("إنفاق آخر 30 يومًا").assertExists()
    }

    @Test
    fun `B-160 the cost segment switches between 24 hours, 7, 30 and 90 days`() = runComposeUiTest {
        val actions = Actions()
        setContent { Screen(ready(), actions) }
        onNodeWithText("7d").performClick()
        onNodeWithText("90d").performClick()
        assertEquals(listOf("range:SevenDays", "range:NinetyDays"), actions.calls)
    }

    @Test
    fun `B-160 the split shows each provider's share of the cost and the token details sit under a fold`() =
        runComposeUiTest {
            setContent { Screen(ready(), Actions()) }
            onNodeWithText("Split by provider", ignoreCase = true).assertExists()
            onNodeWithText("100% of cost · 12K tokens · 1 model").assertExists()
            onNodeWithText("The server reports cost by provider and model, not by agent.").assertExists()
            onNodeWithText("Input read from cache: 0").assertDoesNotExist()
            onNodeWithText("Cached and uncached tokens").performClick()
            onNodeWithText("Input not cached: 12,000").assertExists()
        }

    @Test
    fun `B-160 a day-by-day chart is drawn for 30 days, and 24 hours says there is no hourly series`() =
        runComposeUiTest {
            var state by mutableStateOf(ready())
            setContent { Screen(state, Actions()) }
            onNodeWithTag("usage-chart").assertExists()
            state = ready().copy(range = UsageRange.Last24Hours)
            waitForIdle()
            onNodeWithTag("usage-chart").assertDoesNotExist()
            onNodeWithText("This server reports activity by day, so there is no hourly chart for 24 hours.").assertExists()
        }
}
