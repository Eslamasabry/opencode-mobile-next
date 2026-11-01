package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.FakeLinuxHost
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The deliberate-stop mark (port of lib/builtin/deliberate_stop.dart and its uses in builtin_server.dart). */
class DeliberateStopTest {
    private class MemoryFlags : RuntimeFlags {
        val values = mutableMapOf<String, Boolean>()
        var unreadable = false

        override suspend fun readBoolean(key: String): Boolean? = if (unreadable) null else values[key]

        override suspend fun writeBoolean(key: String, value: Boolean): Outcome<Unit> {
            values[key] = value
            return Unit.ok()
        }

        override suspend fun remove(key: String): Outcome<Unit> {
            values.remove(key)
            return Unit.ok()
        }
    }

    private val flags = MemoryFlags()
    private val stop = DeliberateStop(flags)
    private val phone = ProfileId("builtin")

    @Test
    fun `B-LINUX-038 the person's Stop is remembered per profile under its swept key`() = runTest {
        assertEquals("oc.phoneServerStopped.builtin", DeliberateStop.keyFor(phone))
        assertFalse(stop.isMarked(phone))
        stop.mark(phone)
        assertTrue(stop.isMarked(phone))
        assertFalse(stop.isMarked(ProfileId("other")))
        flags.unreadable = true
        assertFalse(stop.isMarked(phone), "an unreadable mark reads as not set")
    }

    @Test
    fun `B-LINUX-038 a start that ends answering clears the mark, a failed one keeps it`() = runTest {
        val host = FakeLinuxHost()
        stop.mark(phone)
        host.dies = true
        val starter = PhoneServerStarter(host, schedulerClock(), deliberateStop = stop)
        starter.start(StartTarget(phone, "secret"))
        assertTrue(stop.isMarked(phone))
        host.dies = false
        assertNull(starter.start(StartTarget(phone, "secret")))
        assertFalse(stop.isMarked(phone))
        assertTrue(flags.values.isEmpty())
    }
}
