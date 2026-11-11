package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.model.IntegrationConnectionInfo
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.feature.settings.ui.soleMethodToConnect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

/** W4-30: tapping a provider with nothing to manage and one way to connect goes straight to it. */
class LibraryProviderTapTest {
    private fun m(type: String, id: String? = null, names: List<String> = emptyList()) =
        IntegrationMethodInfo(type, id, type, persistentListOf(), names.toImmutableList(), persistentMapOf())

    private fun info(id: String, connections: List<IntegrationConnectionInfo>, vararg methods: IntegrationMethodInfo) =
        IntegrationInfo(id, id, methods.toList().toImmutableList(), connections.toImmutableList(), connections.size)

    @Test
    fun `a key-only provider with no connection opens the key step`() {
        val anthropic = info("anthropic", emptyList(), m("key"), m("oauth", "claude"))
        assertEquals("key", soleMethodToConnect(anthropic, commandSupported = false)?.type)
    }

    @Test
    fun `a connected provider keeps its sheet`() {
        val connection = IntegrationConnectionInfo("credential", "c1", "Work", "connected")
        assertNull(soleMethodToConnect(info("anthropic", listOf(connection), m("key")), false))
    }

    @Test
    fun `several methods or environment details keep the sheet`() {
        assertNull(soleMethodToConnect(info("openai", emptyList(), m("key"), m("oauth", "a"), m("oauth", "b")), false))
        assertNull(soleMethodToConnect(info("x", emptyList(), m("key"), m("env", names = listOf("X_KEY"))), false))
    }
}
