package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arrayOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.paseoMillis
import io.github.eslamasabry.oc.protocol.paseo.requests.isPaseoProviderId
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.serialization.json.JsonElement

/*
 * Importing a conversation started in Claude Code's own app, a port of lib/paseo/gateway/provider_import.dart and
 * lib/domain/provider_conversation_import.dart (B-AGENT-174): the daemon lists this project's conversations, newest
 * first; importing sends the agent, its handle and folder, then reads the agent the ordinary way.
 */

public data class ImportableConversation(
    /** The runtime (`claude`). */
    val agentId: String,
    /** The runtime's own session handle. */
    val handle: String,
    val directory: String,
    val lastUsedMs: Long,
    val title: String? = null,
    val firstPrompt: String? = null,
) {
    public val displayTitle: String? get() = title ?: firstPrompt
}

public data class ImportableConversations(val items: List<ImportableConversation>, val alreadyImported: Int)

private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
private val BLANKS = Regex("\\s+")

private fun importText(value: JsonElement?, max: Int): String? {
    val clean = value.stringOrNull()?.replace(CONTROL, " ")?.replace(BLANKS, " ")?.trim() ?: return null
    if (clean.isEmpty()) return null
    return if (clean.length > max) clean.substring(0, max) + "…" else clean
}

/** The folder imports are listed for (the gateway's own). */
public val PaseoGateway.providerImportDirectory: String? get() = directory

/** This project's Claude Code conversations the daemon can import, newest first. */
public suspend fun PaseoGateway.importableConversations(): Outcome<ImportableConversations> = laneCall {
    val scope = scopeDir
    val result = workspaceRequest(
        "fetch_recent_provider_sessions_request",
        jsonOf("cwd" to scope, "providers" to listOf("claude"), "limit" to 50),
        timeoutMs = 45_000,
    )
    val items = ArrayList<ImportableConversation>()
    for (raw in result["entries"].arrayOrNull().orEmpty().take(200)) {
        val entry = raw.objectOrNull() ?: continue
        val agentId = entry.str("providerId")
        val handle = entry.str("providerHandleId")
        val directory = entry.str("cwd")
        val last = paseoMillis(entry["lastActivityAt"])
        if (agentId == null || !isPaseoProviderId(agentId) || handle == null || handle.isEmpty() || handle.length > 256 ||
            CONTROL.containsMatchIn(handle) || directory.isNullOrEmpty() || last == null
        ) {
            continue
        }
        items += ImportableConversation(
            agentId, handle, directory, last,
            title = importText(entry["title"], 120),
            firstPrompt = importText(entry["firstPromptPreview"], 120),
        )
    }
    items.sortByDescending { it.lastUsedMs }
    val already = result["filteredAlreadyImportedCount"].longOrNull()?.takeIf { it > 0 }?.toInt() ?: 0
    ImportableConversations(items, already)
}

/** Brings [conversation] into this project's list; a refusal is a failure, never a conversation. */
public suspend fun PaseoGateway.importConversation(conversation: ImportableConversation): Outcome<Session> = laneCall {
    val result = workspaceRequest(
        "import_agent_request",
        jsonOf("providerId" to conversation.agentId, "providerHandleId" to conversation.handle, "cwd" to conversation.directory),
        mutation = true,
        timeoutMs = 90_000,
    )
    val id = result["agent"].objectOrNull()?.str("id")
    if (result.str("status") != "agent_resumed" || id == null) paseoFail(PaseoFailureKind.Unavailable)
    // The ordinary read of one agent: scope, archive and local-work checks all apply.
    fetchAgent(id)
    sessions[app(id)] ?: paseoFail(PaseoFailureKind.ScopeMismatch)
}
