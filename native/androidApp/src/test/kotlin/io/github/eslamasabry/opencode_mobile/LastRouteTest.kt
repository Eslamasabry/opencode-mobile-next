package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import android.os.Bundle
import android.os.Parcel
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P-110 / gap 2: after Android kills the app (the camera in front), it reopens on the pages it was on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LastRouteTest {
    private val chat = ChatRoute(ConversationRef(ProfileId("p"), SessionId("s")))
    private val home = ChatsRoute.Home

    @Test
    fun `P-110 a shell that came back on home gets the whole stack back, not only the top`() {
        val saved = listOf(home, ChatsRoute.All(), chat)
        assertEquals(saved, LastRoute.reopen(saved, current = home, home = home))
        // Before the shell published its top.
        assertEquals(saved, LastRoute.reopen(saved, current = null, home = home))
    }

    @Test
    fun `P-110 a stack that restored itself, or a page a link opened, is left alone`() {
        val saved = listOf(home, chat)
        assertEquals(emptyList(), LastRoute.reopen(saved, current = chat, home = home))
        assertEquals(emptyList(), LastRoute.reopen(saved, current = ChatsRoute.Attention, home = home))
        assertEquals(emptyList(), LastRoute.reopen(emptyList(), current = home, home = home))
    }

    @Test
    fun `P-110 home alone is not a stack to reopen, and a chat's one-shot acts never run twice`() {
        assertEquals(emptyList(), LastRoute.reopen(listOf(home), current = home, home = home))
        val once = chat.copy(requestId = "r1", thenAction = "Share")
        assertEquals(listOf(home, chat), LastRoute.reopen(listOf(home, once), current = home, home = home))
    }

    @Test
    fun `gap 2 the stack survives the saved-state bundle in order, with the chat's one-shot acts dropped`() {
        val once = chat.copy(requestId = "r1", thenAction = "Share")
        val out = Bundle()
        LastRoute.save(listOf(home, ChatsRoute.All("running"), once), out)
        // The bundle goes through a parcel the way Android's saved state does across a process death.
        val parcel = Parcel.obtain()
        out.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val back = Bundle.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        assertEquals(listOf(home, ChatsRoute.All("running"), chat), LastRoute.read(back))
    }

    @Test
    fun `gap 2 a build that no longer knows a saved page skips it and keeps the rest`() {
        val out = Bundle()
        LastRoute.save(listOf(home, chat), out)
        out.getBundle("oc.shell.stack")!!.putBundle("1", Bundle().apply { putString("type", "gone") })
        assertEquals(listOf(home), LastRoute.read(out))
        assertEquals(emptyList(), LastRoute.read(null))
    }
}
