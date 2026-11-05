package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.foundation.Indication
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToSvgPainter

/**
 * Desktop (plan 06 §6): hover/press fill instead of a ripple, no haptics, a fine pointer, frost
 * drawn solid.
 */
public class DesktopKitPlatform : KitPlatform {
    override val name: String = "desktop"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: StateFlow<KitSystemSignals> =
        MutableStateFlow(KitSystemSignals(finePointer = true)).asStateFlow()

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic): Unit = Unit // desktops have no haptics

    override fun graphemePrefix(text: String, count: Int): String {
        require(count >= 0)
        val iterator = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
        iterator.setText(text)
        var end = iterator.first()
        repeat(count) {
            val next = iterator.next()
            if (next == java.text.BreakIterator.DONE) return text
            end = next
        }
        return text.substring(0, end)
    }

    override fun dateTime(atMs: Long, localeTag: String): String =
        mediumLocalDateTime(
            atMs,
            java.util.Locale.forLanguageTag(localeTag),
            java.util.TimeZone.getDefault(),
        )

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

    @OptIn(ExperimentalResourceApi::class)
    override fun svgPainter(text: String, pixels: Int): Painter? {
        if (!isStaticKitSvg(text) || pixels !in 1..256) return null
        return try {
            text.encodeToByteArray().decodeToSvgPainter(Density(1f))
        } catch (_: Exception) {
            null
        }
    }
}

/** Flutter agent_account_screen.dart:614-617: a date and time joined with a space. */
internal fun mediumLocalDateTime(
    atMs: Long,
    locale: java.util.Locale,
    zone: java.util.TimeZone,
): String {
    val instant = java.util.Date(atMs)
    val date =
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale).apply {
            timeZone = zone
        }
    val time =
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale).apply {
            timeZone = zone
        }
    return date.format(instant) + " " + time.format(instant)
}
