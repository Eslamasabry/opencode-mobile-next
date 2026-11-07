package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.engine.linuxsetup.NodeCompat
import io.github.eslamasabry.oc.engine.runtime.setup.registry.NodeCompatScripts
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Node safe-mode shell (#117/#119) lives in :engine:linuxsetup and :engine:runtime, which do not depend on each
 * other; this module sees both, so it holds them equal. A phone set up by either path keeps the same files in Linux.
 */
class NodeCompatTwinTest {
    @Test
    fun `B-LINUX-203 both setup engines carry the same Node safe-mode text`() {
        assertEquals(NodeCompat.FUNCTIONS, NodeCompatScripts.FUNCTIONS)
        assertEquals(NodeCompat.SERVER_ENV, NodeCompatScripts.SERVER_ENV)
        assertEquals(NodeCompat.EARLY_CHECK, NodeCompatScripts.EARLY_CHECK)
        assertEquals(NodeCompat.START_CHECK, NodeCompatScripts.START_CHECK)
        assertEquals(NodeCompat.FAILURE_LINE, NodeCompatScripts.FAILURE_LINE)
        assertEquals(NodeCompat.STATE_FILE, NodeCompatScripts.STATE_FILE)
        assertEquals(NodeCompat.ENV_FILE, NodeCompatScripts.ENV_FILE)
        assertEquals(NodeCompat.ENV_NAMES, NodeCompatScripts.ENV_NAMES)
    }
}
