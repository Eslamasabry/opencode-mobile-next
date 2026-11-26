package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.system.ErrnoException
import android.system.OsConstants
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.OsFileDescriptors
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** B-LINUX-617: exited Process handles must not be pinned forever in BuiltinLinux.processConfinement. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BuiltinLinuxProcessConfinementTest {
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `B-LINUX-617 pruning processes removes exited Process entries from processConfinement`() {
        val app = RuntimeEnvironment.getApplication()
        val linux = BuiltinLinux(app)
        File(linux.home, "ubuntu.ready").apply {
            parentFile?.mkdirs()
            writeText("ready")
        }

        val processesField = BuiltinLinux::class.java.getDeclaredField("processes").apply { isAccessible = true }
        val confinementField =
            BuiltinLinux::class.java.getDeclaredField("processConfinement").apply { isAccessible = true }
        val processes = processesField.get(linux) as MutableList<Process>
        val confinement = confinementField.get(linux) as IdentityHashMap<Process, Boolean>

        val exited = FakeProcess(alive = false)
        val live = FakeProcess(alive = true)
        processes.add(exited)
        processes.add(live)
        confinement[exited] = true
        confinement[live] = false

        // start() prunes exited processes before invoking ProcessBuilder (which fails without proot in host tests).
        runCatching { linux.start("true", null) }

        assertFalse(exited in processes, "exited process must be pruned from processes")
        assertTrue(live in processes, "live process must remain in processes")
        assertFalse(
            confinement.containsKey(exited),
            "exited process must be pruned from processConfinement",
        )
        assertEquals(false, confinement[live], "live process confinement state must remain intact")
    }

    @Test
    fun `B-LINUX-617 OsFileDescriptors swallows ErrnoException on close without masking caller error`() {
        val fd = FileDescriptor()
        var closeCalls = 0
        val value =
            OsFileDescriptors.useFd(
                fd = fd,
                close = {
                    closeCalls++
                    throw ErrnoException("close", OsConstants.EBADF)
                },
            ) { "done" }
        assertEquals("done", value)
        assertEquals(1, closeCalls)

        var errorCloseCalls = 0
        val failure =
            assertFailsWith<IOException> {
                OsFileDescriptors.useFd(
                    fd = fd,
                    close = {
                        errorCloseCalls++
                        throw ErrnoException("close", OsConstants.EBADF)
                    },
                ) {
                    throw IOException("primary failure")
                }
            }
        assertEquals("primary failure", failure.message)
        assertEquals(1, errorCloseCalls)
    }

    private class FakeProcess(private val alive: Boolean) : Process() {
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun exitValue(): Int = 0

        override fun destroy() = Unit

        override fun isAlive(): Boolean = alive
    }
}
