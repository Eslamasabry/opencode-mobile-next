package io.github.eslamasabry.oc.ui.kit.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Indication
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Built once by `:androidApp`'s `MainActivity` and provided through `LocalKitPlatform` (plan 06 §6). Haptics go through
 * [view] (`View.performHapticFeedback` honours the system Touch feedback setting); press feedback is the M3 platform
 * ripple; frost is a `RenderEffect` on API 31+.
 */
public class AndroidKitPlatform(private val view: View) : KitPlatform {
    private val context: Context = view.context.applicationContext ?: view.context
    private val state = MutableStateFlow(readSignals())

    override val name: String = "android"
    override val family: KitPlatformFamily = KitPlatformFamily.Android
    override val signals: StateFlow<KitSystemSignals> = state.asStateFlow()
    override val isDebug: Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    init {
        // Live, not read once (the demo's bug): the developer-options animator scale feeds reduced motion.
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    state.update { it.copy(animatorScale = animatorScale()) }
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
    }

    override fun pressIndication(ink: Color): Indication = ripple(bounded = true, color = ink)

    override fun haptic(kind: KitHaptic) {
        hapticConstant(kind, Build.VERSION.SDK_INT)?.let { view.performHapticFeedback(it) }
    }

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) AndroidFrost.effect(spec, density) else null

    private fun animatorScale(): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

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
 * Plan 06 §4.9 / plan 08 §6.2: today's Flutter constants. Null = no haptic on this API level (Done below API 30).
 */
internal fun hapticConstant(kind: KitHaptic, sdk: Int): Int? =
    when (kind) {
        KitHaptic.Send -> HapticFeedbackConstants.VIRTUAL_KEY
        KitHaptic.Done -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else null
        KitHaptic.Commit -> HapticFeedbackConstants.KEYBOARD_TAP
        KitHaptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
        KitHaptic.ToggleOn -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
        KitHaptic.ToggleOff -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
        KitHaptic.Select -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    }
