package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The download's share in the demo: the sheet says "42 %", and the close-up draws the same. */
private const val DEMO_FRACTION = 0.42f

/** A close-up is the mark at this many times its real size. */
private const val CLOSE_UP_TIMES = 6

/** The sheet's rows, in the brief's order, with the words each mark sits beside. */
private val DEMO_ROWS: List<Pair<KitWaitKind, String>> =
    listOf(
        KitWaitKind.Starting to "Starting OpenCode…",
        KitWaitKind.Warming to "Claude Code is warming up…",
        KitWaitKind.Downloading to "Downloading 42 %",
        KitWaitKind.Writing to "Writing…",
        KitWaitKind.Sending to "Sending…",
        KitWaitKind.NeedsYou to "Needs you",
    )

/**
 * Judge's scene. Draws with whatever KitTheme the caller provides (style, dark or light, motion level). [colour] draws
 * the marks in [KitWaitMode.Colour] (else Ink); [alternates] shows the other drawing of Starting, Writing and Sending
 * (the other kinds have one).
 */
@Composable
public fun KitWaitMarksDemo(phase: Float, closeUp: Int = -1, colour: Boolean = false, alternates: Boolean = false) {
    // No clock here: the same drawing code as KitWaitMark, at the phase it is given. With motion Off, the stills.
    val still = KitMotion.state.reduced
    val clock = remember(phase, still) { WaitClock(still = still, phase = { phase }, fraction = { DEMO_FRACTION }) }
    val mode = if (colour) KitWaitMode.Colour else KitWaitMode.Ink
    val variant = if (alternates) KitWaitVariant.Alternate else KitWaitVariant.Default
    val one = DEMO_ROWS.getOrNull(closeUp)
    if (one != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WaitMarkFace(
                waitDrawing(one.first, variant),
                mode,
                TextTone.Primary,
                clock,
                Modifier.size(KitWaitMarkDefaults.Size * CLOSE_UP_TIMES),
            )
        }
        return
    }
    val tokens = LocalKitTokens.current
    Column(Modifier.fillMaxWidth()) {
        DEMO_ROWS.forEach { (kind, words) ->
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = tokens.minTarget.dp)
                    .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaitMarkFace(waitDrawing(kind, variant), mode, TextTone.Primary, clock, Modifier.size(KitWaitMarkDefaults.Size))
                Spacer(Modifier.width(Space.S2.dp))
                KitText(words, role = TypeRole.Body, tone = TextTone.Primary, kind = KitTextKind.Frame)
            }
        }
    }
}
