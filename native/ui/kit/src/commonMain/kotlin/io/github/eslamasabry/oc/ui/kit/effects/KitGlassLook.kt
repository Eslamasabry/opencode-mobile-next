package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.eslamasabry.oc.ui.design.style.GlassLookPreset
import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance

/*
 * The glass look the kit draws (glass looks 2026-11-09): the person's choice (`KitAppearance.glassLook`, `oc.glassLook`)
 * everywhere, or a preview's own look inside a Glass look preview ([KitGlassLookPreview]), which never changes the page.
 * Every Glass part reads its numbers from here: the clear panes' blur, sheen, lit corner, specular and glint, the liquid
 * faces' rim, specular and starting tint, the frost's saturation and the rim's backdrop light.
 */

/** A look that overrides the person's for the parts under it (a preview); null: the person's. */
internal val LocalKitGlassLookOverride: ProvidableCompositionLocal<GlassLookPreset?> = staticCompositionLocalOf { null }

/** The glass look the kit draws here. */
@Composable
internal fun kitGlassLook(): GlassLookPreset = LocalKitGlassLookOverride.current ?: GlassLooks.of(LocalKitAppearance.current.glassLook)
