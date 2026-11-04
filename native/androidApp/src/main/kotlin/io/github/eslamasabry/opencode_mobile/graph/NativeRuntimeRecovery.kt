package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.requests.consent.AutomationBehavior
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Stages the old budget once, acknowledges the marker, and only then grants native authority. */
internal class NativeRuntimeRecovery(
    private val stores: PreviewStores,
    private val linux: BuiltinLinux,
    private val io: CoroutineDispatcher,
    private val policies: AppAutomationPolicies,
) {
    suspend fun bind(profile: ProfileId): Outcome<Unit> {
        if (!Regex("[A-Za-z0-9_-]{1,80}").matches(profile.raw)) return unavailable()
        val key = "oc.builtinRecovery.${profile.raw}"
        val saved = stores.prefs.get(key)
        if (saved != null && saved !is PrefValue.Str) return unavailable()
        val raw = (saved as? PrefValue.Str)?.value
        val record =
            try {
                raw?.let(::JSONObject)
            } catch (_: Exception) {
                return unavailable()
            }
        val migrated = record?.opt("version") == 2 && record.opt("nativeAuthority") == true
        if (!migrated) {
            if (record != null && record.opt("version") != 1) return unavailable()
            val legacy =
                record?.toMap() ?: mapOf("version" to 1, "attempts" to 0, "pending" to false)
            val staged =
                withContext(io) {
                    try {
                        linux.stageServerRecovery(profile.raw, legacy)
                        Outcome.Ok(Unit)
                    } catch (e: Exception) {
                        Outcome.Failed(classifyRuntime(e))
                    }
                }
            if (staged is Outcome.Failed) return staged
            val marker =
                stores.prefs.commit(stores.lanes.forProfile(profile)) {
                    putString(key, "{\"version\":2,\"nativeAuthority\":true}")
                }
            if (marker is Outcome.Failed) return marker
        }
        return withContext(io) {
            try {
                linux.bindServerRecovery(profile.raw, null, policyAllows(profile.raw))
                Outcome.Ok(Unit)
            } catch (e: Exception) {
                Outcome.Failed(classifyRuntime(e))
            }
        }
    }

    /**
     * A confirmed manual start lifts the runtime-switch suspension (Flutter
     * `managed_server_recovery.dart` clears `_manuallySuspended` there) and rebinds with the
     * policy.
     */
    suspend fun confirmManualStart(profile: ProfileId): Outcome<Unit> {
        val key = "oc.managedServerRecovery.${profile.raw}"
        val saved = stores.prefs.get(key) as? PrefValue.Str ?: return Outcome.Ok(Unit)
        val record =
            try {
                JSONObject(saved.value)
            } catch (_: Exception) {
                return unavailable()
            }
        if (record.opt("manuallySuspended") != true) return Outcome.Ok(Unit)
        record.put("manuallySuspended", false)
        val persisted =
            stores.prefs.commit(stores.lanes.forProfile(profile)) {
                putString(key, record.toString())
            }
        if (persisted is Outcome.Failed) return persisted
        return bind(profile)
    }

    fun policyAllows(profile: String): Boolean {
        val suspended = stores.prefs.get("oc.managedServerRecovery.$profile")
        if (suspended != null) {
            if (suspended !is PrefValue.Str) return false
            try {
                if (JSONObject(suspended.value).opt("manuallySuspended") == true) return false
            } catch (_: Exception) {
                return false
            }
        }
        val id = ProfileId(profile)
        return policies.allows(
            id,
            AutomationBehavior.RestartPhoneServer,
        ) &&
            policies.allows(
                id,
                AutomationBehavior.PollRestartHealth,
            )
    }

    private fun JSONObject.toMap(): Map<String, Any?> =
        keys().asSequence().associateWith { key -> opt(key).takeUnless { it === JSONObject.NULL } }

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
}
