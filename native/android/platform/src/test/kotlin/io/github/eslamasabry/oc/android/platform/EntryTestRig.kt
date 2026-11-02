package io.github.eslamasabry.oc.android.platform

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.entry.BootstrapPhase
import io.github.eslamasabry.oc.engine.entry.DefaultEntryRouter
import io.github.eslamasabry.oc.engine.entry.EntryEffects
import io.github.eslamasabry.oc.engine.entry.EntryHandoff
import io.github.eslamasabry.oc.engine.entry.EntryWorld
import io.github.eslamasabry.oc.engine.entry.EntryWorldSource
import io.github.eslamasabry.oc.engine.entry.LinkState
import io.github.eslamasabry.oc.engine.entry.MonitorToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.time.Clock
import kotlin.time.Instant

internal val P1 = ProfileId("p1")
internal val P2 = ProfileId("p2")
internal val S1 = SessionId("ses_1")
internal val NEW_SESSION = SessionId("ses_new")

internal object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_767_225_600_000)
}

/** Open, P1 active and connected, P2 saved but not connected. */
internal fun readyWorld(): EntryWorld =
    EntryWorld(
        bootstrap = BootstrapPhase.Ready,
        gateOpen = true,
        activeProfileId = P1,
        savedProfileIds = setOf(P1, P2),
        links = mapOf(P1 to LinkState.Ready),
    )

internal class FakeWorldSource(initial: EntryWorld) : EntryWorldSource {
    override val world: MutableStateFlow<EntryWorld> = MutableStateFlow(initial)
}

/** Records every effect. [gatewayCalls] are the ones that touch a server: connect and create. */
internal class RecordingEffects : EntryEffects {
    val calls = mutableListOf<String>()
    val gatewayCalls: List<String> get() = calls.filter { it.startsWith("connect") || it.startsWith("create") }

    override suspend fun connect(profileId: ProfileId): Outcome<Unit> {
        calls += "connect:${profileId.raw}"
        return Outcome.Ok(Unit)
    }

    override suspend fun resolveRef(ref: ConversationRef): Outcome<ConversationRef> {
        calls += "resolve:${ref.sessionId.raw}"
        return Outcome.Ok(ref)
    }

    override suspend fun createSession(profileId: ProfileId): Outcome<ConversationRef> {
        calls += "create:${profileId.raw}"
        return Outcome.Ok(ConversationRef(profileId, NEW_SESSION))
    }

    override suspend fun seedDraft(ref: ConversationRef, text: String): Outcome<Unit> {
        calls += "seed:${ref.sessionId.raw}:$text"
        return Outcome.Ok(Unit)
    }

    override suspend fun resolveMonitoredRequest(
        token: MonitorToken,
        profileId: ProfileId,
        sessionId: SessionId,
    ): Outcome<ConversationRef> {
        calls += "monitor:${profileId.raw}"
        return Outcome.Failed(Failure.Unexpected())
    }

    override suspend fun resolveWidgetConversation(profileId: ProfileId, sessionId: SessionId): Outcome<ConversationRef?> {
        calls += "widget:${profileId.raw}"
        return Outcome.Ok(null)
    }

    override suspend fun verifyQuotaToken(profileId: ProfileId?, token: String): Outcome<Boolean> {
        calls += "quota"
        return Outcome.Ok(true)
    }
}

/** The real router (S2-ENTRY) over fakes, on the test scheduler. */
internal class RouterRig(private val scope: TestScope, world: EntryWorld = readyWorld()) {
    val world = FakeWorldSource(world)
    val effects = RecordingEffects()
    val router =
        DefaultEntryRouter(this.world, effects, FixedClock, scope.backgroundScope, StandardTestDispatcher(scope.testScheduler))

    /** What the shell does after navigating: take the handoff and consume it. */
    fun ack(): EntryHandoff? {
        scope.runCurrent()
        return router.pending.value?.also {
            router.consume(it.id)
            scope.runCurrent()
        }
    }
}
