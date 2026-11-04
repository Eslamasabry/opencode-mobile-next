package io.github.eslamasabry.oc.protocol.oc2.events

import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Delivery
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2InboxItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2PermissionRequest
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import io.github.eslamasabry.oc.protocol.oc2.wire.int
import io.github.eslamasabry.oc.protocol.oc2.wire.long
import io.github.eslamasabry.oc.protocol.oc2.wire.obj
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonObject

private typealias Decoder = (type: String, data: JsonObject) -> Oc2Event?

private val REFRESH_HINTS =
    listOf(
        "agent.updated",
        "command.updated",
        "config.updated",
        "skill.updated",
        "catalog.updated",
        "models-dev.refreshed",
        "integration.updated",
        "credential.updated",
        "credential.switched",
        "plugin.added",
        "plugin.updated",
        "reference.updated",
        "websearch.updated",
        "project.updated",
        "worktree.updated",
        "lsp.updated",
    )

/**
 * Event type string → decoder (plan 04 §7.5). Three lists:
 * - [typed]: exactly Flutter's set in `lib/api2/events.dart` (83 types);
 * - [passThrough]: kept by name with the raw payload (10 types);
 * - [ignored]: known types this client does not use; they decode to [Oc2Event.Unknown] with
 *   `ignored = true`. Anything else is a new type: [Oc2Event.Unknown]. [decode] never throws.
 */
public object Oc2EventRegistry {
    private fun <T : Oc2Event> serial(
        strategy: DeserializationStrategy<T>,
        then: (T) -> Oc2Event = { it },
    ): Decoder = { _, data -> Oc2Wire.decode(strategy, data)?.let(then) }

    private val decoders: Map<String, Decoder> = buildMap {
        put("server.connected") { _, _ -> Oc2Event.ServerConnected }
        for (t in listOf("shell.created", "shell.exited", "shell.deleted")) {
            put(t) { type, data -> Oc2Event.ManagedShell(type, data) }
        }
        put("log.synced", serial(Oc2Event.LogSynced.serializer()))
        put("session.created", serial(Oc2Event.SessionCreated.serializer()))
        put("session.renamed", serial(Oc2Event.SessionRenamed.serializer()))
        put("session.deleted", serial(Oc2Event.SessionDeleted.serializer()))
        put("session.moved", serial(Oc2Event.SessionMoved.serializer()))
        put("session.forked", serial(Oc2Event.SessionForked.serializer()))
        put("session.viewed", serial(Oc2Event.SessionViewed.serializer()))
        put("session.agent.selected", serial(Oc2Event.SessionAgentSelected.serializer()))
        put("session.model.selected", serial(Oc2Event.SessionModelSelected.serializer()))
        put("session.usage.updated", serial(Oc2Event.SessionUsageUpdated.serializer()))
        for ((suffix, phase) in
            listOf(
                "started" to Oc2Phase.Started,
                "succeeded" to Oc2Phase.Succeeded,
                "failed" to Oc2Phase.Failed,
                "interrupted" to Oc2Phase.Interrupted,
            )) {
            put(
                "session.execution.$suffix",
                serial(Oc2Event.SessionExecution.serializer()) { it.copy(phase = phase) },
            )
        }
        for ((t, phase) in
            listOf(
                "session.inbox.enqueued" to Oc2Phase.Enqueued,
                "session.inbox.delivered" to Oc2Phase.Delivered,
                "session.inbox.cancelled" to Oc2Phase.Cancelled,
                "session.inbox.delivery.changed" to Oc2Phase.DeliveryChanged,
            )) {
            put(t) { _, data -> inbox(phase, data) }
        }
        put(
            "session.instructions.updated",
            serial(Oc2Event.SessionInstructionsUpdated.serializer()),
        )
        put("session.synthetic", serial(Oc2Event.SessionSynthetic.serializer()))
        put("session.skill.activated", serial(Oc2Event.SessionSkillActivated.serializer()))
        put(
            "session.shell.started",
            serial(Oc2Event.SessionShell.serializer()) {
                it.copy(phase = Oc2Phase.Started, output = null)
            },
        )
        put(
            "session.shell.ended",
            serial(Oc2Event.SessionShell.serializer()) { it.copy(phase = Oc2Phase.Ended) },
        )
        phased(
            "session.step",
            listOf("started", "streamed", "ended", "failed"),
            Oc2Event.SessionStep.serializer(),
        ) { e, p ->
            e.copy(phase = p)
        }
        phased(
            "session.text",
            listOf("started", "delta", "ended"),
            Oc2Event.SessionText.serializer(),
        ) { e, p ->
            e.copy(phase = p)
        }
        phased(
            "session.reasoning",
            listOf("started", "delta", "ended"),
            Oc2Event.SessionReasoning.serializer(),
        ) { e, p ->
            e.copy(phase = p)
        }
        phased(
            "session.tool.input",
            listOf("started", "delta", "ended"),
            Oc2Event.SessionToolInput.serializer(),
        ) { e, p ->
            e.copy(phase = p)
        }
        put("session.tool.called", serial(Oc2Event.SessionToolCalled.serializer()))
        put("session.tool.progress", serial(Oc2Event.SessionToolProgress.serializer()))
        phased(
            "session.tool",
            listOf("success", "failed"),
            Oc2Event.SessionToolResult.serializer(),
        ) { e, p ->
            e.copy(phase = p)
        }
        put("session.retry.scheduled", serial(Oc2Event.SessionRetryScheduled.serializer()))
        phased(
            "session.compaction",
            listOf("started", "delta", "ended", "failed"),
            Oc2Event.SessionCompaction.serializer(),
        ) { e, p ->
            e.copy(phase = p)
        }
        // Dart reads `revert` only on staged and `to` only on committed.
        put(
            "session.revert.staged",
            serial(Oc2Event.SessionRevert.serializer()) {
                it.copy(phase = Oc2Phase.Staged, toMessageID = null)
            },
        )
        put(
            "session.revert.cleared",
            serial(Oc2Event.SessionRevert.serializer()) {
                Oc2Event.SessionRevert(Oc2Phase.Cleared, it.sessionID)
            },
        )
        put(
            "session.revert.committed",
            serial(Oc2Event.SessionRevert.serializer()) {
                it.copy(phase = Oc2Phase.Committed, revert = null)
            },
        )
        put(
            "session.message.content.updated",
            serial(Oc2Event.SessionMessageContentUpdated.serializer()),
        )
        put("session.status") { _, data ->
            val status = data.obj("status") ?: JsonObject(emptyMap())
            Oc2Event.SessionStatus(
                sessionID = data.str("sessionID") ?: "",
                status = Oc2SessionRunStatus.parse(status.str("type")),
                attempt = status.int("attempt"),
                message = status.str("message"),
                next = status.long("next"),
                raw = status,
            )
        }
        put("session.idle") { _, data ->
            Oc2Event.SessionStatus(data.str("sessionID") ?: "", Oc2SessionRunStatus.Idle)
        }
        put("session.compacted", serial(Oc2Event.SessionCompacted.serializer()))
        put("permission.asked") { _, data ->
            Oc2PermissionRequest.fromJson(data)?.let(Oc2Event::PermissionAsked)
        }
        put("permission.updated") { _, data ->
            Oc2PermissionRequest.fromJson(data)?.let(Oc2Event::PermissionAsked)
        }
        put("permission.replied") { _, data ->
            val id = data.str("requestID") ?: data.str("permissionID") ?: ""
            if (id.isBlank()) null
            else
                Oc2Event.PermissionReplied(
                    sessionID = data.str("sessionID") ?: "",
                    requestID = id,
                    reply = data.str("reply") ?: data.str("response"),
                )
        }
        put("form.created") { _, data ->
            Oc2FormInfo.fromJson(data.obj("form") ?: data)?.let(Oc2Event::FormCreated)
        }
        put("form.replied", serial(Oc2Event.FormReplied.serializer()))
        put("form.cancelled", serial(Oc2Event.FormCancelled.serializer()))
        for (kind in listOf("status", "resources", "tools")) {
            put("mcp.$kind.changed") { _, data -> Oc2Event.McpChanged(kind, data.str("server")) }
        }
        put("vcs.branch.updated", serial(Oc2Event.VcsBranchUpdated.serializer()))
        put("filesystem.changed", serial(Oc2Event.FilesystemChanged.serializer()))
        put("tui.toast.show", serial(Oc2Event.TuiToast.serializer()))
        for (t in REFRESH_HINTS) put(t) { type, _ -> Oc2Event.RefreshHint(type) }
    }

    private inline fun <T : Oc2Event> MutableMap<String, Decoder>.phased(
        prefix: String,
        suffixes: List<String>,
        strategy: DeserializationStrategy<T>,
        crossinline withPhase: (T, Oc2Phase) -> T,
    ) {
        for (suffix in suffixes) {
            val phase = Oc2Phase.entries.first { it.name.equals(suffix, ignoreCase = true) }
            put("$prefix.$suffix") { _, data ->
                Oc2Wire.decode(strategy, data)?.let { withPhase(it, phase) }
            }
        }
    }

    private fun inbox(phase: Oc2Phase, data: JsonObject): Oc2Event {
        val item = data.obj("item")
        val merged = item?.let {
            JsonObject(
                buildMap {
                    data["inboxID"]?.let { v -> put("id", v) }
                    data["sessionID"]?.let { v -> put("sessionID", v) }
                    putAll(it)
                }
            )
        }
        return Oc2Event.SessionInbox(
            phase = phase,
            sessionID = data.str("sessionID") ?: "",
            inboxID = data.str("inboxID") ?: "",
            item = merged?.let(Oc2InboxItem::fromJson),
            delivery = Oc2Delivery.parse(data["delivery"] ?: item?.get("delivery")),
        )
    }

    /** The 83 types with a typed variant (Flutter's set). */
    public val typed: Set<String>
        get() = decoders.keys

    /** Kept by name with the raw payload (plan 04 §7.5). */
    public val passThrough: Set<String> =
        setOf(
            "installation.updated",
            "installation.update-available",
            "pty.created",
            "pty.updated",
            "pty.exited",
            "pty.deleted",
            "worktree.ready",
            "worktree.failed",
            "workspace.ready",
            "workspace.failed",
        )

    /** Known types the client does not use (plan 04 §7.5); new types are not listed here. */
    public val ignored: Set<String> =
        setOf(
            "persistent-pty.added",
            "persistent-pty.removed",
            "workspace.status",
            "global.disposed",
            "tui.prompt.append",
            "tui.command.execute",
            "tui.session.select",
            "worktree.resolved",
            "session.archived",
            "session.unarchived",
        )

    /** [data] is the envelope's `data`, or the whole envelope when it has none. */
    public fun decode(type: String, data: JsonObject): Oc2Event =
        decoders[type]?.invoke(type, data)
            ?: if (type in passThrough) {
                Oc2Event.PassThrough(type, data)
            } else {
                Oc2Event.Unknown(type, data, ignored = type in ignored)
            }
}
