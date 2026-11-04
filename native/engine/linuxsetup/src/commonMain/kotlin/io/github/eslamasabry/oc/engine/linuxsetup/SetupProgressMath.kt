package io.github.eslamasabry.oc.engine.linuxsetup

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ids] plus every required component, expanded by `dependsOn` and put in dependency order (registry order among
 * equals). A dependency cycle is a programming error in the registry: it fails with [IllegalStateException], never
 * a hang.
 */
public fun expandSelection(registry: List<SetupComponent>, ids: Set<String>): List<SetupComponent> {
    val byId = registry.associateBy { it.id }
    val wanted = linkedSetOf<String>()

    fun want(id: String) {
        val component = byId[id] ?: return
        if (!wanted.add(id)) return
        component.dependsOn.forEach(::want)
    }
    for (component in registry) if (component.required || component.id in ids) want(component.id)

    val ordered = mutableListOf<SetupComponent>()
    val placed = mutableSetOf<String>()
    val visiting = mutableSetOf<String>()

    fun place(component: SetupComponent) {
        if (component.id in placed) return
        check(visiting.add(component.id)) { "Setup components depend on each other: ${component.id}" }
        for (dependency in component.dependsOn) {
            val other = byId[dependency]
            if (other != null && dependency in wanted) place(other)
        }
        visiting.remove(component.id)
        placed.add(component.id)
        ordered.add(component)
    }
    for (component in registry) if (component.id in wanted) place(component)
    return ordered
}

/**
 * 0..1 over [components], each weighted by its estimate. A running one counts its real fraction, or half when it only
 * reports stages, and never less than it already reached ([floors], updated here).
 */
public fun overallFraction(
    components: List<SetupComponent>,
    progress: List<ComponentProgress>,
    floors: MutableMap<String, Double>? = null,
): Double {
    var total = 0.0
    var done = 0.0
    var i = 0
    while (i < components.size && i < progress.size) {
        val weight = components[i].estimatedSeconds.toDouble()
        total += weight
        done += weight * fraction(progress[i], floors)
        i++
    }
    if (total <= 0) return 0.0
    return (done / total).coerceIn(0.0, 1.0)
}

private fun fraction(progress: ComponentProgress, floors: MutableMap<String, Double>?): Double {
    val raw =
        when (progress.state) {
            ComponentState.Done, ComponentState.Skipped -> 1.0
            ComponentState.Running -> progress.fraction ?: 0.5
            else -> 0.0
        }
    if (floors == null) return raw
    if (progress.state == ComponentState.Running || progress.state == ComponentState.Done ||
        progress.state == ComponentState.Skipped
    ) {
        // A running component never shows as finished before it is.
        val capped = if (progress.state == ComponentState.Running) raw.coerceIn(0.0, 0.98) else raw
        val floor = floors[progress.id] ?: 0.0
        val best = if (capped > floor) capped else floor
        floors[progress.id] = best
        return best
    }
    // Pending or failed after an interruption: keep what it had reached.
    return floors[progress.id] ?: raw
}

/**
 * Seconds left: the estimates not yet done, scaled by how fast this job has really gone so far. Null until it has made
 * real progress for at least [minimum], so an early guess never shows.
 */
public fun estimateEta(
    components: List<SetupComponent>,
    progress: List<ComponentProgress>,
    overall: Double,
    elapsed: Duration,
    minimum: Duration = 10.seconds,
): Int? {
    if (elapsed < minimum) return null
    var total = 0.0
    var before = 0.0
    var i = 0
    while (i < components.size && i < progress.size) {
        val weight = components[i].estimatedSeconds.toDouble()
        total += weight
        if (progress[i].state == ComponentState.Skipped) before += weight
        i++
    }
    val doneNow = overall * total - before
    if (doneNow <= 0) return null
    val remaining = total * (1 - overall)
    if (remaining <= 0) return 0
    val pace = elapsed.inWholeMilliseconds / 1000.0 / doneNow
    return ceil(remaining * pace).toInt()
}

/**
 * The params a new run gets. Continue keeps what the stopped job was for unless the caller asks for something else;
 * job facts ([SetupJobParams]) are laid over the stopped job's params. Legacy OpenCode 1 runtime/version requests
 * normalize to the only supported runtime, OpenCode 2.
 */
public fun effectiveJobParams(
    requested: Map<String, Map<String, String>>,
    last: SetupJobRecord?,
): Map<String, Map<String, String>> {
    val facts = requested[SetupJobParams.KEY]
    val components = requested.filterKeys { it != SetupJobParams.KEY }
    val base = if (components.isEmpty() && last != null && last.canContinue) last.params else components
    val openCode = (base[SetupComponentIds.OPENCODE] ?: emptyMap()).toMutableMap()
    if (openCode["runtime"] == "opencode1" || (openCode["version"]?.startsWith("1.") == true)) {
        openCode.remove("version")
    }
    openCode["runtime"] = "opencode2"
    val out = LinkedHashMap(base)
    out[SetupComponentIds.OPENCODE] = openCode
    if (facts != null) out[SetupJobParams.KEY] = (base[SetupJobParams.KEY] ?: emptyMap()) + facts
    return out
}
