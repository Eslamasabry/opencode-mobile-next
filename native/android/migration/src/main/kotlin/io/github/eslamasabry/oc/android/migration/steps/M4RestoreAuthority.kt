package io.github.eslamasabry.oc.android.migration.steps

import io.github.eslamasabry.oc.android.migration.KotlinPrefsSource
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref

/**
 * M4, the restore-authority check (plan 05 §7.4 M4; builtin-linux trap T1; B-LINUX-032). Read-only.
 *
 * Kotlin restores the phone server after a reboot or update only if the Flutter-era keys say so AND the Kotlin-owned
 * `builtin_server_recovery` file holds a matching owner and a compatible cold-start recipe. M4 checks the Flutter half
 * for byte equality with the M1 baseline (a difference can only be a bug: `AuthorityChanged`) and only COUNTS the Kotlin
 * half, which the restore receiver owns.
 */
internal class M4RestoreAuthority(
    private val kotlinPrefs: KotlinPrefsSource,
    private val nativeVersionCode: Long,
) {
    sealed interface Result {
        data class Done(val counts: Map<String, Any?>) : Result
        data object AuthorityChanged : Result
    }

    fun run(live: Map<String, RawPref>, baseline: PrefsBaseline?): Result {
        val p = LogicalPrefs(live)
        val ids = (p.profileIds() + p.keys(RECOVERY).map { it.removePrefix(RECOVERY) }).distinct().sorted()
        val checked = ids.flatMap { id -> listOf(RECOVERY + id, AUTOMATION + id, PHONE_AGENT_OWNER + id) } + LogicalPrefs.BUILTIN_OWNER
        if (baseline == null) return Result.AuthorityChanged // M1 wrote it before any write; missing = cannot vouch
        for (key in checked) {
            val physical = FlutterPrefsCodec.physicalKey(key)
            val now = live[physical]?.let(PrefsBaseline::valueHash)
            if (now != baseline.valueHashOf(physical)) return Result.AuthorityChanged
        }

        val owner = p.string(LogicalPrefs.BUILTIN_OWNER)
        val disarmed = sortedMapOf<String, Long>()
        val armed = ArrayList<String>()
        for (id in ids) {
            val reason = RestoreAuthority.disarmReason(
                recovery = p.jsonObject(RECOVERY + id),
                automation = p.jsonObject(AUTOMATION + id),
                owner = owner,
                profileId = id,
            )
            if (reason == null) armed += id else disarmed.merge(reason, 1L, Long::plus)
        }

        // Kotlin half: counts only, never written (the restore receiver owns it, plan 05 §7.1).
        val recovery = kotlinPrefs.read(SERVER_RECOVERY)
        val rootfsGeneration = kotlinPrefs.read(COMPONENT_WRITER)?.get("rootfsGeneration")?.let(::asLong)
        val restoreOwner = recovery?.get("restoreOwner") as? String
        var recipes = 0L
        var recipesCompatible = 0L
        for (id in armed) {
            val recipe = (recovery?.get("oc.builtinRuntimeRecipe.$id") as? String)?.let { MiniJson.parseOrNull(it) } as? Map<*, *> ?: continue
            recipes++
            val pkg = asLong(recipe["packageVersion"])
            val gen = asLong(recipe["rootfsGeneration"])
            if (pkg != null && pkg <= nativeVersionCode && gen != null && gen == rootfsGeneration) recipesCompatible++
        }
        val counts = LinkedHashMap<String, Any?>()
        counts["profiles"] = ids.size.toLong()
        counts["armed"] = armed.size.toLong()
        counts["disarmed"] = disarmed
        counts["kotlinRecoveryFile"] = recovery != null
        counts["restoreOwnerArmed"] = restoreOwner != null && restoreOwner in armed
        counts["recipes"] = recipes
        counts["recipesCompatible"] = recipesCompatible
        counts["kotlinHalfDisarmed"] = (armed.size - recipesCompatible).coerceAtLeast(0L)
        return Result.Done(counts)
    }

    private fun asLong(v: Any?): Long? = when (v) {
        is Long -> v
        is Int -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    companion object {
        const val RECOVERY = "oc.builtinRecovery."
        const val AUTOMATION = "oc.automation."
        const val PHONE_AGENT_OWNER = "oc.phoneAgentOwner."
        const val SERVER_RECOVERY = "builtin_server_recovery"
        const val COMPONENT_WRITER = "builtin_component_writer"
    }
}

/**
 * The authority predicate, kept in `:android:migration` (it may not depend on `:android:linux`). The same vectors run
 * through `NativeRecoveryBudget.migrationAllows` / `policyAllows` in `RestoreAuthorityParityTest` (`:androidApp`).
 */
internal object RestoreAuthority {
    val OWNER_SHAPE: Regex = Regex("^[A-Za-z0-9_-]{1,80}$")

    /** `NativeRecoveryBudget.migrationAllows`: `version == 2 && nativeAuthority == true`. */
    fun migrationAllows(recovery: Map<*, *>?): Boolean = recovery != null && isTwo(recovery["version"]) && recovery["nativeAuthority"] == true

    /** `NativeRecoveryBudget.policyAllows`: the pinned Dart schema; unknown data grants nothing. */
    fun policyAllows(policy: Map<*, *>?): Boolean {
        if (policy == null || !isOne(policy["version"]) || policy["supervision"] !in listOf("high", "balanced", "autonomous")) return false
        val behaviors = policy["behaviors"] as? Map<*, *> ?: return false
        return behaviors["restartPhoneServer"] == true && behaviors["pollRestartHealth"] == true
    }

    /** Null when armed, else the first reason (`recovery`, `policy`, `owner`). */
    fun disarmReason(recovery: Map<*, *>?, automation: Map<*, *>?, owner: String?, profileId: String): String? = when {
        !migrationAllows(recovery) -> "recovery"
        !policyAllows(automation) -> "policy"
        owner == null || !OWNER_SHAPE.matches(owner) || owner != profileId -> "owner"
        else -> null
    }

    /** org.json hands the Kotlin side an Int; a JSON `2.0` is a Double there and never equals 2. Same rule here. */
    private fun isTwo(v: Any?) = (v is Long && v == 2L) || (v is Int && v == 2)
    private fun isOne(v: Any?) = (v is Long && v == 1L) || (v is Int && v == 1)
}
