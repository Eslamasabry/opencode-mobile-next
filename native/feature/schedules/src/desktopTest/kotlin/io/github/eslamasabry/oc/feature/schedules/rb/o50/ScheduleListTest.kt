package io.github.eslamasabry.oc.feature.schedules.rb.o50

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.feature.schedules.presentation.*
import io.github.eslamasabry.oc.feature.schedules.ui.SchedulesScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class ScheduleListTest {
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions =
        Proxy.newProxyInstance(
            SchedulesActions::class.java.classLoader,
            arrayOf(SchedulesActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ScheduleActions"
                else -> {
                    calls += method.name to args?.toList().orEmpty()
                    null
                }
            }
        } as SchedulesActions

    @Composable
    private fun Page(state: SchedulesUi) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            SchedulesScreen(state, actions)
        }
    }

    @Test
    fun `B-NAV-055 schedule words open edit and the separate switch names its target`() =
        runComposeUiTest {
            val row =
                ScheduleRowUi(
                    "daily",
                    "Daily report",
                    Recurrence.daily(9, 0),
                    2_000_000,
                    true,
                    false,
                    null,
                )
            setContent {
                Page(
                    SchedulesUi(
                        phase = SchedulesPhase.List,
                        nowMs = 1_000_000,
                        rows = persistentListOf(row),
                    )
                )
            }
            onNodeWithText("Daily report").performClick()
            onNode(isToggleable() and hasContentDescription("Daily report")).performClick()
            assertEquals(
                listOf("edit" to listOf("daily"), "setEnabled" to listOf("daily", false)),
                calls,
            )
        }

    @Test
    fun `B-NAV-055 soonest schedules lead paused and finished schedules`() = runComposeUiTest {
        val later =
            ScheduleRowUi("later", "Later", Recurrence.daily(9, 0), 3_000_000, true, false, null)
        val sooner = later.copy(id = "sooner", prompt = "Sooner", nextRunAtMs = 2_000_000)
        val paused = later.copy(id = "paused", prompt = "Paused report", enabled = false)
        val finished =
            later.copy(
                id = "finished",
                prompt = "Finished report",
                recurrence = Recurrence.once(0),
                nextRunAtMs = null,
            )
        setContent {
            Page(
                SchedulesUi(
                    phase = SchedulesPhase.List,
                    nowMs = 1_000_000,
                    rows = persistentListOf(finished, paused, later, sooner),
                )
            )
        }
        val positions =
            listOf("sooner", "later", "paused", "finished").map {
                onNodeWithTag("schedule-$it").fetchSemanticsNode().boundsInRoot.top
            }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
        onNodeWithText("No more runs").assertExists()
    }

    @Test
    fun `B-NAV-055 empty schedules keep one pinned New action`() = runComposeUiTest {
        setContent { Page(SchedulesUi(phase = SchedulesPhase.Empty)) }
        onAllNodesWithText("New scheduled prompt").assertCountEquals(1)
        onNodeWithText("New scheduled prompt").performClick()
        assertEquals(listOf("create" to emptyList()), calls)
    }
}
