package io.github.eslamasabry.oc.engine.diagnostics

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.label
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
import kotlinx.serialization.json.putJsonObject
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * One handled error, already redacted and bounded (plan 03 §18.3). [at] is the last time it happened, [firstAt] the
 * first; [occurrences] counts the repeats folded into it.
 */
public data class ErrorEntry(
    val id: Long,
    val at: Instant,
    val source: String,
    val message: String,
    val stack: String,
    val occurrences: Int = 1,
    val firstAt: Instant = at,
)

/**
 * Which build a copied report came from (B-332): the app's version, when it was installed and last updated, and the
 * Android version. Nothing here is private: no device name, serial, account, address or path. Null fields are unknown.
 */
public data class ReportBuild(
    val versionName: String,
    val versionCode: Long,
    val installedAt: Instant? = null,
    val updatedAt: Instant? = null,
    val androidVersion: String? = null,
    val apiLevel: Int? = null,
)

/**
 * Process-local, privacy-bounded diagnostics for handled errors (port of `AppDiagnosticsController`, B-SEC-008).
 * Every field is redacted through [Redactor.diagnostics] before it is kept; only the newest [maxEntries] are kept.
 * Nothing leaves memory unless "Report a problem" exports it.
 *
 * Repeats fold (B-332): an error with the same source, redacted message and redacted stack as a kept entry whose last
 * time is within [dedupeWindow] becomes that entry again: its count grows, its last time moves on, its first time
 * stays, and it moves to the newest end, so a fault that keeps happening is not the one cut at the limit. Another
 * fault in between does not stop the fold; a different fault never folds.
 *
 * Thread-safe and non-suspending: crash paths and any thread may call [record].
 */
public class Diagnostics(
    private val redactor: Redactor,
    private val clock: Clock,
    private val signals: DiagnosticsSignals = DiagnosticsSignals.None,
    public val maxEntries: Int = MAX_ENTRIES,
    public val dedupeWindow: Duration = DEDUPE_WINDOW,
    /** Which build this is, for the export (B-332); read when a report is made, never logged. */
    private val reportBuild: () -> ReportBuild? = { null },
) {
    private val entries = MutableStateFlow<PersistentList<ErrorEntry>>(persistentListOf())
    private val nextId = AtomicLong(1L)
    private val clearListeners = AtomicReference<List<() -> Unit>>(emptyList())
    private val recordListeners = AtomicReference<List<() -> Unit>>(emptyList())

    /** Newest last. */
    public val errors: StateFlow<ImmutableList<ErrorEntry>> = entries.asStateFlow()

    public val isEmpty: Boolean get() = entries.value.isEmpty()

    /** Records a handled error. [message] and [stack] are sanitised here; callers pass raw text. */
    public fun record(source: String, message: String, stack: String? = null, at: Instant = clock.now()) {
        try {
            if (maxEntries <= 0) return
            val safeSource = sanitize(source, SOURCE_LIMIT).ifEmpty { "app" }
            val safeMessage = sanitize(message, MESSAGE_LIMIT).ifEmpty { UNKNOWN_MESSAGE }
            val safeStack = sanitize(stack.orEmpty(), STACK_LIMIT)
            val id = nextId.fetchAndAdd(1L)
            entries.update { list ->
                val match =
                    list.indexOfLast {
                        it.source == safeSource &&
                            it.message == safeMessage &&
                            it.stack == safeStack &&
                            (at - it.at).absoluteValue <= dedupeWindow
                    }
                if (match >= 0) {
                    val previous = list[match]
                    val folded =
                        previous.copy(
                            at = maxOf(at, previous.at),
                            firstAt = minOf(at, previous.firstAt),
                            occurrences = previous.occurrences + 1,
                        )
                    list.removingAt(match).adding(folded)
                } else {
                    val next = list.adding(ErrorEntry(id, at, safeSource, safeMessage, safeStack))
                    if (next.size > maxEntries) next.subList(next.size - maxEntries, next.size).toPersistent() else next
                }
            }
            for (listener in recordListeners.load()) {
                try {
                    listener()
                } catch (e: Exception) {
                    log.w(e) { "diagnostics record listener failed" }
                } catch (_: Throwable) { /* an Error in one listener must not stop the listeners after it */ }
            }
        } catch (_: Throwable) { /* out of memory: drop this entry */ }
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

    /** Same-module hook for the persisted problem report: called after every [record], in the same call. */
    internal fun onRecorded(listener: () -> Unit): () -> Unit {
        while (true) {
            val current = recordListeners.load()
            if (recordListeners.compareAndSet(current, current + listener)) break
        }
        return {
            while (true) {
                val current = recordListeners.load()
                if (recordListeners.compareAndSet(current, current - listener)) break
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

    /**
     * The redacted export Report a problem copies. With [build] it also says which build it came from (B-332); its
     * fields go through the same sanitiser as every entry.
     */
    public fun reportJson(build: ReportBuild? = currentBuild()): JsonObject {
        val list = entries.value
        return buildJsonObject {
            put("app", "opencode-mobile")
            put("privacy", "redacted-process-memory-only")
            if (build != null) {
                putJsonObject("build") {
                    put("versionName", sanitize(build.versionName, SOURCE_LIMIT))
                    put("versionCode", build.versionCode)
                    build.installedAt?.let { put("installed", it.toString()) }
                    build.updatedAt?.let { put("updated", it.toString()) }
                    build.androidVersion?.let { put("android", sanitize(it, SOURCE_LIMIT)) }
                    build.apiLevel?.let { put("apiLevel", it) }
                }
            }
            put("entryCount", list.size)
            putJsonArray("entries") {
                for (entry in list) {
                    addJsonObject {
                        put("time", entry.at.toString())
                        put("source", entry.source)
                        put("message", entry.message)
                        if (entry.stack.isNotEmpty()) put("stack", entry.stack)
                        if (entry.occurrences > 1) {
                            put("occurrences", entry.occurrences)
                            put("firstTime", entry.firstAt.toString())
                            put("lastTime", entry.at.toString())
                        }
                    }
                }
            }
        }
    }

    public fun reportText(build: ReportBuild? = currentBuild()): String =
        REPORT_JSON.encodeToString(JsonObject.serializer(), reportJson(build))

    /** A build reader that fails leaves the report without its build, never without its entries. */
    private fun currentBuild(): ReportBuild? =
        try {
            reportBuild()
        } catch (e: Exception) {
            log.w(e) { "diagnostics build facts unavailable" }
            null
        }

    public companion object {
        public const val MAX_ENTRIES: Int = 20
        public val DEDUPE_WINDOW: Duration = 10.seconds
        public const val MESSAGE_LIMIT: Int = 2_000
        public const val STACK_LIMIT: Int = 12_000
        public const val SOURCE_LIMIT: Int = 80
        internal const val UNKNOWN_MESSAGE = "Unknown application error"
        private val REPORT_JSON = Json { prettyPrint = true }
        private val log = AppLog.tagged("diagnostics")

        /** The failure's stable label, e.g. `Network · Reset`; never its details, never a class name. */
        internal fun failureLabel(failure: Failure): String = failure.label()
    }
}

private fun <T> List<T>.toPersistent(): PersistentList<T> = persistentListOf<T>().addingAll(this)
