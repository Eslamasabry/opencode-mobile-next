package io.github.eslamasabry.oc.ui.kit.rb.o27

import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemBars
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemBarsLook
import kotlin.test.Test
import kotlin.test.assertEquals

/** part-kit_system_bars: every rule of `lib/ui/kit/kit_system_bars.dart` at 8302de537. */
class KitSystemBarsParityTest {
    private val ground = Color(0xFF0B0C0E)

    @Test
    fun `dark theme clear status bar light icons ground nav bar no divider`() {
        assertEquals(
            KitSystemBarsLook(
                statusBarColor = Color.Transparent,
                statusBarDarkIcons = false,
                statusBarDark = true,
                navigationBarColor = ground,
                navigationBarDividerColor = Color.Transparent,
                navigationBarDarkIcons = false,
            ),
            KitSystemBars.of(dark = true, ground = ground),
        )
    }

    @Test
    fun `light theme dark icons on both bars and a light iOS bar`() {
        val look = KitSystemBars.of(dark = false, ground = Color.White)
        assertEquals(true, look.statusBarDarkIcons)
        assertEquals(true, look.navigationBarDarkIcons)
        assertEquals(false, look.statusBarDark)
        assertEquals(Color.White, look.navigationBarColor)
        assertEquals(Color.Transparent, look.statusBarColor)
        assertEquals(Color.Transparent, look.navigationBarDividerColor)
    }
}
