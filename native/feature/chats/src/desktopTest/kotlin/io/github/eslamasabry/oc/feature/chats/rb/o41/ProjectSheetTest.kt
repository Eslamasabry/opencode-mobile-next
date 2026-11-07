package io.github.eslamasabry.oc.feature.chats.rb.o41

import kotlinx.collections.immutable.persistentMapOf
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.feature.chats.ui.projectKindGlyph
import io.github.eslamasabry.oc.feature.chats.presentation.HomeProjectKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeProjectFacts
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.engine.agents.feed.ProjectSummary
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeSheet
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsHomeSheets
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectSheetTest {
    @Test fun `B03 selected choice puts its check after the title and accent action uses the theme ink`() = runComposeUiTest {
        var accent = Color.Unspecified
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                val colors = LocalKitColors.current
                SideEffect { accent = colors.accent }
                Column {
                    KitRow("Open a project…", titleAccent = true, selected = true, trailing = KitRowTrailing.Check, onClick = {})
                }
            }
        }
        val title = onNodeWithTag("kit-row-title", useUnmergedTree = true)
        val check = onNodeWithTag("kit-row-check", useUnmergedTree = true)
        assertTrue(check.fetchSemanticsNode().boundsInRoot.left > title.fetchSemanticsNode().boundsInRoot.left)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(accent, layouts.single().layoutInput.style.color)
    }

    @Test fun `B03 choosing a project dispatches its real directory`() = runComposeUiTest {
        val actions = RecordingChatsActions()
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitSheetHost {
                    ChatsHomeSheets(ChatsUi(sheet = ChatsHomeSheet.Projects,
                        projectChoices = persistentListOf(ProjectSummary("/work/alpha", "alpha", 2, 1, 0))), actions)
                }
            }
        }
        onNodeWithText("alpha").performClick()
        assertEquals(listOf("project /work/alpha"), actions.calls)
    }

    @Test fun `G4 a git project shows the Git pill before its check, a plain one neither`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitSheetHost {
                    ChatsHomeSheets(
                        ChatsUi(
                            sheet = ChatsHomeSheet.Projects,
                            projectChoices = persistentListOf(
                                ProjectSummary("/work/alpha", "alpha", 2, 0, 0),
                                ProjectSummary("/work/beta", "beta", 2, 1, 0),
                            ),
                            filters = ChatsFilters(projectDirectory = "/work/alpha"),
                            projectFacts = persistentMapOf("/work/alpha" to HomeProjectFacts(HomeProjectKind.Dart, git = true)),
                        ),
                        RecordingChatsActions(),
                    )
                }
            }
        }
        // F: chats_project_sheet.dart:96-107: one pill (alpha is git, beta is not), and the chosen row keeps its check.
        onAllNodesWithText("Git").assertCountEquals(1)
        onAllNodesWithTag("kit-row-check", useUnmergedTree = true).assertCountEquals(1)
        val pill = onNodeWithText("Git", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val check = onNodeWithTag("kit-row-check", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(check.left > pill.right, "the check closes the row after the pill")
    }

    @Test fun `G4 each project kind has Flutter's glyph and an unknown kind the open folder`() {
        // F: lib/ui/widgets/phone_project_kind.dart:10-22
        assertEquals(KitIcons.layers, projectKindGlyph(HomeProjectKind.Dart))
        assertEquals(KitIcons.packageBox, projectKindGlyph(HomeProjectKind.Node))
        assertEquals(KitIcons.dataObject, projectKindGlyph(HomeProjectKind.Python))
        assertEquals(KitIcons.folderOpen, projectKindGlyph(null))
        assertEquals(HomeProjectKind.entries.size, HomeProjectKind.entries.map { projectKindGlyph(it) }.distinct().size)
    }
}
