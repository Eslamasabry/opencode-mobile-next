package io.github.eslamasabry.oc.engine.review

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.StagedRevertGateway
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionRevert
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val S = SessionId("ses_1")

internal fun json(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.associate { (k, v) -> k to element(v) })

private fun element(v: Any?): JsonElement =
    when (v) {
        null -> kotlinx.serialization.json.JsonNull
        is JsonElement -> v
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is List<*> -> JsonArray(v.map(::element))
        else -> error("unsupported $v")
    }

internal fun prompt(id: String, at: Long, text: String = "do it", synthetic: Boolean = false): MessageWithParts =
    MessageWithParts(
        MessageInfo(id, S.raw, Role.User, time = MsgTime(at, at)),
        persistentListOf(Part("p_$id", id, S.raw, PartType.Text, "text", text = text, synthetic = synthetic)),
    )

internal fun reply(id: String, at: Long, vararg parts: Part): MessageWithParts =
    MessageWithParts(MessageInfo(id, S.raw, Role.Assistant, time = MsgTime(at, at)), parts.toList().toImmutableList())

internal fun tool(
    name: String,
    input: JsonObject = JsonObject(emptyMap()),
    metadata: JsonObject? = null,
    status: ToolStatus = ToolStatus.Completed,
    id: String = "t_${counter++}",
): Part =
    Part(id, "m", S.raw, PartType.Tool, "tool", toolName = name, tool = ToolState(status, input = input, metadata = metadata, completedAtMs = if (status == ToolStatus.Completed) 1 else null))

private var counter = 0

internal fun edit(path: String, added: Int? = null, removed: Int? = null, before: String? = null, after: String? = null, diff: String? = null, status: ToolStatus = ToolStatus.Completed): Part =
    tool(
        "edit",
        json("filePath" to path),
        json(
            "filediff" to json("file" to path, "before" to before, "after" to after, "additions" to added, "deletions" to removed),
            "diff" to diff,
        ),
        status,
    )

/** A scripted OpenCode 2 style gateway: history pages, a session, statuses and the staged revert facet. */
internal class StagedFake(
    val base: FakeServerGateway = FakeServerGateway(listOf(Session(S))),
) : ServerGateway by base, StagedRevertGateway {
    /** Newest-first pages. */
    var pages: List<List<MessageWithParts>> = listOf(emptyList())
    var staged: SessionRevert? = null
    var stageResult: (MessageId, Boolean) -> Outcome<SessionRevert> = { m, _ -> SessionRevert(m, null, "snap", persistentListOf(FileDiff("a.kt", additions = 1, deletions = 0))).ok() }
    var clearResult: () -> Outcome<Unit> = { Unit.ok() }
    var commitResult: () -> Outcome<Unit> = { Unit.ok() }
    val calls = mutableListOf<String>()
    var onStatuses: () -> Unit = {}

    override suspend fun session(id: SessionId): Outcome<Session> {
        calls += "session"
        return Session(id, reverted = staged != null, stagedRevert = staged).ok()
    }

    override suspend fun sessionStatuses() =
        run { calls += "statuses"; onStatuses(); base.sessionStatuses() }

    override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> {
        val index = cursor?.toInt() ?: 0
        calls += "page $index"
        val items = pages.getOrElse(index) { emptyList() }
        return ServerPage(items.toImmutableList(), if (index + 1 < pages.size) "${index + 1}" else null).ok()
    }

    override suspend fun sessionRevertPrompt(session: SessionId, message: MessageId): Outcome<String?> =
        pages.flatten().firstOrNull { it.info.id == message.raw }?.takeIf { it.info.role == Role.User }?.let(TurnChanges::promptText).ok()

    override suspend fun stageSessionRevert(session: SessionId, message: MessageId, applyFiles: Boolean): Outcome<SessionRevert> {
        calls += "stage ${message.raw} $applyFiles"
        return stageResult(message, applyFiles).also { if (it is Outcome.Ok) staged = it.value }
    }

    override suspend fun clearSessionRevert(session: SessionId): Outcome<Unit> {
        calls += "clear"
        return clearResult().also { if (it is Outcome.Ok) staged = null }
    }

    override suspend fun commitSessionRevert(session: SessionId): Outcome<Unit> {
        calls += "commit"
        return commitResult().also { if (it is Outcome.Ok) staged = null }
    }
}

internal fun failed(f: Failure): Outcome<Nothing> = f.failed()

internal fun <T> ImmutableList<T>.paths(): List<String> = map { (it as ChangedFile).path }
