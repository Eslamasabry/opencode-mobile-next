package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.engine.runtime.messages.SetupMessage
import io.github.eslamasabry.oc.engine.runtime.messages.SetupMessages
import io.github.eslamasabry.oc.engine.runtime.setup.registry.NodeCompatScripts
import io.github.eslamasabry.oc.engine.runtime.setup.registry.PhoneSetupScripts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The Node install's give-up line (#117/#119) reaches the person as one plain message, never as script text. */
class NodeCannotRunTest {
    @Test
    fun `B-LINUX-203 the give-up line is the Node-cannot-run problem for the row and the job`() {
        val (row, job) =
            SetupPlanning.describeFailure(
                SetupJobComponent(id = "node", state = "failed", error = NodeCompatScripts.FAILURE_LINE),
                "[oc] probe both-version 139 # node --version\n",
                "Node.js",
            )
        assertEquals(SetupError.NodeCannotRun, row)
        assertEquals(SetupError.NodeCannotRun, job)
        val fromLog =
            SetupPlanning.describeFailure(
                SetupJobComponent(id = "node", state = "failed", error = "exit 1"),
                "${NodeCompatScripts.FAILURE_LINE}\n",
                "Node.js",
            )
        assertEquals(SetupError.NodeCannotRun, fromLog.second)
        val other =
            SetupPlanning.describeFailure(
                SetupJobComponent(id = "node", state = "failed", error = "exit 1"),
                "tar: broken\n",
                "Node.js",
            )
        assertNotEquals(SetupError.NodeCannotRun, other.second)
    }

    @Test
    fun `B-LINUX-203 the give-up line as a message is the plain words key`() {
        assertEquals(
            SetupMessage.Known("phoneSetupErrorNodeCannotRun"),
            SetupMessages.plain(NodeCompatScripts.FAILURE_LINE),
        )
    }

    @Test
    fun `B-LINUX-203 the phone Node install runs the early and start checks`() {
        val script = PhoneSetupScripts.NODE_INSTALL
        assertTrue(script.contains(NodeCompatScripts.FUNCTIONS))
        assertTrue(script.indexOf(NodeCompatScripts.EARLY_CHECK) < script.indexOf("oc_stage 'Downloading Node.js"))
        assertTrue(script.indexOf("for tool in node npm npx") < script.indexOf(NodeCompatScripts.START_CHECK))
        assertTrue(script.indexOf(NodeCompatScripts.START_CHECK) < script.indexOf("npm config set prefix /usr/local"))
    }
}
