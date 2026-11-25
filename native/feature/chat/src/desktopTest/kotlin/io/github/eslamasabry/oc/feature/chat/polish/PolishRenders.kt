@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.chat.polish

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ChangedFile
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import io.github.eslamasabry.oc.feature.chat.E20Scene
import io.github.eslamasabry.oc.feature.chat.block
import io.github.eslamasabry.oc.feature.chat.footer
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.feature.chat.ui.ChatPane
import io.github.eslamasabry.oc.feature.chat.ui.ReplyActions
import io.github.eslamasabry.oc.feature.chat.ui.ToolStepBody
import io.github.eslamasabry.oc.feature.chat.ui.TurnChangedFiles
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

class PolishRenders {
    companion object {
        const val PHASE = "after"
    }

    private val noReqActions =
        object : ChatRequestActions {
            override fun shown(id: RequestId) = Unit
            override fun allowOnce(id: RequestId) = Unit
            override fun reject(id: RequestId) = Unit
            override fun askAlwaysAllow(id: RequestId) = Unit
            override fun confirmAlwaysAllow() = Unit
            override fun cancelAlwaysAllow() = Unit
            override fun keepAsking(id: RequestId) = Unit
            override fun choose(id: RequestId, option: String) = Unit
            override fun openOther(id: RequestId) = Unit
            override fun editOther(id: RequestId, text: String) = Unit
            override fun sendOther(id: RequestId) = Unit
            override fun openSheet(id: RequestId) = Unit
            override fun closeSheet(id: RequestId) = Unit
            override fun chooseInSheet(id: RequestId, prompt: Int, label: String) = Unit
            override fun editSheetOther(id: RequestId, prompt: Int, text: String) = Unit
            override fun sendSheet(id: RequestId) = Unit
            override fun undo(id: RequestId) = Unit
            override fun retry(id: RequestId) = Unit
        }

    private fun out(n: String, scene: String, scale: String = "1.0", mode: String = "dark"): File =
        File("build/polish-render").also { it.mkdirs() }.let { File(it, "$n-$PHASE-$scene-360-$scale-$mode.png") }

    @Test
    fun `render 561 loading glass`() =
        kitUiTest(360, 780) {
            val scene = ChatScene()
            val model = ComposerModel("anthropic", "claude-sonnet-4", "Claude Sonnet 4", false)
            scene.composer = scene.composer.copy(models = ComposerModels(usesDefault = true, serverDefault = model))
            val state = scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf())
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(style = Style.Glass, mode = AppearanceMode.Light, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            scene.Screen(state, canGoBack = true)
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(ChatPane.LOADING_WORDS_AFTER_MS + 500)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out("561", "loading-glass", "1.0", "light"))
        }

    @Test
    fun `render 564 web ask`() =
        kitUiTest(360, 560) {
            fun perm(id: String, p: String, pat: String): RequestCardUi.Permission {
                val r =
                    PermissionRequest(
                        RequestId(id),
                        SessionId("ses_1"),
                        p,
                        persistentListOf(pat),
                        JsonObject(emptyMap()),
                        persistentListOf("$pat *"),
                        null,
                        true,
                        null,
                    )
                return RequestCardUi.Permission(
                    r.id,
                    null,
                    CardState.Waiting,
                    PermissionAsk.of(r, "shopfront", "/home/me/shopfront"),
                    true,
                    PendingRequest.Permission(r),
                )
            }
            val cards =
                persistentListOf<RequestCardUi>(
                    perm("p1", "bash", "npm test"),
                    perm("p2", "webfetch", "https://example.com/docs"),
                )
            setContent { E20Scene(dark = true) { ChatRequestSlot(RequestSlotUi(cards), noReqActions, nowMs = null) } }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out("564", "web-ask", "1.0", "dark"))
        }

    @Test
    fun `render 567 write step`() =
        kitUiTest(360, 760) {
            val path = "/home/owner/projects/app/src/main/kotlin/WriteTargetWithAVeryLongName.kt"
            val content = (1..24).joinToString("\n") { "val line$it = \"content of line $it\"" }
            val state =
                ToolState(
                    status = ToolStatus.Completed,
                    input = JsonObject(mapOf("filePath" to JsonPrimitive(path), "content" to JsonPrimitive(content))),
                    output = "ok",
                    startedAtMs = 1_000,
                    completedAtMs = 1_040,
                )
            val step =
                TurnStep("w1", toolFacts("write", state), StepState.Done, 40, 1_000, toolOutputView(state, "write"))
            val reply = ReplyActions(KitMarkdownActions(onLink = {}), copyLabel = "Copy", copy = {}, onOpenChanges = {})
            setContent {
                E20Scene(scale = 1.3f, dark = false) {
                    KitToolRow(
                        KitToolView(
                            step.partId,
                            KitToolKind.Write,
                            "Write",
                            step.facts.subject,
                            null,
                            KitToolStatus.Done,
                            null,
                            24,
                            null,
                            "Done",
                        ),
                        opened = true,
                        onToggle = {},
                        body = { ToolStepBody(step, reply) },
                    )
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out("567", "write-step", "1.3", "light"))
        }

    @Test
    fun `render 571 scrolled header`() =
        kitUiTest(360, 720) {
            val scene = ChatScene()
            val rows =
                buildList {
                    for (i in 18 downTo 1) {
                        add(footer("t$i"))
                        add(block("t$i", "a$i", "p$i", 0, "Reply paragraph $i with enough bright text to span."))
                        add(prompt("t$i", "Prompt message $i asking about checkout"))
                    }
                }.toImmutableList()
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalKitScreenInsets provides WindowInsets(top = 24.dp),
                ) {
                    KitTheme(
                        KitAppearance(style = Style.Solid, mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            scene.Screen(scene.live().copy(rows = rows), canGoBack = true)
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list", useUnmergedTree = true).performTouchInput {
                down(Offset(width * 0.5f, height * 0.4f))
                repeat(12) { moveBy(Offset(0f, 30f), delayMillis = 16) }
                up()
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out("571", "scrolled-header", "1.0", "dark"))
        }

    @Test
    fun `render 580 changed files`() =
        kitUiTest(360, 320) {
            setContent {
                E20Scene(dark = true) {
                    Box(Modifier.width(360.dp)) {
                        TurnChangedFiles(
                            files = listOf(ChangedFile("src/app.kt", 3, 1), ChangedFile("src/main.kt", 1, 0)),
                            onOpenFile = {},
                            onAllFiles = {},
                        )
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out("580", "changed-files", "1.0", "dark"))
        }

    @Test
    fun `render 561 plain topbar`() =
        kitUiTest(360, 640) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalKitScreenInsets provides WindowInsets(top = 24.dp),
                ) {
                    KitTheme(
                        KitAppearance(style = Style.Glass, mode = AppearanceMode.Light, motion = MotionLevel.Off),
                        DesktopKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        KitScreen(
                            page = true,
                            topBar = {
                                KitTopBar(
                                    title = "Conversations",
                                    subtitle = "This phone",
                                    exit = KitTopBarExit.None,
                                )
                            },
                        ) {
                            Column(Modifier.fillMaxSize().verticalScroll(kitPageScrollState(initial = 180))) {
                                for (i in 1..20) {
                                    KitText(
                                        "Item $i — scrollable body content under the plain top bar",
                                        Modifier.padding(vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out("561", "plain-topbar", "1.0", "light"))
        }
}
