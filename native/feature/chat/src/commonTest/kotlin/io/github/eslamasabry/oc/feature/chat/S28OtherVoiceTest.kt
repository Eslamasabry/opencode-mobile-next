package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * S28 story 7 (Flutter `chat_message_actions.dart:105-111`, `read_aloud.dart` `chooseVoice`): "Read aloud with another
 * voice" hands the same reply to the reader, asking it to offer the voices first; plain Read aloud does not.
 */
class S28OtherVoiceTest {
    private val world = FakeChatWorld()
    private val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)

    @Test
    fun `B-MEDIA-S28-01 another voice reads the same reply and asks for the voice first`() {
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        presenter.openMessageMenu("footer:u1")
        val target = assertNotNull(presenter.current().menu).target
        presenter.messageAction(target, MessageAction.ReadAloudOtherVoice)
        val asked = assertNotNull(presenter.current().readAloud)
        assertTrue(asked.chooseVoice)
        assertEquals(target.rowKey, asked.rowKey)
        assertTrue(asked.text.isNotBlank())
        presenter.readAloudDone(asked.id)

        presenter.openMessageMenu("footer:u1")
        presenter.messageAction(target, MessageAction.ReadAloud)
        assertFalse(assertNotNull(presenter.current().readAloud).chooseVoice)
        assertTrue(world.calls.isEmpty(), "reading touches no server")
    }
}
