package io.github.eslamasabry.oc.feature.plans.rb.o50

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.feature.plans.ui.PlansScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class PlanBlocksTest {
    @Test
    fun `B-STATE-usage a compact plan opens its own details without starting a read`() =
        runComposeUiTest {
            var reads = 0
            val actions =
                object : PlansActions {
                    override fun back() = Unit

                    override fun refresh() {
                        reads++
                    }
                }
            fun card(key: String, name: String) =
                PlanCardUi(
                    key = key,
                    providerId = key,
                    name = name,
                    connection = null,
                    state = SubscriptionState.Ok,
                    plan = "plus",
                    windows =
                        persistentListOf(
                            PlanWindowUi(UsageWindowKind.FiveHour, "5 hours", 60, null, "five")
                        ),
                    fix = UsageFix.None,
                    fixConnection = null,
                    signedInOn = persistentListOf(),
                    resetSince = false,
                    fetchedAtMs = null,
                    alertThreshold = 80,
                )
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    Box(Modifier.width(412.dp)) {
                        PlansScreen(
                            PlansUi(
                                phase = PlansPhase.List,
                                cards =
                                    persistentListOf(
                                        card("codex", "Codex"),
                                        card("claude", "Claude"),
                                    ),
                            ),
                            actions,
                        )
                    }
                }
            }
            onNodeWithText("Codex Plus", useUnmergedTree = true).assertExists()
            onNodeWithTag("usage-card-claude").performClick()
            onNode(
                    hasText("Claude") and hasAnyAncestor(hasTestTag("kit-sheet")),
                    useUnmergedTree = true,
                )
                .assertExists()
            assertEquals(0, reads)
        }
}
