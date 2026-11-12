package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.feature.chat.header.presentation.PhoneAddressFailure
import io.github.eslamasabry.oc.feature.chat.header.presentation.PhoneAddressOffer
import io.github.eslamasabry.oc.feature.chat.header.presentation.PhoneAddressResult
import io.github.eslamasabry.oc.feature.chat.header.presentation.PhoneSheetRules
import io.github.eslamasabry.oc.feature.chat.header.presentation.PhoneSheetShow
import kotlin.test.Test
import kotlin.test.assertEquals

/** K1 (F: session_handoff_sheets.dart `_ContinueOnPhoneBody`): which code and link the sheet draws. */
class PhoneSheetRulesTest {
    private val local = "opencode-mobile://session?server=p1&session=s1"
    private var builds = 0
    private val offer =
        PhoneAddressOffer("a.b.ts.net:443") { builds++; PhoneAddressResult.Link("opencode-mobile://session/v2?x") }

    @Test
    fun `no link means the unavailable notice`() {
        assertEquals(PhoneSheetShow.None, PhoneSheetRules.show(null, offer, include = true))
    }

    @Test
    fun `without an offer the local link shows and nothing is built`() {
        assertEquals(PhoneSheetShow.Local(local), PhoneSheetRules.show(local, null, include = true))
    }

    @Test
    fun `the switch off shows the local link and builds nothing`() {
        assertEquals(PhoneSheetShow.Local(local), PhoneSheetRules.show(local, offer, include = false))
        assertEquals(0, builds)
    }

    @Test
    fun `the switch on shows the portable link, built again each time`() {
        assertEquals(PhoneSheetShow.Portable("opencode-mobile://session/v2?x"), PhoneSheetRules.show(local, offer, true))
        PhoneSheetRules.show(local, offer, true)
        assertEquals(2, builds)
    }

    @Test
    fun `an address that cannot go in keeps the switch inert`() {
        val unsupported = PhoneAddressOffer(null) { error("never built") }
        assertEquals(PhoneSheetShow.Local(local), PhoneSheetRules.show(local, unsupported, include = true))
    }

    @Test
    fun `a failed build shows why and no code, not the local link`() {
        val failing = PhoneAddressOffer("a.b.ts.net:443") { PhoneAddressResult.Failed(PhoneAddressFailure.Credentials) }
        assertEquals(PhoneSheetShow.Failure(PhoneAddressFailure.Credentials), PhoneSheetRules.show(local, failing, true))
    }
}
