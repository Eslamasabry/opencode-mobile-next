package io.github.eslamasabry.oc.engine.analytics

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Which sink is bound. The vendor ids are reserved for senders written later over `:core:net` (plan 16 §5.5). */
public enum class SinkId { None, DebugJson, Aptabase, PostHog, Plausible, Custom }

/** None: nothing is kept on disk and nothing is handed on. LocalDebug: stays on the phone. Remote: leaves the phone. */
public enum class SinkKind { None, LocalDebug, Remote }

/** What a sink did with a batch: [body] is exactly the bytes it wrote or sent, for "What was sent". */
public data class Delivery(val body: String)

/**
 * Where batches go: exactly one per build (plan 16 §5.1 `AnalyticsSender`). A Remote sink must use HTTPS to a
 * `KnownHosts` Fetch host with no cookies and no redirects; none exists until the owner names a destination (D16-1).
 * A sink never throws for an expected failure: it returns [Outcome.Failed].
 */
public interface AnalyticsSink {
    public val id: SinkId
    public val kind: SinkKind

    /** The host a Remote sink talks to; null otherwise. */
    public val host: String?

    public suspend fun send(batch: WireBatch): Outcome<Delivery>
}

/** The default build's sink (`sender=none`): never called by the engine, and drops anything handed to it. */
public object NoOpSink : AnalyticsSink {
    override val id: SinkId get() = SinkId.None
    override val kind: SinkKind get() = SinkKind.None
    override val host: String? get() = null

    override suspend fun send(batch: WireBatch): Outcome<Delivery> = Outcome.Ok(Delivery(""))
}

/**
 * A local sink for debug and `qa` builds: writes each batch as our own JSON to [write] (for example a file the QA
 * harness reads) and keeps the last [keep] bodies in [bodies]. Nothing leaves the phone.
 */
public class JsonDebugSink(
    private val keep: Int = 50,
    private val write: (String) -> Unit = {},
) : AnalyticsSink {
    override val id: SinkId get() = SinkId.DebugJson
    override val kind: SinkKind get() = SinkKind.LocalDebug
    override val host: String? get() = null

    private val _bodies = MutableStateFlow<ImmutableList<String>>(persistentListOf())

    /** Newest first. */
    public val bodies: StateFlow<ImmutableList<String>> = _bodies.asStateFlow()

    override suspend fun send(batch: WireBatch): Outcome<Delivery> {
        val body = WireCodec.encode(batch)
        write(body)
        _bodies.update { (listOf(body) + it).take(keep).toImmutableList() }
        return Outcome.Ok(Delivery(body))
    }
}
