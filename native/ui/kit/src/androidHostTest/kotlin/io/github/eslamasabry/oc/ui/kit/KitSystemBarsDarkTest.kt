package io.github.eslamasabry.oc.ui.kit

import android.graphics.Color
import android.graphics.Insets
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import io.github.eslamasabry.oc.ui.kit.platform.applySystemBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B-544: a dark app on a phone set to light mode keeps a dark, clear navigation area. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [KitRobolectric.SDK], qualifiers = "notnight")
class KitSystemBarsDarkTest {
    private val ground = 0xFF0B0C0E.toInt()
    private val keyboardHeight = 126
    private lateinit var window: Window

    @Before
    fun setUp() {
        window = Robolectric.buildActivity(ComponentActivity::class.java).setup().get().window
        lightPhoneEdgeToEdge(window)
        applySystemBars(window, darkContent = false, ground = ground)
    }

    @Test
    fun `B-544 a dark app on a light phone keeps a dark clear navigation bar`() {
        assertDarkBars()
    }

    @Test
    fun `B-544 the keyboard opening and closing leaves the dark bars in place`() {
        window.decorView.dispatchApplyWindowInsets(imeInsets(keyboardHeight))
        window.decorView.dispatchApplyWindowInsets(imeInsets(0))
        assertDarkBars()
    }

    @Test
    fun `B-544 applying the bars twice changes nothing`() {
        applySystemBars(window, darkContent = false, ground = ground)
        assertDarkBars()
    }

    @Test
    fun `B-544 a light app keeps its light bars`() {
        lightPhoneEdgeToEdge(window)
        applySystemBars(window, darkContent = true, ground = Color.WHITE)
        val appearance = window.insetsController!!.systemBarsAppearance
        assertTrue(appearance and WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS != 0)
        assertEquals(Color.WHITE, (window.decorView.background as ColorDrawable).color)
    }

    private fun imeInsets(bottom: Int): WindowInsets =
        WindowInsets.Builder().setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, bottom)).build()

    private fun assertDarkBars() {
        assertEquals(ground, (window.decorView.background as ColorDrawable).color)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            assertEquals(Color.TRANSPARENT, window.navigationBarColor)
        }
        assertFalse(window.isNavigationBarContrastEnforced)
        val appearance = window.insetsController!!.systemBarsAppearance
        assertEquals(0, appearance and WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
    }

    // Mirrors what enableEdgeToEdge() does on a light phone, because the kit's host tests cannot see that function.
    @Suppress("DEPRECATION")
    private fun lightPhoneEdgeToEdge(window: Window) {
        val light =
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = true
        window.insetsController!!.setSystemBarsAppearance(light, light)
    }
}
