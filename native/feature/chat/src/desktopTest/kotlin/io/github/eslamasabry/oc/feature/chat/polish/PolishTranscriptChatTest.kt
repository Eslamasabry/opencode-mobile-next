@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.chat.polish

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
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
import java.util.Locale
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolishTranscriptChatTest {
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

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

    private fun permission(id: String, perm: String, pattern: String): RequestCardUi.Permission {
        val req =
            PermissionRequest(
                RequestId(id),
                SessionId("ses_1"),
                perm,
                persistentListOf(pattern),
                JsonObject(emptyMap()),
                persistentListOf("$pattern *"),
                null,
                true,
                null,
            )
        return RequestCardUi.Permission(
            id = req.id,
            waitingSinceMs = null,
            state = CardState.Waiting,
            ask = PermissionAsk.of(req, project = "shopfront", directory = "/home/me/shopfront"),
            canAlwaysAllow = true,
            captured = PendingRequest.Permission(req),
        )
    }

    @Test
    fun `B-561 loading chat keeps Back and no raw Server default`() =
        kitUiTest(360, 780) {
            val scene = ChatScene()
            val defaultModel = ComposerModel("anthropic", "claude-sonnet-4", "Claude Sonnet 4", false)
            scene.composer =
                scene.composer.copy(
                    models = ComposerModels(usesDefault = true, serverDefault = defaultModel),
                )
            val state = scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf())
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(style = Style.Solid, mode = AppearanceMode.Dark, motion = MotionLevel.Off),
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
            onNodeWithTag("kit-top-bar-exit", useUnmergedTree = true).assertExists()
            onNodeWithText("Claude Sonnet 4", useUnmergedTree = true).assertExists()
            val header = onNodeWithTag("kit-header-capsule", useUnmergedTree = true).getBoundsInRoot()
            val skeleton = onNodeWithTag("chat-loading", useUnmergedTree = true).getBoundsInRoot()
            val gap = (skeleton.top - header.bottom).value
            assertTrue(gap in -1f..24f, "skeleton top must be within 24 dp of header bottom, was $gap dp")
            val title = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).getBoundsInRoot()
            val sub = onNodeWithText("shopfront", useUnmergedTree = true).getBoundsInRoot()
            val titleGap = (sub.top - title.bottom).value
            assertTrue(titleGap <= 4.5f, "subtitle top must be <= title bottom + 4 dp, was $titleGap dp")
        }

    @Test
    fun `B-564 web ask row has no question-comma`() {
        val cards =
            listOf(
                permission("p1", "bash", "npm test"),
                permission("p2", "webfetch", "https://example.com/docs"),
            ).toImmutableList()
        for (locale in listOf("en", "ar")) {
            Locale.setDefault(Locale.forLanguageTag(locale))
            kitUiTest(360, 640) {
                setContent {
                    E20Scene(locale = locale, dark = true) {
                        ChatRequestSlot(RequestSlotUi(cards), noReqActions, nowMs = null)
                    }
                }
                waitForIdle()
                val allText =
                    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { s -> s.text } }
                val folded = allText.filter { "example.com" in it }
                assertTrue(folded.isNotEmpty(), "$locale: expected folded web ask row in $allText")
                for (t in folded) {
                    assertFalse("?," in t, "$locale: found '?,' in '$t'")
                    assertFalse("\u061F," in t, "$locale: found '؟,' in '$t'")
                }
            }
        }
    }

    @Test
    fun `B-567 Write step at 1_3x shows the path once and View all changes`() =
        kitUiTest(360, 1000) {
            val path = "/home/owner/projects/app/src/main/kotlin/WriteTargetWithAVeryLongName.kt"
            val content = (1..24).joinToString("\n") { "val line$it = \"content of line $it\"" }
            val state =
                ToolState(
                    ToolStatus.Completed,
                    input = JsonObject(mapOf("filePath" to JsonPrimitive(path), "content" to JsonPrimitive(content))),
                    output = "Wrote file successfully.",
                    startedAtMs = 1_000,
                    completedAtMs = 1_040,
                )
            val step =
                TurnStep("w1", toolFacts("write", state), StepState.Done, 40, 1_000, toolOutputView(state, "write"))
            val reply = ReplyActions(KitMarkdownActions(onLink = {}), copyLabel = "Copy", copy = {}, onOpenChanges = {})
            setContent {
                E20Scene(scale = 1.3f) {
                    KitToolRow(
                        KitToolView(
                            id = step.partId,
                            kind = KitToolKind.Write,
                            title = "Write",
                            subject = step.facts.subject,
                            detail = null,
                            status = KitToolStatus.Done,
                            durationLabel = null,
                            added = 24,
                            removed = null,
                            statusLabel = "Done",
                        ),
                        opened = true,
                        onToggle = {},
                        body = { ToolStepBody(step, reply) },
                    )
                }
            }
            waitForIdle()
            onAllNodesWithText(path, useUnmergedTree = true).assertCountEquals(0)
            onNodeWithText("View all changes", useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-571 no transcript text above the header`() =
        kitUiTest(360, 720) {
            val scene = ChatScene()
            val rows =
                buildList {
                    for (i in 18 downTo 1) {
                        val turn = "t$i"
                        add(footer(turn))
                        val line = "Reply paragraph $i with enough bright text to span the whole width of the screen."
                        add(block(turn, "a$i", "p$i", 0, line))
                        add(prompt(turn, "Prompt message $i asking about checkout"))
                    }
                }.toImmutableList()
            val state = scene.live().copy(rows = rows)
            var ground = Color.Black
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
                        ground = LocalKitColors.current.ground
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            scene.Screen(state, canGoBack = true)
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
            val pill = onNodeWithTag("kit-header-capsule", useUnmergedTree = true).getBoundsInRoot()
            val pixels = onRoot().captureToImage().toPixelMap()
            val topEnd = pill.top.value.toInt().coerceIn(0, pixels.height - 1)
            val pillBottom = pill.bottom.value.toInt().coerceIn(0, pixels.height - 1)
            val pillLeft = (pill.left.value.toInt() - 2).coerceIn(0, pixels.width - 1)
            var leaks = 0
            // Check above the pill's top AND in the left side margin beside the pill.
            for (y in 25 until topEnd) {
                for (x in 16 until pixels.width - 16) {
                    if (colorDiff(pixels[x, y], ground) > 0.06f) leaks++
                }
            }
            for (y in topEnd until pillBottom) {
                for (x in 2 until pillLeft) {
                    if (colorDiff(pixels[x, y], ground) > 0.06f) leaks++
                }
            }
            assertTrue(leaks == 0, "found $leaks non-ground transcript pixels above/beside header pill ($pill)")
        }

    @Test
    fun `B-580 file row glyph aligns with All files`() =
        kitUiTest(360, 480) {
            setContent {
                E20Scene(dark = true) {
                    Box(Modifier.width(360.dp)) {
                        TurnChangedFiles(
                            files = listOf(ChangedFile("src/app.kt", 3, 1)),
                            onOpenFile = {},
                            onAllFiles = {},
                        )
                    }
                }
            }
            waitForIdle()
            val rowInner = onNodeWithTag("kit-row", useUnmergedTree = true).getBoundsInRoot()
            val allFiles = onNodeWithTag("chat-turn-all-files", useUnmergedTree = true).getBoundsInRoot()
            val diff = abs((rowInner.left - allFiles.left).value)
            assertTrue(
                diff <= 1f,
                "file row glyph x (${rowInner.left}) must equal All files x (${allFiles.left}) within 1 dp",
            )
            assertTrue(
                abs(rowInner.left.value - 16f) <= 1f,
                "file row glyph x (${rowInner.left}) must be 16 dp from column edge",
            )
        }

    private fun colorDiff(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
}
