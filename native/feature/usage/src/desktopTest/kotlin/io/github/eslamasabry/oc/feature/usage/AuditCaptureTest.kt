package io.github.eslamasabry.oc.feature.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.usage.ApiKeyAccount
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsPhase
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
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/** Audit capture only: Usage (cost) and Limits in every state their models allow. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "usage"
    private val home = ProfileId("home")

    private val usageActions =
        object : UsageActions {
            override fun back() = Unit
            override fun refresh() = Unit
            override fun chooseRange(range: UsageRange) = Unit
            override fun chooseScope(scope: UsageScope) = Unit
            override fun chooseServer(id: ProfileId) = Unit
            override fun editBudget(unit: UsageBudgetUnit) = Unit
            override fun budgetChanged(value: String) = Unit
            override fun saveBudget() = Unit
            override fun removeBudget() = Unit
            override fun closeBudget() = Unit
            override fun clearBudgets() = Unit
            override fun openServers() = Unit
        }

    private val limitsActions = object : LimitsActions { override fun refresh() = Unit }

    @Composable
    private fun Limits(state: LimitsUi) {
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) { LimitsContent(state, limitsActions) }
    }

    @Composable
    private fun Cost(state: UsageUi) {
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) { UsageScreen(state, usageActions) }
    }

    // ---- Cost

    private val days = listOf(12L, 40, 8, 0, 64, 30, 22)

    private val report =
        UsageReport(
            fromMs = 1_793_491_200_000,
            toMs = 1_794_052_800_001,
            sessions = 31,
            subagents = 4,
            prompts = 88,
            steps = 176,
            activeDays = 6,
            streak = 3,
            cost = 12.84,
            tokens = UsageTokens(input = 410_000, output = 96_000, reasoning = 21_000, cacheRead = 2_300_000, cacheWrite = 180_000),
            tools = UsageTools(calls = 120, succeeded = 100, failed = 12, unfinished = 8),
            models =
                persistentListOf(
                    UsageModel("anthropic", "claude-sonnet", null, 120, UsageTokens(input = 300_000, cacheRead = 2_000_000), 9.6),
                    UsageModel("openai", "gpt-5", "high", 56, UsageTokens(input = 110_000, cacheRead = 300_000), 3.24),
                    UsageModel("local", "a-model-with-a-really-long-identifier-that-keeps-going-and-going-v2", null, 3, UsageTokens(input = 1_000), 0.0),
                ),
            activity = persistentListOf(*days.mapIndexed { i, v -> UsageDay("2026-11-0${i + 1}", v) }.toTypedArray()),
        )

    private val ready =
        UsageUi(
            servers = persistentListOf(UsageServer(home, "Home")),
            server = home,
            range = UsageRange.SevenDays,
            phase = UsagePhase.Ready,
            report = report,
            timezone = "UTC",
            projects = persistentListOf(UsageProject("prj_app", "app"), UsageProject("prj_site", "site")),
        )

    @Test
    fun cost() = Audit.guard("${m}__cost__build") {
        Audit.shoot(m, "cost", "ready-7d", advanceMs = 1_500) { Cost(ready) }
        Audit.shoot(m, "cost", "ready-30d") { Cost(ready.copy(range = UsageRange.ThirtyDays)) }
        Audit.shoot(m, "cost", "ready-today") { Cost(ready.copy(range = UsageRange.Today)) }
        Audit.shoot(m, "cost", "ready-24h") { Cost(ready.copy(range = UsageRange.Last24Hours)) }
        Audit.shoot(m, "cost", "ready-all-time") { Cost(ready.copy(range = UsageRange.AllTime)) }
        Audit.shoot(m, "cost", "project-scope") { Cost(ready.copy(scope = UsageScope.Project("prj_app"))) }
        Audit.shoot(m, "cost", "several-servers") {
            Cost(ready.copy(servers = persistentListOf(UsageServer(home, "Home"), UsageServer(ProfileId("vps"), "vps-with-a-quite-long-display-name"), UsageServer(ProfileId("pc"), "studio-pc"))))
        }
        Audit.shoot(m, "cost", "no-tools") { Cost(ready.copy(report = report.copy(tools = null))) }
        Audit.shoot(m, "cost", "empty-report") {
            Cost(ready.copy(report = report.copy(sessions = 0, prompts = 0, steps = 0, cost = 0.0, tokens = UsageTokens(), tools = null, models = persistentListOf(), activity = persistentListOf())))
        }
        Audit.shoot(m, "cost", "narrower-answer") { Cost(ready.copy(report = report.copy(coversRequest = false))) }
        Audit.shoot(m, "cost", "refreshing") { Cost(ready.copy(refreshing = true)) }
        Audit.shoot(m, "cost", "projects-failed") { Cost(ready.copy(projectsFailed = true)) }
        Audit.shoot(m, "cost", "record-filter-provider") { Cost(ready.copy(providerFilter = "anthropic")) }
        Audit.shoot(m, "cost", "record-search-no-match") { Cost(ready.copy(modelSearch = "zzz")) }
    }

    @Test
    fun costPhases() = Audit.guard("${m}__cost-phases__build") {
        val bare = UsageUi(servers = persistentListOf(UsageServer(home, "Home")), server = home, timezone = "UTC")
        Audit.shoot(m, "cost", "loading") { Cost(bare) }
        Audit.shoot(m, "cost", "no-server") { Cost(UsageUi(phase = UsagePhase.NoServer)) }
        Audit.shoot(m, "cost", "unsupported") { Cost(bare.copy(phase = UsagePhase.Unsupported)) }
        Audit.shoot(m, "cost", "no-timezone") { Cost(bare.copy(phase = UsagePhase.NoTimeZone)) }
        Audit.shoot(m, "cost", "authorization") { Cost(bare.copy(phase = UsagePhase.Authorization)) }
        Audit.shoot(m, "cost", "invalid") { Cost(bare.copy(phase = UsagePhase.Invalid)) }
        Audit.shoot(m, "cost", "failed") { Cost(bare.copy(phase = UsagePhase.Failed, failure = Failure.Network())) }
        Audit.shoot(m, "cost", "failed-kept-previous") { Cost(ready.copy(phase = UsagePhase.Failed, previous = true, failure = Failure.Network())) }
    }

    @Test
    fun budgets() = Audit.guard("${m}__budgets__build") {
        Audit.shoot(m, "cost-budget", "offered") { Cost(ready.copy(budgetsAvailable = true)) }
        Audit.shoot(m, "cost-budget", "usd-set") { Cost(ready.copy(budgetsAvailable = true, usdBudget = 20.0, hasSavedBudgets = true)) }
        Audit.shoot(m, "cost-budget", "usd-over") { Cost(ready.copy(budgetsAvailable = true, usdBudget = 10.0, hasSavedBudgets = true)) }
        Audit.shoot(m, "cost-budget", "tokens-set") { Cost(ready.copy(budgetsAvailable = true, tokenBudget = 5_000_000.0, hasSavedBudgets = true)) }
        Audit.shoot(m, "cost-budget", "usd-dialog") { Cost(ready.copy(budgetsAvailable = true, budgetUnit = UsageBudgetUnit.Usd)) }
        Audit.shoot(m, "cost-budget", "usd-dialog-typed") {
            Cost(ready.copy(budgetsAvailable = true, budgetUnit = UsageBudgetUnit.Usd, usdBudget = 20.0, budgetInput = "25.50", hasSavedBudgets = true))
        }
        Audit.shoot(m, "cost-budget", "usd-dialog-invalid") {
            Cost(ready.copy(budgetsAvailable = true, budgetUnit = UsageBudgetUnit.Usd, budgetInput = "abc", budgetInvalid = true))
        }
        Audit.shoot(m, "cost-budget", "usd-dialog-saving") {
            Cost(ready.copy(budgetsAvailable = true, budgetUnit = UsageBudgetUnit.Usd, budgetInput = "25", budgetSaving = true))
        }
        Audit.shoot(m, "cost-budget", "usd-dialog-save-failed") {
            Cost(ready.copy(budgetsAvailable = true, budgetUnit = UsageBudgetUnit.Usd, budgetInput = "25", budgetFailed = true))
        }
        Audit.shoot(m, "cost-budget", "tokens-dialog") { Cost(ready.copy(budgetsAvailable = true, budgetUnit = UsageBudgetUnit.Tokens)) }
    }

    // ---- Limits

    private val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilliseconds()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val server = UsageConnection("srv", "Home server", UsageConnectionKind.OpenCodeServer)
    private val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)

    private fun window(id: String, used: Double, resetsIn: Long?, seconds: Long, label: String = "") =
        UsageWindow(id, used, seconds, resetsIn?.let { Instant.fromEpochMilliseconds(now + it) }, label)

    private fun at(ago: Long) = Instant.fromEpochMilliseconds(now - ago)

    private fun sub(
        key: String,
        name: String,
        state: SubscriptionState = SubscriptionState.Ok,
        plan: String? = "Max",
        windows: List<UsageWindow> = listOf(window("five_hour", 40.0, hour + 38 * minute, 18_000), window("seven_day", 70.0, 3 * 24 * hour, 604_800)),
        ago: Long = minute,
        fix: UsageFix = UsageFix.None,
        from: UsageConnection? = server,
    ) = UsageSubscription(key = key, providerId = key, displayName = name, state = state, planLabel = plan, windows = windows, fetchedAt = at(ago), readFrom = from, fix = fix, showConnectionName = true)

    private fun limits(vararg subs: UsageSubscription, keys: List<ApiKeyAccount> = emptyList(), loaded: Boolean = true, refreshing: Boolean = false) =
        limitsUi(UsageSnapshot(subscriptions = subs.toList(), apiKeyAccounts = keys, loaded = loaded), refreshing, now, "UTC")

    @Test
    fun limitsStates() = Audit.guard("${m}__limits__build") {
        Audit.shoot(m, "limits", "fixture-mixed") { Limits(LimitsFixtures.state()) }
        Audit.shoot(m, "limits", "loading") { Limits(LimitsUi(phase = LimitsPhase.Loading)) }
        Audit.shoot(m, "limits", "empty") { Limits(LimitsUi(phase = LimitsPhase.Empty, nowMs = now)) }
        Audit.shoot(m, "limits", "refreshing") { Limits(limits(sub("claude", "Claude"), refreshing = true)) }
        Audit.shoot(m, "limits", "healthy") { Limits(limits(sub("claude", "Claude"))) }
        Audit.shoot(m, "limits", "near-limit") {
            Limits(limits(sub("codex", "Codex", SubscriptionState.NearLimit, windows = listOf(window("5h", 92.0, 3 * hour, 18_000), window("weekly", 88.0, 5 * 24 * hour, 604_800)))))
        }
        Audit.shoot(m, "limits", "exhausted") {
            Limits(limits(sub("codex", "Codex", SubscriptionState.Exhausted, windows = listOf(window("5h", 100.0, 3 * hour, 18_000), window("weekly", 100.0, 5 * 24 * hour, 604_800)))))
        }
        Audit.shoot(m, "limits", "passed-reset") {
            Limits(limits(sub("claude", "Claude", windows = listOf(window("five_hour", 40.0, -10 * minute, 18_000)))))
        }
        Audit.shoot(m, "limits", "no-reset-time") {
            Limits(limits(sub("claude", "Claude", windows = listOf(window("five_hour", 40.0, null, 18_000)))))
        }
        Audit.shoot(m, "limits", "stale-offline") { Limits(limits(sub("claude", "Claude", SubscriptionState.OfflineStale, ago = 5 * hour))) }
        Audit.shoot(m, "limits", "no-limits-reported") { Limits(limits(sub("glm", "GLM", SubscriptionState.NoLimits, plan = null, windows = emptyList()))) }
        Audit.shoot(m, "limits", "long-plan-and-label") {
            Limits(
                limits(
                    sub(
                        "claude",
                        "Claude with a very long display name for wrapping",
                        plan = "An unusually long plan label that goes on and on",
                        windows = listOf(
                            window("seven_day_a", 30.0, 3 * 24 * hour, 604_800, label = "A very long model limit label"),
                            window("seven_day_b", 70.0, 3 * 24 * hour, 604_800, label = "Another long model limit label"),
                            window("seven_day_c", 55.0, 3 * 24 * hour, 604_800, label = "Third"),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun limitsFixes() = Audit.guard("${m}__limits-fixes__build") {
        fun broken(state: SubscriptionState, fix: UsageFix) =
            limits(sub("claude", "Claude", state, plan = null, windows = emptyList(), fix = fix))
        Audit.shoot(m, "limits", "cant-read-yet") { Limits(broken(SubscriptionState.CantReadYet, UsageFix.TryAgain)) }
        Audit.shoot(m, "limits", "sign-in-expired") { Limits(broken(SubscriptionState.SignInExpired, UsageFix.SignIn)) }
        Audit.shoot(m, "limits", "not-signed-in") { Limits(broken(SubscriptionState.NotSignedIn, UsageFix.SignIn)) }
        Audit.shoot(m, "limits", "reader-broken") { Limits(broken(SubscriptionState.ReaderBroken, UsageFix.SetUp)) }
        Audit.shoot(m, "limits", "api-key-only") { Limits(broken(SubscriptionState.ApiKeyOnly, UsageFix.AddKey)) }
        Audit.shoot(m, "limits", "api-keys-only-list") {
            Limits(limits(keys = listOf(ApiKeyAccount("anthropic", "Anthropic", phone), ApiKeyAccount("openai", "OpenAI", server))))
        }
    }
}
