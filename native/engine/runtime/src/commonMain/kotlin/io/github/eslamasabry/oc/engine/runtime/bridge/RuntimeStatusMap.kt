package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.engine.runtime.host.RestorePhase
import io.github.eslamasabry.oc.engine.runtime.host.RestoreReason
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads the native `status` map into the frozen [RuntimeStatus] contract (port of `BuiltinLinuxStatus.fromMap` and
 * the restore parsers of lib/builtin/builtin_server_restore.dart). Unknown keys, values and types never fabricate
 * state: an unknown phase reads [RuntimePhase.Idle], a missing intent reads "not wanted", and an idle receipt is valid
 * only when every admission field is present with its exact type.
 */
public object RuntimeStatusMap {
    /** What a runner without the channel honestly has: nothing installed, nothing running, no idle receipt. */
    public val Absent: RuntimeStatus = RuntimeStatus(abi = "", serverIdleReceiptValid = false)

    private val idleBooleans =
        listOf("serverIdleEnabled", "serverIdleStopped", "serverIdleHelperStopped", "serverRestartWanted", "serverRunning")

    public fun parse(map: Map<*, *>): RuntimeStatus {
        val message = map["message"]
        val idleMinutes = idleInteger(map["serverIdleMinutes"])
        val idleGeneration = idleInteger(map["serverIdleGeneration"])
        val idleStoppedAny = map["serverIdleStopped"] == true || map["serverIdleHelperStopped"] == true
        val idleValid =
            map["serverIdlePolicySupported"] == true &&
                idleBooleans.all { map[it] is Boolean } &&
                idleMinutes != null && idleMinutes in 1..60 &&
                idleGeneration != null &&
                (!idleStoppedAny || idleGeneration > 0)
        return RuntimeStatus(
            installed = map["installed"] == true,
            phase = phase(map["phase"]),
            message = (message as? String)?.takeIf { it.isNotBlank() },
            serverRunning = map["serverRunning"] == true,
            serverRestartWanted = map["serverRestartWanted"] == true,
            serverRecoveryGeneration = asLong(map["serverRecoveryGeneration"]),
            serverRecoveryAuthority = map["serverRecoveryAuthority"] == true,
            serverRecoveryScheduled = map["serverRecoveryScheduled"] == true,
            restorePhase = restorePhase(map["restorePhase"]),
            restoreReason = restoreReason(map["restoreReason"]),
            serverPort = asLong(map["serverPort"])?.toInt(),
            serverUptime = asLong(map["serverUptimeMs"])?.takeIf { it >= 0 }?.milliseconds,
            services = (map["services"] as? List<*>).orEmpty().filterIsInstance<String>(),
            abi = map["abi"]?.toString() ?: "",
            bytesUsed = asLong(map["bytesUsed"]),
            serverIdlePolicySupported = map["serverIdlePolicySupported"] == true,
            serverIdleEnabled = map["serverIdleEnabled"] == true,
            serverIdleMinutes = idleMinutes?.toInt(),
            serverIdleStopped = map["serverIdleStopped"] == true,
            serverIdleHelperStopped = map["serverIdleHelperStopped"] == true,
            serverIdleGeneration = idleGeneration,
            serverIdleReceiptValid = idleValid,
        )
    }

    public fun phase(value: Any?): RuntimePhase =
        when (value) {
            "installing" -> RuntimePhase.Installing
            "ready" -> RuntimePhase.Ready
            "failed" -> RuntimePhase.Failed
            else -> RuntimePhase.Idle
        }

    /** Null and `idle` read idle; anything unknown reads [RestorePhase.Unavailable], never a raw copy. */
    public fun restorePhase(value: Any?): RestorePhase =
        when (value) {
            null, "idle" -> RestorePhase.Idle
            "waiting" -> RestorePhase.Waiting
            "restoring" -> RestorePhase.Restoring
            else -> RestorePhase.Unavailable
        }

    /**
     * Fixed reasons only; anything unknown is [RestoreReason.OwnershipUnknown]. `idleStopped` (sent by today's
     * BuiltinLinux.kt, which the Dart parser predates) reads [RestoreReason.IdleStopped].
     */
    public fun restoreReason(value: Any?): RestoreReason? =
        when (value) {
            null -> null
            "stopped" -> RestoreReason.Stopped
            "policyDisabled" -> RestoreReason.PolicyDisabled
            "budgetExhausted" -> RestoreReason.BudgetExhausted
            "storageUnavailable" -> RestoreReason.StorageUnavailable
            "componentRecoveryRequired" -> RestoreReason.ComponentRecoveryRequired
            "systemTimeout" -> RestoreReason.SystemTimeout
            "idleStopped" -> RestoreReason.IdleStopped
            else -> RestoreReason.OwnershipUnknown
        }

    /** Dart `value is num ? value.toInt() : null`: any finite number, truncated. */
    internal fun asLong(value: Any?): Long? =
        when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Short -> value.toLong()
            is Byte -> value.toLong()
            is Double -> value.takeIf { it.isFinite() }?.toLong()
            is Float -> value.takeIf { it.isFinite() }?.toLong()
            else -> null
        }

    /** Dart `value is int && value >= 0`: an integer type only; 9.0 and "9" are not. */
    internal fun exactInteger(value: Any?): Long? =
        when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> null
        }

    private fun idleInteger(value: Any?): Long? = exactInteger(value)?.takeIf { it >= 0 }
}
