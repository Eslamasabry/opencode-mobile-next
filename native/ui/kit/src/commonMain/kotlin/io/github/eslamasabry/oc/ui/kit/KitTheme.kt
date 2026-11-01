package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.design.ColorRoles
import io.github.eslamasabry.oc.ui.design.PlaceholderPalette

/** Theme root (slice-0 placeholder palette; the Style resolver arrives with :ui:design in slice 1). */
@Composable
public fun KitTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val roles = if (dark) PlaceholderPalette.dark else PlaceholderPalette.light
    MaterialTheme(colorScheme = roles.toScheme(dark), content = content)
}

private fun ColorRoles.toScheme(dark: Boolean) =
    if (dark) {
        darkColorScheme(
            background = Color(background), onBackground = Color(onBackground), surface = Color(surface),
            onSurface = Color(onSurface), primary = Color(primary), onPrimary = Color(onPrimary),
            onSurfaceVariant = Color(muted),
        )
    } else {
        lightColorScheme(
            background = Color(background), onBackground = Color(onBackground), surface = Color(surface),
            onSurface = Color(onSurface), primary = Color(primary), onPrimary = Color(onPrimary),
            onSurfaceVariant = Color(muted),
        )
    }
