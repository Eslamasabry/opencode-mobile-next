package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.coroutines.delay

/**
 * The one arrival request of an opened page (Dart `_KitArrivalRequest`): the row [rowId] a search result named, claimed
 * at most once, so a rebuild or the same row built anew never repeats the arrival. Pure, so it is tested without
 * Compose.
 */
public class KitArrivalRequest(public val rowId: String) {
    private var claimed = false

    /** True once: for the first row asking with [id] equal to [rowId]; false for any other id and every later ask. */
    public fun claim(id: String): Boolean {
        if (claimed || id != rowId) return false
        claimed = true
        return true
    }
}

private val LocalKitArrival = compositionLocalOf<KitArrivalRequest?> { null }

/**
 * Where a search result arrives on the page it opens (Dart `KitArrivalScope`, kit_arrival.dart): one row, named by a
 * stable anchor id such as `effects-vibration`. The first [KitArrival] below it with that id claims the request, and
 * nothing claims it again. Build it once per opened page so the request lives as long as that page.
 */
@Composable
public fun KitArrivalScope(rowId: String, content: @Composable () -> Unit) {
    val request = remember(rowId) { KitArrivalRequest(rowId) }
    CompositionLocalProvider(LocalKitArrival provides request, content = content)
}

/** The row the page under this composition was opened for, or null (Dart `KitArrivalScope.maybeRowId`). */
@Composable
public fun kitArrivalRowId(): String? = LocalKitArrival.current?.rowId

/** How long the wash stays after the row came into view (Dart `KitArrival.hold`). */
public const val KIT_ARRIVAL_HOLD_MS: Int = MotionTokens.ARRIVAL_HOLD

/** The wash's strength: the find mark's passive hit (Dart `KitArrival.washAlpha`). */
public const val KIT_ARRIVAL_WASH_ALPHA: Float = .18f // F: lib/ui/kit/kit_arrival.dart:80

/**
 * One row a search result can arrive at (Dart `KitArrival`). When the page was opened for this row
 * ([KitArrivalScope]), it brings the row into view once laid out and washes it in the accent for
 * [KIT_ARRIVAL_HOLD_MS], then fades the wash over the entrance time; otherwise it draws only [content].
 * The wash sits behind the row (never a text colour), so the words keep their contrast. Reduced motion
 * drops the wash at once instead of fading it.
 *
 * States: none — a passive mark around one row. Text: none. Cap: Wrapper. Motion: the wash fade.
 * Replaces: KitArrival (lib/ui/kit/kit_arrival.dart)
 */
@Composable
public fun KitArrival(id: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val request = LocalKitArrival.current
    // Asked once per row, as Dart's didChangeDependencies guard: a recomposition never claims again.
    val claimed = remember(request, id) { request?.claim(id) == true }
    if (!claimed) {
        Box(modifier) { content() }
        return
    }
    val motion = KitMotion.state
    val wash = LocalKitColors.current.accent.copy(alpha = KIT_ARRIVAL_WASH_ALPHA)
    var marked by remember { mutableStateOf(true) }
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        bring.bringIntoView()
        delay(KIT_ARRIVAL_HOLD_MS.toLong())
        marked = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (marked) 1f else 0f,
        // Dart: Duration.zero while marked or reduced, else KitMotion.entrance on KitMotion.enter.
        animationSpec =
            if (marked || motion.reduced) snap() else tween(FlutterKitMotion.ENTRANCE_MS, easing = KitEasing.Enter),
        label = "kit-arrival-wash",
    )
    Box(modifier.bringIntoViewRequester(bring).semantics { isTraversalGroup = true }) {
        Box(Modifier.matchParentSize().drawBehind { if (alpha > 0f) drawRect(wash, alpha = alpha) })
        content()
    }
}
