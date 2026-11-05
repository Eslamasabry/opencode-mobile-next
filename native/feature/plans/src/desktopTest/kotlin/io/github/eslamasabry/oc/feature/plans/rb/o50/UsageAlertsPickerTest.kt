package io.github.eslamasabry.oc.feature.plans.rb.o50

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.feature.plans.ui.PlansManagementScreen
import io.github.eslamasabry.oc.feature.plans.ui.ThresholdChooser
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UsageAlertsPickerTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(
            PlansManagementActions::class.java.classLoader,
            arrayOf(PlansManagementActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "AlertsActions"
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as PlansManagementActions

    @Composable
    private fun Page(busy: Boolean = false, phase: PlansPhase = PlansPhase.List) {
        KitTheme(
            KitAppearance(motion = MotionLevel.Off),
            DesktopKitPlatform(),
            localeTag = "en",
            stillFrame = true,
        ) {
            PlansManagementScreen(
                PlansManagementUi(
                    page = PlanManagePage.Alerts,
                    busy = busy,
                    preferences = PlanAlertPreferences(enabled = true),
                ),
                PlansUi(phase = phase),
                actions,
            ) { _, _ ->
            }
        }
    }

    @Test
    fun `B-NAV-073 default alert picker converts percent left back to stored used threshold`() =
        runComposeUiTest {
            setContent { Page() }
            onNodeWithTag("usage-alert-threshold").performClick()
            onNodeWithText("40% left").performClick()
            onNodeWithTag("kit-sheet").assertDoesNotExist()
            assertEquals(listOf("setDefaultThreshold" to listOf(60)), calls)
        }

    @Test
    fun `B-NAV-073 pending alert save holds the switch and picker`() = runComposeUiTest {
        setContent { Page(busy = true) }
        onNodeWithTag("usage-alert-threshold").assertIsNotEnabled().performClick()
        onNodeWithTag("kit-sheet").assertDoesNotExist()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `B-NAV-073 unread plans never claim there is nothing to watch`() = runComposeUiTest {
        setContent { Page(phase = PlansPhase.Loading) }
        onNodeWithText("There are no readable plans to watch yet.").assertDoesNotExist()
    }

    @Test
    fun `B-NAV-073 plan threshold reads percent used and inheritance carries the null sentinel`() =
        runComposeUiTest {
            val selected = mutableListOf<Int>()
            setContent {
                KitTheme(
                    KitAppearance(motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    ThresholdChooser(80, true, 90, onSelect = { selected += it })
                }
            }
            onNodeWithText("Alert me at").performClick()
            onNodeWithText("Same as other plans (90%)").performClick()
            assertEquals(listOf(-1), selected)
        }
}
