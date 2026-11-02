package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role

/** One bounded history page: items oldest-first within the page; [olderCursor] reads further back. */
public data class CardHistoryPage(val items: List<MessageWithParts>, val olderCursor: String?, val hasMore: Boolean)

/** The bounded transcript reads recovery needs (Flutter `GenUiHistoryGateway`). */
public fun interface CardHistorySource {
    public suspend fun page(sessionId: String, cursor: String?, limit: Int): Outcome<CardHistoryPage>
}

/** A read of a session's recent history, oldest first, and whether it is whole enough to decide card state. */
public data class CardTail(val messages: List<MessageWithParts>, val complete: Boolean)

/**
 * B-CHAT-038: card history reads are bounded: pages of 50, newest first, at most two pages, returned oldest first;
 * one page holding a user boundary is enough; an invalid limit sends nothing. Server text never reaches the result:
 * a failed read is the source's typed Failure only.
 */
public object CardHistory {
    public const val PAGE: Int = 50
    public const val PAGES: Int = 2

    public suspend fun readTail(
        source: CardHistorySource,
        sessionId: String,
        limit: Int = PAGE,
        missingMessageIds: Set<String> = emptySet(),
    ): Outcome<CardTail> {
        if (limit !in 1..PAGE || sessionId.isEmpty()) return Outcome.Failed(Failure.Rejected(status = null))
        val messages = ArrayList<MessageWithParts>()
        var cursor: String? = null
        var more = false
        for (page in 0 until PAGES) {
            val result = source.page(sessionId, cursor, limit)
            if (result is Outcome.Failed) return result
            val p = (result as Outcome.Ok).value
            messages.addAll(0, p.items)
            more = p.hasMore
            if (!more) break
            if (p.olderCursor == null || p.olderCursor == cursor) break
            cursor = p.olderCursor
            val missing = missingMessageIds.any { id -> messages.none { it.info.id == id } }
            if (!missing && messages.any { it.info.role == Role.User }) break
        }
        val missing = missingMessageIds.any { id -> messages.none { it.info.id == id } }
        val complete = !missing && !(more && messages.none { it.info.role == Role.User })
        return Outcome.Ok(CardTail(messages, complete))
    }
}

/**
 * A session's idle proof (B-CHAT-039): accepted only when a fresh idle snapshot was taken in the same directory, for
 * a session that exists and is not in the active map. A claim without fresh proof is refused.
 */
public data class IdleSnapshot(val sessionId: String, val directory: String, val exists: Boolean, val active: Boolean, val takenAtMs: Long)

public object IdleProof {
    public const val FRESH_MS: Long = 5_000

    public fun idle(snapshot: IdleSnapshot?, sessionId: String, directory: String, nowMs: Long): Boolean =
        snapshot != null &&
            snapshot.sessionId == sessionId &&
            snapshot.directory == directory &&
            snapshot.exists &&
            !snapshot.active &&
            nowMs - snapshot.takenAtMs in 0..FRESH_MS
}

/** Persistence for the coverage watermarks (`oc.genui.covered.<profileId>`): one JSON string per profile. */
public interface CoverageStore {
    public fun read(profileId: String): String?

    public suspend fun write(profileId: String, json: String?): Outcome<Unit>
}

/**
 * Recovery coverage (B-CHAT-043/044, Flutter `gen_ui_coverage.dart`): a conversation whose recent history held no card
 * is skipped until its newest prompt changes. Watermarks only, the 200 most recent sessions; corrupt data reads as
 * empty (history is read again); a closed profile accepts no late write; an endpoint change is a different identity.
 */
public class CardCoverage(private val store: CoverageStore) {
    private val closed = HashSet<String>()

    public companion object {
        public const val MAX: Int = 200

        /** The identity a watermark is kept under: endpoint, place and session (an endpoint change resets it). */
        public fun identity(endpoint: String, scope: CardScope, sessionId: String): String =
            CardJson.encode(listOf(endpoint, CardJson.encode(listOf(scope.profileId, scope.sourceId, scope.directory, scope.workspace)), sessionId))
    }

    @Suppress("UNCHECKED_CAST")
    private fun entries(profileId: String): LinkedHashMap<String, String> {
        val raw = store.read(profileId) ?: return LinkedHashMap()
        return try {
            val list = CardJson.parse(raw) as List<List<String>>
            LinkedHashMap<String, String>().also { map -> list.forEach { (k, v) -> map[k] = v } }
        } catch (e: RuntimeException) {
            LinkedHashMap() // corrupt → fall back to reading history
        }
    }

    public fun contains(profileId: String, identity: String, promptStamp: String): Boolean =
        profileId !in closed && entries(profileId)[identity] == promptStamp

    public suspend fun remember(profileId: String, identity: String, promptStamp: String): Outcome<Unit> {
        if (profileId in closed) return Outcome.Ok(Unit)
        val map = entries(profileId)
        map.remove(identity)
        map[identity] = promptStamp
        while (map.size > MAX) map.remove(map.keys.first())
        if (profileId in closed) return Outcome.Ok(Unit)
        return store.write(profileId, CardJson.encode(map.map { listOf(it.key, it.value) }))
    }

    /** Fences in-flight writes; the stored data is left to profile deletion. */
    public fun closeProfile(profileId: String) {
        closed += profileId
    }

    public fun reopenProfile(profileId: String) {
        closed -= profileId
    }
}

/** What recovery knows about one conversation before reading it. */
public data class RecoveryTarget(
    val scope: CardScope,
    val sessionId: String,
    val endpoint: String,
    /** False = reading would wake a sleeping stored agent (Paseo); background recovery skips it. */
    val mayLoad: Boolean = true,
    /** Cheap snapshot: idle and the newest prompt's stamp; null = unknown (always read). */
    val idle: Boolean? = null,
    val promptStamp: String? = null,
    /** A journaled card or an uncertain answer lives here: always reconcile. */
    val journaled: Boolean = false,
)

/** The recovery decision rules (Flutter `_needsRecovery` and the pass caps). */
public object CardRecovery {
    /** Per pass: at most 20 conversations, each at most [CardHistory.PAGES] pages (B-CHAT-052). */
    public const val SESSIONS_PER_PASS: Int = 20

    public fun needsRead(target: RecoveryTarget, coverage: CardCoverage, explicit: Boolean = false): Boolean {
        if (explicit) return true // answering a card or opening the chat still reads it
        if (!target.mayLoad) return false // never wake a sleeping agent in the background
        val stamp = target.promptStamp
        if (target.idle != true || stamp == null) return true // a running turn or an unknown prompt is never covered
        if (target.journaled) return true
        return !coverage.contains(target.scope.profileId, CardCoverage.identity(target.endpoint, target.scope, target.sessionId), stamp)
    }

    /** The conversations this pass reads, in order, capped; the rest wait for the next pass (flagged incomplete). */
    public fun pass(targets: List<RecoveryTarget>, coverage: CardCoverage): Pair<List<RecoveryTarget>, Boolean> {
        val wanted = targets.filter { needsRead(it, coverage) }
        return wanted.take(SESSIONS_PER_PASS) to (wanted.size > SESSIONS_PER_PASS)
    }

    /**
     * Whether a finished read may cover its conversation: whole, card-free, idle before and after on the same prompt.
     * A read that held any card call (whatever its state) is never covered.
     */
    public fun mayCover(tail: CardTail, before: RecoveryTarget, after: RecoveryTarget): Boolean =
        tail.complete &&
            before.idle == true &&
            after.idle == true &&
            before.promptStamp != null &&
            before.promptStamp == after.promptStamp &&
            tail.messages.none { m -> m.parts.any { it.toolName in CARD_TOOL_NAMES } }
}
