package io.github.eslamasabry.oc.feature.usage.rb.o50

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.usage.presentation.*
import io.github.eslamasabry.oc.feature.usage.ui.UsageScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class UsageFiltersTest {
    @Test
    fun `B-PROTO-122 range and scope pickers dispatch their named values and dismiss`() =
        runComposeUiTest {
            val calls = mutableListOf<Pair<String, List<Any?>>>()
            val actions =
                Proxy.newProxyInstance(
                    UsageActions::class.java.classLoader,
                    arrayOf(UsageActions::class.java),
                ) { proxy, method, args ->
                    when (method.name) {
                        "equals" -> proxy === args?.getOrNull(0)
                        "hashCode" -> System.identityHashCode(proxy)
                        "toString" -> "UsageFilterActions"
                        else -> {
                            calls += method.name to args?.toList().orEmpty()
                            null
                        }
                    }
                } as UsageActions
            val state =
                UsageUi(
                    phase = UsagePhase.Ready,
                    projects = persistentListOf(UsageProject("shop", "Shopfront")),
                )
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    UsageScreen(state, actions)
                }
            }
            onNodeWithText("Today").assertDoesNotExist()
            onNodeWithTag("usage-range").performClick()
            onNodeWithText("Today").performClick()
            onNodeWithText("Today").assertDoesNotExist()
            onNodeWithTag("usage-scope").performClick()
            onNodeWithText("Shopfront").performClick()
            assertEquals(
                listOf(
                    "chooseRange" to listOf(UsageRange.Today),
                    "chooseScope" to listOf(UsageScope.Project("shop")),
                ),
                calls,
            )
        }
}
