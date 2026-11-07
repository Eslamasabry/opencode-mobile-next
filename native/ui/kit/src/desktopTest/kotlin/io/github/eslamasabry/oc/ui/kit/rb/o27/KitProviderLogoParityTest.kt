package io.github.eslamasabry.oc.ui.kit.rb.o27

import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogoRules
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** part-kit_vector_logo: the load rules of `lib/ui/kit/kit_vector_logo.dart` at 8302de537. */
class KitProviderLogoParityTest {
    private val mark = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0h24v24z"/></svg>"""

    @Test
    fun `a load that throws is no logo, never a crash (dart 54-56)`() = runTest {
        val failing = KitLogoLoader { _, _ -> throw IllegalStateException("offline") }
        assertNull(KitProviderLogoRules.load(failing, "anthropic", agent = false))
    }

    @Test
    fun `no loader or no logo is no logo`() = runTest {
        assertNull(KitProviderLogoRules.load(null, "anthropic", agent = false))
        assertNull(KitProviderLogoRules.load({ _, _ -> null }, "anthropic", agent = false))
    }

    @Test
    fun `a static mark is drawn and an active one is refused`() = runTest {
        assertEquals(mark, KitProviderLogoRules.load({ _, _ -> mark }, "anthropic", agent = false))
        val active = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>"""
        assertNull(KitProviderLogoRules.load({ _, _ -> active }, "anthropic", agent = false))
    }
}
