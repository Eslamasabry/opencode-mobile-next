package io.github.eslamasabry.oc.ui.kit

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Insets
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.applySystemBars
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B-544: a dark app on a phone set to light mode keeps a dark, clear navigation area. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [KitRobolectric.SDK], qualifiers = "notnight")
class KitSystemBarsDarkTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        assertBars(window, ground, lightBars = false)
    }

    @Test
    fun `B-544 the keyboard opening and closing leaves the dark bars in place`() {
        window.decorView.dispatchApplyWindowInsets(imeInsets(keyboardHeight))
        window.decorView.dispatchApplyWindowInsets(imeInsets(0))
        assertBars(window, ground, lightBars = false)
    }

    @Test
    fun `B-544 applying the bars twice changes nothing`() {
        applySystemBars(window, darkContent = false, ground = ground)
        assertBars(window, ground, lightBars = false)
    }

    @Test
    fun `B-544 a light app keeps its light bars`() {
        lightPhoneEdgeToEdge(window)
        applySystemBars(window, darkContent = true, ground = Color.WHITE)
        assertBars(window, Color.WHITE, lightBars = true)
    }

    @Test
    fun `B-544 the keyboard opening and closing leaves the light bars in place`() {
        lightPhoneEdgeToEdge(window)
        applySystemBars(window, darkContent = true, ground = Color.WHITE)
        window.decorView.dispatchApplyWindowInsets(imeInsets(keyboardHeight))
        window.decorView.dispatchApplyWindowInsets(imeInsets(0))
        assertBars(window, Color.WHITE, lightBars = true)
    }

    @Test
    fun `B-544 after a configuration change the theme's bars come back`() {
        val themed = compose.activity.window
        val platform = AndroidKitPlatform(themed.decorView, themed)
        compose.setContent {
            platform.SystemBars(darkContent = false, ground = androidx.compose.ui.graphics.Color(ground))
        }
        compose.waitForIdle()
        assertBars(themed, ground, lightBars = false)
        lightPhoneEdgeToEdge(themed)
        themed.decorView.dispatchConfigurationChanged(Configuration())
        compose.waitForIdle()
        assertBars(themed, ground, lightBars = false)
    }

    @Test
    fun `B-544 KitPageOverlay popup window is edge to edge with theme bar contrast`() {
        val themed = compose.activity.window
        val platform = AndroidKitPlatform(themed.decorView, themed)
        var mode by mutableStateOf(AppearanceMode.Light)
        var popupView: View? = null
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                platform = platform,
                localeTag = "en",
            ) {
                KitPageOverlay(visible = true, title = "Overlay", onDismissRequest = {}) {
                    popupView = LocalView.current.rootView
                    KitText("Overlay body")
                }
            }
        }
        compose.waitForIdle()
        assertNotNull("KitPageOverlay must attach a popup view", popupView)
        assertNotSame(themed.decorView, popupView)
        assertPopupWindow(popupView!!, lightBars = true)

        mode = AppearanceMode.Dark
        compose.waitForIdle()
        assertPopupWindow(popupView!!, lightBars = false)
    }

    @Test
    fun `B-544 KitMenu and KitTip popups configure their own popup windows`() {
        val themed = compose.activity.window
        val android = AndroidKitPlatform(themed.decorView, themed)
        val views = mutableListOf<View>()
        val platform =
            object : KitPlatform by android {
                @Composable
                override fun PopupSystemBars(darkContent: Boolean, ground: androidx.compose.ui.graphics.Color) {
                    val root = LocalView.current.rootView
                    if (views.lastOrNull() !== root) views += root
                    android.PopupSystemBars(darkContent, ground)
                }
            }
        var showMenu by mutableStateOf(true)
        var showTip by mutableStateOf(false)
        var mode by mutableStateOf(AppearanceMode.Light)
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                platform = platform,
                localeTag = "en",
            ) {
                KitMenu(
                    expanded = showMenu,
                    items = listOf(KitMenuItem("Copy", onSelect = {})),
                    onDismissRequest = { showMenu = false },
                ) {
                    KitText("More")
                }
                KitTip(message = "Hold to send", visible = showTip, onDismissed = { showTip = false }) {
                    KitText("Send")
                }
            }
        }
        compose.waitForIdle()
        assertTrue("KitMenu popup must call PopupSystemBars", views.isNotEmpty())
        val menuPopup = views.last()
        assertNotSame(themed.decorView, menuPopup)
        assertPopupWindow(menuPopup, lightBars = true)

        showMenu = false
        mode = AppearanceMode.Dark
        showTip = true
        compose.waitForIdle()
        assertTrue("KitTip popup must call PopupSystemBars", views.size >= 2)
        val tipPopup = views.last()
        assertNotSame(themed.decorView, tipPopup)
        assertPopupWindow(tipPopup, lightBars = false)
    }

    @Suppress("DEPRECATION")
    private fun assertPopupWindow(root: View, lightBars: Boolean) {
        val params = root.layoutParams as WindowManager.LayoutParams
        val drawsBars = WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        val inScreen = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val translucent =
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        assertTrue("Popup must draw behind system bars", (params.flags and drawsBars) != 0)
        assertTrue("Popup must lay out in full screen", (params.flags and inScreen) != 0)
        assertEquals("Popup must not use translucent bar flags", 0, params.flags and translucent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals("Popup must draw edge to edge without fitting system bars", 0, params.fitInsetsTypes)
            val controller = root.windowInsetsController
            assertNotNull("Attached popup view must expose WindowInsetsController", controller)
            val light =
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            assertEquals(if (lightBars) light else 0, controller!!.systemBarsAppearance and light)
        }
    }

    private fun imeInsets(bottom: Int): WindowInsets =
        WindowInsets.Builder().setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, bottom)).build()

    private fun assertBars(window: Window, ground: Int, lightBars: Boolean) {
        assertEquals(ground, (window.decorView.background as ColorDrawable).color)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            assertEquals(Color.TRANSPARENT, window.statusBarColor)
            @Suppress("DEPRECATION")
            assertEquals(Color.TRANSPARENT, window.navigationBarColor)
        }
        assertFalse(window.isNavigationBarContrastEnforced)
        val appearance = window.insetsController!!.systemBarsAppearance
        val light =
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        assertEquals(if (lightBars) light else 0, appearance and light)
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
