package io.github.eslamasabry.oc.engine.runtime.recovery

import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * The two "What runs by itself" choices the phone runtime's automatic work reads (Flutter `AutomationBehavior`
 * `restartPhoneServer` and `thermalRecovery`, lib/state/automation_policy.dart). [wire] is the stored enum name inside
 * `oc.automation.<profileId>`; the binding reads the one shared per-profile policy owner (`:engine:settings`, plan 05
 * Appendix A). Both default on; a corrupt or unreadable policy allows nothing.
 */
public enum class RuntimeAutomation(public val wire: String) {
    /** Bring the phone's OpenCode back after Android ended the app. */
    RestartPhoneServer("restartPhoneServer"),

    /** Give back what the heat guard paused once the phone is cool. Never disables the protective pause or stop. */
    ThermalRecovery("thermalRecovery"),
}

/**
 * The per-profile automation policy, read fresh on every call: a choice changed mid-run is seen at the next step. Not
 * suspend: the binding answers the shared policy owner's current value (its StateFlow), as Flutter reads the shared
 * controller's cached value, so it can also serve as a start's `stillWanted`.
 */
public fun interface AutomationGate {
    public fun allows(profile: ProfileId, behavior: RuntimeAutomation): Boolean
}

/**
 * Automatic recovery that asks the policy before every step (port of `GasCityThermalTeams._allowsRecovery` checks and
 * `autoStartIfStopped(mayStart:)`): a choice turned off while a step was reading stops the next step from running.
 */
public class RecoveryGate(
    private val gate: AutomationGate,
    private val behavior: RuntimeAutomation,
) {
    public fun allows(profile: ProfileId): Boolean = gate.allows(profile, behavior)

    /**
     * Runs [steps] in order for [profile], asking [allows] before each (the first included). True only when every step
     * ran and confirmed; false at the first refusal or unconfirmed step, and nothing after it runs.
     */
    public suspend fun steps(profile: ProfileId, steps: List<suspend () -> Boolean>): Boolean {
        for (step in steps) {
            if (!allows(profile)) return false
            if (!step()) return false
        }
        return true
    }
}
