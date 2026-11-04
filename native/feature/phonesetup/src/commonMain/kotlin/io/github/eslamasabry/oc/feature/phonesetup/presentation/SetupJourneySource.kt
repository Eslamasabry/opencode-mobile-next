package io.github.eslamasabry.oc.feature.phonesetup.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import kotlinx.coroutines.flow.StateFlow

/** The one durable native job, shared with Linux, This phone and the welcome. */
public interface SetupJourneySource {
    public val progress: StateFlow<SetupProgress>
    public val toolsChanged: StateFlow<Int>
    public val selection: StateFlow<Set<String>>

    public fun details(progress: SetupProgress): Redacted?

    public suspend fun restore(): Outcome<Unit>

    public suspend fun options(adding: Boolean): Outcome<SetupOptions>

    public suspend fun choose(ids: Set<String>): Outcome<Unit>

    public suspend fun run(ids: Set<String>, adding: Boolean): Outcome<Unit>

    public suspend fun resume(): Outcome<Unit>

    public suspend fun cancel(): Outcome<Unit>

    public suspend fun removeVoice(): Outcome<Unit>

    public fun openStorage(): Boolean
}

public data class SetupOptions(
    val components: List<SetupComponent>,
    val installed: Set<String>,
    val device: SetupDeviceInfo,
)

/**
 * Selection is exactly what the native dependency planner will run, minus installed add-mode
 * dependencies.
 */
public object SetupSelection {
    public fun toggle(
        registry: List<SetupComponent>,
        chosen: Set<String>,
        installed: Set<String>,
        id: String,
        on: Boolean,
    ): Set<String> {
        val byId = registry.associateBy { it.id }
        val next = chosen.toMutableSet()
        fun enable(value: String) {
            val component = byId[value] ?: return
            if (component.required || value in installed || !next.add(value)) return
            component.dependsOn.forEach(::enable)
        }
        fun disable(value: String) {
            if (!next.remove(value)) return
            registry.filter { value in it.dependsOn }.forEach { disable(it.id) }
        }
        if (on) enable(id) else disable(id)
        return next
    }

    public fun install(
        options: SetupOptions,
        chosen: Set<String>,
        adding: Boolean,
    ): List<SetupComponent> {
        val expanded =
            io.github.eslamasabry.oc.engine.runtime.setup.SetupPlanning.expandSelection(
                options.components,
                chosen,
            )
        return expanded.filter {
            !it.jobStep &&
                (!adding || (!it.required && it.id !in options.installed)) &&
                (!it.app || it.id !in options.installed)
        }
    }

    public fun bytes(components: List<SetupComponent>): Long =
        components.fold(0L) { total, c ->
            val add = (c.downloadBytes ?: 0).coerceAtLeast(0)
            if (total > Long.MAX_VALUE - add) Long.MAX_VALUE else total + add
        }

    public fun seconds(components: List<SetupComponent>): Long = components.sumOf {
        it.estimatedSeconds.toLong().coerceAtLeast(0)
    }
}
