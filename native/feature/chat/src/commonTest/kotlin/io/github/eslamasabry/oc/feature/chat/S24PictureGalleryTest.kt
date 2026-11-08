package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ConversationPictures
import io.github.eslamasabry.oc.feature.chat.presentation.GalleryPicture
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S24-CHAT story 3 (Flutter `chat_gallery.dart`): the viewer pages through every server picture
 * the conversation's replies show, oldest first, and opens on the one tapped.
 */
class S24PictureGalleryTest {
    private val rows =
        stateOf(
            prompt("u1", "draw it"),
            block("u1", "a1", "p1", 0, "First ![plot](/root/projects/app/plot.png) and ![](https://example.com/web.png)"),
            prompt("u2", "again"),
            block("u2", "a2", "p1", 0, "Then ![](/root/projects/app/shot.jpg) and ![plot](/root/projects/app/plot.png)"),
        ).rows

    @Test
    fun `B-CHAT-S24-30 the pictures are the server ones, oldest first, each once`() {
        assertEquals(
            listOf(
                GalleryPicture("/root/projects/app/plot.png", "plot"),
                GalleryPicture("/root/projects/app/shot.jpg", "shot.jpg"),
            ),
            ConversationPictures.of(rows),
        )
    }

    @Test
    fun `B-CHAT-S24-31 the viewer opens on the tapped picture, and a path no reply named joins the end`() {
        assertEquals(1, ConversationPictures.around(rows, "/root/projects/app/shot.jpg", "shot.jpg").second)
        val (pages, at) = ConversationPictures.around(rows, "/tmp/out.png", "out.png")
        assertEquals(3, pages.size)
        assertEquals(2, at)
    }

    @Test
    fun `B-CHAT-S24-32 opening a picture gives the viewer the whole conversation's pictures`() {
        val world = FakeChatWorld()
        world.transcript.value = stateOf(*rows.reversed().toTypedArray())
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        p.openPicture("/root/projects/app/shot.jpg", "shot.jpg")
        val picture = p.current().picture!!
        assertEquals(listOf("plot", "shot.jpg"), picture.gallery.map { it.name })
        assertEquals(1, picture.index)
    }
}
