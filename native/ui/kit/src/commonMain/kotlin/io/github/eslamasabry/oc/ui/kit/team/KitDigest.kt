package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

// ---------------------------------------------------------------------------------------------------------------------
// Team data and the shared team card and row (lib/ui/kit/team/kit_team_data.dart, team_parts.dart).
// Their own home is order 28 (KitTeamData.kt, TeamParts.kt), which is not merged on this base; the parts of order 20
// need them to draw at all, so they live here until order 28 lands. On that merge, delete this block in favour of
// order 28's files (same names, same package): see docs/design/native-ui-rebuild/evidence/20.md.
// ---------------------------------------------------------------------------------------------------------------------

/** Presentation state; the caller supplies the localized explanation and age (Dart `KitTeamState`). */
public enum class KitTeamState { Empty, Loading, Running, NeedsYou, Stalled, Failed, Done, Stale }

/** One line of a team card (Dart `KitTeamItem`). [trailing] is a short value at the row's end. */
@Immutable
public data class KitTeamItem(
    val title: String,
    val detail: String? = null,
    val meta: String? = null,
    val trailing: String? = null,
    val state: KitTeamState = KitTeamState.Done,
    val onPressed: (() -> Unit)? = null,
)

/** How much a finding matters (Dart `KitFindingSeverity`). */
public enum class KitFindingSeverity { Critical, Major, Minor }

/** One finding a person can pick (Dart `KitTeamFinding`); [onChanged] null leaves it read-only. */
@Immutable
public data class KitTeamFinding(
    val id: String,
    val title: String,
    val severityLabel: String,
    val severity: KitFindingSeverity = KitFindingSeverity.Minor,
    val detail: String? = null,
    val location: String? = null,
    val selected: Boolean = false,
    val onChanged: ((Boolean) -> Unit)? = null,
)

/** One task line of a plan: number, title, a one-line detail, and who does it (Dart `KitPlanTask`). */
@Immutable
public data class KitPlanTask(
    val number: Int,
    val title: String,
    val detail: String? = null,
    val who: String? = null,
    /** When given, [who] is drawn as a neutral button that calls this; null leaves it plain text. */
    val onChangeWho: (() -> Unit)? = null,
)

/** A named group of plan tasks; [flagLabel] carries the worded review point (Dart `KitPlanPhase`). */
@Immutable
public data class KitPlanPhase(val title: String, val tasks: List<KitPlanTask>, val flagLabel: String? = null)

/** The rules of team_parts.dart that are not drawing, kept apart so they are unit-tested. */
public object KitTeamRules {
    /** The status line's tone (Dart `teamTone`). */
    public fun tone(state: KitTeamState): TextTone =
        when (state) {
            KitTeamState.NeedsYou -> TextTone.Attention
            KitTeamState.Failed -> TextTone.Primary
            else -> TextTone.Secondary
        }

    /** Actions show only while the data is current (Dart `teamCard` `mutations`). */
    public fun mutations(state: KitTeamState): Boolean = state != KitTeamState.Stale && state != KitTeamState.Loading

    /** A counted bar shows only for an honest count: total above zero and 0 ≤ completed ≤ total. */
    public fun progress(completed: Int?, total: Int?): Float? =
        if (completed != null && total != null && total > 0 && completed >= 0 && completed <= total) completed.toFloat() / total
        else null

    /** The card heading carries the state glyph only when it failed or needs the person. */
    public fun headingGlyph(state: KitTeamState): Boolean = state == KitTeamState.Failed || state == KitTeamState.NeedsYou
}

/** The Material glyph Flutter's team parts draw per state (Dart `teamIcon`), as 24-unit outline paths. */
internal fun teamIcon(state: KitTeamState): ImageVector =
    when (state) {
        KitTeamState.Empty -> TeamGlyphs.inboxOutlined
        KitTeamState.Loading -> TeamGlyphs.hourglassEmpty
        KitTeamState.Running -> TeamGlyphs.pendingOutlined
        KitTeamState.NeedsYou -> TeamGlyphs.notificationsNone
        KitTeamState.Stalled -> TeamGlyphs.pauseCircleOutline
        KitTeamState.Failed -> TeamGlyphs.errorOutline
        KitTeamState.Done -> TeamGlyphs.checkCircleOutline
        KitTeamState.Stale -> TeamGlyphs.history
    }

/** Material Icons outline glyphs (Apache 2.0) that team_parts.dart uses; the kit's own set has no twin for them. */
internal object TeamGlyphs {
    private const val VIEW = 24f // NV: Material icon viewport (24-unit design grid), not a kit size

    private fun glyph(name: String, d: String): ImageVector =
        ImageVector.Builder(name, VIEW.dp, VIEW.dp, VIEW, VIEW)
            .addPath(addPathNodes(d), fill = SolidColor(Color.Black))
            .build()

    val inboxOutlined: ImageVector by lazy {
        glyph("team.inbox", "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3zM19,19H5v-3h3.56c0.69,1.19 1.97,2 3.45,2s2.75,-0.81 3.45,-2H19V19zM19,14h-4.99c0,1.1 -0.9,2 -2,2s-2,-0.9 -2,-2H5V5h14V14z")
    }
    val hourglassEmpty: ImageVector by lazy {
        glyph("team.hourglass", "M6,2v6h0.01L6,8.01 10,12l-4,4 0.01,0.01H6V22h12v-5.99h-0.01L18,16l-4,-4 4,-3.99 -0.01,-0.01H18V2H6zM16,16.5V20H8v-3.5l4,-4 4,4zM12,11.5l-4,-4V4h8v3.5l-4,4z")
    }
    val pendingOutlined: ImageVector by lazy {
        glyph("team.pending", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10s10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8c0,-4.42 3.58,-8 8,-8s8,3.58 8,8C20,16.42 16.42,20 12,20zM7,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5s1.5,-0.67 1.5,-1.5S7.83,10.5 7,10.5zM12,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5s1.5,-0.67 1.5,-1.5S12.83,10.5 12,10.5zM17,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5s1.5,-0.67 1.5,-1.5S17.83,10.5 17,10.5z")
    }
    val notificationsNone: ImageVector by lazy {
        glyph("team.notifications", "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2zM16,17H8v-6c0,-2.48 1.51,-4.5 4,-4.5s4,2.02 4,4.5v6z")
    }
    val pauseCircleOutline: ImageVector by lazy {
        glyph("team.pause", "M9,16h2V8H9v8zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM13,16h2V8h-2v8z")
    }
    val errorOutline: ImageVector by lazy {
        glyph("team.error", "M11,15h2v2h-2zM11,7h2v6h-2zM11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z")
    }
    val checkCircleOutline: ImageVector by lazy {
        glyph("team.check", "M16.59,7.58L10,14.17l-3.59,-3.58L5,12l5,5 8,-8zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z")
    }
    val history: ImageVector by lazy {
        glyph("team.history", "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z")
    }
    val chevronRight: ImageVector by lazy {
        glyph("team.chevron", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    }
}

/** A decorative glyph at the small icon size, hidden from semantics (Flutter `ExcludeSemantics(Icon(…))`). */
@Composable
internal fun TeamGlyph(icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Image(
        icon,
        contentDescription = null,
        modifier = modifier.size(LocalKitTokens.current.smallIcon.dp).clearAndSetSemantics {},
        colorFilter = ColorFilter.tint(color),
    )
}

/** A flat team row with an honest counted bar (Dart `teamRow`, lib/ui/kit/team/team_parts.dart:24-127). */
@Composable
internal fun TeamRow(
    title: String,
    status: String,
    state: KitTeamState = KitTeamState.Done,
    detail: String? = null,
    meta: String? = null,
    trailing: String? = null,
    onPressed: (() -> Unit)? = null,
    completed: Int? = null,
    total: Int? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val color =
        when (state) {
            KitTeamState.NeedsYou -> colors.attention
            KitTeamState.Failed -> colors.text1
            else -> colors.text2
        }
    val progress = KitTeamRules.progress(completed, total)
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.S3.dp), verticalAlignment = Alignment.Top) {
            // The leading column: the glyph centred in a tile-wide slot, so the words start on the list's text rail.
            Box(Modifier.width(tokens.iconTile.dp), contentAlignment = Alignment.Center) { TeamGlyph(teamIcon(state), color) }
            Spacer(Modifier.width(Space.S3.dp))
            Column(Modifier.weight(1f)) {
                KitText(title, role = TypeRole.RowTitle)
                if (status.isNotEmpty()) KitText(status, role = TypeRole.Secondary, tone = KitTeamRules.tone(state))
                if (detail != null) KitText(detail, role = TypeRole.Secondary, tone = TextTone.Secondary)
                if (meta != null) KitText(meta, role = TypeRole.Secondary, tone = TextTone.Secondary)
                if (progress != null) {
                    val track = colors.surface3
                    val bar = colors.text2
                    Canvas(Modifier.padding(top = Space.S2.dp).fillMaxWidth().height(Space.S1.dp)) {
                        drawRect(track)
                        drawRect(bar, size = size.copy(width = size.width * progress))
                    }
                }
            }
            if (trailing != null || onPressed != null) {
                Spacer(Modifier.width(Space.S2.dp))
                if (trailing != null) {
                    KitText(
                        trailing,
                        Modifier.weight(1f, fill = false),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        maxLines = 2, // F: lib/ui/kit/team/team_parts.dart:108
                    )
                }
                if (onPressed != null) TeamGlyph(TeamGlyphs.chevronRight, colors.text3)
            }
        }
    }
    if (onPressed == null) content() else KitTappable(onPressed, Modifier.fillMaxWidth(), role = KitTappableRole.Row) { content() }
}

/** A team card, or (when [flat]) its column with its own gap below (Dart `teamCard`, team_parts.dart:129-257). */
@Composable
internal fun TeamCard(
    title: String,
    status: String,
    state: KitTeamState,
    summary: String? = null,
    items: List<KitTeamItem> = emptyList(),
    primary: KitAction? = null,
    secondary: KitAction? = null,
    actions: List<KitAction> = emptyList(),
    flat: Boolean = false,
    neutralStatus: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val colors = LocalKitColors.current
    val attention = state == KitTeamState.NeedsYou
    val column: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (KitTeamRules.headingGlyph(state)) {
                    TeamGlyph(teamIcon(state), if (attention) colors.attention else colors.text1)
                    Spacer(Modifier.width(Space.S2.dp))
                }
                KitText(
                    title,
                    Modifier.weight(1f),
                    role = TypeRole.Headline,
                    tone = if (attention) TextTone.Attention else TextTone.Primary,
                )
            }
            Spacer(Modifier.height(Space.S1.dp))
            KitText(
                status,
                role = if (state == KitTeamState.Failed) TypeRole.RowTitle else TypeRole.Secondary,
                tone = if (neutralStatus) TextTone.Secondary else KitTeamRules.tone(state),
            )
            if (summary != null) {
                Spacer(Modifier.height(Space.S2.dp))
                KitText(summary, role = TypeRole.Body)
            }
            items.forEach {
                TeamRow(it.title, "", it.state, it.detail, it.meta, it.trailing, it.onPressed)
            }
            content()
            if (KitTeamRules.mutations(state) && (primary != null || secondary != null || actions.isNotEmpty())) {
                Spacer(Modifier.height(Space.S3.dp))
                KitActionBlock(primary = primary, secondary = secondary, tertiary = actions)
            }
        }
    }
    // A flat part has no card around it, so it brings its own gap below: the next heading or card must not touch its
    // last action (2.0x text).
    if (flat) {
        column(Modifier.padding(bottom = Space.S4.dp))
        return
    }
    val own = if (attention) colors.attention.copy(alpha = .09f).compositeOver(colors.surface1) else colors.surface1 // F: lib/ui/kit/team/team_parts.dart:227
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Content, shape = KitShapeId.Card, ownFill = own))
    val edge =
        if (attention) Modifier.border(kitHairline(), colors.attention.copy(alpha = .30f), look.shape) // F: lib/ui/kit/team/team_parts.dart:217
        else Modifier
    Box(Modifier.fillMaxWidth().then(look.modifier).then(edge)) { column(Modifier.padding(Space.S4.dp)) }
}

/**
 * Digest: a localized, controlled team surface with no engine dependencies (Dart `KitDigest`).
 *
 * States: loading, empty, error, working, disabled (stale), answered, needs you, stalled
 * Text: reading
 * Cap: Reading (720)
 * Motion: none of its own
 * Replaces: KitDigest (lib/ui/kit/team/kit_digest.dart)
 */
@Composable
public fun KitDigest(
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    state: KitTeamState = KitTeamState.Done,
    summary: String? = null,
    items: List<KitTeamItem> = emptyList(),
    primary: KitAction? = null,
    actions: List<KitAction> = emptyList(),
) {
    Box(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        TeamCard(title, status, state, summary, items, primary, actions = actions, flat = false)
    }
}
