package io.github.eslamasabry.oc.engine.analytics

import kotlinx.serialization.Serializable

/**
 * Where the engine keeps its one small document (queue, consent, install id, daily ledger, backoff). App-wide, holds
 * no profile data, so it is not in ProfileScope; Manage space › Delete everything calls [AnalyticsEngine.deleteEverything].
 * TODO(app-wiring): the file binding (`files/native/analytics/queue.json` through one WriterLane, plan 16 §5.6
 * rule 7) belongs to `:core:data`; until then the app binds an implementation of this interface.
 * Implementations may throw; the engine wraps every call.
 */
public interface AnalyticsStore {
    public suspend fun read(): String?

    public suspend fun write(text: String)

    public suspend fun delete()
}

public class InMemoryAnalyticsStore(initial: String? = null) : AnalyticsStore {
    public var text: String? = initial
        private set

    override suspend fun read(): String? = text

    override suspend fun write(text: String) {
        this.text = text
    }

    override suspend fun delete() {
        text = null
    }
}

/** The stored document. The session id is deliberately absent (plan 16 §5.10). */
@Serializable
internal data class PersistedState(
    val version: Int = VERSION,
    val consent: ConsentState = ConsentState(),
    val install: InstallIdRecord? = null,
    val nextSeq: Long = 0,
    val queue: List<QueuedEvent> = emptyList(),
    val backoff: Backoff = Backoff(),
    val ledger: DailyLedger? = null,
) {
    companion object {
        const val VERSION: Int = 1
    }
}
