package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The pure rules of the team parts (Dart `teamTone`, `teamIcon` and the branches of `teamRow` / `teamCard`,
 * lib/ui/kit/team/team_parts.dart at 8302de537), kept apart so they are tested without a composition.
 */
public object KitTeamRules {
    /** The attention card's edge: `attention` at this alpha, one hairline wide (team_parts.dart:217-218). */
    public const val ATTENTION_EDGE_ALPHA: Float = .30f // F: lib/ui/kit/team/team_parts.dart:217

    /** The attention card's Solid fill: `attention` at this alpha over `surface1` (team_parts.dart:226-229). */
    public const val ATTENTION_FILL_ALPHA: Float = .09f // F: lib/ui/kit/team/team_parts.dart:227

    /** Dart `teamTone`: needs-you reads in attention, failed in primary text, everything else secondary. */
    public fun tone(state: KitTeamState): TextTone =
        when (state) {
            KitTeamState.NeedsYou -> TextTone.Attention
            KitTeamState.Failed -> TextTone.Primary
            else -> TextTone.Secondary
        }

    /** Dart `teamIcon`: the Material glyph of each state. */
    public fun glyph(state: KitTeamState): ImageVector =
        when (state) {
            KitTeamState.Empty -> KitTeamGlyphs.inboxOutlined
            KitTeamState.Loading -> KitTeamGlyphs.hourglassEmpty
            KitTeamState.Running -> KitTeamGlyphs.pendingOutlined
            KitTeamState.NeedsYou -> KitTeamGlyphs.notificationsNone
            KitTeamState.Stalled -> KitTeamGlyphs.pauseCircleOutline
            KitTeamState.Failed -> KitTeamGlyphs.errorOutline
            KitTeamState.Done -> KitTeamGlyphs.checkCircleOutline
            KitTeamState.Stale -> KitTeamGlyphs.history
        }

    /**
     * The row's progress fraction, or null when no bar is drawn: both counts given, a positive total and
     * 0 <= completed <= total (team_parts.dart:83-87).
     */
    public fun progress(completed: Int?, total: Int?): Float? =
        if (completed != null && total != null && total > 0 && completed >= 0 && completed <= total) {
            completed.toFloat() / total
        } else {
            null
        }

    /** A row ends in a trailing value, a chevron or both when either is present (team_parts.dart:100). */
    public fun hasEnd(trailing: String?, opens: Boolean): Boolean = trailing != null || opens

    /** A stale or loading card offers no actions: its data may be out of date (team_parts.dart:144-145). */
    public fun allowsActions(state: KitTeamState): Boolean = state != KitTeamState.Stale && state != KitTeamState.Loading

    /** The action block shows when actions are allowed and there is at least one (team_parts.dart:196-197). */
    public fun showsActions(state: KitTeamState, hasPrimary: Boolean, hasSecondary: Boolean, others: Int): Boolean =
        allowsActions(state) && (hasPrimary || hasSecondary || others > 0)

    /** Only a failed or needs-you card leads its title with the state glyph (team_parts.dart:153). */
    public fun cardMarked(state: KitTeamState): Boolean = state == KitTeamState.Failed || state == KitTeamState.NeedsYou

    /** The card title reads in attention when the team needs the person (team_parts.dart:167). */
    public fun cardTitleTone(state: KitTeamState): TextTone =
        if (state == KitTeamState.NeedsYou) TextTone.Attention else TextTone.Primary

    /** The card's leading glyph: attention when the team needs the person, primary text when it failed (:157). */
    public fun cardGlyphTone(state: KitTeamState): TextTone =
        if (state == KitTeamState.NeedsYou) TextTone.Attention else TextTone.Primary

    /** A failed card's status line is a row title; every other status is secondary (team_parts.dart:175-177). */
    public fun cardStatusRole(state: KitTeamState): TypeRole =
        if (state == KitTeamState.Failed) TypeRole.RowTitle else TypeRole.Secondary

    /** [neutral] keeps the status secondary whatever the state (team_parts.dart:178). */
    public fun cardStatusTone(state: KitTeamState, neutral: Boolean): TextTone =
        if (neutral) TextTone.Secondary else tone(state)
}

/**
 * The Material glyphs the Flutter team parts draw (`Icons.*` in team_parts.dart:15-24 and :114), as vectors: the
 * Phosphor set of `KitIcons` has none of them. Artwork: Material Icons (Apache-2.0), the same shapes Flutter's
 * MaterialIcons font draws. The chevron mirrors in RTL, as `Icons.chevron_right` does (matchTextDirection).
 */
public object KitTeamGlyphs {
    public val inboxOutlined: ImageVector by lazy {
        material("inbox_outlined", "M19 3H5c-1.1 0-2 .9-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5v-3h3.56c.69 1.19 1.97 2 3.45 2s2.75-.81 3.45-2H19v3zm0-5h-4.99c0 1.1-.9 2-2 2s-2-.9-2-2H5V5h14v9z")
    }
    public val hourglassEmpty: ImageVector by lazy {
        material("hourglass_empty", "M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6zm10 14.5V20H8v-3.5l4-4 4 4zm-4-5-4-4V4h8v3.5l-4 4z")
    }
    public val pendingOutlined: ImageVector by lazy {
        material(
            "pending_outlined",
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z" +
                "M5.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z" +
                "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z" +
                "M15.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z",
        )
    }
    public val notificationsNone: ImageVector by lazy {
        material("notifications_none", "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z")
    }
    public val pauseCircleOutline: ImageVector by lazy {
        material("pause_circle_outline", "M9 16h2V8H9v8zm3-14C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm1-4h2V8h-2v8z")
    }
    public val errorOutline: ImageVector by lazy {
        material("error_outline", "M11 15h2v2h-2zm0-8h2v6h-2zm.99-5C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z")
    }
    public val checkCircleOutline: ImageVector by lazy {
        material("check_circle_outline", "M16.59 7.58 10 14.17l-3.59-3.58L5 12l5 5 8-8zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z")
    }
    public val history: ImageVector by lazy {
        material("history", "M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z")
    }
    public val chevronRight: ImageVector by lazy {
        material("chevron_right", "M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z", mirrors = true)
    }

    /** The Material artwork grid: a 24 × 24 viewport drawn at the size the icon is given. */
    private const val GRID: Float = 24f // NV: Material Icons artwork viewport, not a layout size

    private fun material(name: String, path: String, mirrors: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = "Icons.$name",
            defaultWidth = GRID.dp,
            defaultHeight = GRID.dp,
            viewportWidth = GRID,
            viewportHeight = GRID,
            autoMirror = mirrors,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}

/**
 * One line of a team part (Dart `teamRow`): the state glyph centred in a tile-wide leading slot so the words start on
 * the list's text rail, then the title, an optional [status] in the state's tone, [detail] and [meta], an optional
 * progress bar ([completed] of [total]), and at the end a short [trailing] value and, when the row opens something
 * ([onPressed]), a chevron. Glyphs are decorative; the words carry the meaning.
 *
 * States: the eight [KitTeamState]s
 * Text: reading
 * Motion: none of its own (the press is the kit ripple)
 * Replaces: teamRow (lib/ui/kit/team/team_parts.dart)
 */
@Composable
public fun KitTeamRow(
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    state: KitTeamState = KitTeamState.Done,
    detail: String? = null,
    meta: String? = null,
    trailing: String? = null,
    onPressed: (() -> Unit)? = null,
    completed: Int? = null,
    total: Int? = null,
) {
    val tokens = LocalKitTokens.current
    val tone = KitTeamRules.tone(state)
    val content: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth().padding(vertical = Space.S3.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(tokens.iconTile.dp), contentAlignment = Alignment.Center) {
                KitIcon(KitTeamRules.glyph(state), size = KitIconSize.Small, tone = tone)
            }
            Spacer(Modifier.width(Space.S3.dp))
            Column(Modifier.weight(1f)) {
                KitText(title, role = TypeRole.RowTitle)
                if (status.isNotEmpty()) KitText(status, role = TypeRole.Secondary, tone = tone)
                detail?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
                meta?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
                KitTeamRules.progress(completed, total)?.let { TeamProgress(it, Modifier.padding(top = Space.S2.dp)) }
            }
            if (KitTeamRules.hasEnd(trailing, onPressed != null)) {
                Spacer(Modifier.width(Space.S2.dp))
                trailing?.let {
                    val lines = 2 // F: lib/ui/kit/team/team_parts.dart:108
                    KitText(it, Modifier.weight(1f, fill = false), role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = lines)
                }
                if (onPressed != null) KitIcon(KitTeamGlyphs.chevronRight, size = KitIconSize.Small, tone = TextTone.Tertiary)
            }
        }
    }
    if (onPressed == null) {
        content(modifier)
    } else {
        KitTappable(onPressed, modifier.fillMaxWidth(), role = KitTappableRole.Row, shape = KitShapeId.Square) {
            content(Modifier)
        }
    }
}

/**
 * Dart's `LinearProgressIndicator` as `teamRow` sets it: a determinate bar one `space1` high, `text2` over a
 * `surface3` track, filling from the start edge (team_parts.dart:88-96).
 */
@Composable
private fun TeamProgress(value: Float, modifier: Modifier) {
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier.fillMaxWidth().height(Space.S1.dp).progressSemantics(value).drawBehind {
            drawRect(colors.surface3)
            val w = size.width * value
            drawRect(colors.text2, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height))
        },
    )
}

/**
 * A team card (Dart `teamCard`): a headline [title] (led by the state glyph when the team failed or needs the person),
 * a [status] line, an optional [summary], [items] as team rows, the caller's [content], and the action block, which a
 * stale or loading card never shows. The card is a content surface in the person's style; when the team needs the
 * person it has an amber hairline edge, and under Solid an amber-tinted fill (the other styles keep their plain face,
 * contract F2). [flat] drops the card and leaves a `space4` gap below instead. [neutralStatus] keeps the status
 * secondary whatever the state.
 *
 * States: the eight [KitTeamState]s, flat
 * Text: reading
 * Motion: the style change fades the face (KitSurface), nothing of its own
 * Replaces: teamCard (lib/ui/kit/team/team_parts.dart)
 */
@Composable
public fun KitTeamCard(
    title: String,
    status: String,
    state: KitTeamState,
    modifier: Modifier = Modifier,
    summary: String? = null,
    items: List<KitTeamItem> = emptyList(),
    primary: KitAction? = null,
    secondary: KitAction? = null,
    actions: List<KitAction> = emptyList(),
    flat: Boolean = false,
    neutralStatus: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val attention = state == KitTeamState.NeedsYou
    val column: @Composable (Modifier) -> Unit = { m ->
        Column(m.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                if (KitTeamRules.cardMarked(state)) {
                    KitIcon(KitTeamRules.glyph(state), size = KitIconSize.Small, tone = KitTeamRules.cardGlyphTone(state))
                    Spacer(Modifier.width(Space.S2.dp))
                }
                KitText(title, Modifier.weight(1f), role = TypeRole.Headline, tone = KitTeamRules.cardTitleTone(state))
            }
            Spacer(Modifier.height(Space.S1.dp))
            KitText(status, role = KitTeamRules.cardStatusRole(state), tone = KitTeamRules.cardStatusTone(state, neutralStatus))
            summary?.let {
                Spacer(Modifier.height(Space.S2.dp))
                KitText(it, role = TypeRole.Body)
            }
            items.forEach { item ->
                KitTeamRow(
                    title = item.title,
                    status = "",
                    state = item.state,
                    detail = item.detail,
                    meta = item.meta,
                    trailing = item.trailing,
                    onPressed = item.onPressed,
                )
            }
            content()
            if (KitTeamRules.showsActions(state, primary != null, secondary != null, actions.size)) {
                Spacer(Modifier.height(Space.S3.dp))
                KitActionBlock(primary = primary, secondary = secondary, tertiary = actions)
            }
        }
    }
    // A flat part has no card around it, so it brings its own gap below: the next heading or card must not touch its
    // last action (2.0x text).
    if (flat) {
        column(modifier.padding(bottom = Space.S4.dp))
        return
    }
    val colors = LocalKitColors.current
    val edge = colors.attention.copy(alpha = KitTeamRules.ATTENTION_EDGE_ALPHA)
    // Solid's own face; the other styles paint their plain content card (the amber tint is Solid's alone).
    val own = if (attention) colors.attention.copy(alpha = KitTeamRules.ATTENTION_FILL_ALPHA).compositeOver(colors.surface1) else colors.surface1
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, SurfaceLevel.Surface1, ownFill = own))
    // Under Solid the edge is the shape's side; under the other styles it is drawn over the face
    // (team_parts.dart:241-255). Both are one hairline over the card's outline, so one border serves both.
    var box = modifier.then(look.modifier)
    if (attention) box = box.border(kitHairline(), edge, look.shape)
    Box(box) {
        column(Modifier.padding(Space.S4.dp))
    }
}
