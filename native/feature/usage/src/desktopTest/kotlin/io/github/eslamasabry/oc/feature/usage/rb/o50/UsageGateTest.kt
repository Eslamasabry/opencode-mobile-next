package io.github.eslamasabry.oc.feature.usage.rb.o50

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
import io.github.eslamasabry.oc.feature.usage.presentation.UsageActions
import io.github.eslamasabry.oc.feature.usage.presentation.UsagePhase
import io.github.eslamasabry.oc.feature.usage.presentation.UsageRange
import io.github.eslamasabry.oc.feature.usage.presentation.UsageScope
import io.github.eslamasabry.oc.feature.usage.presentation.UsageServer
import io.github.eslamasabry.oc.feature.usage.presentation.UsageUi
import io.github.eslamasabry.oc.feature.usage.ui.UsageScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/**
 * Order 50: Usage and cost, with test/revamp/screen_usage_1_golden_test.dart's scenes (one server, "Studio", 30 days,
 * all projects, Asia/Dubai, the range Aug 7 to Sep 5 2026 12:00).
 */
class UsageGateTest {
    private object Actions : UsageActions {
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

    private val studio = ProfileId("usage-fixture")
    private val base =
        UsageUi(
            servers = persistentListOf(UsageServer(studio, "Studio")),
            server = studio,
            range = UsageRange.ThirtyDays,
            timezone = "Asia/Dubai",
            budgetsAvailable = true,
        )

    /** `usage_stats_unsupported`: the server has no usage statistics. */
    @Test
    fun usage_stats_unsupported() {
        FlutterGoldenGate.check("usage_stats_unsupported", frame = FlutterGoldenGate.Frame.Screen) {
            UsageScreen(base.copy(phase = UsagePhase.Unsupported), Actions)
        }
    }
}
