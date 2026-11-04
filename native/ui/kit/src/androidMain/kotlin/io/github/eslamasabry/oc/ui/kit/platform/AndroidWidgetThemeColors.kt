package io.github.eslamasabry.oc.ui.kit.platform

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.DynamicSource
import io.github.eslamasabry.oc.ui.design.color.DynamicTones
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.withAccent
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.StyleResolver

/** RemoteViews have no composition; resolve the same stored appearance and role adjustments as KitTheme. */
public fun androidWidgetThemeColors(context: Context, appearance: KitAppearance): Map<String, Int> {
    val dark = when (appearance.mode) {
        AppearanceMode.System -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        AppearanceMode.Light -> false
        AppearanceMode.Dark, AppearanceMode.Saver -> true
    }
    val dynamic = if (appearance.pack == PackId.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        DynamicSource(dynamicDarkColorScheme(context).widgetTones(), dynamicLightColorScheme(context).widgetTones())
    } else null
    val base = ThemeResolver.roles(appearance.pack, appearance.accent, dark, appearance.saver, dynamic)
    val adjusted =
        StyleResolver.adjust(base, StyleResolver.effectiveStyle(appearance.style, highContrast = false, saver = appearance.saver))
    val roles = if (adjusted == base) base else adjusted.withAccent(adjusted.accent)
    return mapOf("background" to roles.ground.v, "text" to roles.text1.v, "muted" to roles.text2.v, "accent" to roles.accent.v)
}

private fun ColorScheme.widgetTones(): DynamicTones = DynamicTones(
    primary = Argb(primary.toArgb()), secondary = Argb(secondary.toArgb()), tertiary = Argb(tertiary.toArgb()),
    onSurface = Argb(onSurface.toArgb()), surfaceContainerLowest = Argb(surfaceContainerLowest.toArgb()),
    surfaceContainerLow = Argb(surfaceContainerLow.toArgb()),
)
