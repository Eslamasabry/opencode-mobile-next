package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.paseoMillis
import io.github.eslamasabry.oc.protocol.paseo.requests.isPaseoProviderId
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/** Actual Paseo provider-import requests, ported from lib/paseo/gateway/provider_import.dart. */
internal class PaseoProviderImport(private val gateway: PaseoGateway) {
    private val mutex = Mutex()
    private var listedScope: String? = null
    private var listedEpoch = -1
    private var candidates: Map<Pair<String, String>, ImportableConversation> = emptyMap()

    fun clear() {
        listedScope = null
        listedEpoch = -1
        candidates = emptyMap()
    }

    suspend fun list(): ImportableConversations = mutex.withLock {
        val directory = gateway.scopeDir
        val epoch = gateway.locationEpoch
        val result =
            gateway.transport.request(
                "fetch_recent_provider_sessions_request",
                jsonOf("cwd" to directory, "providers" to listOf("claude"), "limit" to 50),
                timeoutMs = 45_000,
                beforeSend = { gateway.checkLocation(directory, epoch) },
            )
        gateway.checkLocation(directory, epoch)
        val items =
            result
                .arr("entries")
                .orEmpty()
                .take(200)
                .mapNotNull { raw ->
                    val entry = raw as? JsonObject ?: return@mapNotNull null
                    val provider = entry.str("providerId") ?: return@mapNotNull null
                    val handle = entry.str("providerHandleId") ?: return@mapNotNull null
                    val cwd = entry.str("cwd") ?: return@mapNotNull null
                    val last = paseoMillis(entry["lastActivityAt"]) ?: return@mapNotNull null
                    if (
                        !isPaseoProviderId(provider) ||
                            handle.isEmpty() ||
                            handle.length > 256 ||
                            handle.any(::isControl) ||
                            cwd.isEmpty() ||
                            cwd.length > 4096 ||
                            cwd.any(::isControl)
                    )
                        return@mapNotNull null
                    // The list and imported record must both belong to this exact project, as
                    // ordinary session reads do.
                    if (cwd != directory) return@mapNotNull null
                    ImportableConversation(
                        agentId = provider,
                        handle = handle,
                        directory = cwd,
                        lastUsed = Instant.fromEpochMilliseconds(last),
                        title = importText(entry.str("title")),
                        firstPrompt = importText(entry.str("firstPromptPreview")),
                    )
                }
                .distinctBy { it.agentId to it.handle }
                .sortedByDescending { it.lastUsed }
                .toImmutableList()
        candidates = items.associateBy { it.agentId to it.handle }
        listedScope = directory
        listedEpoch = epoch
        ImportableConversations(
            items = items,
            alreadyImported =
                result["filteredAlreadyImportedCount"]
                    .longOrNull()
                    ?.coerceIn(0, Int.MAX_VALUE.toLong())
                    ?.toInt() ?: 0,
        )
    }

    suspend fun importConversation(conversation: ImportableConversation): Session = mutex.withLock {
        val directory = gateway.scopeDir
        val epoch = gateway.locationEpoch
        val key = conversation.agentId to conversation.handle
        if (listedScope != directory || listedEpoch != epoch || candidates[key] != conversation) {
            paseoFail(PaseoFailureKind.StaleRequest)
        }
        // Retire the selection before dispatch: an uncertain answer can be reconciled by a fresh
        // list, never replayed.
        candidates = candidates - key
        val result =
            gateway.transport.request(
                "import_agent_request",
                jsonOf(
                    "providerId" to conversation.agentId,
                    "providerHandleId" to conversation.handle,
                    "cwd" to conversation.directory,
                ),
                mutation = true,
                timeoutMs = 90_000,
                beforeSend = { gateway.checkLocation(directory, epoch) },
            )
        gateway.checkLocation(directory, epoch)
        val id = result.obj("agent")?.str("id")
        if (
            result.str("status") != "agent_resumed" ||
                id.isNullOrEmpty() ||
                id.length > 256 ||
                id.any(::isControl)
        ) {
            paseoFail(PaseoFailureKind.Unavailable)
        }
        gateway.fetchAgent(id)
        gateway.checkLocation(directory, epoch)
        gateway.sessions[gateway.app(id)] ?: paseoFail(PaseoFailureKind.ScopeMismatch)
    }

    private fun importText(raw: String?): String? {
        val text =
            raw?.map { if (isControl(it)) ' ' else it }
                ?.joinToString("")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return null
        return if (text.length > 120) text.take(120) + "…" else text
    }

    private fun isControl(char: Char): Boolean = char.code <= 0x1f || char.code == 0x7f
}
