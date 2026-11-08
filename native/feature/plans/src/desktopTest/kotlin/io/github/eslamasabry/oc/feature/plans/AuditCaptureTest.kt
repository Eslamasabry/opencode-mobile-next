@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.plans

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindowKind
import io.github.eslamasabry.oc.feature.plans.presentation.ComputerReaderUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanAlertPreferences
import io.github.eslamasabry.oc.feature.plans.presentation.PlanAuthenticationUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCardUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanConnectionUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanKeyUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanManagePage
import io.github.eslamasabry.oc.feature.plans.presentation.PlanReaderKind
import io.github.eslamasabry.oc.feature.plans.presentation.PlanReaderPhase
import io.github.eslamasabry.oc.feature.plans.presentation.PlanReaderProblem
import io.github.eslamasabry.oc.feature.plans.presentation.PlanReaderStatus
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInChoice
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInStep
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanWindowUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlansActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansManagementActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlansManagementUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlansPhase
import io.github.eslamasabry.oc.feature.plans.presentation.PlansUi
import io.github.eslamasabry.oc.feature.plans.ui.PlanSignInScreen
import io.github.eslamasabry.oc.feature.plans.ui.PlansManagementScreen
import io.github.eslamasabry.oc.feature.plans.ui.PlansScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import java.lang.reflect.Proxy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test

/** Audit capture only: Plans (list, empty, loading, details), alerts and widget settings, readers, sign-in. */
class AuditCaptureTest {
    /** Extra states run only when OC_AUDIT_FULL is set, to bound the default CI time. */
    private fun more(block: () -> Unit) {
        if (System.getenv("OC_AUDIT_FULL") != null) block()
    }

    private val nowMs = 1_793_606_400_000L

    /** No-op actions through a proxy, as the existing plans tests do, so every default method stays valid. */
    private val plansActions =
        Proxy.newProxyInstance(PlansActions::class.java.classLoader, arrayOf(PlansActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PlansActionsProxy"
                else -> null
            }
        } as PlansActions

    private val manageActions =
        Proxy.newProxyInstance(PlansManagementActions::class.java.classLoader, arrayOf(PlansManagementActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PlansManagementActionsProxy"
                else -> null
            }
        } as PlansManagementActions

    private val signInActions =
        Proxy.newProxyInstance(PlanSignInActions::class.java.classLoader, arrayOf(PlanSignInActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PlanSignInActionsProxy"
                else -> null
            }
        } as PlanSignInActions

    private val phone = PlanConnectionUi("This phone", isPhone = true, id = "phone")
    private val workstation = PlanConnectionUi("Workstation", isPhone = false, id = "pc")

    private fun windows(fiveLeft: Int?, weeklyLeft: Int? = null): ImmutableList<PlanWindowUi> =
        persistentListOf<PlanWindowUi>().let { empty ->
            var list = empty
            if (fiveLeft != null) list = list.add(PlanWindowUi(UsageWindowKind.FiveHour, "5 hours", fiveLeft, nowMs + 2 * 3_600_000L, "five"))
            if (weeklyLeft != null) list = list.add(PlanWindowUi(UsageWindowKind.Weekly, "Weekly", weeklyLeft, nowMs + 3 * 86_400_000L, "week"))
            list
        }

    private fun card(
        key: String,
        name: String,
        state: SubscriptionState = SubscriptionState.Ok,
        fiveLeft: Int? = 60,
        weeklyLeft: Int? = null,
        plan: String? = "plus",
        connection: PlanConnectionUi? = phone,
        fix: UsageFix = UsageFix.None,
        canFix: Boolean = false,
    ) =
        PlanCardUi(
            key = key,
            providerId = key,
            name = name,
            connection = connection,
            state = state,
            plan = plan,
            windows = windows(fiveLeft, weeklyLeft),
            fix = fix,
            fixConnection = if (fix == UsageFix.None) null else connection,
            signedInOn = persistentListOf(PlanSignInUi(phone, SignInState.SignedIn, online = true, readsFromHere = true)),
            resetSince = false,
            fetchedAtMs = nowMs - 60_000L,
            alertThreshold = 80,
            canFix = canFix,
        )

    private val cards =
        persistentListOf(
            card("claude", "Claude", fiveLeft = 60, weeklyLeft = 35),
            card("codex", "Codex", fiveLeft = 8, weeklyLeft = 70, state = SubscriptionState.NearLimit, connection = workstation),
        )

    private fun plans(state: String, ui: PlansUi, setup: ComposeUiTest.() -> Unit = {}) {
        Audit.shoot("plans", "plans", state, setup = setup) {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) { PlansScreen(ui, plansActions) }
        }
    }

    private fun openCard(key: String): ComposeUiTest.() -> Unit = {
        runCatching {
            onNodeWithTag("usage-card-$key").performClick()
            waitForIdle()
            mainClock.advanceTimeBy(600)
            waitForIdle()
        }
    }

    @Test
    fun plansStates() = Audit.guard("plans__plans__build") {
        val list = PlansUi(phase = PlansPhase.List, cards = cards, nowMs = nowMs)
        plans("loading", PlansUi(phase = PlansPhase.Loading))
        plans("empty", PlansUi(phase = PlansPhase.Empty, nowMs = nowMs, addable = persistentListOf("claude", "codex")))
        plans("list", list)
        more { plans("list-refreshing", list.copy(refreshing = true)) }
        plans("list-failed", list.copy(failed = true))
        plans("card-details", list, setup = openCard("claude"))
        plans(
            "list-all-states",
            list.copy(
                cards = persistentListOf(
                    card("claude", "Claude", fiveLeft = 60, weeklyLeft = 35),
                    card("codex", "Codex", fiveLeft = 0, state = SubscriptionState.Exhausted),
                    card("gemini", "Gemini", fiveLeft = null, state = SubscriptionState.SignInExpired, fix = UsageFix.SignIn),
                    card("copilot", "Copilot", fiveLeft = null, state = SubscriptionState.NotSignedIn, fix = UsageFix.SignIn, plan = null),
                    card("cursor", "Cursor", fiveLeft = null, state = SubscriptionState.ReaderBroken, fix = UsageFix.TryAgain, canFix = true),
                    card("kimi", "Kimi", fiveLeft = null, state = SubscriptionState.OfflineStale, connection = workstation),
                    card("grok", "Grok", fiveLeft = null, state = SubscriptionState.NoLimits),
                    card("zed", "Zed", fiveLeft = null, state = SubscriptionState.CantReadYet, fix = UsageFix.SetUp, connection = workstation),
                ),
            ),
        )
        plans(
            "list-with-api-keys",
            list.copy(
                keys = persistentListOf(PlanKeyUi("Claude", workstation, viaOpenCode = true), PlanKeyUi("OpenAI", phone, viaOpenCode = false)),
                addable = persistentListOf("gemini"),
            ),
        )
        plans(
            "list-computer-readers",
            list.copy(
                computerReaders = persistentListOf(
                    ComputerReaderUi("pc", "Workstation", enabled = true),
                    ComputerReaderUi("nas", "Home NAS", enabled = false),
                ),
            ),
        )
        plans(
            "list-long-names",
            list.copy(
                cards = persistentListOf(
                    card("claude", "Claude with a really long provider display name that keeps going", connection = PlanConnectionUi("A computer with a very long connection name that goes on", false, "x")),
                ),
            ),
        )
    }

    // ---- Management pages

    private fun manage(screen: String, state: String, ui: PlansManagementUi, plansUi: PlansUi) {
        Audit.shoot("plans", screen, state) {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                PlansManagementScreen(ui, plansUi, manageActions) { _, _ -> }
            }
        }
    }

    @Test
    fun alertsStates() = Audit.guard("plans__usage-alerts__build") {
        val list = PlansUi(phase = PlansPhase.List, cards = cards, nowMs = nowMs)
        manage("usage-alerts", "off", PlansManagementUi(page = PlanManagePage.Alerts), list)
        more { manage("usage-alerts", "on", PlansManagementUi(page = PlanManagePage.Alerts, preferences = PlanAlertPreferences(enabled = true)), list) }
        manage(
            "usage-alerts",
            "on-quiet-wifi",
            PlansManagementUi(
                page = PlanManagePage.Alerts,
                preferences = PlanAlertPreferences(enabled = true, threshold = 90, notifyReset = true, quietStart = 22 * 60, quietEnd = 8 * 60, wifiOnly = true),
            ),
            list,
        )
        manage("usage-alerts", "busy", PlansManagementUi(page = PlanManagePage.Alerts, busy = true, preferences = PlanAlertPreferences(enabled = true)), list)
        manage("usage-alerts", "save-failed", PlansManagementUi(page = PlanManagePage.Alerts, preferences = PlanAlertPreferences(enabled = true), failure = Failure.StorageRefused()), list)
        manage("usage-alerts", "no-plans", PlansManagementUi(page = PlanManagePage.Alerts, preferences = PlanAlertPreferences(enabled = true)), PlansUi(phase = PlansPhase.Empty))
    }

    @Test
    fun widgetStates() = Audit.guard("plans__usage-widget__build") {
        val list = PlansUi(phase = PlansPhase.List, cards = cards, nowMs = nowMs)
        manage("usage-widget", "with-plans", PlansManagementUi(page = PlanManagePage.Widget), list)
        manage("usage-widget", "no-plans", PlansManagementUi(page = PlanManagePage.Widget), PlansUi(phase = PlansPhase.Empty))
        manage("usage-widget", "busy", PlansManagementUi(page = PlanManagePage.Widget, busy = true), list)
    }

    @Test
    fun addStates() = Audit.guard("plans__usage-add__build") {
        manage("usage-add", "providers", PlansManagementUi(page = PlanManagePage.Add), PlansUi(phase = PlansPhase.Empty, addable = persistentListOf("claude", "codex", "gemini")))
        more { manage("usage-add", "none-left", PlansManagementUi(page = PlanManagePage.Add), PlansUi(phase = PlansPhase.List, cards = cards, nowMs = nowMs)) }
    }

    @Test
    fun readerStates() = Audit.guard("plans__usage-reader__build") {
        fun reader(state: String, status: PlanReaderStatus?, busy: Boolean = false, name: String = "Work") {
            manage(
                "usage-reader",
                state,
                PlansManagementUi(
                    page = PlanManagePage.Reader,
                    readerId = "work",
                    readerName = name,
                    busy = busy,
                    readers = if (status == null) persistentMapOf() else persistentMapOf("work" to status),
                ),
                PlansUi(),
            )
        }
        reader("computer-off", PlanReaderStatus("work", PlanReaderKind.Computer))
        reader("computer-ready", PlanReaderStatus("work", PlanReaderKind.Computer, enabled = true, phase = PlanReaderPhase.Ready, foundProviderIds = persistentListOf("claude")))
        reader("computer-node-too-old", PlanReaderStatus("work", PlanReaderKind.Computer, enabled = true, problem = PlanReaderProblem.NodeTooOld))
        more { reader("computer-unreachable", PlanReaderStatus("work", PlanReaderKind.Computer, enabled = true, problem = PlanReaderProblem.Unreachable)) }
        more { reader("computer-long-name", PlanReaderStatus("work", PlanReaderKind.Computer), name = "A computer with a very long name that keeps going and going") }
        reader("phone-off", PlanReaderStatus("work", PlanReaderKind.InAppUbuntu))
        reader("phone-copying", PlanReaderStatus("work", PlanReaderKind.InAppUbuntu, busy = true, phase = PlanReaderPhase.Copying), busy = true)
        more { reader("phone-linux-not-ready", PlanReaderStatus("work", PlanReaderKind.InAppUbuntu, problem = PlanReaderProblem.LinuxNotReady)) }
        reader("gone", null)
    }

    // ---- Sign-in

    private fun signIn(state: String, ui: PlanAuthenticationUi) {
        Audit.shoot("plans", "plan-sign-in", state) {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) { PlanSignInScreen(ui, signInActions) }
        }
    }

    @Test
    fun signInStates() = Audit.guard("plans__plan-sign-in__build") {
        val choices =
            persistentListOf(
                PlanSignInChoice("phone", "This phone", phone = true),
                PlanSignInChoice("pc", "Workstation"),
                PlanSignInChoice("ubuntu", "Linux on this phone", inApp = true),
            )
        signIn("places", PlanAuthenticationUi(active = true, provider = "claude", step = PlanSignInStep.Places, choices = choices))
        more { signIn("places-busy", PlanAuthenticationUi(active = true, provider = "claude", step = PlanSignInStep.Places, choices = choices, busy = true)) }
        signIn("places-failed", PlanAuthenticationUi(active = true, provider = "claude", step = PlanSignInStep.Places, choices = choices, failure = Failure.Unknown()))
        more { signIn("places-none", PlanAuthenticationUi(active = true, provider = "claude", step = PlanSignInStep.Places)) }
        signIn("unavailable", PlanAuthenticationUi(active = true, provider = "claude", step = PlanSignInStep.Unavailable, place = choices.first()))
        signIn("complete", PlanAuthenticationUi(active = true, provider = "claude", step = PlanSignInStep.Complete, place = choices.first()))
    }
}
