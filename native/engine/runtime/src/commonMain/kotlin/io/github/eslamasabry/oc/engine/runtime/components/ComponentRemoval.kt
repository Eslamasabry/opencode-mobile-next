package io.github.eslamasabry.oc.engine.runtime.components

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.json.TolerantJson
import io.github.eslamasabry.oc.core.common.ok
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of lib/builtin/setup/component_removal.dart (This phone › Components › Remove: the inventory
 * and the removal engine). Inventory uses presence probes, never version checks: an outdated
 * dependent still blocks removal. Every remove refreshes the setup record and the inventory before
 * mutating; unknown always fails closed.
 *
 * Only registry-authored scripts run; ids never become shell input. Nothing is persisted or logged.
 * The UI owns the localized confirmation and copy (one sentence per [ComponentRemovalBlock]).
 */
public enum class ComponentPresence {
    Installed,
    Absent,
    Unknown,
}

/**
 * Stable reasons for the UI's localized copy; no shell output crosses this API. Flutter's
 * `ComponentRemovalBlock` (same names); the Flutter UI maps each to a `phoneSetupRemove…` string in
 * lib/l10n/app_en.arb.
 */
public enum class ComponentRemovalBlock {
    DependentInstalled,
    DependencyUnknown,
    RequiredComponent,
    Unsupported,
    NotInstalled,
    InventoryUnavailable,
    SetupRunning,
    Busy,
    Failed,
}

/**
 * One inventory row. [blockingDependents] are installed or unknown dependents, including indirect
 * ones, by id.
 */
public data class ComponentRemovalEntry(
    val component: RemovableComponent,
    val presence: ComponentPresence,
    val block: ComponentRemovalBlock?,
    val blockingDependents: ImmutableList<String>,
) {
    val canRemove: Boolean
        get() = block == null
}

/** What [ComponentRemoval.remove] did. */
public sealed interface RemovalResult {
    /**
     * The script (or the team's lifecycle) succeeded and a fresh presence probe confirmed absence.
     */
    public data object Removed : RemovalResult

    /**
     * Nothing was removed ([code] is an admission block), or the removal failed
     * ([ComponentRemovalBlock.Failed]).
     */
    public data class Blocked(val code: ComponentRemovalBlock) : RemovalResult
}

/**
 * The engine-wide shape of [RemovalResult]: blocks become `Failure.Runtime` problems (the reason
 * code is lost).
 */
public fun RemovalResult.toOutcome(): Outcome<Unit> =
    when (this) {
        RemovalResult.Removed -> Unit.ok()
        is RemovalResult.Blocked ->
            Failure.Runtime(
                    when (code) {
                        ComponentRemovalBlock.Busy -> RuntimeProblem.SlotBusy
                        ComponentRemovalBlock.SetupRunning -> RuntimeProblem.Installing
                        ComponentRemovalBlock.NotInstalled -> RuntimeProblem.NotInstalled
                        ComponentRemovalBlock.InventoryUnavailable ->
                            RuntimeProblem.StorageUnavailable
                        ComponentRemovalBlock.Failed -> RuntimeProblem.RemovalFailed
                        else -> RuntimeProblem.Generic
                    }
                )
                .failed()
    }

public class ComponentRemoval(
    private val shell: LinuxShell,
    private val status: SetupStatusReader,
    registry: List<RemovableComponent>,
    /** AI Team's lifecycle API; without it AI Team is [ComponentRemovalBlock.Unsupported]. */
    private val team: TeamRemover? = null,
    private val toolsChanged: ToolsChangedListener = ToolsChangedListener {},
    /** The app's serial runtime scope keeps a shared probe alive when an individual page leaves. */
    private val scope: CoroutineScope? = null,
) {
    private val registry: ImmutableList<RemovableComponent> = registry.toImmutableList()

    init {
        require(registry.map { it.id }.toSet().size == registry.size) {
            "Component ids must be unique."
        }
    }

    private val busyState = MutableStateFlow(false)

    /**
     * True while a removal runs. The native runner has no cross-operation lock: callers disable
     * setup meanwhile.
     */
    public val busy: StateFlow<Boolean> = busyState.asStateFlow()

    private class InventoryRead(
        val revision: Int,
        val read: Deferred<ImmutableList<ComponentRemovalEntry>>,
    )

    private val inventoryLock = Mutex()
    private val inventoryRevision = MutableStateFlow(0)
    private var pendingInventory: InventoryRead? = null
    private var lastInventory: Pair<Int, ImmutableList<ComponentRemovalEntry>>? = null

    /**
     * Called when setup, repair or removal may have changed tools; old in-flight readings cannot
     * win.
     */
    public fun invalidateInventory() {
        inventoryRevision.update { it + 1 }
    }

    public suspend fun inventory(): ImmutableList<ComponentRemovalEntry> {
        val ownerScope = scope ?: return readInventory()
        var staleRead = false
        while (true) {
            val revision = inventoryRevision.value
            val read = inventoryLock.withLock {
                val previous = lastInventory
                val pending = pendingInventory
                when {
                    staleRead && previous != null && previous.first == revision ->
                        return previous.second
                    pending != null && pending.revision == revision -> pending.read
                    else ->
                        ownerScope
                            .async { readInventory() }
                            .also {
                                pendingInventory = InventoryRead(revision, it)
                            }
                }
            }
            val result = read.await()
            inventoryLock.withLock {
                if (pendingInventory?.read === read) {
                    pendingInventory = null
                    lastInventory = revision to result
                }
            }
            if (revision == inventoryRevision.value) return result
            staleRead = true
        }
    }

    private suspend fun readInventory(): ImmutableList<ComponentRemovalEntry> {
        // Unknown is not absence and cannot authorize removal.
        val linux = (attempt(GENERIC) { status.linuxState() }.flatten())
        val listed = registry.filter { !it.jobStep }
        val presence = mutableMapOf<String, ComponentPresence>()
        for (component in listed) {
            presence[component.id] =
                when {
                    linux == null || linux.installing -> ComponentPresence.Unknown
                    !linux.installed -> ComponentPresence.Absent
                    component.native -> ComponentPresence.Installed
                    else -> presenceOf(component)
                }
        }
        return listed.map { entry(it, presence) }.toImmutableList()
    }

    private fun entry(
        component: RemovableComponent,
        presence: Map<String, ComponentPresence>,
    ): ComponentRemovalEntry {
        val dependents =
            registry
                .filter {
                    !it.jobStep &&
                        it.id != component.id &&
                        dependsOn(it, component.id, mutableSetOf()) &&
                        presence[it.id] != ComponentPresence.Absent
                }
                .map { it.id }
        val own = presence.getValue(component.id)
        val removeScript = component.removeScript
        val block =
            when {
                own == ComponentPresence.Unknown -> ComponentRemovalBlock.InventoryUnavailable
                own == ComponentPresence.Absent -> ComponentRemovalBlock.NotInstalled
                dependents.any { presence[it] == ComponentPresence.Installed } ->
                    ComponentRemovalBlock.DependentInstalled
                dependents.isNotEmpty() -> ComponentRemovalBlock.DependencyUnknown
                component.required -> ComponentRemovalBlock.RequiredComponent
                component.native ||
                    removeScript == null ||
                    removeScript.isBlank() ||
                    (component.id == ComponentIds.AITEAM &&
                        (removeScript != AiTeamRemovalScripts.REMOVE_SCRIPT || team == null)) ->
                    ComponentRemovalBlock.Unsupported
                else -> null
            }
        return ComponentRemovalEntry(component, own, block, dependents.toImmutableList())
    }

    private fun dependsOn(
        component: RemovableComponent,
        id: String,
        seen: MutableSet<String>,
    ): Boolean {
        if (!seen.add(component.id)) return false
        for (dependency in component.dependsOn) {
            if (dependency == id) return true
            for (next in registry.filter { it.id == dependency }) {
                if (dependsOn(next, id, seen)) return true
            }
        }
        return false
    }

    private suspend fun presenceOf(component: RemovableComponent): ComponentPresence {
        val script = component.presenceScript
        if (script == null || script.isBlank()) return ComponentPresence.Unknown
        val result = attempt(GENERIC) { shell.run(script, PRESENCE_TIMEOUT) }.flatten()
        return when (result?.exitCode) {
            0 -> ComponentPresence.Installed
            1 -> ComponentPresence.Absent
            else -> ComponentPresence.Unknown
        }
    }

    /**
     * Null when setup is not running; otherwise the block. A missing record means no setup ever
     * ran.
     */
    private suspend fun setupBlock(): ComponentRemovalBlock? {
        val read = attempt(GENERIC) { status.setupRecord() }
        val raw =
            when (read) {
                is Outcome.Failed -> return ComponentRemovalBlock.InventoryUnavailable
                is Outcome.Ok ->
                    when (val inner = read.value) {
                        is Outcome.Failed -> return ComponentRemovalBlock.InventoryUnavailable
                        is Outcome.Ok -> inner.value ?: return null
                    }
            }
        val record =
            TolerantJson.parse(raw) as? JsonObject
                ?: return ComponentRemovalBlock.InventoryUnavailable
        val state = (record["state"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when (state) {
            null -> ComponentRemovalBlock.InventoryUnavailable
            "running" -> ComponentRemovalBlock.SetupRunning
            in SETTLED_STATES -> null
            else -> ComponentRemovalBlock.InventoryUnavailable
        }
    }

    /**
     * Removes only [id], never its dependents. [RemovalResult.Removed] means the script succeeded
     * and a fresh presence probe confirmed absence. A failure can leave partially removed tools;
     * [toolsChanged] then tells listeners to refresh the inventory (after [busy] is released).
     * Admission-only blocks leave the tools untouched and say nothing.
     */
    public suspend fun remove(id: String): RemovalResult {
        if (!busyState.compareAndSet(expect = false, update = true)) {
            return RemovalResult.Blocked(ComponentRemovalBlock.Busy)
        }
        var mutationAttempted = false
        try {
            setupBlock()?.let {
                return RemovalResult.Blocked(it)
            }
            // A removal never uses a probe begun before its own admission check.
            invalidateInventory()
            val entry =
                inventory().firstOrNull { it.component.id == id }
                    ?: return RemovalResult.Blocked(ComponentRemovalBlock.Unsupported)
            entry.block?.let {
                return RemovalResult.Blocked(it)
            }
            setupBlock()?.let {
                return RemovalResult.Blocked(it)
            }
            mutationAttempted = true
            val succeeded =
                if (id == ComponentIds.AITEAM) {
                    val remover =
                        team ?: return RemovalResult.Blocked(ComponentRemovalBlock.Unsupported)
                    attempt(GENERIC) { remover.remove() }.flatten() != null
                } else {
                    val script =
                        entry.component.removeScript
                            ?: return RemovalResult.Blocked(ComponentRemovalBlock.Failed)
                    attempt(GENERIC) { shell.run(script, REMOVE_TIMEOUT) }.flatten()?.ok == true
                }
            if (!succeeded || presenceOf(entry.component) != ComponentPresence.Absent) {
                return RemovalResult.Blocked(ComponentRemovalBlock.Failed)
            }
            return RemovalResult.Removed
        } finally {
            busyState.value = false
            // A failed removal can still change what is installed.
            if (mutationAttempted) {
                invalidateInventory()
                toolsChanged.toolsChanged()
            }
        }
    }

    /**
     * What removing [id] gives back, in bytes, measured now from its size script (kilobytes on its
     * last line). Null when it cannot be measured or measures nothing: callers then leave the
     * figure out, never "0 B".
     */
    public suspend fun freedBytes(id: String, timeout: Duration = SIZE_TIMEOUT): Long? {
        val component = registry.firstOrNull { it.id == id } ?: return null
        val script = component.sizeScript
        if (script == null || script.isBlank()) return null
        val result = attempt(GENERIC) { shell.run(script, timeout) }.flatten() ?: return null
        if (!result.ok) return null
        val kilobytes = result.output.trim().split('\n').last().trim().toLongOrNull() ?: return null
        return if (kilobytes in 1..(Long.MAX_VALUE / 1024)) kilobytes * 1024 else null
    }

    private companion object {
        val PRESENCE_TIMEOUT = 30.seconds
        val REMOVE_TIMEOUT = 5.minutes
        val SIZE_TIMEOUT = 20.seconds
        val SETTLED_STATES = setOf("idle", "done", "failed", "interrupted", "cancelled")

        /**
         * A thrown port error keeps only its class name (never its message, which may carry a
         * credential).
         */
        val GENERIC: (Throwable) -> Failure = {
            Failure.Runtime(RuntimeProblem.Generic, Redacted.shape(it))
        }

        fun <T> Outcome<Outcome<T>>.flatten(): T? =
            when (this) {
                is Outcome.Failed -> null
                is Outcome.Ok -> (value as? Outcome.Ok)?.value
            }
    }
}
