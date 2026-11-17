package io.github.eslamasabry.oc.ui.gate

import org.junit.AssumptionViolatedException

/**
 * What a guard test does when the reference it compares with (a Flutter golden, a seeded fixture) is not on disk.
 * One rule for every harness, never a per-test copy (tool/ci/LANES.md, "Guards that cannot run"):
 *
 *  - In a lane that fetches its references (CI `full` and `candidate`, or any run with `OC_REFERENCES=required`) a
 *    missing reference FAILS, with a plain message naming it.
 *  - Anywhere else the test is SKIPPED (a JUnit assumption) and prints one `OC-GUARD-SKIPPED` line, which
 *    `tool/ci/test_report.py` counts and `summary.py` / `release_verdict.py` refuse for a candidate.
 *    A missing reference never reads as a pass.
 */
object ReferenceGuard {
    const val MARKER = "OC-GUARD-SKIPPED"

    private val requiredLanes = setOf("full", "candidate")

    /** True when this run is expected to have its references (the lane fetches them). */
    fun required(env: Map<String, String> = System.getenv()): Boolean =
        env["OC_REFERENCES"] == "required" || env["LANE"] in requiredLanes

    enum class Outcome { Fail, Skip }

    fun outcome(env: Map<String, String> = System.getenv()): Outcome = if (required(env)) Outcome.Fail else Outcome.Skip

    /** The plain message of a missing reference in a lane that must have it. */
    fun failureMessage(what: String): String =
        "reference $what is missing: this lane must fetch it (native/tool/design/fetch_all_goldens.sh)"

    /** The line a skipped guard prints; the CI report counts these. */
    fun skipLine(what: String): String = "$MARKER $what"

    /** Call when the reference [what] is not there. Never returns: fails or skips by the lane. */
    fun missing(what: String, env: Map<String, String> = System.getenv()): Nothing {
        if (outcome(env) == Outcome.Fail) throw AssertionError(failureMessage(what))
        System.out.append(skipLine(what)).append('\n').flush() // the CI report counts this line
        throw AssumptionViolatedException("guard skipped, reference $what is missing (references not fetched in this lane)")
    }
}
