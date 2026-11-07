package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.feature.chat.ui.durationWords
import io.github.eslamasabry.oc.feature.chat.ui.shownStepDurationMs
import io.github.eslamasabry.oc.feature.chat.ui.stepRowSubject
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** E20-S14 (owner's phone, 11-07): the step row's time and chevron share one line; a waiting Write names its file. */
@OptIn(ExperimentalTestApi::class)
class E20S14ToolRowsTest {
    @Test
    fun `E20-S14 the time and the chevron share the title's line at its end`() {
        for (scale in listOf(1f, 1.3f)) runDesktopComposeUiTest(360, 400) {
            setContent {
                E20Scene(scale) {
                    KitToolRow(
                        KitToolView("r", KitToolKind.Read, "Read", "main.kt", null, KitToolStatus.Done, "2.4s", null, null, "Done"),
                        opened = false,
                        onToggle = {},
                        body = { },
                    )
                }
            }
            waitForIdle()
            val title = boundsOfText("Read")
            val time = boundsOfTag("kit-tool-duration")
            val chevron = boundsOfTag("kit-tool-chevron")
            assertTrue(abs(time.center.y - chevron.center.y) < 1f, "${scale}x: time $time and chevron $chevron on one line")
            assertTrue(abs(time.center.y - title.center.y) < 2f, "${scale}x: on the title's line, $time vs $title")
            assertTrue(chevron.left >= time.right, "${scale}x: the chevron ends the line, $chevron after $time")
            assertTrue(chevron.right >= 360 - 16 - 1, "${scale}x: end-aligned on the column's edge, $chevron")
        }
    }

    @Test
    fun `E20-S14 the row states as goldens - no 0_0s, a waiting Write named, a running step`() {
        val pending =
            ToolState(ToolStatus.Pending, inputRaw = """{"filePath":"/repo/app/src/main/Checkout.kt","content":"pack""")
        val pendingOut = toolOutputView(pending, "write")
        runDesktopComposeUiTest(360, 420) {
            setContent {
                E20Scene {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        fun time(state: StepState, ms: Long?, started: Long?) = shownStepDurationMs(state, ms, started, 10_000)
                        @Composable
                        fun row(id: String, kind: KitToolKind, title: String, subject: String?, status: KitToolStatus, ms: Long?, word: String) =
                            KitToolRow(
                                KitToolView(id, kind, title, subject, null, status, ms?.let { durationWords(it) }, null, null, word),
                                opened = false,
                                onToggle = {},
                                body = { },
                            )
                        row("a", KitToolKind.Read, "Read", "main.kt", KitToolStatus.Done, time(StepState.Done, 0, 1_000), "Done")
                        row("b", KitToolKind.Write, "Write", "notes.md", KitToolStatus.Done, time(StepState.Done, 2_400, 1_000), "Done")
                        row(
                            "c",
                            KitToolKind.Write,
                            "Write",
                            stepRowSubject(ToolKind.Write, StepState.Pending, null, pendingOut.input, pendingOut.rawInput),
                            KitToolStatus.Pending,
                            null,
                            "Waiting",
                        )
                        row("d", KitToolKind.Shell, "Run", "./gradlew test", KitToolStatus.Running, time(StepState.Running, null, 7_500), "Running")
                        row("e", KitToolKind.Search, "Search", "TODO", KitToolStatus.Failed, time(StepState.Failed, 900, 1_000), "Didn't finish")
                    }
                }
            }
            waitForIdle()
            boundsOfText("Checkout.kt")
            compareGolden("e20-s14-row-states")
        }
    }
}
