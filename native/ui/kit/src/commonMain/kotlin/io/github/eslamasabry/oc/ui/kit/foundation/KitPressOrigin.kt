package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Where the last kit control was pressed, in window px (P-108). A [io.github.eslamasabry.oc.ui.kit.modal.KitMenu]
 * with no anchor of its own (a screen-level menu a row's ⋯ opens through its state) opens from that control, as
 * Flutter's `showKitMenu(position:)` opens from the tap, instead of from the screen's corner over the very button
 * that opened it. Only a press from the moment just before the menu opens counts ([FRESH]), and a menu takes it once.
 */
public class KitPressOrigin(private val time: TimeSource = TimeSource.Monotonic) {
    private var bounds: IntRect? = null
    private var at: TimeMark? = null

    /** A kit control was pressed; [pressed] is its box in the window. */
    public fun record(pressed: IntRect) {
        bounds = pressed
        at = time.markNow()
    }

    /** The control pressed within [fresh] of now, once; null when nothing was, or too long ago. */
    public fun take(fresh: Duration = FRESH): IntRect? {
        val pressed = bounds
        val mark = at
        bounds = null
        at = null
        return if (pressed != null && mark != null && mark.elapsedNow() <= fresh) pressed else null
    }

    public companion object {
        /** A menu opens a frame or two after its tap; a press older than this did not open it. */
        public val FRESH: Duration = 500.milliseconds
    }
}

/** One record for the app (a press opens at most one menu); a test may provide its own. */
public val LocalKitPressOrigin: ProvidableCompositionLocal<KitPressOrigin> = staticCompositionLocalOf { KitPressOrigin() }

internal fun Rect.toIntRect(): IntRect = IntRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
