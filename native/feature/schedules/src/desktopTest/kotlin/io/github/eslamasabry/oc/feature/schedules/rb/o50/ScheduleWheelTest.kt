package io.github.eslamasabry.oc.feature.schedules.rb.o50

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.feature.schedules.presentation.*
import io.github.eslamasabry.oc.feature.schedules.ui.ScheduleEditScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ScheduleWheelTest {
    @Test
    fun `B-NAV-055 wheel keeps an off grid saved minute and passes the visible choice to save`() =
        runComposeUiTest {
            val fields =
                EditorFields(
                    prompt = "Run tests",
                    project = "/work/app",
                    kind = RecurrenceKind.Daily,
                    time = "09:07",
                )
            val saves = mutableListOf<EditorFields>()
            val actions =
                object : ScheduleEditActions {
                    override fun back() = Unit

                    override fun save(input: EditorFields) {
                        saves += input
                    }
                }
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    ScheduleEditScreen(
                        ScheduleEditUi(phase = EditPhase.Ready, fields = fields),
                        actions,
                    )
                }
            }
            onNodeWithTag("schedule-save").performClick()
            assertEquals(listOf("09:07"), saves.map { it.time })
            onNodeWithTag("schedule-wheel-time").performSemanticsAction(
                SemanticsActions.SetProgress
            ) {
                it(38f)
            }
            waitForIdle()
            onNodeWithTag("schedule-save").performClick()
            assertEquals(listOf("09:07", "09:15"), saves.map { it.time })
            assertEquals(fields.project, saves.last().project)
            assertEquals(fields.model, saves.last().model)
        }

    @Test
    fun `B-NAV-055 destination choices change only the conversation target`() = runComposeUiTest {
        val fields =
            EditorFields(
                prompt = "Check progress",
                project = "/work/app",
                kind = RecurrenceKind.Daily,
            )
        val saves = mutableListOf<EditorFields>()
        val actions =
            object : ScheduleEditActions {
                override fun back() = Unit

                override fun save(input: EditorFields) {
                    saves += input
                }
            }
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
            ) {
                ScheduleEditScreen(
                    ScheduleEditUi(
                        phase = EditPhase.Ready,
                        fields = fields,
                        canChooseConversation = true,
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("app").performScrollTo().performClick()
        onNodeWithText("New conversation each run").performClick()
        onNodeWithTag("schedule-save").performClick()
        assertEquals(listOf(false), saves.map { it.thisConversation })
        assertEquals(fields.prompt, saves.single().prompt)
        assertEquals(fields.project, saves.single().project)
    }
}
