package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The router's mechanics (07 §7.3 rules 1–9) over fakes, on virtual time. */
class EntryRouterTest {
    private class Rig(scope: TestScope, initial: EntryWorld) {
        val world = FakeWorldSource(initial)
        val effects = FakeEffects()
        val clock = SchedulerClock(scope.testScheduler)
        val router =
            DefaultEntryRouter(world, effects, clock, scope.backgroundScope, StandardTestDispatcher(scope.testScheduler))

        /** What the shell does: take the handoff, navigate, consume. */
        fun ack(): EntryHandoff? = router.pending.value?.also { router.consume(it.id) }
    }

    private val chat = EntryTarget.Chat(ConversationRef(P1, S1))

    private fun TestScope.rig(world: EntryWorld = readyWorld()) = Rig(this, world).also { runCurrent() }

    @Test
    fun `B-ANDR-073 a launch request is held until bootstrap and the gate open, then delivered once`() =
        runTest {
            val rig = rig(EntryWorld())
            rig.router.submit(EntryRequest.PinnedSession(P1, S1, T0))
            runCurrent()
            assertNull(rig.router.pending.value)
            assertEquals(EntryWaiting(WaitReason.Bootstrap, null), rig.router.waiting.value)

            rig.world.world.value = readyWorld().copy(gateOpen = false)
            runCurrent()
            assertNull(rig.router.pending.value)

            rig.world.world.value = readyWorld()
            runCurrent()
            assertEquals(chat, rig.ack()?.target)
            runCurrent()
            assertNull(rig.router.pending.value)
            assertNull(rig.router.waiting.value)
            // Further changes never replay it.
            rig.world.world.value = readyWorld().copy(isAndroid = true, phoneSetup = PhoneSetupStatus.Running)
            runCurrent()
            assertNull(rig.router.pending.value)
        }

    @Test
    fun `B-ANDR-027 a newer live tap beats a stale cold-start one on the same channel`() =
        runTest {
            val rig = rig(EntryWorld())
            rig.router.submit(EntryRequest.Launch(LaunchAction.Connect, T0)) // cold start, before the gate
            rig.router.submit(EntryRequest.PinnedSession(P1, S1, T0)) // live, newer
            rig.world.world.value = readyWorld()
            runCurrent()
            assertEquals(chat, rig.ack()?.target)
            runCurrent()
            assertNull(rig.router.pending.value) // the stale shortcut never opens
        }

    @Test
    fun `B-ANDR-028 each request is delivered once and the next waits for the acknowledgement`() =
        runTest {
            val rig = rig()
            rig.router.submit(EntryRequest.Launch(LaunchAction.Connect, T0))
            rig.router.submit(EntryRequest.TilePreferences(TileId.Pause, T0)) // same channel: newer wins
            rig.router.submit(EntryRequest.Link("opencode-mobile://team/gate/p1/g1", T0))
            runCurrent()
            val first = rig.router.pending.value!!
            runCurrent()
            assertEquals(first, rig.router.pending.value) // still the same until consumed
            rig.router.consume(first.id + 99) // a stale id is ignored
            runCurrent()
            assertEquals(first, rig.router.pending.value)
            val seen = mutableListOf(rig.ack()?.target)
            runCurrent()
            seen += rig.ack()?.target
            runCurrent()
            assertNull(rig.ack())
            assertEquals(setOf(EntryTarget.Settings(SettingsSectionTarget.Background), EntryTarget.NeedsYou), seen.toSet())
        }

    @Test
    fun `B-NAV-064 the link that launched the app opens once and a live link while running is accepted`() =
        runTest {
            val rig = rig(EntryWorld())
            rig.router.submit(EntryRequest.Link("opencode-mobile://session?profile=p1&session=ses_1", T0))
            rig.world.world.value = readyWorld()
            runCurrent()
            assertEquals(chat, rig.ack()?.target)
            runCurrent()
            assertNull(rig.router.pending.value)
            rig.router.submit(EntryRequest.Link("opencode-mobile://session?profile=p1&session=ses_2", T0))
            runCurrent()
            assertEquals(EntryTarget.Chat(ConversationRef(P1, SessionId("ses_2"))), rig.ack()?.target)
        }

    @Test
    fun `B-NAV-064 malformed links never become pending`() =
        runTest {
            val rig = rig()
            rig.router.submit(EntryRequest.Link("opencode-mobile://session?profile=-bad&session=x", T0))
            runCurrent()
            assertNull(rig.router.pending.value)
            assertNull(rig.router.waiting.value)
        }

    @Test
    fun `B-NAV-061 a link for a server not saved here is parked under a ticket until forgotten`() =
        runTest {
            val rig = rig()
            rig.router.submit(EntryRequest.Link("opencode-mobile://session?profile=other&session=ses_1", T0))
            runCurrent()
            val target = rig.ack()?.target as EntryTarget.SessionAddress
            assertEquals(ParsedLink.Session(ProfileId("other"), S1), rig.router.linkFor(target.linkTicket))
            rig.router.forgetLink(target.linkTicket)
            assertNull(rig.router.linkFor(target.linkTicket))
        }

    @Test
    fun `B-ANDR-075 a share waits for the server, is delivered once and a newer share wins`() =
        runTest {
            val rig = rig(readyWorld(links = mapOf(P1 to LinkState.Connecting)))
            rig.router.submit(EntryRequest.SharedText("first", T0))
            rig.router.submit(EntryRequest.SharedText("second!", T0))
            runCurrent()
            assertEquals(EntryWaiting(WaitReason.Connection, P1), rig.router.waiting.value)
            assertTrue(rig.effects.calls.isEmpty())
            rig.world.world.value = readyWorld()
            runCurrent()
            val handoff = rig.ack()!!
            assertEquals(EntryTarget.Chat(ConversationRef(P1, SessionId("ses_new")), focusComposer = true, opened = Opened.NewlyCreated), handoff.target)
            assertEquals(listOf("create:p1", "seed:ses_new:7"), rig.effects.calls) // only the newest text, once
            runCurrent()
            assertNull(rig.router.pending.value)
        }

    @Test
    fun `B-ANDR-075 a failed share ends on a notice, never a broken page`() =
        runTest {
            val rig = rig()
            rig.effects.createResult = Outcome.Failed(Failure.Unexpected())
            rig.router.submit(EntryRequest.SharedText("text", T0))
            runCurrent()
            assertEquals(EntryHandoff(1, null, EntryNotice.ShareFailed), rig.ack())
        }

    @Test
    fun `B-ANDR-027 an item that arrives while effects run is not swallowed (rule 5)`() =
        runTest {
            val rig = rig()
            val gate = CompletableDeferred<Unit>()
            rig.effects.onCreate = { gate.await() }
            rig.router.submit(EntryRequest.Launch(LaunchAction.NewTask, T0, EntryOrigin.Internal))
            runCurrent()
            rig.router.submit(EntryRequest.Launch(LaunchAction.Connect, T0)) // same channel, while CreateSession runs
            gate.complete(Unit)
            runCurrent()
            assertEquals(EntryTarget.Servers(null), rig.ack()?.target) // the newer request wins
            runCurrent()
            assertNull(rig.router.pending.value)
        }

    @Test
    fun `B-NAV-054 a scheduled tap is re-evaluated on a 1 s tick and opens the history after 30 s`() =
        runTest {
            val world =
                readyWorld(links = mapOf(P1 to LinkState.Connecting))
                    .copy(schedules = mapOf("sch_1" to ScheduleInfo(P1, ProjectDir("/w"), morningSummary = false)))
            val rig = rig(world)
            rig.router.submit(EntryRequest.ScheduledOpen(P1, "sch_1", S1, rig.clock.now()))
            runCurrent()
            assertEquals(EntryWaiting(WaitReason.ScheduleSettling, P1), rig.router.waiting.value)
            advanceTimeBy(29.seconds)
            runCurrent()
            assertNull(rig.router.pending.value)
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(EntryTarget.ScheduledPrompts(P1, ProjectDir("/w"), "sch_1"), rig.ack()?.target)
        }

    @Test
    fun `B-NAV-005 a resolved ref replaces the ids the notification carried`() =
        runTest {
            val rig = rig()
            val agentRef = ConversationRef(ProfileId("p1.agents"), S1)
            rig.effects.resolveResult = { agentRef }.let { f -> { _: ConversationRef -> Outcome.Ok(f()) } }
            rig.router.submit(EntryRequest.AlertOpen(AlertKind.Complete, P1, S1, null, reply = true, receivedAt = T0))
            runCurrent()
            assertEquals(EntryTarget.Chat(agentRef, focusComposer = true), rig.ack()?.target)
        }

    @Test
    fun `B-NAV-005 a monitored alert with a token that no longer matches opens nothing and says so`() =
        runTest {
            val rig = rig()
            rig.router.submit(EntryRequest.AlertOpen(AlertKind.Permission, P2, S1, MonitorToken("b".repeat(64)), false, T0))
            runCurrent()
            assertEquals(EntryHandoff(1, null, EntryNotice.RequestGone), rig.ack())
        }

    @Test
    fun `B-ANDR-028 untrusted arrivals make zero gateway calls before a tap`() =
        runTest {
            val rig = rig(readyWorld(links = mapOf(P1 to LinkState.Ready, P2 to LinkState.Idle)).copy(
                usageProviders = setOf(UsageProvider(P2, "openai", false)),
            ))
            val actions = EntryValidators.launchActions.values + LaunchAction.UsageTarget(UsageTargetKind.SignIn, "openai", P2)
            for (action in actions) {
                rig.router.submit(EntryRequest.Launch(action, T0, EntryOrigin.Untrusted))
                runCurrent()
                rig.ack()
                runCurrent()
            }
            assertEquals(emptyList(), rig.effects.gatewayCalls)
        }

    @Test
    fun `B-ANDR-028 the same new task from an app-owned PendingIntent runs its effect`() =
        runTest {
            val rig = rig()
            rig.router.submit(EntryRequest.Launch(LaunchAction.NewTask, T0, EntryOrigin.Internal))
            runCurrent()
            assertEquals(listOf("create:p1"), rig.effects.gatewayCalls)
            assertEquals(Opened.NewlyCreated, (rig.ack()?.target as EntryTarget.Chat).opened)
        }
}
