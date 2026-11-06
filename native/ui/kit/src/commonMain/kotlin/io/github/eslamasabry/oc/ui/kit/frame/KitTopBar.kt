package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape

/**
 * How the bar leaves the page: Back at the start, Close at the end (LAY-8), or nothing at a root or
 * in a pane.
 */
public enum class KitTopBarExit {
    Back,
    Close,
    None,
}

/**
 * The page's bar (Dart `KitTopBar`): the place in the person's words, one quiet line of state, Back
 * at the start or Close at the end, and the page's actions — at most 2 on compact and 3 on medium;
 * wide windows show them all. Actions past the window's limit go to [more], the one overflow (a
 * `KitRowMenu` from S1-KITA2); a bar with hidden actions and no [more] asserts in debug. Frame
 * text: it never grows past 1.3×, and the title wraps to two lines from 1.3×.
 *
 * An action with an [KitLineAction.icon] is an icon button on phones and tablets (its words are what TalkBack says);
 * wide windows show the words. Words never break inside a word: one line, then "…". [large] is a hub's bar: the
 * title in the 28 sp large size on its own line, the actions on the title's line at the end (or beside Back).
 *
 * States: default, subtitle, switcher, disabled (action) Text: frame Cap: Fill Motion: none Hidden:
 * none Replaces: KitTopBar (lib/ui/kit/kit_top_bar.dart)
 */
@Composable
public fun KitTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scope: (@Composable () -> Unit)? = null,
    centred: Boolean = false,
    exit: KitTopBarExit = KitTopBarExit.None,
    exitLabel: String? = null,
    onExit: (() -> Unit)? = null,
    onTitleTap: (() -> Unit)? = null,
    titleTapLabel: String? = null,
    actions: List<KitLineAction> = emptyList(),
    large: Boolean = false,
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
    if (centred) {
        kitAssert(
            actions.isEmpty() &&
                more == null &&
                scope == null &&
                subtitle == null &&
                onTitleTap == null
        ) {
            "KitTopBar: a centred title stands alone beside its exit"
        }
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = tokens.rowHeightOne.dp)
                .padding(vertical = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width((tokens.minTarget + Space.S1).dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (exit == KitTopBarExit.Back && onExit != null)
                    ExitButton(ExitGlyph.Back, exitLabel.orEmpty(), onExit, -1f)
            }
            KitText(
                title,
                Modifier.weight(1f).semantics { heading() },
                role = TypeRole.Title,
                kind = KitTextKind.Frame,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Box(
                Modifier.width((tokens.minTarget + Space.S1).dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (exit == KitTopBarExit.Close && onExit != null)
                    ExitButton(ExitGlyph.Close, exitLabel.orEmpty(), onExit, 1f)
            }
        }
        return
    }
    val shown = actions.take(limit)
    val hidden = actions.drop(limit)
    kitAssert(exit == KitTopBarExit.None || (exitLabel != null && onExit != null)) {
        "KitTopBar: an exit needs its label and action"
    }
    kitAssert(onTitleTap == null || !titleTapLabel.isNullOrBlank()) {
        "KitTopBar: a switcher title says what it does"
    }
    kitAssert(hidden.isEmpty() || more != null) {
        "KitTopBar: ${hidden.size} actions past the bar's limit need the overflow"
    }
    val largeText = TextScalePolicy.isLargeText(kitTextScale())
    val icons = window.cls == WindowClass.Compact || window.cls == WindowClass.Medium
    val actionRow: @Composable () -> Unit = {
        if (shown.isNotEmpty()) {
            Row(
                Modifier.layout { measurable, constraints ->
                    val cap =
                        (constraints.maxWidth * (if (large) LARGE_ACTIONS_SHARE else ACTIONS_SHARE))
                            .toInt()
                            .coerceAtLeast(constraints.minWidth)
                    val placeable = measurable.measure(constraints.copy(maxWidth = cap))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shown.forEach { action ->
                    val glyph = action.icon
                    if (icons && glyph != null) {
                        KitIconButton(
                            icon = glyph,
                            label = action.semanticsLabel ?: action.label,
                            onClick = action.onClick,
                            modifier = action.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                            enabled = KitEnabled.of(action.enabled, action.disabledReason),
                        )
                    } else {
                        // QA wave 4 N1: inline actions never take more than their share of the bar, so the
                        // title always keeps room. One line, ending in "…", never broken inside a word.
                        KitInlineAction(action, Modifier.weight(1f, fill = false), maxLines = 1)
                    }
                }
            }
        }
        if (hidden.isNotEmpty() || more != null) more?.invoke(hidden)
    }
    if (large) {
        Column(
            modifier
                .fillMaxWidth()
                .semantics { isTraversalGroup = true }
                .testTag("kit-top-bar"),
        ) {
            if (exit == KitTopBarExit.Back && onExit != null) Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = tokens.minTarget.dp)
                    .padding(horizontal = Space.S1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (exit == KitTopBarExit.Back && onExit != null)
                    ExitButton(ExitGlyph.Back, exitLabel.orEmpty(), onExit, -1f)
                if (exit == KitTopBarExit.Back && onExit != null) {
                    Box(Modifier.weight(1f))
                    actionRow()
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = tokens.gutter.dp, end = Space.S1.dp, bottom = Space.S2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    KitText(
                        title,
                        Modifier.semantics { heading() }.testTag("kit-top-bar-title"),
                        role = TypeRole.LargeTitle,
                        kind = KitTextKind.Frame,
                        maxLines = 2,
                        pageTitle = true,
                    )
                    if (scope != null) scope()
                    else subtitle?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1) }
                }
                if (exit != KitTopBarExit.Back || onExit == null) actionRow()
                if (exit == KitTopBarExit.Close && onExit != null)
                    ExitButton(ExitGlyph.Close, exitLabel.orEmpty(), onExit, 1f)
            }
        }
        return
    }
    // The title sits on the Back arrow's line (Flutter's chat header); the project · branch line or the subtitle
    // starts under the title's first letter, one left edge for both.
    val back = exit == KitTopBarExit.Back && onExit != null
    val titleRow: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            KitText(
                title,
                Modifier.weight(1f, fill = false)
                    .semantics { heading() }
                    .testTag("kit-top-bar-title"),
                role = TypeRole.Headline,
                kind = KitTextKind.Frame,
                maxLines = if (largeText) 2 else 1,
                pageTitle = true,
            )
            if (onTitleTap != null) KitExitGlyph(ExitGlyph.Chevron, colors.text2)
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.S1.dp)
            .semantics { isTraversalGroup = true }
            .testTag("kit-top-bar"),
    ) {
        val hasLine = scope != null || subtitle != null
        Row(
            Modifier.fillMaxWidth().heightIn(min = (if (hasLine) tokens.minTarget else tokens.rowHeightOne).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            if (back) ExitButton(ExitGlyph.Back, exitLabel.orEmpty(), onExit!!, -1f)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (onTitleTap != null) {
                    KitTappable(
                        onClick = onTitleTap,
                        onClickLabel = titleTapLabel,
                        shape = KitShapeId.Button,
                    ) {
                        titleRow()
                    }
                } else {
                    titleRow()
                }
            }
            actionRow()
            if (exit == KitTopBarExit.Close && onExit != null)
                ExitButton(ExitGlyph.Close, exitLabel.orEmpty(), onExit, 1f)
        }
        val under: (@Composable () -> Unit)? =
            scope
                ?: subtitle?.let {
                    {
                        KitText(
                            it,
                            Modifier.padding(horizontal = Space.S2.dp),
                            role = TypeRole.Secondary,
                            tone = TextTone.Secondary,
                            kind = KitTextKind.Frame,
                            maxLines = 1,
                        )
                    }
                }
        if (under != null) {
            // Past the Back arrow and its gap; the scope's own words keep their 8 dp inset like the title's.
            val lead = (if (back) tokens.minTarget + Space.S1 else 0).dp + if (scope != null) Space.S1.dp else 0.dp
            // The line's words keep their 48 dp targets, but the targets overlap the bar's empty edges by 8 dp so the
            // line reads as the title's second line (Flutter: "my-app · master" right under the title).
            // N-50: only the scope line has targets to tuck; a plain subtitle takes its own room, so the page's first
            // part never sits on it.
            Box(
                Modifier.fillMaxWidth()
                    .padding(start = lead)
                    .then(if (scope != null) Modifier.tuckedTarget(Space.S2.dp) else Modifier)
            ) {
                under()
            }
        }
    }
}

/**
 * The one exit control of every bar: a bare glyph on a 48 dp circle target. Pressing fills the circle; keyboard
 * focus draws a ring instead of a resting fill, so Back looks the same on every screen however it was reached.
 */
@Composable
private fun ExitButton(glyph: ExitGlyph, label: String, onExit: () -> Unit, order: Float) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = LocalKitColors.current
    Box(
        Modifier.semantics {
                contentDescription = label
                traversalIndex = order
            }
            .testTag("kit-top-bar-exit")
            .kitTappable(
                onClick = onExit,
                shape = KitShapeId.Circle,
                interactionSource = interactions,
                focusFill = false,
            )
            .then(if (focused) Modifier.border(2.dp, colors.accent, KitShapeId.Circle.shape()) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        KitExitGlyph(glyph, colors.text1)
    }
}

/** The most of the bar's free width the inline actions may take (QA wave 4 N1). */
private const val ACTIONS_SHARE = 0.35f

/** A large bar's actions sit beside a title that may wrap, so they may take a little more. */
private const val LARGE_ACTIONS_SHARE = 0.45f

/** Reports [by] less height above and below than the content takes, so a 48 dp target can overlap empty edges. */
private fun Modifier.tuckedTarget(by: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val inset = by.roundToPx()
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height - 2 * inset).coerceAtLeast(0)) { placeable.place(0, -inset) }
    }
