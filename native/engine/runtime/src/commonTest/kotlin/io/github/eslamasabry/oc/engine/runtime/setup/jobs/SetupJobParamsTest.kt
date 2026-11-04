package io.github.eslamasabry.oc.engine.runtime.setup.jobs

import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.setup.FakeLinuxChannel
import io.github.eslamasabry.oc.engine.runtime.setup.LinuxServices
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Add-tools job labels (test/aiteam_component_test.dart "adding tools", minus the AI Team component itself: D6) and the
 * named services on the Linux channel ("named services on the Linux channel"; discovery is deferred, amended row).
 */
class SetupJobParamsTest {
    private val titles = mapOf("python" to "Python", "voice" to "Voice typing", "node" to "Node.js")

    private fun title(ids: List<String>) =
        "Adding " + addingTitleNames(titles, ids, ", ") { first, last -> "$first and $last" }

    @Test
    fun `B-LINUX-007 the job remembers what it adds, and the title names it`() {
        val params = SetupJobParams.adding(listOf("python", "voice"))
        assertEquals(listOf("python", "voice"), SetupJobParams.addingIds(params))
        assertFalse(SetupJobParams.isFirstSetup(params))
        assertTrue(SetupJobParams.isFirstSetup(SetupJobParams.firstSetup))
        assertEquals("Adding Voice typing", title(listOf("voice")))
        assertEquals("Adding Python and Voice typing", title(listOf("python", "voice")))
        assertEquals("Adding Node.js, Python and Voice typing", title(listOf("node", "python", "voice")))
        assertEquals("Adding Python", title(listOf("python", "unknown")), "an id not in the registry is not named")
        assertEquals(emptyList(), SetupJobParams.addingIds(mapOf(SetupJobParams.KEY to mapOf("adding" to " , ,"))))
    }

    @Test
    fun `B-LINUX-007 a continued add job keeps its label`() {
        val last = SetupJobRecord(jobId = "j", state = "interrupted", components = emptyList(), params = SetupJobParams.adding(listOf("voice")))
        val params = effectiveJobParams(emptyMap(), last)
        assertEquals(listOf("voice"), SetupJobParams.addingIds(params))
        assertEquals("opencode2", params[SetupIds.OPEN_CODE]?.get("runtime"))
        // A job that cannot continue (done) lends nothing; facts given now are laid over the earlier ones.
        assertEquals(emptyList(), SetupJobParams.addingIds(effectiveJobParams(emptyMap(), last.copy(state = "done"))))
        val relabelled = effectiveJobParams(SetupJobParams.adding(listOf("python")), last)
        assertEquals(listOf("python"), SetupJobParams.addingIds(relabelled))
        // An OpenCode 1 pin never leaks into the OpenCode 2 installer.
        val legacy = effectiveJobParams(mapOf(SetupIds.OPEN_CODE to mapOf("runtime" to "opencode1", "version" to "1.18.29")), null)
        assertEquals(mapOf("runtime" to "opencode2"), legacy[SetupIds.OPEN_CODE])
    }

    @Test
    fun `B-LINUX-007 progress read back from setup json carries the label`() {
        val record = SetupJobRecord.parse(
            """{"jobId":"j","state":"running","order":["voice"],""" +
                """"components":{"voice":{"state":"running"}},"params":{"_job":{"adding":"voice"}}}""",
        )
        assertNotNull(record)
        val progress = progressFromRecord(record)
        assertEquals(listOf("voice"), progress.adding)
        assertFalse(progress.firstSetup)
        assertEquals(SetupState.Running, progress.state)
        assertEquals(listOf(ComponentProgress("voice", ComponentState.Running)), progress.components)
        // Text that is not a job is no job; an unknown state reads as interrupted.
        assertEquals(null, SetupJobRecord.parse("{unreadable"))
        assertEquals(null, SetupJobRecord.parse("""{"state":"running"}"""))
        assertEquals("interrupted", SetupJobRecord.parse("""{"jobId":"k"}""")?.state)
    }

    @Test
    fun `B-LINUX-008 start, stop and log speak the contract`() = runTest {
        val channel = FakeLinuxChannel { method, _ -> (if (method == "serviceLog") "tail" else null).ok() }
        val services = LinuxServices(channel)
        services.start("helper", "exec helper run", port = 8472, notice = "OpenCode and the helper are running")
        services.start("other", "sleep 1")
        services.stop("helper")
        assertEquals("tail", (services.log("helper", tailBytes = 10) as? io.github.eslamasabry.oc.core.common.Outcome.Ok)?.value)
        assertEquals(listOf("startService", "startService", "stopService", "serviceLog"), channel.calls.map { it.method })
        assertEquals(
            mapOf("name" to "helper", "script" to "exec helper run", "port" to 8472, "notice" to "OpenCode and the helper are running"),
            channel.calls[0].arguments,
        )
        // No port and no notice: the keys are left out, not sent as null.
        assertEquals(mapOf<String, Any?>("name" to "other", "script" to "sleep 1"), channel.calls[1].arguments)
        assertEquals(mapOf<String, Any?>("name" to "helper"), channel.calls[2].arguments)
        assertEquals(mapOf<String, Any?>("name" to "helper", "tailBytes" to 10), channel.calls[3].arguments)
        // No log at all reads as empty text.
        channel.answer = { _, _ -> null.ok() }
        assertEquals("", (services.log("helper") as io.github.eslamasabry.oc.core.common.Outcome.Ok).value)
        assertEquals(LinuxServices.DEFAULT_LOG_TAIL_BYTES, channel.calls.last().arguments?.get("tailBytes"))
    }

    @Test
    fun `B-LINUX-008 status lists the running services`() = runTest {
        val channel = FakeLinuxChannel { _, _ ->
            mapOf("installed" to true, "phase" to "ready", "serverRunning" to true, "services" to listOf("server", "helper", 42)).ok()
        }
        val running = (LinuxServices(channel).running() as io.github.eslamasabry.oc.core.common.Outcome.Ok).value
        assertEquals(listOf("server", "helper"), running)
        assertTrue(LinuxServices.serviceRunning(running, "helper"))
        assertFalse(LinuxServices.serviceRunning(running, "nope"))
        assertEquals(listOf("status"), channel.calls.map { it.method })
        assertEquals(emptyList(), LinuxServices.servicesOf(null), "an absent status has no services")
        assertEquals(emptyList(), LinuxServices.servicesOf("server"))
    }
}
