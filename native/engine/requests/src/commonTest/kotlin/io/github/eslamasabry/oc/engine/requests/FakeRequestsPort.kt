package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.model.QuestionChoice
import io.github.eslamasabry.oc.core.model.QuestionPrompt
import io.github.eslamasabry.oc.core.testing.FakeGatewayEvents
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A scripted connection: records every call in [calls], answers lists from [permissions] / [questions], fails the
 * next reply with [failNextReply], and holds replies at [replyGate] while it is set (to make two callers overlap).
 */
internal class FakeRequestsPort : RequestsPort {
    override val events: FakeGatewayEvents = FakeGatewayEvents()
    override val capabilities: MutableStateFlow<ServerCapabilities> = MutableStateFlow(ServerCapabilities.Unknown)
    val calls: MutableList<String> = mutableListOf()
    var permissions: List<PermissionRequest> = emptyList()
    var questions: List<PendingQuestion> = emptyList()
    var failNextReply: Failure? = null
    var failNextList: Failure? = null
    var replyGate: CompletableDeferred<Unit>? = null
    var listGate: CompletableDeferred<Unit>? = null

    /** Runs when a reply reaches the wire, before it answers (a settle racing the write). */
    var onReply: (() -> Unit)? = null

    override suspend fun awaitActionTransport(): Outcome<Unit> {
        calls += "wake"
        return Unit.ok()
    }

    override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> {
        val snapshot = permissions.toImmutableList()
        calls += "pendingPermissions"
        listGate?.await()
        failNextList?.let {
            failNextList = null
            return it.failed()
        }
        return snapshot.ok()
    }

    override suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> {
        calls += "pendingQuestions"
        return questions.toImmutableList().ok()
    }

    override suspend fun replyPermission(session: SessionId, request: RequestId, reply: PermissionReply, note: String?): Outcome<Unit> =
        reply("reply ${request.raw} $reply${note?.let { " note=$it" } ?: ""}")

    override suspend fun answerQuestion(
        session: SessionId,
        request: RequestId,
        answers: ImmutableList<ImmutableList<String>>,
    ): Outcome<Unit> = reply("answer ${request.raw} $answers")

    override suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit> = reply("reject ${request.raw}")

    private suspend fun reply(call: String): Outcome<Unit> {
        calls += call
        replyGate?.await()
        onReply?.invoke()
        failNextReply?.let {
            failNextReply = null
            return it.failed()
        }
        return Unit.ok()
    }

    /** Only the reply calls, in order. */
    val replies: List<String> get() = calls.filter { it.startsWith("reply") || it.startsWith("answer") || it.startsWith("reject") }
}

internal val S1 = SessionId("ses_1")
internal val S2 = SessionId("ses_2")

internal fun permission(
    id: String,
    session: SessionId = S1,
    permission: String = "shell",
    patterns: List<String> = listOf("npm install"),
    always: List<String> = listOf("npm install *"),
    metadata: Map<String, String> = emptyMap(),
    canAlwaysAllow: Boolean = true,
    callId: String? = null,
): PermissionRequest =
    PermissionRequest(
        id = RequestId(id),
        sessionId = session,
        permission = permission,
        patterns = patterns.toImmutableList(),
        metadata = JsonObject(metadata.mapValues { JsonPrimitive(it.value) }),
        always = always.toImmutableList(),
        tool = callId?.let { PermissionTool(MessageId("msg_1"), it) },
        canAlwaysAllow = canAlwaysAllow,
        message = null,
    )

internal fun question(
    id: String,
    session: SessionId = S1,
    prompts: Int = 1,
    multiple: Boolean = false,
    choices: List<String> = listOf("Yes", "No"),
): PendingQuestion =
    PendingQuestion(
        id = RequestId(id),
        sessionId = session,
        prompts =
            List(prompts) { i ->
                QuestionPrompt(
                    title = "Deploy",
                    question = "Ship it now? ($i)",
                    multiple = multiple,
                    custom = true,
                    optional = false,
                    choices = choices.map { QuestionChoice(it, "") }.toImmutableList(),
                    markdown = false,
                )
            }.toImmutableList(),
    )

internal fun answers(vararg labels: String): ImmutableList<ImmutableList<String>> = persistentListOf(labels.toList().toImmutableList())
