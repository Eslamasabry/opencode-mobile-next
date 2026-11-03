package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A fake activity result registry: registered callbacks by key, and what was launched. */
private class FakeHost : MicHost {
    val callbacks = mutableMapOf<String, (Boolean) -> Unit>()
    var launches = 0

    override fun register(key: String, onResult: (Boolean) -> Unit): MicLauncher {
        callbacks[key] = onResult
        return object : MicLauncher {
            override fun launch() {
                launches++
            }

            override fun unregister() {
                if (callbacks[key] === onResult) callbacks.remove(key)
            }
        }
    }

    fun answer(ok: Boolean) = callbacks[MicRequestRelay.KEY]?.invoke(ok)
}

class MicRequestRelayTest {
    @Test
    fun `B-MEDIA-023 the microphone answer reaches the request after the activity is recreated`() = runTest {
        val relay = MicRequestRelay()
        val old = FakeHost()
        val asked = async { relay.ask(old) }
        runCurrent()
        assertEquals(1, old.launches)
        // Rotation: the old activity is gone (its registry never answers); the new one gets the dialog's result.
        val replacement = FakeHost()
        relay.hostCreated(replacement)
        replacement.answer(true)
        runCurrent()
        assertTrue(asked.await())
        assertEquals(0, replacement.launches) // the dialog is not shown twice
        assertTrue(replacement.callbacks.isEmpty()) // released after the answer
    }

    @Test
    fun `B-MEDIA-023 an activity created with no request waiting registers nothing`() = runTest {
        val relay = MicRequestRelay()
        val host = FakeHost()
        relay.hostCreated(host)
        assertTrue(host.callbacks.isEmpty())
        val asked = async { relay.ask(host) }
        runCurrent()
        host.answer(false)
        assertEquals(false, asked.await())
    }
}
