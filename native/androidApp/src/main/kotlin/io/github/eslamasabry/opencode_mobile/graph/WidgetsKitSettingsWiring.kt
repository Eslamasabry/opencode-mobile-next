package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetOtherConversation
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetRowDetails
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetServerLine
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSession
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotInput
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetSnapshotWords
import io.github.eslamasabry.oc.android.widgets.snapshot.WidgetText
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import kotlinx.serialization.json.JsonPrimitive

/*
 * BUILD-WIDGETS-KIT-SETTINGS wiring. Each worker of the unit adds its own section below; never rewrite another's.
 */

// ---- A1: home-screen widget snapshot (B-ANDR-111..117, B-ANDR-120) ----------------------------------------------------

/**
 * Maps the live conversation feed to the `:android:widgets` snapshot builder's input (Flutter
 * `ConnectionController._widgetSessionDetails` / `_widgetOtherConversations` / `_widgetServers`). The active server's
 * conversations keep their details (project, reply preview, the permission ask with its request id, or a question);
 * other servers' conversations are plain rows that name their server and carry no request identity.
 */
internal object WidgetSnapshotWiring {
    private val fileKeys = listOf("filePath", "filepath", "file_path", "path", "file")

    fun input(
        feed: ChatFeedSnapshot,
        waiting: List<Waiting>,
        active: ProfileId,
        names: Map<ProfileId, String>,
        online: Set<ProfileId>,
        offlineSince: MutableMap<ProfileId, Long>,
        nowMs: Long,
        text: WidgetText,
        words: WidgetSnapshotWords,
    ): WidgetSnapshotInput {
        val items = feed.items.filter { !it.isSubagent }
        val own = items.filter { it.profileId == active }
        val details =
            own.associate { item ->
                val asks = waiting.filter { it.profile == active && it.request.sessionId == item.sessionId }
                val permission = asks.firstNotNullOfOrNull { it.request as? PendingRequest.Permission }
                val question = asks.firstNotNullOfOrNull { it.request as? PendingRequest.Question }
                val base = WidgetRowDetails(project = item.directory ?: item.projectName, preview = item.preview)
                item.sessionId.raw to
                    when {
                        permission != null -> base.copy(requestId = permission.id.raw, ask = ask(permission, text, words))
                        question != null -> {
                            val prompt = question.question.prompts.firstOrNull()
                            val q = prompt?.question?.takeIf { it.isNotBlank() } ?: prompt?.title.orEmpty()
                            base.copy(needsYou = true, ask = if (q.isBlank()) null else mapOf("kind" to "other", "q" to q))
                        }
                        else -> base
                    }
            }
        val others =
            items
                .filter { it.profileId != active }
                .take(12)
                .map { item ->
                    WidgetOtherConversation(
                        id = item.sessionId.raw,
                        title = item.title,
                        project = item.projectName,
                        updatedAt = item.lastActivityMs,
                        busy = item.status == ChatStatus.Running,
                        needsYou =
                            item.status == ChatStatus.NeedsYou ||
                                waiting.any { it.profile == item.profileId && it.request.sessionId == item.sessionId },
                        server = names[item.profileId].orEmpty(),
                        profileId = item.profileId.raw,
                    )
                }
        offlineSince.keys.retainAll(names.keys)
        val servers =
            names.map { (id, name) ->
                if (id in online) {
                    offlineSince.remove(id)
                    WidgetServerLine(name, "online")
                } else {
                    WidgetServerLine(name, "offline", since = offlineSince.getOrPut(id) { nowMs })
                }
            }
        return WidgetSnapshotInput(
            sessions = own.map { WidgetSession(it.sessionId.raw, it.title, it.lastActivityMs) },
            busy = own.filter { it.status == ChatStatus.Running }.map { it.sessionId.raw }.toSet(),
            connected = active in online,
            profileId = active.raw,
            pendingCount = waiting.count { it.profile == active },
            details = details,
            others = others,
            server = names[active].orEmpty(),
            servers = servers,
            projects = own.mapNotNull { it.directory },
        )
    }

    private fun ask(r: PendingRequest.Permission, text: WidgetText, words: WidgetSnapshotWords): Map<String, String> {
        fun meta(key: String) = (r.request.metadata[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val command = meta("command")
        val file = fileKeys.firstNotNullOfOrNull(::meta)
        return text.askFor(
            permission = r.request.permission,
            patterns = r.request.patterns,
            words = words,
            tool = if (command != null) "bash" else null,
            target = command ?: file,
        )
    }
}
