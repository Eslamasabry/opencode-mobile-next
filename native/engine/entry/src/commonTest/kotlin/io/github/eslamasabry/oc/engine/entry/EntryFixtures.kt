package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal val P1 = ProfileId("p1")
internal val P2 = ProfileId("p2")
internal val S1 = SessionId("ses_1")
internal val T0: Instant = Instant.fromEpochMilliseconds(1_767_225_600_000)
internal val TOKEN = MonitorToken("a".repeat(64))

/** Open, one active saved server connected and loaded. */
internal fun readyWorld(
    links: Map<ProfileId, LinkState> = mapOf(P1 to LinkState.Ready),
    active: ProfileId? = P1,
    saved: Set<ProfileId> = setOf(P1, P2),
): EntryWorld =
    EntryWorld(
        bootstrap = BootstrapPhase.Ready,
        gateOpen = true,
        activeProfileId = active,
        savedProfileIds = saved,
        links = links,
    )

internal class FakeWorldSource(initial: EntryWorld = EntryWorld()) : EntryWorldSource {
    override val world: MutableStateFlow<EntryWorld> = MutableStateFlow(initial)
}

internal class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
    override fun now(): Instant = T0 + scheduler.currentTime.milliseconds
}

/** Records every server-touching call: the untrusted-arrival proofs assert this stays empty. */
internal class FakeEffects : EntryEffects {
    val calls = mutableListOf<String>()
    var createResult: Outcome<ConversationRef> = Outcome.Ok(ConversationRef(P1, SessionId("ses_new")))
    var resolveResult: (ConversationRef) -> Outcome<ConversationRef> = { Outcome.Ok(it) }
    var seedResult: Outcome<Unit> = Outcome.Ok(Unit)
    var connectResult: Outcome<Unit> = Outcome.Ok(Unit)
    var quotaBelongs = true
    var widgetResult: ConversationRef? = null
    var onCreate: suspend () -> Unit = {}

    val gatewayCalls: List<String> get() = calls.filter { it.startsWith("connect") || it.startsWith("create") }

    override suspend fun connect(profileId: ProfileId): Outcome<Unit> {
        calls += "connect:${profileId.raw}"
        return connectResult
    }

    override suspend fun resolveRef(ref: ConversationRef): Outcome<ConversationRef> {
        calls += "resolve:${ref.sessionId.raw}"
        return resolveResult(ref)
    }

    override suspend fun createSession(profileId: ProfileId): Outcome<ConversationRef> {
        calls += "create:${profileId.raw}"
        onCreate()
        return createResult
    }

    override suspend fun seedDraft(ref: ConversationRef, text: String): Outcome<Unit> {
        calls += "seed:${ref.sessionId.raw}:${text.length}"
        return seedResult
    }

    override suspend fun resolveMonitoredRequest(
        token: MonitorToken,
        profileId: ProfileId,
        sessionId: SessionId,
    ): Outcome<ConversationRef> {
        calls += "monitor:${profileId.raw}"
        return if (token == TOKEN) Outcome.Ok(ConversationRef(profileId, sessionId)) else Outcome.Failed(Failure.Unexpected())
    }

    override suspend fun resolveWidgetConversation(profileId: ProfileId, sessionId: SessionId): Outcome<ConversationRef?> {
        calls += "widget:${profileId.raw}"
        return Outcome.Ok(widgetResult)
    }

    override suspend fun verifyQuotaToken(profileId: ProfileId?, token: String): Outcome<Boolean> {
        calls += "quota"
        return Outcome.Ok(quotaBelongs)
    }
}
