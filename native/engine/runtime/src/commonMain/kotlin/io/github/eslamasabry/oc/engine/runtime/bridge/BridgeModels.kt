package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.ids.ProfileId

/** The answer of one `run` (port of `BuiltinLinuxRunResult`). [output] is the last 64 KB of stdout and stderr. */
public data class RunResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0

    override fun toString(): String = "RunResult(exitCode=$exitCode, output=<${output.length} chars>)"
}

/**
 * Admission of one named chat lease, independent of other work's wake lock (port of `BuiltinWorkLeaseStatus`).
 * [held]: this lease has an admitted CPU hold, never the aggregate work-lock state. [capped]: native monotonic
 * lifetime expired or exhausted this logical lease.
 */
public data class WorkLeaseStatus(val held: Boolean = false, val capped: Boolean = false) {
    public companion object {
        public val NotAdmitted: WorkLeaseStatus = WorkLeaseStatus()

        /** Both keys must be booleans, and held-and-capped together is contradictory: anything else is not admitted. */
        public fun parse(reply: Any?): WorkLeaseStatus {
            val map = reply as? Map<*, *> ?: return NotAdmitted
            val held = map["held"] as? Boolean ?: return NotAdmitted
            val capped = map["capped"] as? Boolean ?: return NotAdmitted
            if (held && capped) return NotAdmitted
            return WorkLeaseStatus(held, capped)
        }
    }
}

/**
 * Selects a canonical native command (port of `BuiltinServerRestoreRecipe`). Contains no script or account data; the
 * runtime is always OpenCode 2, the only startable one.
 */
public data class RestoreRecipe(val profileId: ProfileId) {
    public fun toMap(): Map<String, Any?> = mapOf("version" to 1, "profileId" to profileId.raw, "runtime" to RUNTIME)

    public companion object {
        /** Dart `TermuxRuntime.openCode2.name`. */
        public const val RUNTIME: String = "openCode2"
    }
}
