package io.github.eslamasabry.oc.feature.plans.rb.o50

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.feature.plans.ui.PlansManagementScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UsageWidgetSettingsTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(
            PlansManagementActions::class.java.classLoader,
            arrayOf(PlansManagementActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "WidgetSettingsActions"
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as PlansManagementActions

    @Test
    fun `B-NAV-073 widget windows use the named preference and setup help is visible`() =
        runComposeUiTest {
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    PlansManagementScreen(
                        PlansManagementUi(page = PlanManagePage.Widget),
                        PlansUi(phase = PlansPhase.Empty),
                        actions,
                    ) { _, _ ->
                    }
                }
            }
            onNodeWithText("No subscriptions yet").assertExists()
            onNodeWithText("Sign in, add key and set up rows").assertExists()
            onNodeWithText("Weekly").performClick()
            assertEquals(listOf("setWidgetWindows" to listOf(UsageWidgetWindows.Weekly)), calls)
        }

    @Test
    fun `B-NAV-073 widget preferences hold while their write is pending`() = runComposeUiTest {
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                PlansManagementScreen(
                    PlansManagementUi(page = PlanManagePage.Widget, busy = true),
                    PlansUi(phase = PlansPhase.Empty),
                    actions,
                ) { _, _ ->
                }
            }
        }
        onNodeWithText("Weekly").assertIsNotEnabled()
        onNodeWithTag("usage-widget-setup").assertIsNotEnabled()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `B-NAV-073 plan switch hides only its named subscription`() = runComposeUiTest {
        val card =
            PlanCardUi(
                key = "claude:work",
                providerId = "claude",
                name = "Claude",
                connection = null,
                state = SubscriptionState.Ok,
                plan = "Pro",
                windows = kotlinx.collections.immutable.persistentListOf(),
                fix = UsageFix.None,
                fixConnection = null,
                signedInOn = kotlinx.collections.immutable.persistentListOf(),
                resetSince = false,
                fetchedAtMs = null,
                alertThreshold = 80,
            )
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                PlansManagementScreen(
                    PlansManagementUi(page = PlanManagePage.Widget),
                    PlansUi(
                        phase = PlansPhase.List,
                        cards = kotlinx.collections.immutable.persistentListOf(card),
                    ),
                    actions,
                ) { _, _ ->
                }
            }
        }
        onNodeWithTag("usage-widget-plan-claude:work").performClick()
        assertEquals(listOf("setWidgetHidden" to listOf("claude:work", true)), calls)
    }
}
