package io.github.eslamasabry.oc.engine.diagnostics

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** One handled error, already redacted and bounded (plan 03 §18.3). */
public data class ErrorEntry(
    val id: Long,
    val at: Instant,
    val source: String,
    val message: String,
    val stack: String,
    val occurrences: Int = 1,
)

/**
 * Process-local, privacy-bounded diagnostics for handled errors (port of `AppDiagnosticsController`, B-SEC-008).
 * Every field is redacted through [Redactor.diagnostics] before it is kept; repeats inside [dedupeWindow] fold into
 * the last entry; only the newest [maxEntries] are kept. Nothing leaves memory unless "Report a problem" exports it.
 *
 * Thread-safe and non-suspending: crash paths and any thread may call [record].
 */
public class Diagnostics(
    private val redactor: Redactor,
    private val clock: Clock,
    private val signals: DiagnosticsSignals = DiagnosticsSignals.None,
    public val maxEntries: Int = MAX_ENTRIES,
    public val dedupeWindow: Duration = DEDUPE_WINDOW,
) {
    private val entries = MutableStateFlow<PersistentList<ErrorEntry>>(persistentListOf())
    private val nextId = AtomicLong(1L)
    private val clearListeners = AtomicReference<List<() -> Unit>>(emptyList())

    /** Newest last. */
    public val errors: StateFlow<ImmutableList<ErrorEntry>> = entries.asStateFlow()

    public val isEmpty: Boolean get() = entries.value.isEmpty()

    /** Records a handled error. [message] and [stack] are sanitised here; callers pass raw text. */
    public fun record(source: String, message: String, stack: String? = null, at: Instant = clock.now()) {
        if (maxEntries <= 0) return
        val safeSource = sanitize(source, SOURCE_LIMIT).ifEmpty { "app" }
        val safeMessage = sanitize(message, MESSAGE_LIMIT).ifEmpty { UNKNOWN_MESSAGE }
        val safeStack = sanitize(stack.orEmpty(), STACK_LIMIT)
        val id = nextId.fetchAndAdd(1L)
        entries.update { list ->
            val previous = list.lastOrNull()
            if (previous != null &&
                previous.source == safeSource &&
                previous.message == safeMessage &&
                previous.stack == safeStack &&
                (at - previous.at).absoluteValue <= dedupeWindow
            ) {
                list.replacingAt(list.lastIndex, previous.copy(at = at, occurrences = previous.occurrences + 1))
            } else {
                val next = list.adding(ErrorEntry(id, at, safeSource, safeMessage, safeStack))
                if (next.size > maxEntries) next.subList(next.size - maxEntries, next.size).toPersistent() else next
            }
        }
    }

    /** A thrown error: its text and frames go through the same sanitiser as any other line. */
    public fun record(source: String, error: Throwable, at: Instant = clock.now()) {
        record(source, error.toString(), error.stackTraceToString(), at)
    }

    /**
     * A [Failure] the person was shown (plan 18 `error_seen`): the category goes to [DiagnosticsSignals]; the ring keeps
     * only the failure's kind, never its details text.
     */
    public fun failureShown(failure: Failure, source: String = "failure") {
        signals.safely { error(failure) }
        record(source, failureLabel(failure))
    }

    /** Erases every entry; listeners (the crash store) erase their restored evidence too. */
    public fun clear() {
        entries.value = persistentListOf()
        for (listener in clearListeners.load()) {
            try {
                listener()
            } catch (e: Exception) {
                log.w(e) { "diagnostics clear listener failed" }
            }
        }
    }

    /** Same-module hook for the crash store; returns the remover. */
    internal fun onCleared(listener: () -> Unit): () -> Unit {
        while (true) {
            val current = clearListeners.load()
            if (clearListeners.compareAndSet(current, current + listener)) break
        }
        return {
            while (true) {
                val current = clearListeners.load()
                if (clearListeners.compareAndSet(current, current - listener)) break
            }
        }
    }

    /** The diagnostics sanitiser: redaction, URL query/userinfo removal, long tokens, bounded. */
    public fun sanitize(value: String, limit: Int = STACK_LIMIT): String = redactor.diagnostics(value, limit).trim()

    public fun reportJson(): JsonObject {
        val list = entries.value
        return buildJsonObject {
            put("app", "opencode-mobile")
            put("privacy", "redacted-process-memory-only")
            put("entryCount", list.size)
            putJsonArray("entries") {
                for (entry in list) {
                    addJsonObject {
                        put("time", entry.at.toString())
                        put("source", entry.source)
                        put("message", entry.message)
                        if (entry.stack.isNotEmpty()) put("stack", entry.stack)
                        if (entry.occurrences > 1) put("occurrences", entry.occurrences)
                    }
                }
            }
        }
    }

    public fun reportText(): String = REPORT_JSON.encodeToString(JsonObject.serializer(), reportJson())

    public companion object {
        public const val MAX_ENTRIES: Int = 20
        public val DEDUPE_WINDOW: Duration = 10.seconds
        public const val MESSAGE_LIMIT: Int = 2_000
        public const val STACK_LIMIT: Int = 12_000
        public const val SOURCE_LIMIT: Int = 80
        internal const val UNKNOWN_MESSAGE = "Unknown application error"
        private val REPORT_JSON = Json { prettyPrint = true }
        private val log = AppLog.tagged("diagnostics")

        /** The failure's kind, e.g. `Net.Unreachable Timeout`; never its details. */
        internal fun failureLabel(failure: Failure): String {
            val kind = failure::class.simpleName ?: "Failure"
            val name = if (failure is Failure.Net) "Net.$kind" else kind
            val why =
                when (failure) {
                    is Failure.Refused -> failure.reason.name
                    is Failure.Storage -> failure.problem.name
                    is Failure.Folder -> failure.problem.name
                    is Failure.Unavailable -> failure.feature.name
                    is Failure.Net.Unreachable -> failure.why.name
                    is Failure.Net.TrustRefused -> failure.why.name
                    is Failure.Net.SignInNeeded -> failure.why.name
                    is Failure.Net.ServerBusy -> failure.kind.name
                    is Failure.Net.Incompatible -> failure.kind.name
                    is Failure.Net.Rejected -> "HTTP ${failure.status}"
                    is Failure.Net.ServerFailed -> "HTTP ${failure.status}"
                    else -> null
                }
            return if (why == null) name else "$name $why"
        }
    }
}

private fun <T> List<T>.toPersistent(): PersistentList<T> = persistentListOf<T>().addingAll(this)
