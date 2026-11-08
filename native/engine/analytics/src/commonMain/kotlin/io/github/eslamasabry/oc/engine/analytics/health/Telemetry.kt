package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.LeakDetector
import io.github.eslamasabry.oc.engine.analytics.ReportRedactor
import io.github.eslamasabry.oc.engine.analytics.Reporting
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.ProductFeature
import io.github.eslamasabry.oc.engine.analytics.product.cleanProductProps
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * B-180/B-190: the app's own analytics, crash reports and Performance layer, the twin of Flutter 2.1.5
 * `lib/telemetry/telemetry.dart`. Vendor-neutral: app code depends only on [Telemetry]; the vendor (Firebase
 * Crashlytics, Analytics and Performance in `:androidApp`) is one [TelemetryVendor], so a vendor swap is one class.
 *
 * Rules every report obeys, enforced here and not left to each call site:
 * - nothing reaches the vendor while the matching consent is off (both are off until the person turns one on);
 * - event, report, key and trace names are fixed identifiers, never prose;
 * - every value is a listed word, a listed range or a matched number; anything else is dropped, never cleaned up:
 *   no messages, paths, URLs, host names, accounts or tokens;
 * - crash stacks keep only `Class.method:line` frames ([ReportRedactor.stack]).
 */

/** The two answers: `usage` (sections B, C and D) and `crashes` (section A). Both off by default. */
public data class TelemetryConsent(val usage: Boolean = false, val crashes: Boolean = false)

/** What the app reports. Never throws, never blocks. Every send is dropped unless its consent is on. */
public interface Telemetry : Reporting {
    /** Usage and app health: the events, Performance traces and device context. */
    public val usage: StateFlow<Boolean>

    /** Crash reports. The same flow as [reportingConsent]. */
    public val crashes: StateFlow<Boolean>

    /** Whether a vendor is built into this build at all (the private Firebase config was present). */
    public val available: Boolean

    public val consent: TelemetryConsent
        get() = TelemetryConsent(usage.value, crashes.value)

    /**
     * Both answers at once: applies them to the vendor at once (off stops collection and forgets what the vendor kept
     * on the phone), then stores them. False when the store refused (the answers still apply for this run).
     */
    public suspend fun setConsent(usage: Boolean, crashes: Boolean): Boolean

    /** A failure the app handled but wants counted (`setup_step_failed`, `{step: node}`). Needs `crashes`. */
    public fun recordFailure(name: String, keys: Map<String, Any?> = emptyMap())

    /** The Crashlytics custom keys (section A): only listed keys with listed values. Needs `crashes` to leave. */
    public fun setHealthKeys(keys: Map<String, Any?>)

    /** The device-context user properties (section D). Needs `usage`. */
    public fun setUserProperties(properties: Map<String, Any?>)

    /** A product or health event; only events and values listed in the schema are sent. Needs `usage`. */
    public fun track(event: ProductEvent, props: Map<String, Any?> = emptyMap())

    /** A finished custom Performance trace; only [PerfSchema] names and values. Needs `usage`. */
    public fun performanceTrace(name: String, elapsedMs: Long, attributes: Map<String, Any?> = emptyMap())

    /**
     * Sends the person's problem report: [redactedText] is exactly what they saw in the preview and agreed to send.
     * Needs no switch (it is asked each time) but a vendor. Returns the report code, or null when it was not sent.
     */
    public suspend fun sendProblemReport(redactedText: String): String?

    /** The code to quote for this install ("Report code" in Settings › About), or null without a vendor. */
    public suspend fun reportCode(): String?

    /** The custom keys last set, for the problem report's header. */
    public val healthKeys: Map<String, String>

    /** The app's own log ring (read only by the problem report). */
    public val ring: AppLogRing

    public fun feature(feature: ProductFeature) {
        track(ProductEvent.FeatureUsed, mapOf("feature" to feature))
    }
}

/**
 * The vendor side. Receives only what [TelemetryService] already cleaned, and only while the matching consent is on,
 * except [setCollection] and [problemReport]. Calls must not throw for an expected failure.
 */
public interface TelemetryVendor {
    /**
     * Starts or stops collection: analytics and Performance follow [usage], crash reports follow [crashes]. Turning
     * one off also asks the vendor to forget what it has not sent (unsent crash reports; the analytics data and its
     * random install id).
     */
    public fun setCollection(usage: Boolean, crashes: Boolean)

    public fun crash(category: String, frames: List<String>, fatal: Boolean)

    public fun failure(name: String, keys: Map<String, String>)

    public fun breadcrumb(name: String)

    public fun event(name: String, props: Map<String, String>)

    public fun setKeys(keys: Map<String, String>)

    public fun setUserProperties(properties: Map<String, String>)

    /** One finished trace, its [durationMs] kept as the metric [PerfSchema.DURATION_METRIC]. */
    public fun perfTrace(name: String, attributes: Map<String, String>, durationMs: Long)

    /**
     * One non-fatal report with [text] (already redacted, shown to and accepted by the person) attached as logs.
     * [collecting] says whether crash collection is on; when off, the vendor sends this report and nothing else.
     */
    public suspend fun problemReport(text: String, collecting: Boolean)

    /** The vendor's id for this install, or null when it gives none (analytics off). Only 8 characters are used. */
    public suspend fun appInstanceId(): String?
}

/** The build without the private Firebase config: does nothing, keeps nothing. */
public object NoOpTelemetryVendor : TelemetryVendor {
    override fun setCollection(usage: Boolean, crashes: Boolean): Unit = Unit

    override fun crash(category: String, frames: List<String>, fatal: Boolean): Unit = Unit

    override fun failure(name: String, keys: Map<String, String>): Unit = Unit

    override fun breadcrumb(name: String): Unit = Unit

    override fun event(name: String, props: Map<String, String>): Unit = Unit

    override fun setKeys(keys: Map<String, String>): Unit = Unit

    override fun setUserProperties(properties: Map<String, String>): Unit = Unit

    override fun perfTrace(name: String, attributes: Map<String, String>, durationMs: Long): Unit = Unit

    override suspend fun problemReport(text: String, collecting: Boolean): Unit = Unit

    override suspend fun appInstanceId(): String? = null
}

/** Where the two answers are kept (Flutter's keys in the adopted preferences, in the app). */
public interface TelemetryConsentStore {
    /** Saves both answers; false when the store refused. */
    public suspend fun write(consent: TelemetryConsent): Boolean
}

/** Where the report counter and the local fallback id are kept (`oc.telemetry.reportCounter`, `.installCode`). */
public interface TelemetryReportStore {
    public fun readCounter(): Int

    public suspend fun writeCounter(value: Int)

    public fun readLocalId(): String?

    public suspend fun writeLocalId(value: String)
}

public class MemoryTelemetryReportStore : TelemetryReportStore {
    private var counter = 0
    private var id: String? = null

    override fun readCounter(): Int = counter

    override suspend fun writeCounter(value: Int) {
        counter = value
    }

    override fun readLocalId(): String? = id

    override suspend fun writeLocalId(value: String) {
        id = value
    }
}

/**
 * [Telemetry] over one [TelemetryVendor]: consent gates, cleaning, never throws.
 *
 * It is built first thing at start-up with [early] (the app's private early copy of the answers, both off when there
 * is none) so the earliest crash of a person who agreed is kept; nothing is applied to the vendor then. [start] runs
 * once the stored answers are readable: it adopts them and applies them to the vendor, as Flutter's `start()` does.
 * Until then the vendor keeps its own last setting (off in the manifest on a first run).
 */
public class TelemetryService(
    private val vendor: TelemetryVendor,
    override val available: Boolean,
    early: TelemetryConsent = TelemetryConsent(),
    private val scope: CoroutineScope,
    private var store: TelemetryConsentStore? = null,
    private var reports: TelemetryReportStore = MemoryTelemetryReportStore(),
    override val ring: AppLogRing = AppLogRing(),
    private val random: Random = Random.Default,
) : Telemetry {
    private val usageFlow = MutableStateFlow(early.usage)
    private val crashesFlow = MutableStateFlow(early.crashes)
    private val keys = LinkedHashMap<String, String>()

    override val usage: StateFlow<Boolean> = usageFlow.asStateFlow()
    override val crashes: StateFlow<Boolean> = crashesFlow.asStateFlow()
    override val reportingConsent: StateFlow<Boolean> = crashes

    override val healthKeys: Map<String, String>
        get() = keys.toMap()

    /** Adopts the stored answers and applies them to the vendor. Call once the store is readable. */
    public fun start(stored: TelemetryConsent, store: TelemetryConsentStore, reports: TelemetryReportStore) {
        this.store = store
        this.reports = reports
        usageFlow.value = stored.usage
        crashesFlow.value = stored.crashes
        // Collection flips first: the vendor drops anything sent before it was enabled (Flutter dbbed699b).
        if (available) safely { vendor.setCollection(stored.usage, stored.crashes) }
    }

    override suspend fun setConsent(usage: Boolean, crashes: Boolean): Boolean {
        if (usage == usageFlow.value && crashes == crashesFlow.value) return true
        apply(usage, crashes)
        return persist(TelemetryConsent(usage, crashes))
    }

    /** The crash answer alone (the older single port); stored in the background. */
    override fun setReportingConsent(granted: Boolean) {
        if (granted == crashesFlow.value) return
        val next = TelemetryConsent(usageFlow.value, granted)
        apply(next.usage, next.crashes)
        scope.launch { persist(next) }
    }

    private fun apply(usage: Boolean, crashes: Boolean) {
        if (available) safely { vendor.setCollection(usage, crashes) }
        // Listeners send what needs collection on already, so the flows change after the vendor switch.
        usageFlow.value = usage
        crashesFlow.value = crashes
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun persist(consent: TelemetryConsent): Boolean {
        val target = store ?: return true
        return try {
            target.write(consent)
        } catch (_: Throwable) {
            false
        }
    }

    private val crashLive: Boolean
        get() = crashesFlow.value && available

    private val usageLive: Boolean
        get() = usageFlow.value && available

    override fun reportCrash(category: String, safeStack: List<String>) {
        if (!crashLive) return
        val name = ReportRedactor.category(category)
        ring.add("error ${name.substringAfterLast('.')}")
        val frames = ReportRedactor.stack(safeStack)
        safely { vendor.crash(name, frames, fatal = true) }
    }

    override fun reportFailure(name: String, keys: Map<String, String>) {
        recordFailure(name, keys)
    }

    override fun recordFailure(name: String, keys: Map<String, Any?>) {
        if (!crashLive) return
        val clean = cleanName(name) ?: return
        val safe = cleanKeys(keys)
        ring.add("failure $clean")
        safely { vendor.failure(clean, safe) }
    }

    override fun breadcrumb(text: String) {
        if (!BreadcrumbWords.isListed(text)) return
        // The app's own log keeps the trail on this phone whatever was agreed; it leaves only inside a problem
        // report the person approved.
        ring.add("crumb $text")
        if (!crashLive) return
        safely { vendor.breadcrumb(text) }
    }

    override fun setHealthKeys(keys: Map<String, Any?>) {
        val safe = cleanListed(keys, healthKeyWords, healthKeyPatterns)
        if (safe.isEmpty()) return
        this.keys.putAll(safe)
        if (!crashLive) return
        safely { vendor.setKeys(safe) }
    }

    override fun setUserProperties(properties: Map<String, Any?>) {
        if (!usageLive) return
        val safe = cleanListed(properties, healthPropertyWords, healthPropertyPatterns)
        if (safe.isEmpty()) return
        safely { vendor.setUserProperties(safe) }
    }

    override fun track(event: ProductEvent, props: Map<String, Any?>) {
        if (!usageLive) return
        val safe = cleanProductProps(event, props) ?: return
        ring.add("event ${event.wire}" + safe.entries.joinToString("") { " ${it.key}=${it.value}" })
        safely { vendor.event(event.wire, safe) }
    }

    override fun performanceTrace(name: String, elapsedMs: Long, attributes: Map<String, Any?>) {
        if (!usageLive) return
        val clean = PerfSchema.clean(name, attributes) ?: return
        val ms = elapsedMs.coerceAtLeast(0)
        ring.add("trace ${clean.name}" + clean.attributes.entries.joinToString("") { " ${it.key}=${it.value}" })
        safely { vendor.perfTrace(clean.name, clean.attributes, ms) }
    }

    override suspend fun sendProblemReport(redactedText: String): String? {
        if (!available) return null
        return try {
            val code = nextReportCode() ?: return null
            keys["report_code"] = code
            if (crashLive) vendor.setKeys(mapOf("report_code" to code))
            vendor.problemReport(redactedText, collecting = crashesFlow.value)
            code
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun reportCode(): String? {
        if (!available) return null
        return try {
            installPrefix()?.let { "$it-${reports.readCounter()}" }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun nextReportCode(): String? {
        val prefix = installPrefix() ?: return null
        val next = reports.readCounter() + 1
        reports.writeCounter(next)
        return "$prefix-$next"
    }

    /** The first 8 characters of the vendor's install id; without one, 8 random hex characters kept on this phone. */
    private suspend fun installPrefix(): String? {
        val fromVendor = vendor.appInstanceId()?.lowercase()
        HEX8.find(fromVendor.orEmpty())?.let { return it.value }
        var local = reports.readLocalId()
        if (local == null || !HEX8.matches(local)) {
            local = (1..8).joinToString("") { random.nextInt(16).toString(16) }
            reports.writeLocalId(local)
        }
        return local
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Telemetry must never be the reason the app fails.
        }
    }

    public companion object {
        private val HEX8 = Regex("^[0-9a-f]{8}")
        private val NAME = Regex("^[a-z][a-z0-9_]{0,39}$")
        private val TOKEN = Regex("^[A-Za-z0-9_.\\-]{1,40}$")
        private const val MAX_KEYS = 12
        private const val MAX_VALUE = 40

        /** A report or key name, or null when it is not a plain identifier. A short sentence becomes snake_case. */
        public fun cleanName(raw: String): String? {
            val name = raw.trim()
            if (NAME.matches(name)) return name
            if (!Regex("^[A-Za-z ]{1,40}$").matches(name)) return null
            val snake = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            return snake.takeIf { NAME.matches(it) }
        }

        /**
         * Keys whose name is an identifier and whose value is a number, a bool or a short token that passes the
         * analytics value check. A key the contract lists (`exit_reason`, ...) must hold a listed value. At most 12.
         */
        public fun cleanKeys(raw: Map<String, Any?>): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for ((key, v) in raw.entries.take(MAX_KEYS)) {
                if (!NAME.matches(key)) continue
                val value =
                    when (v) {
                        is Int, is Long -> v.toString()
                        is Boolean -> v.toString()
                        is String -> v
                        else -> null
                    } ?: continue
                if (!TOKEN.matches(value)) continue
                val listed = key in healthKeyWords || key in healthKeyPatterns
                if (listed) {
                    if (cleanListed(mapOf(key to value), healthKeyWords, healthKeyPatterns).isEmpty()) continue
                } else if (ReportRedactor.value(value) != value || LeakDetector.detect(key) != null) {
                    // Stricter than Flutter's KitRedact check: a host, address, path, id or credential shape drops.
                    continue
                }
                out[key] = value
            }
            return out
        }

        /** [raw] reduced to the keys of [words] or [patterns] whose value is listed or matches. The rest drops. */
        public fun cleanListed(
            raw: Map<String, Any?>,
            words: Map<String, Set<String>>,
            patterns: Map<String, Regex>,
        ): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for ((key, v) in raw) {
                val value =
                    when (v) {
                        is String -> v
                        is Int, is Long -> v.toString()
                        is Boolean -> if (v) "yes" else "no"
                        else -> null
                    } ?: continue
                if (value.length > MAX_VALUE) continue
                if (words[key]?.contains(value) == true || patterns[key]?.matches(value) == true) out[key] = value
            }
            return out
        }
    }
}
