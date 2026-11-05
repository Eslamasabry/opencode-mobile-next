package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration

/**
 * Glass painting for a surface (Dart `kit_surface_look.dart` / `kit_surface_paint.dart` at GLASS_REF 1bbdc3f9c): the
 * blur behind the pane, the top-start sheen, the inner glow and the rims. Lead stub (rebuild standard §12 seams): it
 * returns the modifier unchanged until order 34-glass fills it. `KitSurface.kt` calls it once per decorated surface.
 */
@Composable
internal fun Modifier.kitSurfaceGlass(decoration: SurfaceDecoration?): Modifier = this
