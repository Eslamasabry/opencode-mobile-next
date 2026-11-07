package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.domain.platform.AppNotice
import io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction
import io.github.eslamasabry.oc.feature.chats.ui.homeAppNoticeStatus
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** E05-S22: the home's "OpenCode Mobile was closed" line carries the restart glyph, not an empty ring. */
class HomeAppNoticeTest {
    private val closed =
        AppNotice(
            "app-exit",
            "OpenCode Mobile was closed at 3:10 PM. Your phone's OpenCode stopped with it.",
            action = AppNoticeAction.KeepRunning,
            actionLabel = "Keep it running",
            dismissLabel = "Dismiss",
            stoppedApp = true,
        )

    @Test
    fun `E05-S22 the app-closed line uses the kit restart glyph`() {
        val acted = mutableListOf<AppNoticeAction>()
        val dismissed = mutableListOf<String>()
        val status = homeAppNoticeStatus(closed, { acted += it }, { dismissed += it })
        assertEquals(KitStatusKind.AppStopped, status.kind)
        assertSame(KitIcons.restart, status.icon)
        assertEquals("Keep it running", status.action?.label)
        status.action?.let { it.onClick() }
        status.onDismiss?.invoke()
        assertEquals(listOf(AppNoticeAction.KeepRunning), acted)
        assertEquals(listOf("app-exit"), dismissed)
    }

    @Test
    fun `E05-S22 other conditions keep their tone glyph`() {
        val pause = AppNotice("thermal", "Paused while the phone cools down.", dismissLabel = "Dismiss")
        val status = homeAppNoticeStatus(pause, {}, {})
        assertEquals(KitStatusKind.Info, status.kind)
        assertNull(status.icon)
    }
}
