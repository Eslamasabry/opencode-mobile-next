package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.ui.TranscriptFindBar
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FindBarTest {
    @Test fun emptySearchHidesNavigationAndCloseLeavesFind() = runComposeUiTest {
        val calls = mutableListOf<String?>()
        val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        val actions = object : ChatActions by presenter {
            override fun find(query: String?) { calls += query }
        }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off)) {
                TranscriptFindBar(FindUi("", 0, -1, null, -1, false), actions)
            }
        }
        onNodeWithTag("transcript-find-next").assertDoesNotExist()
        onNodeWithContentDescription("Close search").performClick()
        assertEquals(listOf<String?>(null), calls)
    }

    @Test fun noMatchesDisablesBothDirections() = runComposeUiTest {
        val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off)) {
                TranscriptFindBar(FindUi("absent", 0, -1, null, -1, false), presenter)
            }
        }
        onNodeWithTag("transcript-find-next").assertIsNotEnabled()
        onNodeWithTag("transcript-find-previous").assertIsNotEnabled()
        onNodeWithText("No matches").assertIsDisplayed()
    }
}
