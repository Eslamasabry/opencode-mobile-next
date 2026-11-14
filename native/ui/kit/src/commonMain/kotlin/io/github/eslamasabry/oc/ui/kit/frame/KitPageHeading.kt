package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The quiet line's look (Dart `kit_page_heading.dart`, `_buildContent`): the needs-you line is amber at label weight. */
internal data class KitPageHeadingLineLook(val role: TypeRole, val tone: TextTone, val chevronForward: Boolean)

internal fun kitPageHeadingLineLook(lineNeedsYou: Boolean): KitPageHeadingLineLook =
    if (lineNeedsYou) KitPageHeadingLineLook(TypeRole.Label, TextTone.Attention, chevronForward = true)
    else KitPageHeadingLineLook(TypeRole.Body, TextTone.Secondary, chevronForward = false)

/**
 * A page's own heading under a bar that carries no title (Dart `KitPageHeading`): the large title, then one quiet line
 * close under it saying what the page belongs to ("neon-breaker") or where it is ("neon-breaker / src").
 *
 * With [onLine] the line is a switcher: a chevron after the words and a 48 dp target that reaches up into the title's
 * line gap, labelled [lineTapLabel] for screen readers. With [lineNeedsYou] the line is the page's one needs-you line
 * ("2 need you"): amber at label weight with a forward chevron. [lineNeedsYou] defaults to true when [onLine] is set
 * and no [lineTapLabel] is given, which keeps the Chats home's needs-you line as it was.
 *
 * States: default, switcher, needs-you, plain line (disabled)
 * Text: reading
 * Cap: Reading
 * Motion: none
 * Hidden: none
 * Replaces: KitPageHeading (lib/ui/kit/kit_page_heading.dart)
 */
@Composable
public fun KitPageHeading(
    title: String,
    modifier: Modifier = Modifier,
    line: String? = null,
    onLine: (() -> Unit)? = null,
    lineTapLabel: String? = null,
    lineNeedsYou: Boolean = onLine != null && lineTapLabel == null,
    /** Plain icon buttons (48 dp, no ring) at the end of the title's line: search and Settings on the Conversations home. */
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading, CapAlign.Start)
            .fillMaxWidth()
            // A step of air under the bar: gutter at both sides, space2 on top.
            .padding(start = tokens.gutter.dp, end = if (actions == null) tokens.gutter.dp else (tokens.gutter - 12).dp, top = Space.S2.dp)
            .testTag("kit-page-heading"),
    ) {
        // A short window (a phone on its side, cut audit 2 finding 10): the large title took a fifth of the height above
        // a list that had room for half a row, so the heading drops to the title size on one line there. It still
        // scrolls away with the list like every other part of the header.
        val short = io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow.current.short
        val titleText: @Composable (Modifier) -> Unit = { m ->
            KitText(
                title,
                m.semantics { heading() },
                role = if (short) TypeRole.Title else TypeRole.LargeTitle, // F: lib/ui/kit/kit_page_heading.dart:133
                maxLines = if (short) 1 else 2,
            )
        }
        if (actions == null) titleText(Modifier)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            titleText(Modifier.weight(1f))
            actions()
        }
        if (line != null) {
            val look = kitPageHeadingLineLook(lineNeedsYou)
            val words: @Composable () -> Unit = { KitText(line, role = look.role, tone = look.tone, maxLines = 1) } // F: lib/ui/kit/kit_page_heading.dart:76
            // The words sit a step up, close under the title, as one heading. Flutter lifts the whole 48 dp target
            // into the title's line gap; here the target keeps the kit's control gap (space1) under the title, so no
            // control overlaps the words above, and only its words move up: the picture and the heading's height
            // stay Flutter's.
            val lifted = Modifier.offset(y = -Space.S2.dp)
            val target = Modifier.defaultMinSize(minHeight = tokens.minTarget.dp)
            if (onLine == null) {
                Box(lifted.then(target), contentAlignment = Alignment.CenterStart) { words() }
            } else {
                val clear = Space.S1.dp
                val dropped = Modifier.layout { m, c ->
                    val p = m.measure(c)
                    layout(p.width, p.height) { p.place(0, clear.roundToPx()) }
                }
                KitTappable(onLine, dropped.then(target), onClickLabel = lineTapLabel ?: line, ink = look.tone) {
                    Row(Modifier.offset(y = -(Space.S2 + Space.S1).dp).then(target), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f, fill = false)) { words() }
                        Spacer(Modifier.width(Space.S1.dp))
                        KitIcon(
                            if (look.chevronForward) KitIcons.chevronRight else KitIcons.chevronDown,
                            size = KitIconSize.Small,
                            tone = look.tone,
                        )
                    }
                }
            }
        }
    }
}
