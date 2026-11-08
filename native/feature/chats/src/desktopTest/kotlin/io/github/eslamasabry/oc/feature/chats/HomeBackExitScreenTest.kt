package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.collections.immutable.persistentListOf

/**
 * E05-S21 on the real Conversations screen: Back goes through a navigation-event dispatcher as Android's back input
 * (key, button or predictive swipe) does. "Fell through" is the system taking Back, which on the root closes the app.
 */
@OptIn(ExperimentalTestApi::class)
class HomeBackExitScreenTest {
    private val back =
        object : NavigationEventInput() {
            fun press() {
                dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f))
                dispatchOnBackCompleted()
            }
        }
    private var fellThrough = 0
    private val dispatcher =
        NavigationEventDispatcher(onBackCompletedFallback = { fellThrough++ }).also { it.addInput(back) }
    private val owner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }

    private val state =
        ChatsUi(
            phase = ChatsPhase.List,
            recent =
                persistentListOf(
                    ConversationRowUi(
                        "login",
                        ConversationRef(ProfileId("p"), SessionId("s")),
                        RowTitle.Text("Fix login"),
                        "alpha",
                        RowStatus.None,
                    )
                ),
        )

    @Composable
    private fun Home(root: Boolean, overlay: @Composable () -> Unit = {}) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
            ChatEnvironment {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                ) {
                    KitSheetHost {
                        ChatsScreen(state, RecordingChatsActions(), root = root)
                        overlay()
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.hintShows(): Boolean =
        onAllNodesWithText(HINT).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `E05-S21 first back on the list shows the hint and a second inside 2 s leaves`() = runComposeUiTest {
        setContent { Home(root = true) }
        waitForIdle()
        mainClock.autoAdvance = false
        runOnIdle { back.press() }
        mainClock.advanceTimeByFrame()
        assertEquals(0, fellThrough, "one Back on the conversation list must not close the app")
        assert(hintShows()) { "the status line says \"$HINT\"" }
        mainClock.advanceTimeBy(1_000)
        runOnIdle { back.press() }
        assertEquals(1, fellThrough, "a second Back inside the window leaves the app")
    }

    @Test
    fun `E05-S21 after the window the hint folds away and back starts over`() = runComposeUiTest {
        setContent { Home(root = true) }
        waitForIdle()
        mainClock.autoAdvance = false
        runOnIdle { back.press() }
        mainClock.advanceTimeByFrame()
        assert(hintShows())
        mainClock.advanceTimeBy(2_200)
        assertFalse(hintShows(), "the hint folds away after 2 s")
        runOnIdle { back.press() }
        mainClock.advanceTimeByFrame()
        assertEquals(0, fellThrough, "after the window the next Back is a first Back again")
        assert(hintShows())
    }

    @Test
    fun `E05-S21 a sheet open on the list closes before the guard`() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent {
            Home(root = true) { KitSheet(open, { open = false }, "Filter") { KitText("Projects") } }
        }
        waitForIdle()
        runOnIdle { back.press() }
        waitForIdle()
        assertFalse(open, "Back closes the sheet first")
        assertFalse(hintShows(), "closing the sheet is not the first of two exit Backs")
        assertEquals(0, fellThrough)
    }

    @Test
    fun `E05-S21 the guard holds Back only on the bare list`() = runComposeUiTest {
        setContent { Home(root = false) }
        waitForIdle()
        runOnIdle { back.press() }
        waitForIdle()
        assertEquals(1, fellThrough, "covered by a page, Back belongs to the page stack, not the exit guard")
        assertFalse(hintShows())
    }

    private companion object {
        const val HINT = "Press back again to exit"
    }
}
