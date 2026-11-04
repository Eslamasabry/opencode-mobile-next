package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.foundation.Indication
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Desktop (plan 06 §6): hover/press fill instead of a ripple, no haptics, a fine pointer, frost drawn solid. */
public class DesktopKitPlatform : KitPlatform {
    override val name: String = "desktop"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: StateFlow<KitSystemSignals> =
        MutableStateFlow(KitSystemSignals(finePointer = true)).asStateFlow()

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic): Unit = Unit // desktops have no haptics

    override fun messageTime(createdMs: Long, nowMs: Long): String {
        val created = java.util.Date(createdMs)
        val a = java.util.Calendar.getInstance().apply { timeInMillis = createdMs }
        val b = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val sameDay = a.get(java.util.Calendar.ERA) == b.get(java.util.Calendar.ERA) &&
            a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(created)
        return if (sameDay) time else java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(created) + ", " + time
    }

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
}
