package io.github.eslamasabry.oc.engine.runtime.setup.jobs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.FakeLinuxHost
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.schedulerClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The setup finisher (test/builtin/setup_finisher_lifetime_test.dart:93, "legacy setup keeps profile identity"). */
class PhoneSetupFinisherTest {
    private class Store(var profile: PhoneProfile) : PhoneSetupProfiles {
        val saved = mutableListOf<PhoneProfile>()

        /** Other per-profile data (a local draft) the finisher must never touch. */
        val drafts = mutableMapOf("oc.localDraft.owner" to "keep this draft")

        override suspend fun ensureBuiltinProfile(version: String?): Outcome<PhoneProfile> = profile.ok()

        override suspend fun save(profile: PhoneProfile): Outcome<Unit> {
            saved.removeAll { it.id == profile.id }
            saved += profile
            this.profile = profile
            return Unit.ok()
        }
    }

    private class Server(private val host: FakeLinuxHost) : PhoneSetupServer {
        var stops = 0

        override suspend fun serverRunning(): Boolean = host.running

        override suspend fun stopServer(): Outcome<Unit> {
            stops++
            host.running = false
            return Unit.ok()
        }
    }

    @Test
    fun `B-LINUX-015 legacy setup keeps the profile identity and requires history proof`() = runTest {
        for (admitted in listOf(true, false)) {
            val original = PhoneProfile(ProfileId("owner"), password = "synthetic-only", requiresOpenCode2Migration = true)
            val store = Store(original)
            val host = FakeLinuxHost().apply {
                running = true
                historyMoveRefused = !admitted // The admission check exits 74.
            }
            val server = Server(host)
            var connected: PhoneProfile? = null
            val finisher = PhoneSetupFinisher(
                profiles = store,
                server = server,
                starter = PhoneServerStarter(host, schedulerClock()),
                connect = { profile ->
                    connected = profile
                    Unit.ok()
                },
                isConnectedTo = { false },
            )
            val result = finisher.finish(SetupFinishRequest(openCodeChanged = true, version = "2.2.5"))

            assertEquals("keep this draft", store.drafts["oc.localDraft.owner"])
            assertEquals(ProfileId("owner"), store.profile.id)
            assertEquals(1, server.stops, "the replaced program is stopped first")
            assertEquals(if (admitted) 1 else 0, host.launchCount)
            if (admitted) {
                assertEquals(Outcome.Ok(Unit), result)
                assertEquals(listOf(ProfileId("owner")), store.saved.map { it.id })
                assertEquals(ProfileId("owner"), connected?.id)
                assertTrue(connected!!.openCode2HistoryPending)
                assertFalse(store.profile.requiresOpenCode2Migration)
                assertTrue(store.profile.openCode2HistoryPending)
            } else {
                assertTrue(result is Outcome.Failed && result.failure is Failure.Runtime)
                assertEquals(null, connected)
                assertTrue(store.saved.isEmpty())
                assertTrue(store.profile.requiresOpenCode2Migration)
                assertFalse(store.profile.openCode2HistoryPending)
            }
        }
    }

    @Test
    fun `B-LINUX-015 a running server the app is connected to is left alone, and Termux never starts the phone`() = runTest {
        val host = FakeLinuxHost().apply { running = true }
        val store = Store(PhoneProfile(ProfileId("phone"), password = "synthetic-only"))
        var connects = 0
        val finisher = PhoneSetupFinisher(
            store,
            Server(host),
            PhoneServerStarter(host, schedulerClock()),
            connect = {
                connects++
                Unit.ok()
            },
            isConnectedTo = { true },
        )
        assertEquals(Outcome.Ok(Unit), finisher.finish(SetupFinishRequest(openCodeChanged = false)))
        assertEquals(0, host.launchCount)
        assertEquals(0, connects)
        assertTrue(finisher.finish(SetupFinishRequest(host = SetupHost.Termux, openCodeChanged = true)) is Outcome.Failed)
        assertEquals(0, host.launchCount)
    }
}
