package io.github.eslamasabry.oc.protocol.oc2.fixtures

import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Event
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Phase
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2AgentInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2AssistantContent
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Command
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ContentTime
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ErrorEnvelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormState
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FsEntry
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2InboxItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Location
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Message
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelRef
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Page
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2PermissionRequest
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ProviderInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Session
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Skill
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2StructuredError
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Tokens
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolResultItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolState
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2UsageStatistics
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The Kotlin half of native/fixtures/oc2/tool/dart_variants.dart: the same neutral descriptor, built from the Kotlin
 * DTOs. Keep the two files in step; a field added to one must be added to the other.
 */
internal object ParityDescriber {
    // ------------------------------------------------------------ building blocks

    private fun j(value: Any?): JsonElement? =
        when (value) {
            null -> null
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map { j(it) ?: JsonNull })
            else -> error("cannot describe ${value::class}")
        }

    /** Like the Dart `clean({...})`: null values are left out. */
    private fun o(vararg pairs: Pair<String, Any?>): JsonObject =
        JsonObject(pairs.mapNotNull { (k, v) -> j(v)?.let { k to it } }.toMap())

    private fun phase(p: Oc2Phase): String = p.name.replaceFirstChar { it.lowercase() }

    private fun ref(r: Oc2ModelRef?): String? = r?.toString()

    private fun tokens(t: Oc2Tokens): JsonObject =
        o("input" to t.input, "output" to t.output, "reasoning" to t.reasoning, "cacheRead" to t.cacheRead,
            "cacheWrite" to t.cacheWrite)

    private fun error(e: Oc2StructuredError?): JsonObject? =
        e?.let { o("type" to it.type, "message" to it.message, "status" to it.status) }

    fun location(l: Oc2Location?): JsonObject? =
        l?.let {
            o("directory" to it.directory, "workspaceID" to it.workspaceID, "projectID" to it.project?.id,
                "projectDirectory" to it.project?.directory, "canonical" to it.project?.canonical)
        }

    fun <T> page(p: Oc2Page<T>, describe: (T) -> JsonElement?): JsonObject =
        o("data" to p.data.map(describe), "previous" to p.previousCursor, "next" to p.nextCursor)

    // ------------------------------------------------------------ sessions and messages

    fun session(s: Oc2Session?): JsonObject? =
        s?.let {
            o(
                "id" to it.id,
                "parentID" to it.parentID,
                "fork" to it.fork?.let { f ->
                    o("sessionID" to f.sessionID,
                        "boundary" to f.boundary?.let { b -> o("type" to b.type, "messageID" to b.messageID) })
                },
                "projectID" to it.projectID,
                "agent" to it.agent,
                "model" to ref(it.model),
                "cost" to it.cost,
                "tokens" to tokens(it.tokens),
                "outcome" to it.outcome?.name?.replaceFirstChar { c -> c.lowercase() },
                "time" to o("created" to it.time.created, "updated" to it.time.updated, "idle" to it.time.idle,
                    "viewed" to it.time.viewed, "archived" to it.time.archived),
                "title" to it.title,
                "location" to location(it.location),
                "subpath" to it.subpath,
                "reverted" to it.reverted,
                "revert" to it.revert?.let { r ->
                    o("messageID" to r.messageID, "partID" to r.partID, "snapshot" to r.snapshot,
                        "files" to r.files?.size)
                },
                "metadata" to it.metadata,
                "archived" to it.archived,
            )
        }

    private fun resultItem(i: Oc2ToolResultItem): JsonObject =
        when (i) {
            is Oc2ToolResultItem.Text -> o("v" to "text", "text" to i.text)
            is Oc2ToolResultItem.File -> o("v" to "file", "uri" to i.uri, "mime" to i.mime, "name" to i.name)
            is Oc2ToolResultItem.Unknown -> o("v" to "unknown")
        }

    private fun toolState(s: Oc2ToolState): JsonObject =
        when (s) {
            is Oc2ToolState.Streaming -> o("v" to "streaming", "rawInput" to s.rawInput)
            is Oc2ToolState.Running -> o("v" to "running", "input" to s.input, "metadata" to s.metadata)
            is Oc2ToolState.Completed ->
                o("v" to "completed", "input" to s.input, "items" to s.content.map(::resultItem),
                    "metadata" to s.metadata)
            is Oc2ToolState.Error ->
                o("v" to "error", "input" to s.input, "error" to error(s.error),
                    "items" to s.content.map(::resultItem), "metadata" to s.metadata)
            is Oc2ToolState.Unknown -> o("v" to "unknown")
        }

    private fun contentTime(t: Oc2ContentTime): JsonObject =
        o("created" to t.created, "ran" to t.ran, "completed" to t.completed, "pruned" to t.pruned)

    private fun content(c: Oc2AssistantContent): JsonObject =
        when (c) {
            is Oc2AssistantContent.Text -> o("v" to "text", "text" to c.text, "state" to c.state)
            is Oc2AssistantContent.Reasoning ->
                o("v" to "reasoning", "text" to c.text, "state" to c.state, "time" to contentTime(c.time))
            is Oc2AssistantContent.Tool ->
                o("v" to "tool", "id" to c.id, "name" to c.name, "executed" to c.executed,
                    "state" to toolState(c.state), "time" to contentTime(c.time))
            is Oc2AssistantContent.Unknown -> o("v" to "unknown", "type" to c.type)
        }

    fun message(m: Oc2Message?): JsonObject? {
        if (m == null) return null
        val base = listOf(
            "id" to m.id,
            "time" to o("created" to m.time.created, "streamed" to m.time.streamed, "completed" to m.time.completed),
            "metadata" to m.metadata,
        )
        val variant: List<Pair<String, Any?>> =
            when (m) {
                is Oc2Message.User -> listOf(
                    "v" to "User", "text" to m.text,
                    "files" to m.files.map {
                        o("data" to it.data, "mime" to it.mime, "sourceType" to it.sourceType,
                            "sourceUri" to it.sourceUri, "name" to it.name, "description" to it.description)
                    },
                )
                is Oc2Message.Assistant -> listOf(
                    "v" to "Assistant", "agent" to m.agent, "model" to ref(m.model),
                    "content" to m.content.map(::content), "finish" to m.finish, "rawFinish" to m.rawFinish,
                    "cost" to m.cost, "tokens" to m.tokens?.let(::tokens), "error" to error(m.error),
                    "completed" to m.completed, "text" to m.text,
                )
                is Oc2Message.Synthetic -> listOf("v" to "Synthetic", "text" to m.text, "description" to m.description)
                is Oc2Message.System -> listOf("v" to "System", "text" to m.text, "description" to m.description)
                is Oc2Message.Skill -> listOf("v" to "Skill", "skill" to m.skill, "name" to m.name, "text" to m.text)
                is Oc2Message.Shell -> listOf(
                    "v" to "Shell", "shellID" to m.shellID, "command" to m.command, "status" to m.status,
                    "exit" to m.exit, "output" to m.outputText, "outputTruncated" to m.outputTruncated,
                )
                is Oc2Message.AgentSwitched -> listOf("v" to "AgentSwitched", "agent" to m.agent, "previous" to m.previous)
                is Oc2Message.ModelSwitched ->
                    listOf("v" to "ModelSwitched", "model" to ref(m.model), "previous" to ref(m.previous))
                is Oc2Message.LocationSwitched -> listOf(
                    "v" to "LocationSwitched", "location" to location(m.location), "projectID" to m.projectID,
                    "subpath" to m.subpath,
                )
                is Oc2Message.Compaction -> listOf(
                    "v" to "Compaction", "status" to m.status, "reason" to m.reason, "summary" to m.summary,
                    "error" to error(m.error),
                )
                is Oc2Message.Unknown -> listOf("v" to "Unknown", "type" to m.type)
            }
        return o(*(base + variant).toTypedArray())
    }

    // ------------------------------------------------------------ inbox, forms, permissions

    fun inbox(i: Oc2InboxItem?): JsonObject? =
        i?.let {
            o("id" to it.id, "sessionID" to it.sessionID, "timeCreated" to it.timeCreated, "type" to it.type,
                "payload" to it.payload, "delivery" to it.delivery?.wire, "promptText" to it.promptText)
        }

    fun form(f: Oc2FormInfo?): JsonObject? =
        f?.let {
            o(
                "id" to it.id, "sessionID" to it.sessionID, "title" to it.title, "metadata" to it.metadata,
                "fields" to it.fields.map { x ->
                    o(
                        "key" to x.key, "type" to x.type.wire, "title" to x.title, "description" to x.description,
                        "required" to x.required,
                        "when" to x.`when`.map { w -> o("key" to w.key, "op" to w.op, "value" to w.value) },
                        "format" to x.format, "minLength" to x.minLength, "maxLength" to x.maxLength,
                        "pattern" to x.pattern, "placeholder" to x.placeholder, "default" to x.defaultValue,
                        "options" to x.options.map { op ->
                            o("value" to op.value, "label" to op.label, "description" to op.description)
                        },
                        "custom" to x.custom, "minimum" to x.minimum, "maximum" to x.maximum,
                        "minItems" to x.minItems, "maxItems" to x.maxItems, "url" to x.url,
                    )
                },
            )
        }

    fun formState(s: Oc2FormState): JsonObject = o("status" to s.status.wire, "answer" to s.answer)

    private fun permissionRequest(r: Oc2PermissionRequest): JsonObject =
        o(
            "id" to r.id, "sessionID" to r.sessionID, "action" to r.action, "resources" to r.resources,
            "save" to r.save, "metadata" to r.metadata,
            "source" to r.source?.let { o("type" to it.type, "messageID" to it.messageID, "id" to it.id) },
            "message" to r.message,
        )

    // ------------------------------------------------------------ catalog, fs, errors, usage

    fun modelInfo(m: Oc2ModelInfo?): JsonObject? =
        m?.let {
            o(
                "id" to it.id, "modelID" to it.modelID, "providerID" to it.providerID, "family" to it.family,
                "name" to it.name, "status" to it.status, "enabled" to it.enabled,
                "tools" to it.capabilities.tools, "input" to it.capabilities.input,
                "output" to it.capabilities.output, "variants" to it.variants,
                "limit" to o("context" to it.limit.context, "input" to it.limit.input, "output" to it.limit.output),
                "released" to it.released,
                "cost" to it.cost.map { c ->
                    o("input" to c.input, "output" to c.output, "cacheRead" to c.cacheRead,
                        "cacheWrite" to c.cacheWrite, "tierSize" to c.tierSize)
                },
                "ref" to it.ref().toString(),
            )
        }

    fun provider(p: Oc2ProviderInfo?): JsonObject? =
        p?.let {
            o("id" to it.id, "integrationID" to it.integrationID, "name" to it.name, "activation" to it.activation,
                "package" to it.packageName)
        }

    fun agent(a: Oc2AgentInfo?): JsonObject? =
        a?.let {
            o(
                "id" to it.id, "name" to it.name, "description" to it.description, "mode" to it.mode,
                "hidden" to it.hidden, "color" to it.color, "model" to ref(it.model), "selectable" to it.selectable,
                "permissions" to it.permissions.map { p ->
                    o("action" to p.action, "resource" to p.resource, "effect" to p.effect?.wire)
                },
            )
        }

    fun command(c: Oc2Command?): JsonObject? = c?.let { o("name" to it.name, "description" to it.description) }

    fun skill(s: Oc2Skill?): JsonObject? =
        s?.let {
            o("id" to it.id, "name" to it.name, "description" to it.description, "location" to it.location,
                "contentLength" to it.content?.length, "slash" to it.slash)
        }

    fun fsEntry(e: Oc2FsEntry?): JsonObject? =
        e?.let { o("path" to it.path, "type" to it.type, "isDirectory" to it.isDirectory) }

    fun errorEnvelope(e: Oc2ErrorEnvelope): JsonObject = o("tag" to e.tag, "message" to e.message)

    fun usage(u: Oc2UsageStatistics): JsonObject {
        fun tok(t: Oc2UsageStatistics.Tokens) =
            o("input" to t.input, "output" to t.output, "reasoning" to t.reasoning, "cacheRead" to t.cacheRead,
                "cacheWrite" to t.cacheWrite)
        return o(
            "from" to u.from, "to" to u.to, "sessions" to u.sessions, "subagents" to u.subagents,
            "prompts" to u.prompts, "steps" to u.steps, "activeDays" to u.activeDays, "streak" to u.streak,
            "cost" to u.cost, "tokens" to tok(u.tokens),
            "tools" to u.tools?.let {
                o("calls" to it.calls, "succeeded" to it.succeeded, "failed" to it.failed,
                    "unfinished" to it.unfinished)
            },
            "models" to u.models.map {
                o("providerID" to it.providerID, "modelID" to it.modelID, "variant" to it.variant,
                    "steps" to it.steps, "tokens" to tok(it.tokens), "cost" to it.cost)
            },
            "activity" to u.activity.map { o("date" to it.date, "steps" to it.steps) },
        )
    }

    // ------------------------------------------------------------ events

    fun envelope(e: Oc2Envelope): JsonObject =
        o(
            "type" to e.type, "id" to e.id, "created" to e.created, "location" to location(e.location),
            "metadata" to e.metadata,
            "durable" to e.durable?.let { o("aggregateID" to it.aggregateID, "seq" to it.seq, "version" to it.version) },
            "event" to event(e.event),
        )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun event(e: Oc2Event): JsonObject =
        when (e) {
            is Oc2Event.ServerConnected -> o("v" to "ServerConnected")
            is Oc2Event.ManagedShell -> o("v" to "ManagedShell", "type" to e.type, "data" to e.data)
            is Oc2Event.LogSynced -> o("v" to "LogSynced", "aggregateID" to e.aggregateID, "seq" to e.seq)
            is Oc2Event.SessionCreated -> o(
                "v" to "SessionCreated", "sessionID" to e.sessionID, "projectID" to e.projectID,
                "parentID" to e.parentID, "slug" to e.slug, "title" to e.title, "agent" to e.agent,
                "model" to ref(e.model), "location" to location(e.location), "subpath" to e.subpath,
                "version" to e.version,
            )
            is Oc2Event.SessionRenamed -> o("v" to "SessionRenamed", "sessionID" to e.sessionID, "title" to e.title)
            is Oc2Event.SessionDeleted -> o("v" to "SessionDeleted", "sessionID" to e.sessionID)
            is Oc2Event.SessionMoved -> o(
                "v" to "SessionMoved", "sessionID" to e.sessionID, "location" to location(e.location),
                "projectID" to e.projectID, "subpath" to e.subpath,
            )
            is Oc2Event.SessionForked -> o(
                "v" to "SessionForked", "sessionID" to e.sessionID, "parentID" to e.parentID,
                "boundary" to e.boundary?.let { o("type" to it.type, "messageID" to it.messageID) },
            )
            is Oc2Event.SessionViewed -> o("v" to "SessionViewed", "sessionID" to e.sessionID, "idle" to e.idle)
            is Oc2Event.SessionAgentSelected ->
                o("v" to "SessionAgentSelected", "sessionID" to e.sessionID, "agent" to e.agent, "previous" to e.previous)
            is Oc2Event.SessionModelSelected ->
                o("v" to "SessionModelSelected", "sessionID" to e.sessionID, "model" to ref(e.model))
            is Oc2Event.SessionUsageUpdated ->
                o("v" to "SessionUsageUpdated", "sessionID" to e.sessionID, "cost" to e.cost, "tokens" to tokens(e.tokens))
            is Oc2Event.SessionExecution -> o(
                "v" to "SessionExecution", "phase" to phase(e.phase), "sessionID" to e.sessionID,
                "error" to error(e.error), "reason" to e.reason,
            )
            is Oc2Event.SessionInbox -> o(
                "v" to "SessionInbox", "phase" to phase(e.phase), "sessionID" to e.sessionID, "inboxID" to e.inboxID,
                "item" to inbox(e.item), "delivery" to e.delivery?.wire,
            )
            is Oc2Event.SessionInstructionsUpdated ->
                o("v" to "SessionInstructionsUpdated", "sessionID" to e.sessionID, "delta" to e.delta, "text" to e.text)
            is Oc2Event.SessionSynthetic ->
                o("v" to "SessionSynthetic", "sessionID" to e.sessionID, "text" to e.text, "description" to e.description)
            is Oc2Event.SessionSkillActivated -> o(
                "v" to "SessionSkillActivated", "sessionID" to e.sessionID, "id" to e.id, "name" to e.name,
                "text" to e.text,
            )
            is Oc2Event.SessionShell -> o(
                "v" to "SessionShell", "phase" to phase(e.phase), "sessionID" to e.sessionID, "shell" to e.shell,
                "output" to e.output,
            )
            is Oc2Event.SessionStep -> o(
                "v" to "SessionStep", "phase" to phase(e.phase), "sessionID" to e.sessionID,
                "assistantMessageID" to e.assistantMessageID, "agent" to e.agent, "model" to ref(e.model),
                "finish" to e.finish, "error" to error(e.error), "cost" to e.cost,
                "tokens" to e.tokens?.let(::tokens), "snapshot" to e.snapshot,
            )
            is Oc2Event.SessionText -> o(
                "v" to "SessionText", "phase" to phase(e.phase), "sessionID" to e.sessionID,
                "assistantMessageID" to e.assistantMessageID, "ordinal" to e.ordinal, "delta" to e.delta,
                "text" to e.text,
            )
            is Oc2Event.SessionReasoning -> o(
                "v" to "SessionReasoning", "phase" to phase(e.phase), "sessionID" to e.sessionID,
                "assistantMessageID" to e.assistantMessageID, "ordinal" to e.ordinal, "delta" to e.delta,
                "text" to e.text,
            )
            is Oc2Event.SessionToolInput -> o(
                "v" to "SessionToolInput", "phase" to phase(e.phase), "sessionID" to e.sessionID,
                "assistantMessageID" to e.assistantMessageID, "callID" to e.callID, "name" to e.name,
                "delta" to e.delta, "text" to e.text,
            )
            is Oc2Event.SessionToolCalled -> o(
                "v" to "SessionToolCalled", "sessionID" to e.sessionID, "assistantMessageID" to e.assistantMessageID,
                "callID" to e.callID, "input" to e.input, "executed" to e.executed,
            )
            is Oc2Event.SessionToolProgress -> o(
                "v" to "SessionToolProgress", "sessionID" to e.sessionID,
                "assistantMessageID" to e.assistantMessageID, "callID" to e.callID, "metadata" to e.metadata,
            )
            is Oc2Event.SessionToolResult -> o(
                "v" to "SessionToolResult", "phase" to phase(e.phase), "sessionID" to e.sessionID,
                "assistantMessageID" to e.assistantMessageID, "callID" to e.callID,
                "items" to e.content.map(::resultItem), "error" to error(e.error), "metadata" to e.metadata,
                "executed" to e.executed,
            )
            is Oc2Event.SessionRetryScheduled -> o(
                "v" to "SessionRetryScheduled", "sessionID" to e.sessionID,
                "assistantMessageID" to e.assistantMessageID, "attempt" to e.attempt, "at" to e.at,
                "error" to error(e.error),
            )
            is Oc2Event.SessionCompaction -> o(
                "v" to "SessionCompaction", "phase" to phase(e.phase), "sessionID" to e.sessionID,
                "reason" to e.reason, "text" to e.text, "error" to error(e.error),
            )
            is Oc2Event.SessionRevert -> o(
                "v" to "SessionRevert", "phase" to phase(e.phase), "sessionID" to e.sessionID, "revert" to e.revert,
                "toMessageID" to e.toMessageID,
            )
            is Oc2Event.SessionMessageContentUpdated -> o(
                "v" to "SessionMessageContentUpdated", "sessionID" to e.sessionID, "messageID" to e.messageID,
                "content" to e.content.map(::content),
            )
            is Oc2Event.SessionStatus -> o(
                "v" to "SessionStatus", "sessionID" to e.sessionID,
                "status" to e.status.name.replaceFirstChar { it.lowercase() }, "attempt" to e.attempt,
                "message" to e.message, "next" to e.next,
            )
            is Oc2Event.SessionCompacted -> o("v" to "SessionCompacted", "sessionID" to e.sessionID)
            is Oc2Event.PermissionAsked -> o("v" to "PermissionAsked", "request" to permissionRequest(e.request))
            is Oc2Event.PermissionReplied ->
                o("v" to "PermissionReplied", "sessionID" to e.sessionID, "requestID" to e.requestID, "reply" to e.reply)
            is Oc2Event.FormCreated -> o("v" to "FormCreated", "form" to form(e.form))
            is Oc2Event.FormReplied ->
                o("v" to "FormReplied", "id" to e.id, "sessionID" to e.sessionID, "answer" to e.answer)
            is Oc2Event.FormCancelled -> o("v" to "FormCancelled", "id" to e.id, "sessionID" to e.sessionID)
            is Oc2Event.McpChanged -> o("v" to "McpChanged", "kind" to e.kind, "server" to e.server)
            is Oc2Event.VcsBranchUpdated -> o("v" to "VcsBranchUpdated", "branch" to e.branch)
            is Oc2Event.FilesystemChanged -> o("v" to "FilesystemChanged", "file" to e.file, "change" to e.change)
            is Oc2Event.TuiToast -> o(
                "v" to "TuiToast", "title" to e.title, "message" to e.message, "variant" to e.variant,
                "duration" to e.duration,
            )
            is Oc2Event.RefreshHint -> o("v" to "RefreshHint", "topic" to e.topic)
            // Plan 04 §7.5 keeps these ten types by name; the Dart client reads them as Unknown.
            is Oc2Event.PassThrough -> o("v" to "Unknown", "type" to e.type)
            is Oc2Event.Unknown -> o("v" to "Unknown", "type" to e.type)
        }
}
