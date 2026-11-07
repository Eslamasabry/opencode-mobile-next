package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.domain.platform.AppNotice
import io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction
import io.github.eslamasabry.oc.feature.chat.presentation.shownInChat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** E05-S22: "OpenCode Mobile was closed" shows on the home list only, never above a conversation. */
class ChatAppNoticeTest {
    @Test
    fun `E05-S22 the app-closed notice is not shown inside a chat`() {
        val closed =
            AppNotice(
                "app-exit",
                "OpenCode Mobile was closed at 3:10 PM. Your phone's OpenCode stopped with it.",
                action = AppNoticeAction.KeepRunning,
                actionLabel = "Keep it running",
                dismissLabel = "Dismiss",
                stoppedApp = true,
            )
        assertNull(closed.shownInChat())
    }

    @Test
    fun `E05-S22 other app conditions still show inside a chat`() {
        val pause =
            AppNotice(
                "background-pause",
                "Background connection paused at 3:10 PM to save battery.",
                action = AppNoticeAction.ResumeBackground,
                actionLabel = "Resume",
                dismissLabel = "Dismiss",
            )
        assertEquals(pause, pause.shownInChat())
        assertNull((null as AppNotice?).shownInChat())
    }
}
