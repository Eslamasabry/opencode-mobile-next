package io.github.eslamasabry.oc.ui.shell.nav

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Desktop has no system animator scale (plan 08 §3.1): always 1. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class DesktopSystemMotion : SystemMotion {
    override val animatorScale: StateFlow<Float> = MutableStateFlow(1f).asStateFlow()
}
