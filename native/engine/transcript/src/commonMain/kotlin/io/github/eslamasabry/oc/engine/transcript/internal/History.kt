package io.github.eslamasabry.oc.engine.transcript.internal

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import kotlinx.collections.immutable.toImmutableList

/** The version fences a merge reads (plan 09 §3.2): what moved since the read started wins over the snapshot. */
internal interface VersionFence {
    fun message(id: String): Long

    fun part(messageId: String, partId: String): Long

    /** Live parts of a message the stream knew before its message arrived (`_deferredParts`). Consumed. */
    fun deferredParts(messageId: String): Map<String, Part>? = null

    /** A late edit that waited for history (`_deferredMessages`). Consumed. */
    fun deferredMessage(messageId: String): MessageInfo? = null
}

/**
 * Port of `chat_history.dart` `_mergeHydratedMessages` (plan 09 §3.4 step 2, map CHAT-GENUI-03), without the
 * pending-send reconcile, which `:engine:delivery` owns (§4.6):
 * - for every hydrated message, keep the live copy if its version advanced since [versionAtStart]; a message stamped
 *   since the read with no live copy was removed meanwhile and is skipped;
 * - parts likewise by part version; live parts missing from the snapshot but newer are appended;
 * - messages absent from the snapshot are kept only when newer than the read or [preserveUnseen] (older-page loads);
 * - [withdrawn] prompts are skipped; [prefix] (older pages kept by a recent refresh) leads the result.
 */
internal fun mergeHydrated(
    current: List<MessageWithParts>,
    hydrated: List<MessageWithParts>,
    versionAtStart: Long,
    fence: VersionFence,
    prefix: List<MessageWithParts> = emptyList(),
    preserveUnseen: Boolean = false,
    withdrawn: Set<String> = emptySet(),
): List<MessageWithParts> {
    val currentById = HashMap<String, MessageWithParts>(current.size * 2)
    for (m in current) currentById[m.info.id] = m
    val merged = ArrayList<MessageWithParts>(hydrated.size + 8)
    val hydratedIds = HashSet<String>()
    for (snapshot in hydrated) {
        val messageId = snapshot.info.id
        if (messageId in withdrawn) continue
        if (!hydratedIds.add(messageId)) continue
        val deferredInfo = fence.deferredMessage(messageId)
        val current0 = currentById[messageId] ?: deferredInfo?.let { MessageWithParts(it, emptyList<Part>().toImmutableList()) }
        val messageChanged = fence.message(messageId) > versionAtStart
        if (messageChanged && current0 == null) continue
        val currentParts = LinkedHashMap<String, Part>()
        fence.deferredParts(messageId)?.let { currentParts.putAll(it) }
        for (p in current0?.parts.orEmpty()) currentParts[p.id.ifEmpty { p.callId.orEmpty() }] = p
        val parts = ArrayList<Part>(snapshot.parts.size)
        val included = HashSet<String>()
        var samePartsAsLive = current0 != null
        for (sp in snapshot.parts) {
            val partId = sp.id.ifEmpty { sp.callId.orEmpty() }
            if (partId.isEmpty()) {
                parts += sp
                samePartsAsLive = false
                continue
            }
            included += partId
            if (fence.part(messageId, partId) > versionAtStart) {
                // Newer live part wins; with none, the snapshot is kept and deferred deltas apply on adoption.
                parts += currentParts[partId] ?: sp
            } else {
                parts += sp
            }
        }
        for ((id, p) in currentParts) {
            if (id !in included && fence.part(messageId, id) > versionAtStart) parts += p
        }
        val info = if (messageChanged) current0!!.info else snapshot.info
        // Keep the live instance when nothing differs, so its rows keep their identity.
        val live = currentById[messageId]
        merged +=
            if (live != null && samePartsAsLive && info == live.info && sameParts(live.parts, parts)) {
                live
            } else {
                MessageWithParts(info, parts.toImmutableList())
            }
    }
    val prefixIds = prefix.mapTo(HashSet()) { it.info.id }
    for (m in current) {
        val id = m.info.id
        if (id in hydratedIds) continue
        val newerMessage = fence.message(id) > versionAtStart
        val newerPart = m.parts.any { p -> fence.part(id, p.id) > versionAtStart }
        if ((preserveUnseen || newerMessage || newerPart) && id !in prefixIds) merged += m
    }
    if (prefix.isEmpty()) return merged
    return prefix.filter { it.info.id !in hydratedIds } + merged
}

/** Equal parts reuse the live instances (`===` or `==`), so a refresh that changes nothing changes no row. */
private fun sameParts(live: List<Part>, next: MutableList<Part>): Boolean {
    if (live.size != next.size) return false
    for (i in live.indices) {
        val a = live[i]
        val b = next[i]
        if (a === b) continue
        if (a != b) return false
        next[i] = a
    }
    return true
}
