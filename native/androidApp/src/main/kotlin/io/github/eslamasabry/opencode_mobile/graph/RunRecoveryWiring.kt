package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.engine.delivery.RunRecoveryVisibility

/** A hidden app never starts tier probes, even when another background owner keeps the stream connected. */
@ContributesIntoSet(AppScope::class, binding = binding<RunRecoveryVisibility>())
@Inject
class NativeRunRecoveryVisibility(private val visibility: AppVisibility) : RunRecoveryVisibility {
    override fun state() = visibility.state
}
