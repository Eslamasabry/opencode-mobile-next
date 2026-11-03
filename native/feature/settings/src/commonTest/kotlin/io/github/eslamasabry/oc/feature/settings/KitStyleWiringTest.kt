package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.settings.prefs.Corners
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferences
import io.github.eslamasabry.oc.engine.settings.prefs.Effects
import io.github.eslamasabry.oc.engine.settings.prefs.FineTune
import io.github.eslamasabry.oc.engine.settings.prefs.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.presentation.PreferenceMapping
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The stored Style and Corners reach what `KitTheme` wears (B-KIT-037 five styles, B-KIT-005 Sharp corners). */
class KitStyleWiringTest {
    @Test
    fun `B-KIT-037 the stored style becomes the theme's style, every one of the five`() {
        for (stored in SurfaceStyle.entries) {
            val kit = PreferenceMapping.appearance(DevicePreferences(effects = Effects(surfaceStyle = stored)))
            assertEquals(stored.wire, kit.style.storageName)
        }
        assertEquals(KitSurfaceStyle.Solid, PreferenceMapping.appearance(DevicePreferences()).style)
    }

    @Test
    fun `B-KIT-005 Fine-tune Corners reaches the theme and none keeps the style's own`() {
        val sharp = DevicePreferences(effects = Effects(surfaceStyle = SurfaceStyle.Brutal, fineTune = FineTune(corners = Corners.Sharp)))
        assertEquals(KitCorners.Sharp, PreferenceMapping.appearance(sharp).corners)
        assertNull(PreferenceMapping.appearance(DevicePreferences()).corners)
    }
}
