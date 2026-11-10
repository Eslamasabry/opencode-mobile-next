package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** Jank pass on the home: pieces that change after the first frame must change in place, not be rebuilt. */
@OptIn(ExperimentalTestApi::class)
class HomeJankScreenTest {
    private val row =
        ConversationRowUi(
            "login",
            ConversationRef(ProfileId("p"), SessionId("s")),
            RowTitle.Text("Fix login"),
            "alpha",
            RowStatus.None,
        )
    private val base = ChatsUi(phase = ChatsPhase.List, recent = persistentListOf(row))

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: () -> ChatsUi) =
        setContent {
            ChatEnvironment {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                ) {
                    KitSheetHost { ChatsScreen(state(), RecordingChatsActions()) }
                }
            }
        }

    @Test
    fun `JANK-HOME-4 a row that becomes archivable is the same row, not a rebuilt one`() = runComposeUiTest {
        var state by mutableStateOf(base)
        show { state }
        waitForIdle()
        val before = onNodeWithText("Fix login").fetchSemanticsNode().id
        state = base.copy(archivable = persistentSetOf("login"))
        waitForIdle()
        assertEquals(before, onNodeWithText("Fix login").fetchSemanticsNode().id, "the row keeps its node when it gains Archive")
    }

    @Test
    fun `JANK-HOME-5 the filter chips stay put when the connection arrives beside them`() = runComposeUiTest {
        var state by mutableStateOf(base.copy(home = ChatsHomeExtraState(known = true)))
        show { state }
        waitForIdle()
        val before = onNodeWithText("Running").fetchSemanticsNode().id
        val source = ChatsListSourceUi(ProfileId("p"), "Office PC", shown = true, main = true, onThisPhone = false, unreachable = false)
        state = base.copy(home = ChatsHomeExtraState(sources = persistentListOf(source), known = true))
        waitForIdle()
        onNodeWithText("Office PC").assertExists()
        assertEquals(before, onNodeWithText("Running").fetchSemanticsNode().id, "the chips are not rebuilt when the pill arrives")
    }
}
