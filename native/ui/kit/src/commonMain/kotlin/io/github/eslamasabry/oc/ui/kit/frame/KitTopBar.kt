package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/** How the bar leaves the page: Back at the start, Close at the end (LAY-8), or nothing at a root or in a pane. */
public enum class KitTopBarExit { Back, Close, None }

/**
 * The page's bar (Dart `KitTopBar`): the place in the person's words, one quiet line of state, Back at the start or
 * Close at the end, and the page's actions — at most 2 on compact and 3 on medium; wide windows show them all. Actions
 * past the window's limit go to [more], the one overflow (a `KitRowMenu` from S1-KITA2); a bar with hidden actions
 * and no [more] asserts in debug. Frame text: it never grows past 1.3×, and the title wraps to two lines from 1.3×.
 *
 * States: default, subtitle, switcher, disabled (action)
 * Text: frame
 * Cap: Fill
 * Motion: none
 * Hidden: none
 * Replaces: KitTopBar (lib/ui/kit/kit_top_bar.dart)
 */
@Composable
public fun KitTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    exit: KitTopBarExit = KitTopBarExit.None,
    exitLabel: String? = null,
    onExit: (() -> Unit)? = null,
    onTitleTap: (() -> Unit)? = null,
    titleTapLabel: String? = null,
    actions: List<KitLineAction> = emptyList(),
    more: (@Composable (hidden: List<KitLineAction>) -> Unit)? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val window = LocalKitWindow.current
    val limit =
        when (window.cls) {
            WindowClass.Compact -> 2
            WindowClass.Medium -> 3
            else -> Int.MAX_VALUE
        }
    val shown = actions.take(limit)
    val hidden = actions.drop(limit)
    kitAssert(exit == KitTopBarExit.None || (exitLabel != null && onExit != null)) { "KitTopBar: an exit needs its label and action" }
    kitAssert(onTitleTap == null || !titleTapLabel.isNullOrBlank()) { "KitTopBar: a switcher title says what it does" }
    kitAssert(hidden.isEmpty() || more != null) { "KitTopBar: ${hidden.size} actions past the bar's limit need the overflow" }
    val large = TextScalePolicy.isLargeText(kitTextScale())
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = tokens.rowHeightOne.dp)
            .padding(horizontal = Space.S1.dp)
            .semantics { isTraversalGroup = true }
            .testTag("kit-top-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        if (exit == KitTopBarExit.Back && onExit != null) ExitButton(ExitGlyph.Back, exitLabel.orEmpty(), onExit, -1f)
        val titleBlock: @Composable () -> Unit = {
            Column(Modifier.padding(horizontal = Space.S2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    KitText(
                        title,
                        Modifier.weight(1f, fill = false).semantics { heading() }.testTag("kit-top-bar-title"),
                        role = TypeRole.Headline,
                        kind = KitTextKind.Frame,
                        maxLines = if (large) 2 else 1,
                        pageTitle = true,
                    )
                    if (onTitleTap != null) KitExitGlyph(ExitGlyph.Chevron, colors.text2)
                }
                subtitle?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1) }
            }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (onTitleTap != null) {
                KitTappable(onClick = onTitleTap, onClickLabel = titleTapLabel, shape = KitShapeId.Button) { titleBlock() }
            } else {
                titleBlock()
            }
        }
        // QA wave 4 N1: the inline actions never take more than [ACTIONS_SHARE] of the bar, so the title always keeps
        // room (a "Context 34 % used" action once squeezed it to 2 px). Labels wrap or end in "…" inside that share.
        if (shown.isNotEmpty()) {
            Row(
                Modifier.layout { measurable, constraints ->
                    val cap = (constraints.maxWidth * ACTIONS_SHARE).toInt().coerceAtLeast(constraints.minWidth)
                    val placeable = measurable.measure(constraints.copy(maxWidth = cap))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shown.forEach { KitInlineAction(it, Modifier.weight(1f, fill = false)) }
            }
        }
        if (hidden.isNotEmpty() || more != null) more?.invoke(hidden)
        if (exit == KitTopBarExit.Close && onExit != null) ExitButton(ExitGlyph.Close, exitLabel.orEmpty(), onExit, 1f)
    }
}

@Composable
private fun ExitButton(glyph: ExitGlyph, label: String, onExit: () -> Unit, order: Float) {
    KitTappable(
        onClick = onExit,
        modifier = Modifier.semantics { contentDescription = label; traversalIndex = order }.testTag("kit-top-bar-exit"),
        shape = KitShapeId.Circle,
    ) {
        KitExitGlyph(glyph, LocalKitColors.current.text1)
    }
}

/** The most of the bar's free width the inline actions may take (QA wave 4 N1). */
private const val ACTIONS_SHARE = 0.35f
