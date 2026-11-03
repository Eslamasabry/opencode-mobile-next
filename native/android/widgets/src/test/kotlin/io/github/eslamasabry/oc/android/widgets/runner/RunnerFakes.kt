@file:OptIn(io.github.eslamasabry.oc.core.common.RedactorApi::class)

package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred

/** Test-only stand-in for one cold connection (Flutter `_Gateway` of widget_action_runner_test.dart). */
internal class FakeTransport : WidgetRunTransport {
    val mutations = mutableListOf<String>()
    var closed = false
    var failAnswer = false
    var healthy = true
    var failHealth = false
    var healthReads = 0
    var permissionReads = 0
    var settlementReads = 0
    var statusReads = 0
    var settledStatuses = mutableListOf<String?>("idle")
    var failStatus = false
    var statusGate: CompletableDeferred<Unit>? = null
    var afterStatus: (() -> Unit)? = null
    var healthGate: CompletableDeferred<Unit>? = null
    var afterHealth: (() -> Unit)? = null
    var marker = "user-1"
    var status: String? = "busy"
    var assistantReply: String? = null
    var pending = mutableListOf(WidgetPendingAsk("permission", "session"))
    var readGate: CompletableDeferred<Unit>? = null
    var afterRead: (() -> Unit)? = null

    override suspend fun health(): Outcome<Boolean> {
        healthReads++
        healthGate?.await()
        afterHealth?.invoke()
        if (failHealth) return Outcome.Failed(Failure.Unexpected(Redacted("private health failure")))
        return Outcome.Ok(healthy)
    }

    override suspend fun pendingPermissions(): Outcome<List<WidgetPendingAsk>> {
        permissionReads++
        readGate?.await()
        afterRead?.invoke()
        return Outcome.Ok(pending.toList())
    }

    override suspend fun messages(session: String): Outcome<List<MessageWithParts>> =
        Outcome.Ok(
            listOfNotNull(
                message(marker, session, Role.User, 1),
                assistantReply?.let { message("assistant", session, Role.Assistant, 2, it) },
            )
        )

    override suspend fun sessionStatuses(): Outcome<Map<String, String>> {
        statusReads++
        if (!settledOnly) {
            // The status runner's single read: gated and observed.
            statusGate?.await()
            afterStatus?.invoke()
            return Outcome.Ok(status?.let { mapOf("session" to it) } ?: emptyMap())
        }
        if (mutations.isEmpty()) return Outcome.Ok(status?.let { mapOf("session" to it) } ?: emptyMap())
        settlementReads++
        statusGate?.await()
        afterStatus?.invoke()
        if (failStatus) return Outcome.Failed(Failure.Unexpected(Redacted("private status failure")))
        val value = if (settledStatuses.size > 1) settledStatuses.removeAt(0) else settledStatuses.single()
        return Outcome.Ok(value?.let { mapOf("session" to it) } ?: emptyMap())
    }

    /** Action runner tests gate only the settlement read; status runner tests gate the only read. */
    var settledOnly = true

    override suspend fun answerPermission(session: String, request: String, allowOnce: Boolean): Outcome<Unit> {
        mutations.add("$request:${if (allowOnce) "once" else "reject"}:$session")
        return if (failAnswer) Outcome.Failed(Failure.Unexpected(Redacted("credential or remote payload must never escape")))
        else Outcome.Ok(Unit)
    }

    override suspend fun stop(session: String): Outcome<Unit> {
        mutations.add("stop:$session")
        return Outcome.Ok(Unit)
    }

    override suspend fun close() {
        closed = true
    }
}

internal fun message(id: String, session: String, role: Role, created: Long, text: String? = null, type: String? = null) =
    MessageWithParts(
        MessageInfo(id = id, sessionId = session, role = role, time = MsgTime(created, null)),
        listOfNotNull(
                text?.let { Part("p-$id", id, session, PartType.Text, "text", text = it) },
                type?.let { Part("p-$id", id, session, PartType.Notice, it) },
            )
            .toImmutableList()
            .let { if (it.isEmpty()) persistentListOf() else it },
    )
