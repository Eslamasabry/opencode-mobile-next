package io.github.eslamasabry.oc.ui.kit.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.compose.foundation.Indication
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.toArgb
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.DynamicSource
import io.github.eslamasabry.oc.ui.design.color.DynamicTones
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Built once by `:androidApp`'s `MainActivity` and provided through `LocalKitPlatform` (plan 06
 * §6). Haptics go through [view] (`View.performHapticFeedback` honours the system Touch feedback
 * setting); press feedback is the M3 platform ripple; frost is a `RenderEffect` on API 31+.
 */
public class AndroidKitPlatform(
    private val view: View,
    private val window: Window? = view.context.findWindow(),
) : KitPlatform {
    private val context: Context = view.context.applicationContext ?: view.context
    private val state = MutableStateFlow(readSignals())

    override val name: String = "android"
    override val family: KitPlatformFamily = KitPlatformFamily.Android
    override val signals: StateFlow<KitSystemSignals> = state.asStateFlow()
    override val isDebug: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    init {
        // Live, not read once (the demo's bug): the developer-options animator scale feeds reduced
        // motion.
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    state.update { it.copy(animatorScale = animatorScale()) }
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
    }

    override fun pressIndication(ink: Color): Indication = ripple(bounded = true, color = ink)

    override fun haptic(kind: KitHaptic) {
        hapticConstant(kind, Build.VERSION.SDK_INT)?.let { view.performHapticFeedback(it) }
    }

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) AndroidFrost.effect(spec, density)
        else null

    /**
     * Material You (plan 06 §4.2): the system's dynamic schemes on API 31+, mirroring Dart
     * `dynamicThemePack`.
     */
    override fun dynamicSource(): DynamicSource? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicSource(
                dark = dynamicDarkColorScheme(context).tones(),
                light = dynamicLightColorScheme(context).tones(),
            )
        } else {
            null
        }

    /**
     * Plan 06 §5.6 (Dart `KitSystemBars`): transparent status bar and the navigation bar in the
     * ground, icons opposite the theme (the app's own dark choice is not a configuration change, so
     * the kit sets it), and no grey contrast scrim on 3-button navigation.
     */
    @Composable
    override fun SystemBars(darkContent: Boolean, ground: androidx.compose.ui.graphics.Color) {
        val hostWindow = window ?: return
        SideEffect { applySystemBars(hostWindow, darkContent, ground.toArgb()) }
    }

    private fun animatorScale(): Float =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )

    private fun readSignals(): KitSystemSignals {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return KitSystemSignals(
            animatorScale = animatorScale(),
            powerSave = power?.isPowerSaveMode == true,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
    }
}

/**
 * Plan 06 §4.9 / plan 08 §6.2: today's Flutter constants. Null = no haptic on this API level (Done
 * below API 30).
 */
internal fun hapticConstant(kind: KitHaptic, sdk: Int): Int? =
    when (kind) {
        KitHaptic.Send -> HapticFeedbackConstants.VIRTUAL_KEY
        KitHaptic.Done -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else null
        KitHaptic.Commit -> HapticFeedbackConstants.KEYBOARD_TAP
        KitHaptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
        KitHaptic.ToggleOn ->
            if (sdk >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
        KitHaptic.ToggleOff ->
            if (sdk >= 34) HapticFeedbackConstants.TOGGLE_OFF
            else HapticFeedbackConstants.CLOCK_TICK
        KitHaptic.Select ->
            if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK
    }

private fun ColorScheme.tones(): DynamicTones =
    DynamicTones(
        primary = Argb(primary.toArgb()),
        secondary = Argb(secondary.toArgb()),
        tertiary = Argb(tertiary.toArgb()),
        onSurface = Argb(onSurface.toArgb()),
        surfaceContainerLowest = Argb(surfaceContainerLowest.toArgb()),
        surfaceContainerLow = Argb(surfaceContainerLow.toArgb()),
    )

private tailrec fun Context.findWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findWindow()
        else -> null
    }

@Suppress("DEPRECATION")
internal fun applySystemBars(window: Window, darkContent: Boolean, ground: Int) {
    // B14 (QA wave 1): the window itself wears the app's ground, so a recreated Activity (language switch) never
    // shows the platform theme's grey behind the status and navigation bars.
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(ground))
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    // Ignored from API 35 (edge-to-edge): KitScreen paints the ground under the navigation bar
    // there.
    window.navigationBarColor = ground
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        window.isNavigationBarContrastEnforced = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bits =
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (darkContent) bits else 0, bits)
    } else {
        val decor = window.decorView
        val bits = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        decor.systemUiVisibility =
            if (darkContent) decor.systemUiVisibility or bits
            else decor.systemUiVisibility and bits.inv()
    }
}
