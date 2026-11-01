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

/** The durable counters and boot identity a restore reservation was granted under. */
internal data class NativeServerRestoreAuthority(
    val wantedRevision: Long,
    val generation: Long,
    val scheduleId: Long,
    val boot: String,
    val idleCounter: Long,
)

/** Native in-memory capability: never serialized or reconstructed from Intent extras. */
internal class NativeServerRestoreTicket internal constructor(
    internal val event: NativeServerRestoreEvent,
    internal val originalRecipe: NativeServerRecipe,
    internal val recipe: NativeServerRecipe,
    internal val previousReceipt: NativeRuntimeReceipt,
    internal val authority: NativeServerRestoreAuthority,
    internal val budget: NativeRecoveryBudget,
) {
    internal val profile: String get() = recipe.profileId
    internal val wantedRevision: Long get() = authority.wantedRevision
    internal val generation: Long get() = authority.generation
    internal val scheduleId: Long get() = authority.scheduleId
    internal val boot: String get() = authority.boot
    internal val idleCounter: Long get() = authority.idleCounter
}

/** Pure event-only admission; existing reclaim recipe and ownership checks remain strict. */
internal object NativeServerEventPolicy {
    /** The profile that holds supervision and the one saved as the restore owner. */
    data class Owners(val supervision: String?, val restore: String?)

    /** [nativeAdmitted] also requires the idle state to be available. */
    fun admitted(
        nativeAdmitted: Boolean, idle: NativeIdleState.Snapshot,
        owners: Owners, recipeOwner: String, attempts: Int,
    ): Boolean = nativeAdmitted && !idle.enabled && !idle.stopped &&
        !idle.helperStopped && (idle.owner == null || idle.owner == recipeOwner) &&
        owners.supervision == recipeOwner && owners.restore == recipeOwner && attempts in 0..2

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
        current: NativeServerRestoreAuthority, packageVersion: Long, rootfsGeneration: String,
    ): Boolean = ticket === active && ticket.authority == current &&
        ticket.recipe.compatible(packageVersion, rootfsGeneration)
}
