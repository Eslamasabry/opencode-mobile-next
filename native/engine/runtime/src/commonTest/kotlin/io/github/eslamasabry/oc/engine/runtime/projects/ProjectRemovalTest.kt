package io.github.eslamasabry.oc.engine.runtime.projects

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.setup.FakeLinuxChannel
import io.github.eslamasabry.oc.engine.runtime.setup.LinuxChannel
import io.github.eslamasabry.oc.engine.runtime.setup.ToolsChanged
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** The removal bridge (test/builtin_project_removal_test.dart): projects kept by default, exact confirmation, measured totals. */
class ProjectRemovalTest {
    private val channel = FakeLinuxChannel()
    private val changed = ToolsChanged()
    private val removal = ProjectRemoval(channel, changed)

    private fun Outcome<*>.failure(): Failure = (this as? Outcome.Failed)?.failure ?: fail("expected a failure, got $this")

    private val valid = mapOf("runtimeBytes" to 1024, "projectsBytes" to 4096, "measuredAtMilliseconds" to 1_790_467_200_000L)

    @Test
    fun `B-LINUX-029 remove keeps projects unless explicitly asked otherwise`() = runTest {
        assertEquals(Outcome.Ok(Unit), removal.remove())
        assertEquals(listOf(FakeLinuxChannel.Call("removeRuntime", mapOf("alsoDeleteProjects" to false))), channel.calls)
        assertEquals(1L, changed.revision.value, "a removal attempt tells the tool pages")
    }

    @Test
    fun `B-LINUX-029 legacy uninstall also keeps projects`() = runTest {
        removal.uninstall()
        assertEquals(listOf(FakeLinuxChannel.Call("removeRuntime", mapOf("alsoDeleteProjects" to false))), channel.calls)
    }

    @Test
    fun `B-LINUX-029 delete everything sends the exact typed confirmation`() = runTest {
        assertEquals("OpenCode", ProjectRemoval.DELETION_CONFIRMATION_NAME)
        removal.remove(alsoDeleteProjects = true, confirmationName = ProjectRemoval.DELETION_CONFIRMATION_NAME)
        assertEquals(
            listOf(FakeLinuxChannel.Call("removeRuntime", mapOf("alsoDeleteProjects" to true, "confirmationName" to "OpenCode"))),
            channel.calls,
        )
    }

    @Test
    fun `B-LINUX-029 a wrong confirmation deletes nothing`() = runTest {
        for (name in listOf(null, "", "opencode", "OpenCode ", " OpenCode", "Another project")) {
            val result = removal.remove(alsoDeleteProjects = true, confirmationName = name)
            assertEquals(Failure.Runtime(RuntimeProblem.ConfirmationRequired), result.failure(), "$name")
        }
        assertTrue(channel.calls.isEmpty())
        assertEquals(0L, changed.revision.value, "nothing was attempted")
    }

    @Test
    fun `B-LINUX-029 an older app never receives its destructive uninstall`() = runTest {
        channel.answer = FakeLinuxChannel.missingPlugin
        assertEquals(Failure.Unavailable(LinuxChannel.BUILTIN_LINUX), removal.uninstall().failure())
        assertFalse(channel.calls.any { it.method == "uninstall" })
    }

    @Test
    fun `B-LINUX-029 keep projects does not forward an unrelated typed value`() = runTest {
        removal.remove(confirmationName = "unrelated text")
        assertEquals(mapOf<String, Any?>("alsoDeleteProjects" to false), channel.calls.single().arguments)
    }

    @Test
    fun `B-LINUX-030 each removal choice has its own measured freed-byte total`() = runTest {
        channel.answer = { _, _ -> valid.ok() }
        val storage = (removal.measure() as Outcome.Ok).value
        assertEquals(listOf(FakeLinuxChannel.Call("projectStorage", null)), channel.calls)
        assertEquals(ProjectStorage(1024, 4096, 1_790_467_200_000L), storage)
        assertEquals(1024L, storage.keepProjectsFreedBytes)
        assertEquals(5120L, storage.deleteEverythingFreedBytes)
    }

    @Test
    fun `B-LINUX-030 retained projects stay visible with no runtime, and an empty install measures zero`() = runTest {
        channel.answer = { _, _ -> (valid + ("runtimeBytes" to 0)).ok() }
        val retained = (removal.measure() as Outcome.Ok).value
        assertEquals(0L, retained.keepProjectsFreedBytes)
        assertEquals(4096L, retained.projectsBytes)
        assertEquals(4096L, retained.deleteEverythingFreedBytes)
        channel.answer = { _, _ -> (valid + ("runtimeBytes" to 0) + ("projectsBytes" to 0)).ok() }
        val empty = (removal.measure() as Outcome.Ok).value
        assertEquals(0L, empty.keepProjectsFreedBytes)
        assertEquals(0L, empty.deleteEverythingFreedBytes)
    }

    @Test
    fun `B-LINUX-030 invalid measurement data is rejected and cannot imply free space`() = runTest {
        for (field in listOf("runtimeBytes", "projectsBytes", "measuredAtMilliseconds")) {
            for (invalid in listOf<Any?>(null, -1, 1.5, "1024", true)) {
                channel.answer = { _, _ -> (valid + (field to invalid)).ok() }
                assertEquals(Failure.Runtime(RuntimeProblem.StorageUnavailable), removal.measure().failure(), "$field = $invalid")
            }
        }
        for (invalid in listOf<Any?>(null, emptyMap<String, Any?>(), "not a map")) {
            channel.answer = { _, _ -> invalid.ok() }
            assertEquals(Failure.Runtime(RuntimeProblem.StorageUnavailable), removal.measure().failure(), "$invalid")
        }
    }

    @OptIn(RedactorApi::class)
    @Test
    fun `B-LINUX-030 native error text and absent channels fail safely`() = runTest {
        val operations = mapOf<String, suspend () -> Outcome<*>>(
            "measure" to { removal.measure() },
            "keep projects" to { removal.remove() },
            "delete everything" to { removal.remove(alsoDeleteProjects = true, confirmationName = "OpenCode") },
        )
        for ((name, operation) in operations) {
            channel.answer = { _, _ -> Failure.Runtime(RuntimeProblem.Generic, Redacted("synthetic-private-native-message")).failed() }
            val failure = operation().failure()
            assertTrue(failure is Failure.Runtime, name)
            assertEquals(null, failure.details, "$name: no native diagnostic content")
            assertFalse(failure.toString().contains("synthetic-private-native"), name)
            assertEquals(
                if (name == "measure") RuntimeProblem.StorageUnavailable else RuntimeProblem.RemovalFailed,
                failure.problem,
            )
            channel.answer = FakeLinuxChannel.missingPlugin
            assertEquals(Failure.Unavailable(LinuxChannel.BUILTIN_LINUX), operation().failure(), name)
        }
    }
}
