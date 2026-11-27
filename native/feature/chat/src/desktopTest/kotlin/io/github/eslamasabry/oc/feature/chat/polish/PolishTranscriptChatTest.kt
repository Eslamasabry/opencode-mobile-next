@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.chat.polish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
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
    fun `B-561 loading words show under a top skeleton`() =
        kitUiTest(360, 780) {
            val scene = ChatScene()
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
            val header = onNodeWithTag("kit-header-capsule", useUnmergedTree = true).getBoundsInRoot()
            val prompts = onAllNodesWithTag("kit-skeleton-prompt", useUnmergedTree = true)
            val promptTop = prompts.fetchSemanticsNodes().indices.minOf { prompts[it].getBoundsInRoot().top }
            val promptGap = (promptTop - header.bottom).value
            assertTrue(promptGap in -1f..24f, "kit-skeleton-prompt top must be within 24 dp of header, was $promptGap")
            val lineMatcher =
                SemanticsMatcher("kit-skeleton-line-*") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("kit-skeleton-line-") == true
                }
            val lines = onAllNodes(lineMatcher, useUnmergedTree = true)
            val lineCount = lines.fetchSemanticsNodes().size
            assertTrue(lineCount > 0, "expected kit-skeleton-line-* nodes")
            val lastLineBottom = (0 until lineCount).maxOf { lines[it].getBoundsInRoot().bottom }
            val words = onNodeWithTag("chat-loading-words", useUnmergedTree = true).getBoundsInRoot()
            assertTrue((words.bottom - words.top).value > 0f, "chat-loading-words height must be > 0, was $words")
            assertTrue(
                words.top >= lastLineBottom,
                "chat-loading-words top (${words.top}) must sit below last skeleton line ($lastLineBottom)",
            )
        }

    @Test
    fun `B-561 waiting words stay visible in a short chat`() =
        kitUiTest(360, 480) {
            val scene = ChatScene()
            val state = scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf())
            mainClock.autoAdvance = false
            setContent {
                E20Scene(scale = 1.3f, dark = true) {
                    scene.Screen(state, canGoBack = true)
                }
            }
            mainClock.advanceTimeBy(ChatPane.LOADING_WORDS_AFTER_MS + 500)
            waitForIdle()
            val lineMatcher =
                SemanticsMatcher("kit-skeleton-line-*") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("kit-skeleton-line-") == true
                }
            val lines = onAllNodes(lineMatcher, useUnmergedTree = true)
            val lineCount = lines.fetchSemanticsNodes().size
            assertTrue(lineCount > 0, "expected kit-skeleton-line-* nodes")
            val lastLineBottom = (0 until lineCount).maxOf { lines[it].getBoundsInRoot().bottom }
            val words = onNodeWithTag("chat-loading-words", useUnmergedTree = true).getBoundsInRoot()
            val composer = onNodeWithTag("kit-composer-box", useUnmergedTree = true).getBoundsInRoot()
            val wordsHeight = (words.bottom - words.top).value
            assertTrue(wordsHeight >= 14f, "chat-loading-words must stay visible at 1.3x, was $wordsHeight dp")
            assertTrue(
                words.top >= lastLineBottom,
                "chat-loading-words top (${words.top}) must sit below skeleton ($lastLineBottom)",
            )
            assertTrue(
                words.bottom <= composer.top + 1.dp,
                "chat-loading-words bottom (${words.bottom}) must stay above composer (${composer.top})",
            )
        }

    @Test
    fun `B-564 web ask row has no question-comma`() {
        val cards =
            listOf(
                permission("p1", "bash", "npm test"),
                permission("p2", "webfetch", "https://example.com/docs"),
            ).toImmutableList()
        for (locale in listOf("en", "ar", "zh", "ja")) {
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
                    assertFalse("\uFF1F," in t, "$locale: found '？,' in '$t'")
                    val beforeDot = t.substringBefore(" · ").trimEnd()
                    assertFalse(
                        beforeDot.endsWith('?') || beforeDot.endsWith('\u061F') || beforeDot.endsWith('\uFF1F'),
                        "$locale: stray question mark before dot in '$t'",
                    )
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
            val title = onNodeWithTag("kit-row-title", useUnmergedTree = true).getBoundsInRoot()
            val glyphX = title.left - 38.dp // 28 dp tile - 2 dp centred glyph inset + Space.S3 (12 dp)
            val allFiles = onNodeWithTag("chat-turn-all-files", useUnmergedTree = true).getBoundsInRoot()
            val diff = abs((glyphX - allFiles.left).value)
            assertTrue(
                diff <= 1f,
                "file row glyph x ($glyphX) must equal All files x (${allFiles.left}) within 1 dp",
            )
            assertTrue(
                abs(glyphX.value - 16f) <= 1f,
                "file row glyph x ($glyphX) must be 16 dp from column edge",
            )
        }

    @Test
    fun `B-580 file and folder glyphs share an edge`() {
        for (style in listOf(Style.Solid, Style.Glass)) {
            kitUiTest(360, 480) {
                setContent {
                    E20Scene(dark = true, style = style) {
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
                val fileGlyph = onNodeWithText("\ue23a", useUnmergedTree = true).getBoundsInRoot()
                val folderGlyph = onNodeWithText("\ue256", useUnmergedTree = true).getBoundsInRoot()
                val diff = abs((fileGlyph.left - folderGlyph.left).value)
                assertTrue(
                    diff <= 1f,
                    "$style: file glyph left (${fileGlyph.left}) must match folder glyph (${folderGlyph.left})",
                )
                assertTrue(
                    abs(fileGlyph.left.value - 16f) <= 1f,
                    "$style: file glyph left (${fileGlyph.left}) must be 16 dp from column edge",
                )
            }
        }
    }

    @Test
    fun `B-580 TurnChangedFiles draws no forced panel box in Solid`() =
        kitUiTest(360, 240) {
            var ground = Color.Unspecified
            setContent {
                E20Scene(dark = true, style = Style.Solid) {
                    ground = LocalKitColors.current.ground
                    Box(Modifier.width(360.dp).background(ground)) {
                        TurnChangedFiles(
                            files = listOf(ChangedFile("src/app.kt", 3, 1)),
                            onOpenFile = {},
                            onAllFiles = {},
                        )
                    }
                }
            }
            waitForIdle()
            val pixels = onRoot().captureToImage().toPixelMap()
            val sample = pixels[10, 24]
            val diff = maxOf(
                abs(sample.red - ground.red),
                abs(sample.green - ground.green),
                abs(sample.blue - ground.blue),
            )
            assertTrue(diff < 0.01f, "Solid TurnChangedFiles must not force a panel box (Owner #2), got $sample")
        }

    @Test
    fun `B-561 usable title width is equal in Solid and Glass`() {
        fun titleWidth(style: Style): Float {
            var width = 0f
            kitUiTest(360, 780) {
                val scene = ChatScene()
                val state =
                    scene.live().copy(
                        title = "A very long conversation title that fills the usable header width cleanly",
                    )
                setContent {
                    E20Scene(dark = true, style = style) {
                        scene.Screen(state, canGoBack = true)
                    }
                }
                waitForIdle()
                val bounds = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).getBoundsInRoot()
                width = (bounds.right - bounds.left).value
            }
            return width
        }
        val solid = titleWidth(Style.Solid)
        val glass = titleWidth(Style.Glass)
        assertTrue(
            abs(solid - glass) <= 1f,
            "usable title width must match: Solid=$solid dp, Glass=$glass dp",
        )
    }
}
