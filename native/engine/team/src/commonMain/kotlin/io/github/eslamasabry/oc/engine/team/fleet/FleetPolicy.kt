package io.github.eslamasabry.oc.engine.team.fleet

// ---- the host front's read-only policy document (Dart `models/policy.dart`)
// Lenient like Dart: a missing field is its zero value, nothing here throws on odd JSON.

/** Dart `OrchestrationSupervision`: how closely the host's owner supervises the agents. */
public enum class FleetSupervision {
    High,
    Balanced,
    Autonomous,
    ;

    public companion object {
        /** `high` / `balanced` / `autonomous`, case-insensitive; anything else is [Balanced], the host's own default. */
        public fun parse(value: String?): FleetSupervision =
            when (value?.trim()?.lowercase()) {
                "high" -> High
                "autonomous" -> Autonomous
                else -> Balanced
            }
    }
}

/** Dart `PolicyBoundary`: one rule the host applies, in its own words ("Never merge without approval"). */
public data class FleetBoundary(val key: String, val text: String)

/** Dart `OrchestrationPolicy`: the front's read-only policy for one rig. The phone shows the host's rules, never edits them. */
public data class FleetPolicy(val rig: String? = null, val supervision: FleetSupervision = FleetSupervision.Balanced, val boundaries: List<FleetBoundary> = emptyList())

internal object FleetPolicyWire {
    /** Dart `OrchestrationPolicy.fromJson`: null when the document carries neither `supervision` nor `boundaries`. */
    fun policy(json: Json): FleetPolicy? {
        if (!json.containsKey("supervision") && !json.containsKey("boundaries")) return null
        return FleetPolicy(
            rig = J.text(json, "rig"),
            supervision = FleetSupervision.parse(J.string(json, "supervision")),
            boundaries = J.list(json, "boundaries") { b -> J.text(b, "key")?.let { FleetBoundary(it, J.text(b, "text") ?: it) } }.filterNotNull(),
        )
    }
}
