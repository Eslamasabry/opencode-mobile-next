package io.github.eslamasabry.oc.engine.analytics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Crash and error reporting through the same vendor-neutral layer as usage analytics (owner decision 2026-11-07:
 * "anything custom built goes through the analytics sdk so tomorrow it can be switched to any other vendor").
 *
 * The app calls [Reporting]. [ErrorReporter] checks consent, redacts everything with [ReportRedactor] and only then
 * hands a [SafeReport] to ONE [ReportSink]. A vendor adapter (Crashlytics today, in the Android layer) implements
 * [ReportSink] and sees nothing but a [SafeReport], so swapping the vendor changes one class.
 */

/** What the app reports. Never throws, never blocks. Nothing leaves the phone while [reportingConsent] is false. */
public interface Reporting {
    /** The person's answer; applied to the sink at once. */
    public val reportingConsent: StateFlow<Boolean>

    public fun setReportingConsent(granted: Boolean)

    /**
     * A fatal crash: [category] is an exception class name and [safeStack] holds `Class.method:line` frames only
     * (no message, no cause text, no file path).
     */
    public fun reportCrash(category: String, safeStack: List<String>)

    /** A non-fatal failure: [name] is a registered snake_case id and [keys] are short enum-like facts, never content. */
    public fun reportFailure(name: String, keys: Map<String, String> = emptyMap())

    /** A short redacted trail of what happened before a report. */
    public fun breadcrumb(text: String)
}

/** The default: reports nothing. */
public object NoReporting : Reporting {
    override val reportingConsent: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override fun setReportingConsent(granted: Boolean): Unit = Unit

    override fun reportCrash(category: String, safeStack: List<String>): Unit = Unit

    override fun reportFailure(name: String, keys: Map<String, String>): Unit = Unit

    override fun breadcrumb(text: String): Unit = Unit
}

/** A report that has been through [ReportRedactor]. Every string is safe to leave the phone. */
public data class SafeReport(
    val kind: ReportKind,
    val name: String,
    val keys: Map<String, String> = emptyMap(),
    val stack: List<String> = emptyList(),
)

public enum class ReportKind { Crash, Failure }

/**
 * Where reports go: one per build. Vendor adapters implement this; they never see raw input. A sink never throws for an
 * expected failure.
 */
public interface ReportSink {
    public val id: SinkId

    /** The vendor's own collection switch: false means it keeps and sends nothing. */
    public fun setCollectionEnabled(enabled: Boolean)

    public fun breadcrumb(text: String)

    public fun record(report: SafeReport)
}

/** The build without a vendor config: drops everything. */
public object NoOpReportSink : ReportSink {
    override val id: SinkId get() = SinkId.None

    override fun setCollectionEnabled(enabled: Boolean): Unit = Unit

    override fun breadcrumb(text: String): Unit = Unit

    override fun record(report: SafeReport): Unit = Unit
}

/**
 * The one [Reporting]. [initialConsent] is the stored answer (or the flavor default); [persist] stores a change.
 */
public class ErrorReporter(
    private val sink: ReportSink,
    initialConsent: Boolean,
    private val persist: (Boolean) -> Unit = {},
) : Reporting {
    private val consent = MutableStateFlow(initialConsent)
    override val reportingConsent: StateFlow<Boolean> = consent.asStateFlow()

    init {
        safely { sink.setCollectionEnabled(initialConsent) }
    }

    override fun setReportingConsent(granted: Boolean) {
        consent.value = granted
        safely { sink.setCollectionEnabled(granted) }
        safely { persist(granted) }
    }

    override fun reportCrash(category: String, safeStack: List<String>) {
        if (!consent.value) return
        val report =
            SafeReport(ReportKind.Crash, ReportRedactor.category(category), stack = ReportRedactor.stack(safeStack))
        safely { sink.record(report) }
    }

    override fun reportFailure(name: String, keys: Map<String, String>) {
        if (!consent.value) return
        val report = SafeReport(ReportKind.Failure, ReportRedactor.name(name), ReportRedactor.keys(keys))
        safely { sink.record(report) }
    }

    override fun breadcrumb(text: String) {
        if (!consent.value) return
        val safe = ReportRedactor.breadcrumb(text)
        safely { sink.breadcrumb(safe) }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Reporting must never be the reason the app fails.
        }
    }
}

/**
 * The privacy gate for reports (the same [LeakDetector] as analytics): a value that looks like a link, path, address,
 * credential, id or sentence becomes `[redacted]`; a name or key that is not snake_case is replaced or dropped.
 */
public object ReportRedactor {
    public const val REDACTED: String = "[redacted]"
    private const val MAX_KEYS = 8
    private const val MAX_FRAMES = 40
    private const val MAX_BREADCRUMB = 60
    private val NAME = Regex("^[a-z][a-z0-9_]{0,39}$")
    private val CLASS = Regex("^[A-Za-z_][A-Za-z0-9_.$]{0,119}$")
    private val FRAME = Regex("^[A-Za-z_$][A-Za-z0-9_$.<>]{0,150}:\\d{1,6}$")
    private val VERSION = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(-[a-z0-9]{1,12})?$")

    public fun name(raw: String): String = if (NAME.matches(raw)) raw else "unnamed_failure"

    public fun category(raw: String): String =
        if (CLASS.matches(raw) && LeakDetector.detect(raw.replace('.', '_').replace('$', '_')) == null) raw else "java.lang.Throwable"

    public fun keys(raw: Map<String, String>): Map<String, String> =
        raw.entries
            .asSequence()
            .filter { NAME.matches(it.key) && LeakDetector.detect(it.key) == null }
            .take(MAX_KEYS)
            .associate { it.key to value(it.value) }

    public fun value(raw: String): String =
        if (VERSION.matches(raw) || LeakDetector.detect(raw) == null) raw else REDACTED

    public fun stack(raw: List<String>): List<String> = raw.filter { FRAME.matches(it) }.take(MAX_FRAMES)

    /** Lower-case words only, each passing [LeakDetector]; anything else is dropped to [REDACTED]. */
    public fun breadcrumb(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty() || text.length > MAX_BREADCRUMB) return REDACTED
        val words = text.split(' ')
        return if (words.all { it.isNotEmpty() && value(it) == it && it.all { c -> c.isLowerCase() || c.isDigit() || c == '_' || c == '-' || c == '.' } }) {
            text
        } else {
            REDACTED
        }
    }
}
