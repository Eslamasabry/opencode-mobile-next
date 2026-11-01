package io.github.eslamasabry.oc.engine.runtime.host

import kotlin.time.Duration

/** The built-in Linux install phase (plan 11 §5.7). */
public enum class RuntimePhase { Idle, Installing, Ready, Failed }

/** Where a native event restore stands (plan 11 §5.7; shown natively for the first time, map Q5). */
public enum class RestorePhase { Idle, Waiting, Restoring, Unavailable }

/** Why a native restore did not bring the server back (plan 11 §5.7). */
public enum class RestoreReason {
    Stopped, PolicyDisabled, BudgetExhausted, OwnershipUnknown, StorageUnavailable, ComponentRecoveryRequired,
    SystemTimeout, IdleStopped,
}

/**
 * The status contract (frozen; plan 11 §5.7, builtin-linux §13): exactly today's keys. The Android binding parses the
 * native map as strictly as Dart's `BuiltinLinuxStatus.fromMap` (negative or fractional numbers read null; an invalid
 * idle receipt reads [serverIdleReceiptValid] = false, and idle and healing then refuse to act) and pushes a new value
 * on every native change. Copy never crosses this contract: [message] is the native install message for Details only.
 */
public data class RuntimeStatus(
    val installed: Boolean = false,
    val phase: RuntimePhase = RuntimePhase.Idle,
    val message: String? = null,
    val serverRunning: Boolean = false,
    val serverRestartWanted: Boolean = false,
    val serverRecoveryGeneration: Long? = null,
    val serverRecoveryAuthority: Boolean = false,
    val serverRecoveryScheduled: Boolean = false,
    val restorePhase: RestorePhase = RestorePhase.Idle,
    val restoreReason: RestoreReason? = null,
    val serverPort: Int? = null,
    val serverUptime: Duration? = null,
    val services: List<String> = emptyList(),
    val abi: String? = null,
    val bytesUsed: Long? = null,
    val serverIdlePolicySupported: Boolean = false,
    val serverIdleEnabled: Boolean = false,
    val serverIdleMinutes: Int? = null,
    val serverIdleStopped: Boolean = false,
    val serverIdleHelperStopped: Boolean = false,
    val serverIdleGeneration: Long? = null,
    val serverIdleReceiptValid: Boolean = true,
) {
    /**
     * The idle owner holds the server: an invalid receipt, an idle stop of the server or the agent helper, or an idle
     * generation with no server running. Healing and recovery do nothing then; only a foreground resume brings it back
     * (plan 11 §6.4 R6, §6.6). Port of phone_server_healing.dart `_idleBlocks`.
     */
    val idleBlocks: Boolean
        get() =
            serverIdlePolicySupported &&
                (!serverIdleReceiptValid || serverIdleStopped || serverIdleHelperStopped ||
                    ((serverIdleGeneration ?: 0) > 0 && !serverRunning))

    /** The app-open start also leaves alone any live idle generation (`_idleBlocksLaunch`). */
    val idleBlocksLaunch: Boolean
        get() = idleBlocks || (serverIdlePolicySupported && (serverIdleGeneration ?: 0) > 0)
}
