package io.github.eslamasabry.oc.ui.kit.lamp

import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.request.KitRequest
import io.github.eslamasabry.oc.ui.kit.request.requestAnswerRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * One surface, one accent: in the Edge light's list only the first row that asks something, top to bottom, carries
 * the accented answer; every later asking row's answer is plain.
 */
class KitLampAccentTest {

    private fun row(key: String, request: KitRequest? = null) =
        KitLampRow(key, "Conversation $key", KitLampRowMark.Quiet, newest = "Created $key.txt", request = request)

    private fun asking(key: String) = KitRequest("req-$key", "Asks to edit: $key.txt")

    @Test
    fun `B-LAMP-K2-080 only a primary request's allowing answer is accented`() {
        assertEquals(KitButtonRole.Primary, requestAnswerRole(true))
        assertEquals(KitButtonRole.Secondary, requestAnswerRole(false))
    }

    @Test
    fun `B-LAMP-K2-081 the first asking row is the accented one`() {
        val sections =
            listOf(
                KitLampSection(KitLampGroup.Recent, "Recent", listOf(row("a"), row("b", asking("b")))),
                KitLampSection(KitLampGroup.NeedsYou, "Needs you", listOf(row("c", asking("c")))),
            )
        assertEquals("b", lampAccentKey(sections))
    }

    @Test
    fun `B-LAMP-K2-082 an asking row in a later section is found when no earlier row asks`() {
        val sections =
            listOf(
                KitLampSection(KitLampGroup.Recent, "Recent", listOf(row("a"), row("d"))),
                KitLampSection(KitLampGroup.NeedsYou, "Needs you", listOf(row("c", asking("c")))),
            )
        assertEquals("c", lampAccentKey(sections))
    }

    @Test
    fun `B-LAMP-K2-083 no row asks, so no row is accented`() {
        val sections = listOf(KitLampSection(KitLampGroup.Recent, "Recent", listOf(row("a"), row("d"))))
        assertNull(lampAccentKey(sections))
    }
}
