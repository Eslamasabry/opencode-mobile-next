package io.github.eslamasabry.oc.engine.agents.models

import io.github.eslamasabry.oc.engine.agents.models.ServerPlaceLanguage.Ar
import io.github.eslamasabry.oc.engine.agents.models.ServerPlaceLanguage.En
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerPlaceTitlesTest {
    private val phone = ServerPlaceWords.phoneCardTitle(En)

    private fun place(name: String) = ServerPlace.of(name, phone)

    private fun en(text: ServerPlaceText) = ServerPlaceWords.render(text, En)

    @Test
    fun `B-NAV-035 a named server reads New conversations on name`() {
        assertEquals("New conversations on Workstation", en(ServerPlaceText.NewConversations(place("Workstation"))))
    }

    @Test
    fun `B-NAV-035 this phone does not read on On this phone`() {
        val title = en(ServerPlaceText.NewConversations(place(phone)))
        assertEquals("New conversations on this phone", title)
        assertFalse("On this" in title)
        val ar = ServerPlaceWords.render(ServerPlaceText.NewConversations(ServerPlace.of(ServerPlaceWords.phoneCardTitle(Ar), ServerPlaceWords.phoneCardTitle(Ar))), Ar)
        assertEquals("المحادثات الجديدة على هذا الهاتف", ar)
    }

    @Test
    fun `B-NAV-035 this phone - heading and notes do not double on`() {
        assertEquals(ServerPlace.ThisPhone, place(phone))
        assertEquals("On this phone", en(ServerPlaceText.SectionOn(place(phone))))
        assertEquals("On Workstation", en(ServerPlaceText.SectionOn(place("Workstation"))))
        assertEquals(
            "Saved on this phone. Other apps using this phone's server get this too.",
            en(ServerPlaceText.SavedOn(place(phone))),
        )
        assertTrue("Saved on Work." in en(ServerPlaceText.SavedOn(place("Work"))))
        assertEquals(
            "Reset on this phone to your own choices: Model.",
            en(ServerPlaceText.UseMyDefaultDone(place(phone), "Model")),
        )
    }
}
