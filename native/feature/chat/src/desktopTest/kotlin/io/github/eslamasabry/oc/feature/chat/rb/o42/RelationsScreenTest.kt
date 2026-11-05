package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chat.relations.presentation.*
import io.github.eslamasabry.oc.feature.chat.relations.ui.SessionRelationsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RelationsScreenTest {
    private class Actions : SessionRelationsActions {
        var refreshes = 0
        var stops = 0
        var cancelled = 0
        override fun back() {}
        override fun refresh() { refreshes++ }
        override fun open(id: SessionId) {}
        override fun showMenu(id: SessionId) {}
        override fun dismissMenu() {}
        override fun togglePin() {}
        override fun showResume() {}
        override fun dismissResume() {}
        override fun copyResume() {}
        override fun clipDone(id: Long) {}
        override fun requestStop() {}
        override fun cancelStop() { cancelled++ }
        override fun confirmStop() { stops++ }
    }

    @Test fun `C33 needs you says the status once and refresh keeps the existing action`() = runComposeUiTest {
        val actions = Actions()
        val parent = RelationRow(SessionId("parent"), "Main conversation", true, true, RelationStatus.Idle, null, false, null)
        val child = RelationRow(SessionId("child"), "Review changes", false, false, RelationStatus.NeedsYou, null, false, null)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off)) {
                SessionRelationsScreen(SessionRelationsUi(loading = false, parent = parent, children = persistentListOf(child)), actions)
            }
        }
        onNodeWithText("Needs you · Open to answer", substring = true).assertDoesNotExist()
        onNodeWithText("Needs you", useUnmergedTree = true).assertExists()
        onNodeWithText(" · open to answer", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Refresh subagent sessions").performClick()
        assertEquals(1, actions.refreshes)
    }
    @Test fun `B-AGENT-221 stopping a subagent asks first and leaves Keep running available`() = runComposeUiTest {
        val actions = Actions()
        val parent = RelationRow(SessionId("parent"), "Main conversation", true, true, RelationStatus.Idle, null, false, null)
        val child = RelationRow(SessionId("child"), "Review changes", false, false, RelationStatus.Working, null, false, null)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                SessionRelationsScreen(SessionRelationsUi(loading = false, parent = parent, children = persistentListOf(child), stopping = child), actions)
            }
        }
        onNodeWithText("Stop Review changes?").assertExists()
        onNodeWithText("Stop subagent").assertIsEnabled()
        assertEquals(0, actions.stops)
        onNodeWithText("Keep running").performClick()
        assertEquals(1, actions.cancelled)
        assertEquals(0, actions.stops)
    }

    @Test fun `C33 menu selection retains the reviewed child until Pin reads it`() = runComposeUiTest {
        val parent = RelationRow(SessionId("parent"), "Main conversation", true, true, RelationStatus.Idle, null, false, null)
        val child = RelationRow(SessionId("child"), "Review changes", false, false, RelationStatus.Working, null, false, null)
        var state by mutableStateOf(SessionRelationsUi(loading = false, parent = parent, children = persistentListOf(child), canPin = true))
        var pinned: SessionId? = null
        var dismissed = 0
        val actions = object : SessionRelationsActions by Actions() {
            override fun showMenu(id: SessionId) { state = state.copy(menu = child.takeIf { it.id == id }) }
            override fun dismissMenu() { dismissed++; state = state.copy(menu = null) }
            override fun togglePin() { pinned = state.menu?.id; state = state.copy(menu = null) }
        }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                SessionRelationsScreen(state, actions)
            }
        }
        onNodeWithContentDescription("Actions for Review changes").performClick()
        onNodeWithText("Pin Review changes").performClick()
        assertEquals(child.id, pinned)
        assertEquals(0, dismissed)
        onNodeWithTag("kit-menu").assertDoesNotExist()
    }

}
