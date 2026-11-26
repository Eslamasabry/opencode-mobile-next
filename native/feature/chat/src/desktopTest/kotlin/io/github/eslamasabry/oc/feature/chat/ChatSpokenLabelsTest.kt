package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipActions
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.ContinueChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChip
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChipSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderTopBar
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitMicButton
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import io.github.eslamasabry.oc.ui.kitchat.header.KitChatMoreMenu
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-485: chat header, message box, and + tray controls expose localised spoken labels cleanly. */
@OptIn(ExperimentalTestApi::class)
class ChatSpokenLabelsTest {
    private val allAttach = setOf(AttachKind.Photos, AttachKind.Camera, AttachKind.Clipboard)

    private class StubHeaderActions : ChatHeaderActions {
        override fun openMenu() = Unit
        override fun closeMenu() = Unit
        override fun choose(action: HeaderAction) = Unit
        override fun copyTranscript(words: TranscriptWords, mask: (String) -> String) = Unit
        override fun renameRequested() = Unit
        override fun renameDone(title: String?) = Unit
        override fun closeSheet() = Unit
        override fun continueWith(choice: ContinueChoice) = Unit
        override fun retryContinueWith() = Unit
        override fun confirm() = Unit
        override fun dismissConfirm() = Unit
        override fun dismissFailure() = Unit
        override fun clipDone(id: Long) = Unit
        override fun copyResumeCommand() = Unit
    }

    private class StubApprovalActions : ApprovalChipActions {
        override fun openApprovalMenu() = Unit
        override fun closeApprovalMenu() = Unit
        override fun chooseApproval(choice: ApprovalChoice) = Unit
        override fun confirmEverything() = Unit
        override fun cancelEverything() = Unit
        override fun dismissApprovalFailure() = Unit
    }

    private class StubComposerActions : BasicComposerActions {
        override fun draftChanged(text: String) = Unit
        override suspend fun send(text: String): Boolean = true
        override fun attachFile() = Unit
        override fun attach(kind: AttachKind) = Unit
    }

    private class StubVoice : KitComposerVoice {
        @Composable
        override fun Mic(field: KitVoiceField, modifier: Modifier) {
            KitMicButton(
                label = "Hold to dictate",
                onTap = {},
                onHoldStart = {},
                onHoldEnd = {},
                onArmedChanged = {},
                modifier = modifier,
                prominent = true,
            )
        }

        @Composable
        override fun Panel(field: KitVoiceField, modifier: Modifier) = Unit
    }

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
            localeTag = "en",
        ) {
            KitStandaloneRedaction { KitSheetHost { content() } }
        }
    }

    private fun String.hasPrivateUseGlyph(): Boolean = any { it in '\uE000'..'\uF8FF' }

    private fun activeDescendants(node: SemanticsNode): List<SemanticsNode> =
        if (node.config.isClearingSemantics) emptyList()
        else node.children.flatMap { child -> listOf(child) + activeDescendants(child) }

    private fun spokenLabel(node: SemanticsNode): String {
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        return (desc + text).filter { it.isNotBlank() && !it.hasPrivateUseGlyph() }.joinToString(" ")
    }

    private fun assertCleanClickableNodes(
        mergedClickable: List<SemanticsNode>,
        unmergedClickable: List<SemanticsNode>,
    ) {
        assertTrue(mergedClickable.isNotEmpty(), "expected at least one clickable control")
        for (node in mergedClickable) {
            val label = spokenLabel(node)
            assertTrue(label.isNotBlank(), "clickable control missing spoken label: ${node.config}")
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertFalse(
                text.any { it.hasPrivateUseGlyph() },
                "clickable control '$label' merged private-use icon glyph into Text: $text",
            )
        }
        for (node in unmergedClickable) {
            for (child in activeDescendants(node)) {
                val cfg = child.config
                val text = cfg.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                assertFalse(
                    text.any { it.hasPrivateUseGlyph() },
                    "clickable control leaked icon glyph child into unmerged tree: $text",
                )
                assertFalse(
                    SemanticsActions.OnClick in cfg,
                    "clickable control has nested clickable child: $cfg",
                )
            }
        }
    }

    @Test
    fun `B-485 chat header icon buttons expose localised spoken labels with no inner icon focus`() =
        kitUiTest(360, 800) {
            val headerState =
                ChatHeaderUi(
                    title = "Fix login",
                    projectName = "app",
                    serverName = "This phone",
                    branch = "main",
                    git = true,
                    menu = persistentListOf(HeaderAction.Find, HeaderAction.Rename),
                )
            setContent {
                Theme {
                    Column {
                        ChatHeaderTopBar(
                            slot = ChatHeaderSlot(headerState, StubHeaderActions()),
                            onBack = {},
                            onFind = {},
                            onProject = {},
                            onBranch = {},
                        )
                        KitTopBar(
                            title = "Plain chat",
                            exit = KitTopBarExit.Back,
                            exitLabel = "Back",
                            onExit = {},
                            backBesideTitle = true,
                            actions =
                                listOf(
                                    KitLineAction("Search", {}, testTag = "chat-find-open", icon = KitIcons.search)
                                ),
                            more = {
                                KitChatMoreMenu(
                                    label = "More actions",
                                    expanded = false,
                                    items = emptyList(),
                                    onOpen = {},
                                    onDismissRequest = {},
                                )
                            },
                        )
                    }
                }
            }
            val merged =
                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
            val unmerged =
                onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
            assertCleanClickableNodes(merged, unmerged)
            val descriptions =
                merged.flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertTrue("Back" in descriptions, "expected Back in $descriptions")
            assertTrue("More actions" in descriptions, "expected More actions in $descriptions")
            assertTrue("Search" in descriptions, "expected Search in $descriptions")
        }

    @Test
    fun `B-485 message box and open plus tray controls expose localised spoken labels cleanly`() =
        kitUiTest(360, 800) {
            val approval = ApprovalChipSlot(ApprovalChipUi(offered = true), StubApprovalActions())
            val chrome =
                ComposerChrome(
                    busy = { true },
                    requestWaiting = { false },
                    onSent = {},
                    pendingInsert = { null },
                    insertDone = {},
                    focusRequest = { null },
                    focusDone = {},
                    onStop = {},
                    stopping = { false },
                    rowChips = { ApprovalChip(approval) },
                )
            val ui =
                BasicComposerUi(
                    available = true,
                    sending = false,
                    steers = false,
                    draft = DraftFacts(),
                    targetKey = "p/s",
                    filesAvailable = true,
                    attachKinds = allAttach,
                    attachmentsSupported = true,
                )
            setContent { Theme { BasicComposer(ui, StubComposerActions(), chrome, voice = StubVoice()) } }
            onNodeWithTag("chat-attach-button").performClick()
            waitForIdle()
            val merged =
                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
            val unmerged =
                onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
            assertCleanClickableNodes(merged, unmerged)
            for ((tag, expected) in listOf(
                "chat-attach-button" to "Close",
                "chat-approval-chip" to "Ask first",
                "composer-tool-gallery" to "Photos",
                "composer-tool-camera" to "Camera",
                "composer-tool-attach" to "File",
                "composer-tray-more" to "More",
            )) {
                val node = onNodeWithTag(tag).fetchSemanticsNode()
                assertEquals(
                    listOf(expected),
                    node.config.getOrNull(SemanticsProperties.ContentDescription),
                    "$tag must carry spoken label '$expected' on clickable node",
                )
            }
            val allDescs =
                merged.flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertTrue("Stop" in allDescs, "expected Stop in $allDescs")
            assertTrue("Hold to dictate" in allDescs, "expected Hold to dictate in $allDescs")
        }
}
