package io.github.eslamasabry.opencode_mobile

/** Only protected system broadcasts enter this path; nothing here is a Dart command. */
internal enum class NativeServerRestoreEvent {
    BOOT_COMPLETED, PACKAGE_REPLACED;

    companion object {
        fun fromAction(action: String?): NativeServerRestoreEvent? = when (action) {
            "android.intent.action.BOOT_COMPLETED" -> BOOT_COMPLETED
            "android.intent.action.MY_PACKAGE_REPLACED" -> PACKAGE_REPLACED
            else -> null
        }
    }
}

/** Native in-memory capability: never serialized or reconstructed from Intent extras. */
internal class NativeServerRestoreTicket internal constructor(
    internal val event: NativeServerRestoreEvent,
    internal val originalRecipe: NativeServerRecipe,
    internal val recipe: NativeServerRecipe,
    internal val previousReceipt: NativeRuntimeReceipt,
    internal val wantedRevision: Long,
    internal val generation: Long,
    internal val scheduleId: Long,
    internal val boot: String,
    internal val idleCounter: Long,
    internal val budget: NativeRecoveryBudget,
) {
    internal val profile: String get() = recipe.profileId
}

/** Pure event-only admission; existing reclaim recipe and ownership checks remain strict. */
internal object NativeServerEventPolicy {
    fun admitted(
        nativeAdmitted: Boolean, idleAvailable: Boolean, idle: NativeIdleState.Snapshot,
        owner: String?, restoreOwner: String?, recipeOwner: String, attempts: Int,
    ): Boolean = nativeAdmitted && idleAvailable && !idle.enabled && !idle.stopped &&
        !idle.helperStopped && (idle.owner == null || idle.owner == recipeOwner) &&
        owner == recipeOwner && restoreOwner == recipeOwner && attempts in 0..2

    fun recipeForEvent(
        event: NativeServerRestoreEvent, recipe: NativeServerRecipe,
        packageVersion: Long, rootfsGeneration: String,
    ): NativeServerRecipe? {
        if (recipe.rootfsGeneration != rootfsGeneration || packageVersion <= 0) return null
        return when (event) {
            NativeServerRestoreEvent.BOOT_COMPLETED -> recipe.takeIf {
                it.compatible(packageVersion, rootfsGeneration)
            }
            NativeServerRestoreEvent.PACKAGE_REPLACED -> recipe.takeIf {
                packageVersion >= it.packageVersion
            }?.copy(packageVersion = packageVersion)
        }
    }

    fun crossBootQuiescent(
        receipt: NativeRuntimeReceipt, boot: String, current: List<RuntimeProcessIdentity>?,
        registered: Set<Int>, ownPid: Int,
    ): Boolean = !receipt.prepared && receipt.boot != boot &&
        Regex("[0-9a-f-]{36}").matches(boot) && current != null && ownPid in registered &&
        current.any { it.pid == ownPid } && current.all { it.pid in registered }

    fun pendingDrainCompatible(
        owner: String?, profile: String, includesOther: Boolean,
        pending: NativeRuntimeReceipt?, expected: NativeRuntimeReceipt,
    ): Boolean = if (owner == null) pending == null else
        owner == profile && !includesOther && pending == expected

    /** Reference identity cannot be copied into a forged or stale dispatch. */
    fun ticketCurrent(
        ticket: NativeServerRestoreTicket, active: NativeServerRestoreTicket?,
        wantedRevision: Long, generation: Long, scheduleId: Long, idleCounter: Long,
        boot: String, packageVersion: Long, rootfsGeneration: String,
    ): Boolean = ticket === active && ticket.wantedRevision == wantedRevision &&
        ticket.generation == generation && ticket.scheduleId == scheduleId &&
        ticket.idleCounter == idleCounter && ticket.boot == boot &&
        ticket.recipe.compatible(packageVersion, rootfsGeneration)
}
