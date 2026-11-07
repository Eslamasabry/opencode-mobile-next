package io.github.eslamasabry.oc.ui.kit.rb.o26

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.text.KitLinkClaim
import io.github.eslamasabry.oc.ui.kit.text.KitLinkScope
import io.github.eslamasabry.oc.ui.kit.text.KitLinkScopes
import io.github.eslamasabry.oc.ui.kit.text.rememberKitLinkOpener
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every rule of lib/ui/kit/kit_link_scope.dart at 8302de537: claim first, otherwise the external opener, once. */
@OptIn(ExperimentalTestApi::class)
class KitLinkScopeParityTest {
    @Test fun `no scope sends the link to the external opener`() {
        val out = mutableListOf<String>()
        KitLinkScopes.open(null, "https://example.com/a", out::add)
        assertEquals(listOf("https://example.com/a"), out)
    }

    @Test fun `a claimed link never reaches the external opener`() {
        val asked = mutableListOf<String>()
        val out = mutableListOf<String>()
        KitLinkScopes.open({ asked += it; true }, "http://localhost:5173/", out::add)
        assertEquals(listOf("http://localhost:5173/"), asked)
        assertEquals(emptyList(), out)
    }

    @Test fun `an unclaimed link goes out exactly once`() {
        val out = mutableListOf<String>()
        KitLinkScopes.open({ false }, "https://example.com/b", out::add)
        assertEquals(listOf("https://example.com/b"), out)
    }

    @Test fun `the nearest scope decides and an outer one is not asked`() = runComposeUiTest {
        val asked = mutableListOf<String>()
        val out = mutableListOf<String>()
        var open: (String) -> Unit = {}
        val outer = KitLinkClaim { asked += "outer"; true }
        val inner = KitLinkClaim { asked += "inner"; false }
        setContent {
            KitLinkScope(outer) { KitLinkScope(inner) { open = rememberKitLinkOpener(out::add) } }
        }
        waitForIdle()
        open("https://example.com/c")
        assertEquals(listOf("inner"), asked)
        assertEquals(listOf("https://example.com/c"), out)
    }

    @Test fun `outside any scope the opener bound in composition opens externally`() = runComposeUiTest {
        val out = mutableListOf<String>()
        var open: (String) -> Unit = {}
        setContent { open = rememberKitLinkOpener(out::add) }
        waitForIdle()
        open("https://example.com/d")
        assertEquals(listOf("https://example.com/d"), out)
    }
}
