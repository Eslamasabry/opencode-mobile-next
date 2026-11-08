package io.github.eslamasabry.oc.engine.analytics.health

/** Everything the vendor was handed, in order (Flutter `test/support/telemetry_recorder.dart`). */
internal class RecordingVendor(private val instanceId: String? = "abcdef0123456789") : TelemetryVendor {
    val collection = mutableListOf<TelemetryConsent>()
    val crashes = mutableListOf<Triple<String, List<String>, Boolean>>()
    val failures = mutableListOf<Pair<String, Map<String, String>>>()
    val crumbs = mutableListOf<String>()
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    val keys = mutableListOf<Map<String, String>>()
    val properties = mutableListOf<Map<String, String>>()
    val traces = mutableListOf<Triple<String, Map<String, String>, Long>>()
    val reports = mutableListOf<Pair<String, Boolean>>()

    override fun setCollection(usage: Boolean, crashes: Boolean) {
        collection += TelemetryConsent(usage, crashes)
    }

    override fun crash(category: String, frames: List<String>, fatal: Boolean) {
        crashes += Triple(category, frames, fatal)
    }

    override fun failure(name: String, keys: Map<String, String>) {
        failures += name to keys
    }

    override fun breadcrumb(name: String) {
        crumbs += name
    }

    override fun event(name: String, props: Map<String, String>) {
        events += name to props
    }

    override fun setKeys(keys: Map<String, String>) {
        this.keys += keys
    }

    override fun setUserProperties(properties: Map<String, String>) {
        this.properties += properties
    }

    override fun perfTrace(name: String, attributes: Map<String, String>, durationMs: Long) {
        traces += Triple(name, attributes, durationMs)
    }

    override suspend fun problemReport(text: String, collecting: Boolean) {
        reports += text to collecting
    }

    override suspend fun appInstanceId(): String? = instanceId

    /** Nothing at all was sent (the collection switch aside). */
    val silent: Boolean
        get() =
            crashes.isEmpty() && failures.isEmpty() && crumbs.isEmpty() && events.isEmpty() && keys.isEmpty() &&
                properties.isEmpty() && traces.isEmpty() && reports.isEmpty()
}
