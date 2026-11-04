package io.github.eslamasabry.oc.engine.runtime.host

import kotlin.time.Duration.Companion.milliseconds

/** The frozen Linux status map. Unknown values never grant recovery or idle-resume admission. */
public object NativeRuntimeStatus {
    public fun read(map: Map<*, *>): RuntimeStatus {
        fun integer(key: String): Long? =
            when (val value = map[key]) {
                is Int -> value.toLong().takeIf { it >= 0 }
                is Long -> value.takeIf { it >= 0 }
                else -> null
            }
        val idleMinutes = integer("serverIdleMinutes")?.takeIf { it in 1..60 }?.toInt()
        val idleGeneration = integer("serverIdleGeneration")
        val idleStopped = map["serverIdleStopped"] == true
        val helperStopped = map["serverIdleHelperStopped"] == true
        val idleValid =
            map["serverIdlePolicySupported"] == true &&
                listOf(
                        "serverIdleEnabled",
                        "serverIdleStopped",
                        "serverIdleHelperStopped",
                        "serverRestartWanted",
                        "serverRunning",
                    )
                    .all { map[it] is Boolean } &&
                idleMinutes != null &&
                idleGeneration != null &&
                (!(idleStopped || helperStopped) || idleGeneration > 0)
        val restorePhase =
            when (map["restorePhase"]) {
                null,
                "idle" -> RestorePhase.Idle
                "waiting" -> RestorePhase.Waiting
                "restoring" -> RestorePhase.Restoring
                else -> RestorePhase.Unavailable
            }
        val restoreReason =
            when (map["restoreReason"]) {
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
        return RuntimeStatus(
            installed = map["installed"] == true,
            phase =
                when (map["phase"]) {
                    "installing" -> RuntimePhase.Installing
                    "ready" -> RuntimePhase.Ready
                    "failed" -> RuntimePhase.Failed
                    else -> RuntimePhase.Idle
                },
            message = (map["message"] as? String)?.takeIf { it.isNotBlank() },
            serverRunning = map["serverRunning"] == true,
            serverRestartWanted = map["serverRestartWanted"] == true,
            serverRecoveryGeneration = integer("serverRecoveryGeneration"),
            serverRecoveryAuthority = map["serverRecoveryAuthority"] == true,
            serverRecoveryScheduled = map["serverRecoveryScheduled"] == true,
            restorePhase = restorePhase,
            restoreReason = restoreReason,
            serverPort = integer("serverPort")?.takeIf { it in 1..65535 }?.toInt(),
            serverUptime = integer("serverUptimeMs")?.milliseconds,
            services = (map["services"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            abi = map["abi"] as? String,
            bytesUsed = integer("bytesUsed"),
            serverIdlePolicySupported = map["serverIdlePolicySupported"] == true,
            serverIdleEnabled = map["serverIdleEnabled"] == true,
            serverIdleMinutes = idleMinutes,
            serverIdleStopped = idleStopped,
            serverIdleHelperStopped = helperStopped,
            serverIdleGeneration = idleGeneration,
            serverIdleReceiptValid = idleValid,
        )
    }
}
