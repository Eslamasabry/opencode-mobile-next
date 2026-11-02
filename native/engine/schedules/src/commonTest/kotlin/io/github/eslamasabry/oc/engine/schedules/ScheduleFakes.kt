package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone

internal const val P = "p1"
internal const val PROJECT = "/work/app"
internal const val T0 = 1_793_606_400_000L // 2026-11-02T08:00:00Z, a Monday
internal const val MIN = 60_000L
internal const val HOUR = 3_600_000L

internal class MemoryBackend(initial: Map<String, String> = emptyMap()) : ScheduleBackend {
    val values = LinkedHashMap(initial)
    var refuseWrites = false
    var writes = 0

    override suspend fun read(key: String): String? = values[key]

    override suspend fun compareAndSet(key: String, expected: String?, value: String?): Boolean {
        if (refuseWrites || values[key] != expected) return false
        writes++
        if (value == null) values.remove(key) else values[key] = value
        return true
    }
}

internal class Clock(var now: Long = T0) {
    fun ms(): Long = now
}

/** A gateway whose replies and statuses the test scripts; everything else is the core fake. */
internal class ScriptedGateway(val base: FakeServerGateway = FakeServerGateway()) : ServerGateway by base {
    var replies: List<MessageWithParts> = emptyList()
    var replyAfterPrompt: String? = "Done: three files changed\nmore"
    var directory: String? = PROJECT
    var title: String? = "Nightly check"

    init {
        base.capabilities.value = ServerCapabilities(scheduledPrompts = true)
    }

    val prompts get() = base.sentPrompts
    val calls get() = base.calls

    override suspend fun createSession(spec: NewSession): Outcome<Session> =
        base.createSession(spec).also {
            if (it is Outcome.Ok) {
                base.sessionList = (base.sessionList.dropLast(1) + it.value.copy(directory = directory, title = title)).toImmutableList()
            }
        }

    override suspend fun prompt(
        session: SessionId,
        prompt: io.github.eslamasabry.oc.core.model.OutgoingPrompt,
        commandId: String?,
    ): Outcome<io.github.eslamasabry.oc.core.model.PromptAdmission> {
        val result = base.prompt(session, prompt, commandId)
        val text = replyAfterPrompt
        if (result is Outcome.Ok && text != null) {
            replies = replies + reply("m${replies.size + 1}", session.raw, text)
        }
        return result
    }

    override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
        base.calls += "messagePage ${id.raw}"
        return ServerPage(replies.filter { it.info.sessionId == id.raw }.toImmutableList(), null).ok()
    }

    companion object {
        fun reply(id: String, session: String, text: String, done: Boolean = true): MessageWithParts =
            MessageWithParts(
                MessageInfo(id, session, Role.Assistant, time = MsgTime(T0, if (done) T0 else null)),
                persistentListOf(Part("$id-p", id, session, PartType.Text, "text", text = text)),
            )
    }
}

internal class FakeProfiles(var gateway: ScriptedGateway = ScriptedGateway()) : ScheduleProfiles {
    val fingerprints = mutableMapOf(P to "n1:server-a")
    var opens = 0
    var closes = 0
    var openFailure: io.github.eslamasabry.oc.core.common.Failure? = null

    override suspend fun ids(): List<String> = fingerprints.keys.toList()

    override suspend fun fingerprint(profileId: String): String? = fingerprints[profileId]

    override suspend fun open(profileId: String, projectPath: String, workspace: String?): Outcome<ScheduleConnection> {
        openFailure?.let { return Outcome.Failed(it) }
        opens++
        return ScheduleConnection(gateway) { closes++ }.ok()
    }
}

internal class FakeAlarms(var supported: Boolean = true) : ScheduleAlarms {
    val armed = mutableMapOf<String, List<ArmedEntry>>()
    val runNows = mutableListOf<Triple<String, String, Int>>()
    val cancelled = mutableListOf<String>()

    override fun status(): SchedulePlatform = SchedulePlatform(supported = supported, notificationsAllowed = true)

    override fun replace(profileId: String, entries: List<ArmedEntry>) {
        armed[profileId] = entries
    }

    override fun runNow(profileId: String, scheduleId: String, revision: Int) {
        runNows += Triple(profileId, scheduleId, revision)
    }

    override fun cancelProfile(profileId: String) {
        cancelled += profileId
        armed.remove(profileId)
    }
}

internal class FakeNotices : ScheduleNotices {
    val events = mutableListOf<String>()
    val reports = mutableListOf<RunReport>()

    override fun started(entry: ArmedEntry) {
        events += "started ${entry.workName}"
    }

    override fun finished(entry: ArmedEntry, report: RunReport) {
        events += "finished ${entry.workName}"
        reports += report
    }

    override fun clear(entry: ArmedEntry) {
        events += "clear ${entry.workName}"
    }
}

internal class Harness(
    val clock: Clock = Clock(),
    val backend: MemoryBackend = MemoryBackend(),
    val profiles: FakeProfiles = FakeProfiles(),
    val alarms: FakeAlarms = FakeAlarms(),
    val notices: FakeNotices = FakeNotices(),
) {
    private var ids = 0
    val engine =
        ScheduleEngine(backend, profiles, alarms, notices, clock::ms, { TimeZone.UTC }, { "s${++ids}" }, pollMs = 1_000)
    val gateway get() = profiles.gateway

    fun draft(recurrence: Recurrence = Recurrence.daily(9, 0), sessionId: String? = null) =
        ScheduleDraft(prompt = "Check the nightly build", projectPath = PROJECT, recurrence = recurrence, sessionId = sessionId)

    suspend fun saved(recurrence: Recurrence = Recurrence.daily(9, 0), sessionId: String? = null): ScheduledPrompt =
        (engine.save(P, draft(recurrence, sessionId)) as Outcome.Ok).value

    fun armed(): List<ArmedEntry> = alarms.armed[P].orEmpty()
}

internal fun prompt(
    id: String = "a1",
    recurrence: Recurrence = Recurrence.daily(9, 0),
    next: Long? = T0 + HOUR,
    revision: Int = 1,
    enabled: Boolean = true,
) = ScheduledPrompt(
    id = id,
    profileId = P,
    profileFingerprint = "n1:server-a",
    prompt = "Check the nightly build",
    projectPath = PROJECT,
    recurrence = recurrence,
    createdAtMs = T0,
    nextRunAtMs = next,
    revision = revision,
    enabled = enabled,
)

internal fun ImmutableList<MessageWithParts>.ids() = map { it.info.id }
