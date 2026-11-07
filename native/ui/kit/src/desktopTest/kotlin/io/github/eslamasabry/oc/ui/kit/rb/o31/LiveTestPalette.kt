package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.KitLivePalette
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors

/** The default theme's live palette (the goldens' AppTheme). */
internal fun livePalette(dark: Boolean): KitLivePalette =
    KitLivePalette.of(ThemeResolver.roles(PackId.Opencode, null, dark, false, null).toKitColors())

internal fun liveFrame(dark: Boolean, t: Double = GOLDEN_T, running: Double = 0.0, strength: Double = 1.0, memory: KitLiveMemory = KitLiveMemory()) =
    KitLiveFrame(t, running, strength, livePalette(dark), memory)
