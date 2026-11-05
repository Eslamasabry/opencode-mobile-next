package io.github.eslamasabry.oc.feature.plans.rb.o50

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.feature.plans.ui.PlansManagementScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@OptIn(ExperimentalTestApi::class)
class UsageReaderTest {
    private val calls = mutableListOf<String>()
    private val actions =
        Proxy.newProxyInstance(
            PlansManagementActions::class.java.classLoader,
            arrayOf(PlansManagementActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ReaderActions"
                else -> {
                    calls += method.name
                    null
                }
            }
        } as PlansManagementActions

    private fun page(
        reader: PlanReaderStatus? = PlanReaderStatus("work", PlanReaderKind.Computer),
        busy: Boolean = false,
    ) =
        PlansManagementUi(
            page = PlanManagePage.Reader,
            readerId = "work",
            readerName = "Work",
            busy = busy,
            readers = if (reader == null) persistentMapOf() else persistentMapOf("work" to reader),
        )

    @Composable
    private fun Page(state: PlansManagementUi) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            PlansManagementScreen(state, PlansUi(), actions) { _, _ -> }
        }
    }

    @Test
    fun `B-SEC-060 reader opening sends nothing and turn on uses its acknowledged action`() =
        runComposeUiTest {
            val state = mutableStateOf(page())
            setContent { Page(state.value) }
            onNodeWithText("Read usage from Work").assertExists()
            assertEquals(emptyList(), calls)
            onNodeWithText("Turn on").performClick()
            assertEquals(listOf("readerStart"), calls)
            runOnIdle {
                state.value =
                    page(
                        PlanReaderStatus(
                            "work",
                            PlanReaderKind.Computer,
                            enabled = true,
                            phase = PlanReaderPhase.Ready,
                            foundProviderIds = persistentListOf("claude"),
                        )
                    )
            }
            onNodeWithText("Found ", substring = true).assertExists()
            onNodeWithText("Done").performClick()
            assertEquals(listOf("readerStart", "dismiss"), calls)
        }

    @Test
    fun `B-SEC-060 gone reader explains why and offers no start command`() = runComposeUiTest {
        setContent { Page(page(null)) }
        onNodeWithText("That connection is gone").assertExists()
        onNodeWithText("Go back to Plans to see what is left.").assertExists()
        onNodeWithText("Turn on").assertDoesNotExist()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `B-SEC-060 setup stage keeps its description and holds actions until acknowledged completion`() =
        runComposeUiTest {
            val local = PlanReaderStatus("work", PlanReaderKind.InAppUbuntu)
            val state = mutableStateOf(page(local))
            setContent { Page(state.value) }
            onNodeWithText("Set up usage").performClick()
            runOnIdle {
                state.value =
                    page(local.copy(busy = true, phase = PlanReaderPhase.Copying), busy = true)
            }
            onNodeWithText("Copying the usage helper in").assertExists()
            onNodeWithText("Not now").assertIsNotEnabled()
            assertEquals(listOf("readerStart"), calls)
            runOnIdle {
                state.value = page(local.copy(enabled = true, phase = PlanReaderPhase.Ready))
            }
            waitForIdle()
            assertEquals(listOf("readerStart", "dismiss"), calls)
        }

    @Test
    fun `B-SEC-060 distinct Node version failure retains read now and off actions`() =
        runComposeUiTest {
            setContent {
                Page(
                    page(
                        PlanReaderStatus(
                            "work",
                            PlanReaderKind.Computer,
                            enabled = true,
                            problem = PlanReaderProblem.NodeTooOld,
                        )
                    )
                )
            }
            onNodeWithText("Work needs Node.js 20 or later.").assertExists()
            onNodeWithText("Read now").performClick()
            assertEquals(listOf("readerRead"), calls)
            onNodeWithText("Turn off").performClick()
            assertEquals(listOf("readerRead", "readerOff"), calls)
        }
}
