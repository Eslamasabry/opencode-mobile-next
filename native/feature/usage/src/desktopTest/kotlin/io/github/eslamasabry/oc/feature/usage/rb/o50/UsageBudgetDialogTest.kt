package io.github.eslamasabry.oc.feature.usage.rb.o50

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit
import io.github.eslamasabry.oc.feature.usage.presentation.*
import io.github.eslamasabry.oc.feature.usage.ui.UsageScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UsageBudgetDialogTest {
    @Test
    fun `B-NAV-021 USD dialog accepts decimal input and remove waits for an existing budget`() =
        runComposeUiTest {
            val calls = mutableListOf<Pair<String, List<Any?>>>()
            val actions =
                Proxy.newProxyInstance(
                    UsageActions::class.java.classLoader,
                    arrayOf(UsageActions::class.java),
                ) { _, method, args ->
                    calls += method.name to args?.toList().orEmpty()
                    null
                } as UsageActions
            val state = UsageUi(budgetsAvailable = true, budgetUnit = UsageBudgetUnit.Usd)
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    Box(Modifier.size(412.dp, 915.dp)) { UsageScreen(state, actions) }
                }
            }
            onNodeWithText("Remove budget").assertDoesNotExist()
            onNodeWithText("Enter a valid USD amount").assertDoesNotExist()
            onNode(hasSetTextAction()).performTextInput("2.50")
            onNodeWithText("Save USD budget").performClick()
            assertEquals("saveBudget", calls.last().first)
            assertEquals("budgetChanged" to listOf("2.50"), calls[calls.lastIndex - 1])
        }

    @Test
    fun `B-NAV-021 clearing budgets requires the named confirmation`() = runComposeUiTest {
        var cleared = 0
        val actions =
            Proxy.newProxyInstance(
                UsageActions::class.java.classLoader,
                arrayOf(UsageActions::class.java),
            ) { _, method, _ ->
                if (method.name == "clearBudgets") cleared++
                null
            } as UsageActions
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
            ) {
                Box(Modifier.size(412.dp, 915.dp)) {
                    UsageScreen(UsageUi(budgetsAvailable = true, hasSavedBudgets = true), actions)
                }
            }
        }
        onNodeWithText("Clear both budgets").performClick()
        assertEquals(0, cleared)
        onNodeWithText("Clear budgets").performClick()
        assertEquals(1, cleared)
    }
}
