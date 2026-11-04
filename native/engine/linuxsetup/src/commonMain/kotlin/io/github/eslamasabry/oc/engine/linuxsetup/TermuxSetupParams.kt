package io.github.eslamasabry.oc.engine.linuxsetup

import io.github.eslamasabry.oc.core.security.Redactor

/**
 * The Termux setup host's parameter allowlist (port of TermuxSetupHost.validateParams): only a supported runtime,
 * a plain version, and the job facts may reach the scripts; anything else, or anything credential-shaped, refuses
 * the whole job with code `invalid_setup` before any install script is generated.
 */
public object TermuxSetupParams {
    public const val INVALID_CODE: String = "invalid_setup"

    private val allowed = mapOf("opencode" to setOf("runtime", "version"), SetupJobParams.KEY to setOf("first", "adding"))
    private val version = Regex("""^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$""")
    private val adding = Regex("""^[a-z][a-z0-9_-]*(?:,[a-z][a-z0-9_-]*)*$""")

    /** True when every group, key and value is allowed. */
    public fun isValid(params: Map<String, Map<String, String>>, redactor: Redactor = Redactor()): Boolean {
        for ((group, values) in params) {
            val keys = allowed[group] ?: return false
            for ((key, value) in values) {
                if (key !in keys || redactor.containsSecret(value)) return false
                val ok =
                    when (key) {
                        "runtime" -> value == "opencode2"
                        "version" -> version.matches(value)
                        "first" -> value == "0" || value == "1"
                        "adding" -> value.isEmpty() || adding.matches(value)
                        else -> false
                    }
                if (!ok) return false
            }
        }
        return true
    }
}
