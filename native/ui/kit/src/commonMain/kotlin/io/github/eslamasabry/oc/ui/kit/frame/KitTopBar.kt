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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowPageTitle
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitContentEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitBrandMark
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouBadge
import io.github.eslamasabry.oc.ui.kit.state.kitNeedsYouSpan
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTopBarSearch
import io.github.eslamasabry.oc.ui.words.res.kitTopBarSwitchProject
import io.github.eslamasabry.oc.ui.words.res.kitTopBarSwitchServer
import io.github.eslamasabry.oc.ui.words.res.librarySettingsTitle
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.unit.Constraints
import io.github.eslamasabry.oc.ui.design.color.StatusTone

/**
 * How the bar leaves the page: Back at the start, Close at the end (LAY-8), or nothing at a root or
 * in a pane.
 */
public enum class KitTopBarExit {
    Back,
    Close,
    None,
}

/** The bar title's size (P-016), pure so it is tested without Compose. */
public object TopBarTitle {
    /**
     * Every pushed page names itself at the headline size with the page-title weight (Flutter `_KitTopBarTitle`:
     * `KitText.styleOf(context, KitTextRole.headline, pageTitle: true)`), switcher or not; the large title belongs
     * to a tab's own body heading (`KitPageHeading`), never to the bar. [switcher] is kept for callers' clarity.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun role(switcher: Boolean): TypeRole = TypeRole.Headline // F: lib/ui/kit/kit_top_bar.dart:591
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
    /**
     * E20-S13 (the chat header): Back sits on the title's row and the subtitle or scope line right under the title, one
     * compact block instead of three rows. The title then starts beside Back, not on the gutter.
     */
    backBesideTitle: Boolean = false,
    /** The product mark beside the title, at a root only (Dart `brand`: exit none). */
    brand: Boolean = false,
    /** Requests waiting elsewhere: a switcher title carries the amber badge (Dart `needsYou`). */
    needsYou: Int = 0,
    /** The subtitle's state colour (Dart `subtitleTone`): neutral is secondary, progress the accent, ok green. */
    subtitleTone: StatusTone = StatusTone.Neutral,
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
                        Modifier.semantics { heading() }.kitContentEdge("bar-title").kitGrowPageTitle().testTag("kit-top-bar-title"),
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
    // The title starts on the page gutter (E20-S08: Back, when there is one, takes the line above); the project ·
    // branch line or the subtitle starts under the title's first letter, one left edge for both.
    val back = exit == KitTopBarExit.Back && onExit != null
    // Flutter's pushed page: Back, the title and the actions share one row (kit_top_bar.dart:346-420).
    val oneRowBack = back && !backBesideTitle
    val titleRole = TopBarTitle.role(switcher = onTitleTap != null)
    val titleRow: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            // Dart brand: the small mark leads the name, the name in the headline size (kit_top_bar.dart:690-708).
            val branded = brand && exit == KitTopBarExit.None
            // space2 to the name: the row's space1 step plus space1 of the mark's own.
            if (branded) KitBrandMark(Modifier.padding(end = Space.S1.dp))
            KitText(
                title,
                Modifier.weight(1f, fill = false)
                    .semantics { heading() }
                    // A pushed page's title starts on the 56 dp text rail beside Back (Flutter), not on the content edge.
                    .then(if (oneRowBack) Modifier else Modifier.kitContentEdge("bar-title")).kitGrowPageTitle()
                    .testTag("kit-top-bar-title"),
                role = if (branded) TypeRole.Headline else titleRole,
                kind = KitTextKind.Frame,
                // Flutter's chat header: a long title takes a second line before it ends in "…" beside the chevron.
                maxLines = 2,
                pageTitle = true,
            )
            if (onTitleTap != null) KitExitGlyph(ExitGlyph.Chevron, colors.text2)
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = if (oneRowBack) 0.dp else Space.S1.dp)
            .semantics { isTraversalGroup = true }
            .testTag("kit-top-bar"),
    ) {
        val hasLine = scope != null || subtitle != null
        val typography = LocalKitTypography.current
        val titleLine = typography.style(titleRole).lineHeight
        val lineLine = typography.style(TypeRole.Secondary).lineHeight
        // E20-S08: the title's words start on the page gutter, the transcript's and the rows' edge, also beside a
        // Back button (they sat 48 dp further in, past it). Back, and the actions, take the line above the title.
        val titleStart = (tokens.gutter - Space.S1 - Space.S2).dp
        val title: @Composable () -> Unit = {
            if (onTitleTap != null) {
                KitNeedsYouBadge(needsYou) {
                    KitTappable(
                        onClick = onTitleTap,
                        onClickLabel = titleTapLabel,
                        shape = KitShapeId.Button,
                    ) {
                        titleRow()
                    }
                }
            } else {
                titleRow()
            }
        }
        val under: (@Composable () -> Unit)? =
            scope
                ?: subtitle?.let {
                    {
                        // Flutter `_KitTopBarTailLine`: a bar that is not a switcher starts its line with the
                        // attention-toned "Needs you · " (the switcher carries the count as a badge instead).
                        val needs = if (needsYou > 0 && onTitleTap == null) kitNeedsYouSpan(needsYou).text else null
                        Row(Modifier.padding(horizontal = Space.S2.dp).then(if (oneRowBack) Modifier else Modifier.kitContentEdge("bar-subtitle"))) {
                            if (needs != null)
                                KitText(needs, role = TypeRole.Secondary, tone = TextTone.Attention, kind = KitTextKind.Frame, maxLines = 1)
                            KitText(
                                it,
                                Modifier.weight(1f, fill = false),
                                role = TypeRole.Secondary,
                                tone = subtitleTone.text,
                                kind = KitTextKind.Frame,
                                maxLines = 1,
                            )
                        }
                    }
                }
        if (back && backBesideTitle) {
            // E20-S13: one block. Back, then the title with its line tight under it (the kit's 2 dp step), then the
            // actions; Back and the actions centre on the block.
            Row(
                Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).testTag("kit-top-bar-compact"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExitButton(ExitGlyph.Back, exitLabel.orEmpty(), onExit, -1f)
                Column(Modifier.weight(1f).padding(vertical = Space.S1.dp)) {
                    Box(
                        if (under != null) Modifier.zIndex(1f).clipToBounds().endUnderText(titleLine, 0.dp)
                        else Modifier
                    ) {
                        title()
                    }
                    if (under != null)
                        Box(
                            Modifier.padding(start = if (scope != null) Space.S1.dp else 0.dp)
                                .riseToText(lineLine, TITLE_LINE_GAP, if (scope != null) Space.S2.dp else 0.dp)
                        ) {
                            under()
                        }
                }
                actionRow()
            }
            return@Column
        }
        if (back) {
            BackBar(
                exit = { ExitButton(ExitGlyph.Back, exitLabel.orEmpty(), onExit, -1f) },
                // E20-S10: with a line under it, the title's room (and its tap area) ends just under its words, and it
                // stays above the line's targets, so the line sits right under it without taking its taps.
                title = {
                    Box(if (hasLine) Modifier.zIndex(1f).clipToBounds().endUnderText(titleLine, 0.dp) else Modifier) { title() }
                },
                titleLine = titleLine,
                trimmed = hasLine,
                under = under?.let { u ->
                    {
                        // A scope's words keep their own 8 dp inset like the title's; the line's words start the kit's
                        // tight 2 dp step under the title's words, its targets keeping their 48 dp below (E20-S10).
                        Box(
                            Modifier.padding(start = if (scope != null) Space.S1.dp else 0.dp)
                                .riseToText(lineLine, TITLE_LINE_GAP, if (scope != null) Space.S2.dp else 0.dp)
                        ) { u() }
                    }
                },
                actions = actionRow,
            )
            return@Column
        } else Row(
            Modifier.fillMaxWidth().heightIn(min = (if (hasLine) tokens.minTarget else tokens.rowHeightOne).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            // No exit: the title's words start on the gutter, like the page's first row (Flutter kit_top_bar.dart,
            // "the title starts on the gutter"); the bar's 4 dp and the title row's 8 dp leave 4 dp to add.
            Row(Modifier.weight(1f).padding(start = titleStart), verticalAlignment = Alignment.CenterVertically) {
                title()
            }
            actionRow()
            if (exit == KitTopBarExit.Close && onExit != null)
                ExitButton(ExitGlyph.Close, exitLabel.orEmpty(), onExit, 1f)
        }
        if (under != null) {
            // Under the title's first letter; the scope's own words keep their 8 dp inset like the title's.
            val lead = titleStart + if (scope != null) Space.S1.dp else 0.dp
            // The line's words keep their 48 dp targets, but the targets overlap the bar's empty edges by 8 dp so the
            // line reads as the title's second line (Flutter: "my-app · master" right under the title).
            // N-50: only the scope line has targets to tuck; a plain subtitle takes its own room, so the page's first
            // part never sits on it.
            Box(
                Modifier.fillMaxWidth()
                    .padding(start = lead)
                    .then(
                        when {
                            // E20-S10 (owner's phone: "new · This phone" sat far below "Hi" on a row of its own): under
                            // a Back bar's title the line's words start the kit's tight title/subtitle step under the
                            // title's words, as a hub bar's do; its targets keep their 48 dp below.
                            back -> Modifier.riseToText(lineLine, TITLE_LINE_GAP, if (scope != null) Space.S2.dp else 0.dp)
                            scope != null -> Modifier.tuckedTarget(Space.S2.dp)
                            else -> Modifier
                        }
                    )
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

/**
 * A pushed page's bar (Dart `KitTopBar`, kit_top_bar.dart:346-420): one row of Back, the title block and the actions.
 * The title's words start on the page's text rail (56 dp: `textRail - minTarget` after Back, less the 8 dp the title
 * row pads itself), its line under it. The title's first line and the buttons share one centre: a line shorter than a
 * touch target moves down onto the buttons' centre, a taller one (large text) moves the buttons down instead. The row
 * sits in the bar's 4 dp step above and below (min height a touch target plus two steps), and ends 4 dp from the edge.
 */
@Composable
private fun BackBar(
    exit: @Composable () -> Unit,
    title: @Composable () -> Unit,
    titleLine: androidx.compose.ui.unit.TextUnit,
    trimmed: Boolean,
    under: (@Composable () -> Unit)?,
    actions: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    Layout(
        contents = listOf(exit, title, { if (under != null) under() }, { Row(verticalAlignment = Alignment.CenterVertically) { actions() } }),
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = (tokens.minTarget + 2 * Space.S1).dp) // F: lib/ui/kit/kit_top_bar.dart:270
            .padding(vertical = Space.S1.dp), // F: lib/ui/kit/kit_top_bar.dart:414
    ) { (exitM, titleM, underM, actionsM), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val exitP = exitM.map { it.measure(loose) }
        // The actions cap their own share of the bar (ACTIONS_SHARE), so the title always keeps room.
        val actionsP = actionsM.map { it.measure(loose) }
        val exitW = exitP.maxOfOrNull { it.width } ?: 0
        val actionsW = actionsP.maxOfOrNull { it.width } ?: 0
        val gap = if (actionsW > 0) Space.S2.dp.roundToPx() else 0 // F: kit_top_bar.dart:394
        val end = Space.S1.dp.roundToPx() // F: kit_top_bar.dart:446 (_endRoom without a last glyph)
        // The title row pads its words by space 2 itself: its box starts that much before the 56 dp text rail.
        // The title row's own 8 dp start padding may lie over Back's last 4 dp of empty target edge.
        val titleX = maxOf(exitW + (Space.S1 - Space.S2).dp.roundToPx(), (FlutterKitTokens.TEXT_RAIL - Space.S2).dp.roundToPx()) // F: kit_top_bar.dart:362-370
        val titleW = (width - titleX - gap - actionsW - end).coerceAtLeast(0)
        val titleP = titleM.map { it.measure(Constraints(maxWidth = titleW)) }
        val underP = underM.map { it.measure(Constraints(maxWidth = titleW)) }
        val t = titleP.firstOrNull()
        // The first line's centre. The words sit centred in the title's box (a switcher's is a touch target tall); a
        // title [trimmed] to end under its words keeps the words' bottom as its own.
        val lineCentre =
            if (t == null) 0
            else {
                val first = t[FirstBaseline]
                val last = t[LastBaseline]
                val line = if (titleLine.isSp) titleLine.roundToPx() else t.height
                val words = if (first != AlignmentLine.Unspecified && last != AlignmentLine.Unspecified) (last - first) + line else t.height
                val wordsTop = if (trimmed) t.height - words else (t.height - words) / 2
                (wordsTop + line / 2).coerceAtLeast(0)
            }
        val lift = tokens.minTarget.dp.roundToPx() / 2 - lineCentre // F: kit_top_bar.dart:332
        val titleTop = lift.coerceAtLeast(0)
        val controlsTop = (-lift).coerceAtLeast(0)
        val titleH = (t?.height ?: 0) + underP.sumOf { it.height }
        val controlsH = maxOf(exitP.maxOfOrNull { it.height } ?: 0, actionsP.maxOfOrNull { it.height } ?: 0)
        val height = maxOf(titleTop + titleH, controlsTop + controlsH, constraints.minHeight)
        layout(width, height) {
            // Flutter puts Back flush on the window edge; native keeps the kit's 4 dp from the edge for a control (layout
            // guard edge-tap). The glyph lands 4 dp further in; the title stays on the text rail.
            exitP.forEach { it.placeRelative(Space.S1.dp.roundToPx(), controlsTop + (tokens.minTarget.dp.roundToPx() - it.height).coerceAtLeast(0) / 2) }
            titleP.forEach { it.placeRelative(titleX, titleTop) }
            var y = titleTop + (t?.height ?: 0)
            underP.forEach {
                it.placeRelative(titleX, y)
                y += it.height
            }
            actionsP.forEach { it.placeRelative(width - end - it.width, controlsTop + (tokens.minTarget.dp.roundToPx() - it.height).coerceAtLeast(0) / 2) }
        }
    }
}

/** The most of the bar's free width the inline actions may take (QA wave 4 N1). */
private const val ACTIONS_SHARE = 0.35f

/** A large bar's actions sit beside a title that may wrap, so they may take a little more. */
private const val LARGE_ACTIONS_SHARE = 0.45f

/** The kit's tight title/subtitle step (a hub bar's title column): the line's words this far under the title's. */
private val TITLE_LINE_GAP = 2.dp

/**
 * How tall the words inside [p] are, from its first and last baselines and one [line] height, or null when it has no
 * baselines. A frame text capped below the window's font scale makes this a little too tall, never too short.
 */
private fun androidx.compose.ui.layout.MeasureScope.wordsHeight(
    p: androidx.compose.ui.layout.Placeable,
    line: androidx.compose.ui.unit.TextUnit,
): Int? {
    val first = p[androidx.compose.ui.layout.FirstBaseline]
    val last = p[androidx.compose.ui.layout.LastBaseline]
    if (first == androidx.compose.ui.layout.AlignmentLine.Unspecified || last == androidx.compose.ui.layout.AlignmentLine.Unspecified || !line.isSp) return null
    return (last - first) + line.roundToPx()
}

/**
 * E20-S10: reports the content's height only down to the bottom of its vertically centred words (below [top], the
 * padding above the centred area), so what follows starts right under them. Unchanged without baselines.
 */
private fun Modifier.endUnderText(line: androidx.compose.ui.unit.TextUnit, top: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val pad = top.roundToPx()
        val words = wordsHeight(p, line)
        val inner = (p.height - pad).coerceAtLeast(0)
        val bottom = if (words == null) p.height else pad + (inner + words.coerceAtMost(inner)) / 2
        layout(p.width, bottom.coerceIn(0, p.height)) { p.place(0, 0) }
    }

/**
 * E20-S10: rises so the content's vertically centred words start [gap] under what is above it, and reports [bottom]
 * less height below (a target overlapping the empty edge under the bar). Unchanged without baselines.
 */
private fun Modifier.riseToText(
    line: androidx.compose.ui.unit.TextUnit,
    gap: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
): Modifier =
    layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val words = wordsHeight(p, line)
        val slack = if (words == null) 0 else ((p.height - words) / 2).coerceAtLeast(0)
        val rise = (slack - gap.roundToPx()).coerceAtLeast(-gap.roundToPx())
        val height = (p.height - rise - bottom.roundToPx()).coerceAtLeast(0)
        layout(p.width, height) { p.place(0, -rise) }
    }

/** Reports [by] less height above and below than the content takes, so a 48 dp target can overlap empty edges. */
private fun Modifier.tuckedTarget(by: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val inset = by.roundToPx()
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height - 2 * inset).coerceAtLeast(0)) { placeable.place(0, -inset) }
    }

/** Where the shell's controls sit (Dart `KitShellControlsLayout`). */
public enum class KitShellControlsLayout {
    /** One row: the server pill at the start; search and, on a phone, the Settings gear at the end. */
    Bar,

    /** The PC sidebar header: the server pill, the project switcher, then a full-width search that looks like a field. */
    Sidebar,
}

/**
 * The shell's top controls (Dart `KitShellControls`, kit_top_bar.dart): the server pill, search and, on a phone, the
 * Settings gear. In the bar the pill is one line at the start ("name · state"); search and the gear are round icon
 * buttons at the end. In the sidebar the name is never cut and the state is a second line ("Connected" is left to the
 * green dot there and stays in the spoken label); the project switcher and a field-like search follow.
 *
 * States: connected, reconnecting, not answering, needs-you badge Text: frame Cap: Fill Motion: the pill's mark only
 * Replaces: KitShellControls (lib/ui/kit/kit_top_bar.dart)
 */
@Composable
public fun KitShellControls(
    server: String,
    serverStatus: String,
    serverTone: KitConnectionTone,
    onServer: () -> Unit,
    modifier: Modifier = Modifier,
    needsYou: Int = 0,
    project: String? = null,
    onProject: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    showServer: Boolean = true,
    layout: KitShellControlsLayout = KitShellControlsLayout.Bar,
) {
    val tokens = LocalKitTokens.current
    val switchServer = stringResource(Res.string.kitTopBarSwitchServer)
    val searchLabel = stringResource(Res.string.kitTopBarSearch)
    if (layout == KitShellControlsLayout.Bar) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Dart: Flexible(Align(centerStart, pill)), or a Spacer without the pill.
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (showServer) {
                    KitNeedsYouBadge(needsYou) {
                        KitConnectionPill(server, serverStatus, serverTone, switchServer, onServer)
                    }
                }
            }
            if (onSearch != null || onSettings != null) {
                Box(Modifier.width(Space.S2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S4.dp), verticalAlignment = Alignment.CenterVertically) { // F: lib/ui/kit/kit_top_bar.dart:1194
                    if (onSearch != null)
                        KitIconButton(
                            icon = KitIcons.search,
                            label = searchLabel,
                            onClick = onSearch,
                            round = true,
                            size = if (onSettings != null) 22 else 24, // F: lib/ui/kit/kit_top_bar.dart:1164
                        )
                    if (onSettings != null)
                        KitIconButton(
                            icon = KitIcons.settingsGear,
                            label = stringResource(Res.string.librarySettingsTitle),
                            onClick = onSettings,
                            round = true,
                            size = 24, // F: lib/ui/kit/kit_top_bar.dart:1173
                        )
                }
            }
        }
        return
    }
    // The sidebar's words grow with the text size without a cap (Dart KitText.capScale max: infinity).
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) { // F: lib/ui/kit/kit_top_bar.dart:1205
        KitNeedsYouBadge(needsYou) { SidebarServerPill(server, serverStatus, serverTone, "$server, $serverStatus, $switchServer", onServer) }
        if (project != null && onProject != null) {
            val switchProject = stringResource(Res.string.kitTopBarSwitchProject)
            KitTappable(
                onClick = onProject,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$project, $switchProject" }, // NV: Dart joins the name and its l10n words with ", " (kit_top_bar.dart:1215)
                shape = KitShapeId.Button,
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).padding(horizontal = Space.S3.dp, vertical = Space.S1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KitText(KitBidi.auto(project), Modifier.weight(1f), role = TypeRole.Headline, maxLines = 2) // F: lib/ui/kit/kit_top_bar.dart:1232
                    Box(Modifier.width(Space.S1.dp))
                    KitIcon(KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
                }
            }
        }
        if (onSearch != null) {
            ShellPillSurface(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .kitTappable(onClick = onSearch, shape = KitShapeId.Pill)
                        .clearAndSetSemantics { contentDescription = searchLabel }
                        .heightIn(min = tokens.minTarget.dp)
                        .padding(horizontal = Space.S3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KitIcon(KitIcons.search, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
                    Box(Modifier.width(Space.S2.dp))
                    KitText(searchLabel, Modifier.weight(1f), role = TypeRole.Label, tone = TextTone.Secondary, maxLines = 1)
                }
            }
        }
    }
}

/** The pill's surface: `surface2` as a control, the style's own look (Dart `KitTappable(surface: surface2)` on KitGlass). */
@Composable
private fun ShellPillSurface(modifier: Modifier, content: @Composable () -> Unit) {
    val colors = LocalKitColors.current
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Control, KitShapeId.Pill, SurfaceLevel.Surface2, ownFill = colors.surface2))
    Box(modifier.then(look.modifier)) { content() }
}

/** The sidebar's server pill (Dart `_KitServerPillContent`, expand): the mark, the whole name, the state word under it unless ok. */
@Composable
private fun SidebarServerPill(server: String, status: String, tone: KitConnectionTone, spoken: String, onClick: () -> Unit) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    ShellPillSurface(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .kitTappable(onClick = onClick, shape = KitShapeId.Pill)
                .clearAndSetSemantics { contentDescription = spoken }
                .heightIn(min = tokens.minTarget.dp)
                .padding(start = Space.S3.dp, end = Space.S2.dp, top = Space.S1.dp, bottom = Space.S1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dot =
                when (tone) {
                    KitConnectionTone.Ok -> colors.success
                    KitConnectionTone.Working -> colors.accent
                    KitConnectionTone.Problem -> colors.attention
                    KitConnectionTone.Off -> colors.text3
                }
            Box(Modifier.size(tokens.smallIcon.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(Space.S2.dp).drawBehind { drawCircle(dot) })
            }
            Box(Modifier.width(Space.S2.dp))
            Column(Modifier.weight(1f)) {
                KitText(KitBidi.auto(server), role = TypeRole.RowTitle)
                if (tone != KitConnectionTone.Ok) KitText(status, role = TypeRole.Caption, tone = TextTone.Secondary)
            }
            Box(Modifier.width(Space.S1.dp))
            KitIcon(KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
        }
    }
}

/**
 * The shell's bar on compact and medium windows (Dart `KitTopBar.shell`): [controls] (a [KitShellControls] in its bar
 * layout), then the page's [trailing] actions, on the gutter with space4 above, a step below and a touch target's
 * height at least.
 *
 * Text: frame Cap: Fill Motion: none Replaces: KitTopBar.shell (lib/ui/kit/kit_top_bar.dart)
 */
@Composable
public fun KitShellTopBar(
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    controls: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S4.dp, bottom = Space.S1.dp) // F: lib/ui/kit/kit_top_bar.dart:353
            .heightIn(min = tokens.minTarget.dp)
            .semantics { isTraversalGroup = true }
            .testTag("kit-shell-top-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { controls() }
        if (trailing != null) {
            Box(Modifier.width(Space.S2.dp))
            trailing()
        }
    }
}
