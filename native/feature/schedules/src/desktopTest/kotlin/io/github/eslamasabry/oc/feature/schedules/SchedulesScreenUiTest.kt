package io.github.eslamasabry.oc.feature.schedules

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import io.github.eslamasabry.oc.feature.schedules.presentation.EditPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.EditorFields
import io.github.eslamasabry.oc.feature.schedules.presentation.LastRunUi
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditActions
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditUi
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleRowUi
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesActions
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesUi
import io.github.eslamasabry.oc.feature.schedules.ui.ScheduleEditScreen
import io.github.eslamasabry.oc.feature.schedules.ui.SchedulesScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real screens with fake state, found by visible text (plan 07 K01/K02). */
@OptIn(ExperimentalTestApi::class)
class SchedulesScreenUiTest {
    private val t0 = 1_793_606_400_000L

    private class Recorder : SchedulesActions, ScheduleEditActions {
        val calls = mutableListOf<String>()
        var saved: EditorFields? = null

        override fun back() {
            calls += "back"
        }

        override fun create() {
            calls += "create"
        }

        override fun edit(id: String) {
            calls += "edit $id"
        }

        override fun setEnabled(id: String, enabled: Boolean) {
            calls += "enabled $id $enabled"
        }

        override fun runNow(id: String) {
            calls += "run $id"
        }

        override fun askDelete(id: String) {
            calls += "ask $id"
        }

        override fun confirmDelete() {
            calls += "delete"
        }

        override fun cancelDelete() {
            calls += "cancel"
        }

        override fun openConversation(sessionId: String) {
            calls += "open $sessionId"
        }

        override fun setDigest(enabled: Boolean) {
            calls += "digest $enabled"
        }

        override fun retry() {
            calls += "retry"
        }

        override fun save(input: EditorFields) {
            saved = input
        }
    }

    @Composable
    private fun Themed(locale: String = "en", content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) { content() }
            }
        }
    }

    private val row =
        ScheduleRowUi(
            id = "a1",
            prompt = "Check the nightly build",
            recurrence = Recurrence.daily(9, 0),
            nextRunAtMs = t0 + 3_600_000L,
            enabled = true,
            continuesConversation = false,
            last = LastRunUi(RunStatus.Failed, "needs_you", t0 - 60_000L, "ses_1", late = false),
        )

    private val list = SchedulesUi(phase = SchedulesPhase.List, serverName = "Home server", rows = persistentListOf(row), nowMs = t0)

    @Test
    fun `SCREENS-NAV-98 the list shows each schedule in plain words with visible actions`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { SchedulesScreen(list, actions) } }
        onNodeWithText("Check the nightly build").assertIsDisplayed()
        onNodeWithText("Run now").performClick()
        onNodeWithText("New scheduled prompt").performClick()
        assertEquals(listOf("run a1", "create"), actions.calls)
    }

    @Test
    fun `B-NAV-055 a failed run says what happened and what to do next`() = runComposeUiTest {
        setContent { Themed { SchedulesScreen(list, Recorder()) } }
        onNodeWithText("It's waiting for your answer. Open the conversation to continue.").assertExists()
    }

    @Test
    fun `an empty server offers one way to start`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { SchedulesScreen(SchedulesUi(phase = SchedulesPhase.Empty, nowMs = t0), actions) } }
        onNodeWithText("No scheduled prompts yet").assertExists()
        onNodeWithText("New scheduled prompt").performClick()
        assertEquals(listOf("create"), actions.calls)
    }

    @Test
    fun `B-L10N-014 the Arabic list reads in plain Arabic words`() = runComposeUiTest {
        setContent { Themed("ar") { SchedulesScreen(list, Recorder()) } }
        onNodeWithText("الطلبات المجدولة").assertExists()
        onNodeWithText("شغّل الآن").assertExists()
    }

    @Test
    fun `the editor saves what was typed`() = runComposeUiTest {
        val actions = Recorder()
        val state = ScheduleEditUi(phase = EditPhase.Ready, fields = EditorFields(prompt = "Summarize", project = "/work/app"), seed = 1)
        setContent { Themed { ScheduleEditScreen(state, actions) } }
        onNodeWithText("Save schedule").performClick()
        assertEquals("Summarize", actions.saved?.prompt)
        assertEquals("09:00", actions.saved?.time)
    }
}
