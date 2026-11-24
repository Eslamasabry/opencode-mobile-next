package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.ReplacedModel
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalTestApi::class)
class ModelGoneWayOnTest {
    private class ModelWorld(choices: List<ComposerModel>) : FakeChatWorld() {
        override val models: StateFlow<ComposerModels> = MutableStateFlow(ComposerModels(choices = choices))

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Outcome<Unit> {
            calls += "selectModel ${choice?.providerId}/${choice?.modelId}"
            return Outcome.Ok(Unit)
        }
    }

    @Composable
    private fun Root(locale: String, scale: Float, motion: MotionLevel, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KitTheme(
                appearance = KitAppearance(motion = motion),
                platform = DesktopKitPlatform(),
                localeTag = locale,
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
                }
            }
        }
    }

    private fun collectTags(node: SemanticsNode, out: MutableList<String>) {
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { out += it }
        for (child in node.children) collectTags(child, out)
    }

    private fun assertStable(base: Rect, now: Rect, frame: Int, tag: String) {
        assertTrue(abs(now.left - base.left) <= 0.5f, "$tag left drifted on frame $frame: $now vs $base")
        assertTrue(abs(now.top - base.top) <= 0.5f, "$tag top drifted on frame $frame: $now vs $base")
        assertTrue(abs(now.right - base.right) <= 0.5f, "$tag right drifted on frame $frame: $now vs $base")
        assertTrue(abs(now.bottom - base.bottom) <= 0.5f, "$tag bottom drifted on frame $frame: $now vs $base")
    }

    private fun twoTurnWorld(m2: ComposerModel): ModelWorld =
        ModelWorld(listOf(m2)).apply {
            transcript.value =
                stateOf(
                    prompt("u1", "first"),
                    block("u1", "a1", "p1", 0, "ok"),
                    FooterRow(
                        "footer:u1",
                        "u1",
                        failed = false,
                        createdMs = 100L,
                        providerId = "prov",
                        modelId = "m2",
                    ),
                    prompt("u2", "go"),
                    FooterRow(
                        "footer:u2",
                        "u2",
                        failed = true,
                        reason = "Model not found",
                        errorKind = MessageErrorKind.ModelNotFound,
                        createdMs = 200L,
                        providerId = "prov",
                        modelId = "gone",
                    ),
                )
        }

    private fun androidx.compose.ui.test.ComposeUiTest.assertSixtyFramesStable() {
        mainClock.advanceTimeByFrame()
        val baseUse = onNodeWithTag("error-action-use-model").fetchSemanticsNode().boundsInRoot
        val baseChoose = onNodeWithTag("error-action-choose-model").fetchSemanticsNode().boundsInRoot
        for (frame in 1..60) {
            mainClock.advanceTimeByFrame()
            val use = onNodeWithTag("error-action-use-model").fetchSemanticsNode().boundsInRoot
            val choose = onNodeWithTag("error-action-choose-model").fetchSemanticsNode().boundsInRoot
            assertStable(baseUse, use, frame, "use")
            assertStable(baseChoose, choose, frame, "choose")
            assertTrue(use.left >= 0f && use.right <= 360f, "use outside 360dp on frame $frame: $use")
            assertTrue(
                choose.left >= 0f && choose.right <= 360f,
                "choose outside 360dp on frame $frame: $choose",
            )
            val tags = mutableListOf<String>()
            collectTags(onRoot().fetchSemanticsNode(), tags)
            val useIdx = tags.indexOf("error-action-use-model")
            val chooseIdx = tags.indexOf("error-action-choose-model")
            assertTrue(useIdx >= 0 && chooseIdx > useIdx, "Use must precede Choose: $tags")
        }
    }

    private fun walk(locale: String, scale: Float, motion: MotionLevel) {
        val beforeLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(locale))
            kitUiTest(360, 800) {
                val m2 = ComposerModel("prov", "m2", "M2", supportsAttachments = false, enabled = true)
                val world = twoTurnWorld(m2)
                val scene = ChatScene(world = world)
                val state = scene.live().copy(status = null, rows = world.transcript.value.rows)

                mainClock.autoAdvance = false
                setContent {
                    Root(locale, scale, motion) {
                        ChatScreen(
                            state,
                            scene.actions,
                            scene.host,
                            header = ChatHeaderSlot(ChatHeaderUi(title = state.title), scene.headerActions),
                            facts = TurnFactsUi.Empty,
                            modelChoices = listOf(m2),
                            canGoBack = false,
                        )
                    }
                }
                assertSixtyFramesStable()

                val expectedLabel = if (locale == "en") "Use M2 and resend" else "استخدام M2 وإعادة الإرسال"
                onNodeWithText(expectedLabel).assertExists()

                mainClock.autoAdvance = true
                onNodeWithTag("error-action-use-model").performClick()
                waitForIdle()
                assertEquals(listOf("selectModel prov/m2", "sendAgain go"), world.calls)
            }
        } finally {
            Locale.setDefault(beforeLocale)
        }
    }

    @Test
    fun `B-523-008 model-gone card offers Use M2 and resend before Choose across 60 frames and configurations`() {
        for (locale in listOf("en", "ar")) {
            for (scale in listOf(1.0f, 1.3f)) {
                for (motion in listOf(MotionLevel.Full, MotionLevel.Off)) {
                    walk(locale, scale, motion)
                }
            }
        }
    }

    @Test
    fun `B-523-009 with no answered turn and nothing listed for the provider, Choose only is shown`() =
        kitUiTest(360, 800) {
            val other = ComposerModel("other", "o1", "Other 1", supportsAttachments = false, enabled = true)
            val world = ModelWorld(listOf(other))
            world.transcript.value =
                stateOf(
                    prompt("u1", "go"),
                    FooterRow(
                        "footer:u1",
                        "u1",
                        failed = true,
                        reason = "Model not found",
                        errorKind = MessageErrorKind.ModelNotFound,
                        createdMs = 200L,
                        providerId = "prov",
                        modelId = "gone",
                    ),
                )
            val scene = ChatScene(world = world)
            val state = scene.live().copy(status = null, rows = world.transcript.value.rows)
            setContent {
                Root("en", 1.0f, MotionLevel.Off) {
                    ChatScreen(
                        state,
                        scene.actions,
                        scene.host,
                        header = ChatHeaderSlot(ChatHeaderUi(title = state.title), scene.headerActions),
                        facts = TurnFactsUi.Empty,
                        modelChoices = listOf(other),
                        canGoBack = false,
                    )
                }
            }
            waitForIdle()
            onAllNodesWithTag("error-action-use-model").assertCountEquals(0)
            onAllNodesWithTag("error-action-choose-model").assertCountEquals(1)
        }

    private fun androidx.compose.ui.test.ComposeUiTest.assertWrapsWithoutCut(expectedText: String) {
        val nodes =
            onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
        var matched: Pair<SemanticsNode, TextLayoutResult>? = null
        for (node in nodes) {
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val hit = results.firstOrNull { it.layoutInput.text.text == expectedText }
            if (hit != null) {
                matched = node to hit
                break
            }
        }
        val (node, layout) = assertNotNull(matched, "No laid-out text node matched: $expectedText")
        assertFalse(layout.hasVisualOverflow, "Text was visually cut: $expectedText")
        assertFalse(layout.didOverflowWidth, "Text overflowed width: $expectedText")
        assertTrue(layout.lineCount >= 2, "Expected 40-char name line to wrap, got ${layout.lineCount} lines")
        val bounds = node.boundsInRoot
        assertTrue(bounds.left >= 0f && bounds.right <= 360f, "Line outside 360dp: $bounds")
    }

    @Test
    fun `B-523-012 error card names gone model and provider when known and wraps 40-char name at 360dp text 1_3`() {
        val longModelId = "alpha-beta-gamma-delta-epsilon-zeta-40ch"
        val longModelDisplay = "Alpha Beta Gamma Delta Epsilon Zeta 40ch"
        assertEquals(40, longModelDisplay.length)
        val expectedNamed = "$longModelDisplay is not available on Anthropic any more."

        kitUiTest(360, 800) {
            val m2 =
                ComposerModel(
                    "anthropic",
                    "sonnet-5",
                    "Sonnet 5",
                    supportsAttachments = false,
                    providerName = "Anthropic",
                    enabled = true,
                )
            val world = ModelWorld(listOf(m2))
            world.transcript.value =
                stateOf(
                    prompt("u1", "go"),
                    FooterRow(
                        "footer:u1",
                        "u1",
                        failed = true,
                        reason = "Model not found",
                        errorKind = MessageErrorKind.ModelNotFound,
                        createdMs = 200L,
                        providerId = "anthropic",
                        modelId = longModelId,
                    ),
                )
            val scene = ChatScene(world = world)
            val state = scene.live().copy(status = null, rows = world.transcript.value.rows)
            setContent {
                Root("en", 1.3f, MotionLevel.Off) {
                    ChatScreen(
                        state,
                        scene.actions,
                        scene.host,
                        header = ChatHeaderSlot(ChatHeaderUi(title = state.title), scene.headerActions),
                        facts = TurnFactsUi.Empty,
                        modelChoices = listOf(m2),
                        canGoBack = false,
                    )
                }
            }
            waitForIdle()
            onNodeWithText(expectedNamed).assertExists()
            onAllNodesWithText("The server doesn't have this model.").assertCountEquals(0)
            assertWrapsWithoutCut(expectedNamed)
        }

        kitUiTest(360, 800) {
            val world = ModelWorld(emptyList())
            world.transcript.value =
                stateOf(
                    prompt("u1", "go"),
                    FooterRow(
                        "footer:u1",
                        "u1",
                        failed = true,
                        reason = "Model not found",
                        errorKind = MessageErrorKind.ModelNotFound,
                        createdMs = 200L,
                    ),
                )
            val scene = ChatScene(world = world)
            val state = scene.live().copy(status = null, rows = world.transcript.value.rows)
            setContent {
                Root("en", 1.3f, MotionLevel.Off) {
                    ChatScreen(
                        state,
                        scene.actions,
                        scene.host,
                        header = ChatHeaderSlot(ChatHeaderUi(title = state.title), scene.headerActions),
                        facts = TurnFactsUi.Empty,
                        modelChoices = emptyList(),
                        canGoBack = false,
                    )
                }
            }
            waitForIdle()
            onNodeWithText("The server doesn't have this model.").assertExists()
        }
    }

    @Test
    fun `B-523-013 stale choice replacement note shows in status slot and wraps 40-char name at 360dp text 1_3`() {
        val oldName = "Alpha Beta Gamma Delta Epsilon Zeta 40ch"
        val newName = "Sigma Tau Upsilon Phi Chi Psi Omega 40ch"
        assertEquals(40, oldName.length)
        assertEquals(40, newName.length)
        val expectedNote = "$oldName is gone. Using $newName."

        kitUiTest(360, 800) {
            val replacement =
                ComposerModel("prov", "m2", newName, supportsAttachments = false, providerName = "Prov", enabled = true)
            val world =
                object : FakeChatWorld() {
                    override val models: StateFlow<ComposerModels> =
                        MutableStateFlow(
                            ComposerModels(
                                choices = listOf(replacement),
                                selected = replacement,
                                replaced = ReplacedModel(oldName, newName),
                            )
                        )
                }
            world.title.value = "Project chat"
            val scene = ChatScene(world = world)
            val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = presenter.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root("en", 1.3f, MotionLevel.Off) {
                    ChatScreen(
                        state,
                        presenter,
                        scene.host,
                        header = ChatHeaderSlot(ChatHeaderUi(title = state.title), scene.headerActions),
                        facts = TurnFactsUi.Empty,
                        modelChoices = listOf(replacement),
                        canGoBack = false,
                    )
                }
            }
            waitForIdle()
            onNodeWithText(expectedNote).assertExists()
            assertWrapsWithoutCut(expectedNote)
            onNodeWithTag("kit-status-dismiss").performClick()
            waitForIdle()
            onAllNodesWithText(expectedNote).assertCountEquals(0)
        }
    }
}
