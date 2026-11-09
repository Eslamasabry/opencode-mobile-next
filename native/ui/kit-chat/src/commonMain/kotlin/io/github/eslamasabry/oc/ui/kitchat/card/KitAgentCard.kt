package io.github.eslamasabry.oc.ui.kitchat.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.form.KitForm
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormField
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormOption
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.row.KitKeyValue
import io.github.eslamasabry.oc.ui.kit.row.KitKeyValueRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopy
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import org.jetbrains.compose.resources.stringResource

// Agent cards (plan 09 §13.7, Flutter `KitAgentCard` + agent_card_nodes/asks/form). Kit-local
// models only: the chat
// feature maps the cards engine onto these and passes every word already written (no copy in the
// kit). Every agent
// string is bidi-isolated by reading text (TextDirection.Content); links are inert until tapped and
// then go to the
// caller, who routes them through the app's external-link policy.

/** Reading cap of a card (plan 09 §13.7): one shape at 720 dp. */

/** One body node, drawn by [KitCardBody]. */
@Immutable
public sealed interface KitCardNode {
    public data class Text(val text: String) : KitCardNode

    public data class KeyValue(val rows: ImmutableList<Pair<String, String>>) : KitCardNode

    public data class Checklist(
        val items: ImmutableList<Pair<String, Boolean>>,
        val checks: Boolean,
    ) : KitCardNode

    public data class Table(
        val columns: ImmutableList<String>,
        val rows: ImmutableList<ImmutableList<String>>,
        val label: String,
    ) : KitCardNode

    /** A chart as labelled value bars; [summary] is its spoken description (B-CHAT-054). */
    public data class Chart(
        val labels: ImmutableList<String>,
        val values: ImmutableList<Double>,
        val unit: String?,
        val summary: String,
        val line: Boolean = false,
    ) : KitCardNode

    public data class Code(val language: String?, val text: String) : KitCardNode

    public data class DiffStat(
        val files: ImmutableList<Triple<String, Long, Long>>,
        val more: String?,
    ) : KitCardNode

    public data class Progress(val label: String, val value: Double, val valueLabel: String) :
        KitCardNode

    public data class Callout(val tone: StatusTone, val text: String) : KitCardNode

    /** [host] is shown so the person sees where the link goes before tapping. */
    public data class Link(val label: String, val host: String, val url: String) : KitCardNode
}

/** How the card is drawn (Flutter `KitAgentCardMode`). */
public enum class KitAgentCardMode {
    /** Asks the person: needs-you tone, the ask slot under the body. */
    Ask,

    /** No ask: neutral. */
    Report,

    /** Answered or held: collapsed to the answer's words; the body opens on request. */
    Receipt,

    /** Not answered (a later message passed it over): collapsed. */
    PassedOver,

    /** The payload could not be shown: one line, the stable reason under Details. */
    Unreadable,
}

/**
 * The agent card (plan 09 §13.7): eyebrow ("Claude Code asks"), title and body, then the [ask]
 * slot; a card with an ask wears the needs-you tone, a report is neutral. A receipt collapses to
 * [receiptLabel] with [onUndo] while the answer is held, and with [expandLabel] opens the body
 * read-only. The size change runs on the quick motion (150 ms; none under reduced motion). Lives in
 * the reply, never inside a folded work line.
 *
 * Text: eyebrow and buttons frame (≤ 1.3×); title and body reading.
 */
@Composable
public fun KitAgentCard(
    eyebrow: String,
    title: String,
    mode: KitAgentCardMode,
    modifier: Modifier = Modifier,
    body: ImmutableList<KitCardNode> = persistentListOf(),
    onLink: (String) -> Unit = {},
    ask: (@Composable () -> Unit)? = null,
    notice: String? = null,
    noticeTone: StatusTone = StatusTone.Neutral,
    receiptLabel: String? = null,
    undoLabel: String? = null,
    onUndo: (() -> Unit)? = null,
    passedOverLabel: String? = null,
    expandLabel: String? = null,
    details: String? = null,
    detailsLabel: String? = null,
    announcement: String? = null,
    icon: ImageVector? = null,
    unreadableLabel: String? = null,
    inList: Boolean = false,
) {
    val glyph = icon ?: KitIcons.agent
    var open by rememberSaveable(mode) { mutableStateOf(false) }
    // kitRequestPlacement (kit_request_shape.dart:13-34): centred at the reading width, the card's border on the page
    // gutter (the ring adds its own width around it), space1 above and below; in a list, as the list places it.
    val placed =
        if (inList) modifier.fillMaxWidth()
        else
            modifier
                .kitMaxWidth(CapRole.Reading)
                .fillMaxWidth()
                .padding(
                    horizontal = (LocalKitTokens.current.gutter - FlutterKitTokens.NEEDS_YOU_RING_WIDTH).dp,
                    vertical = Space.S1.dp,
                )
    // The card does not animate its own size: only the part the person opens does (KitReveal), one clock per change.
    Box(placed.testTag("kit-agent-card")) {
        when (mode) {
            KitAgentCardMode.Ask, KitAgentCardMode.Report -> {
                val attention = mode == KitAgentCardMode.Ask
                AgentFrame(attention) {
                    AgentHeading(glyph, attention, eyebrow, title, announcement)
                    for (piece in body) {
                        Spacer(Modifier.height(Space.S3.dp))
                        KitCardBody(persistentListOf(piece), onLink)
                    }
                    if (ask != null) {
                        Spacer(Modifier.height(Space.S4.dp))
                        ask()
                    }
                    if (notice != null) {
                        Spacer(Modifier.height(Space.S3.dp))
                        KitNotice(notice, Modifier.testTag("kit-agent-card-notice"), tone = noticeTone)
                    }
                }
            }
            KitAgentCardMode.Receipt, KitAgentCardMode.PassedOver ->
                AgentAnsweredRow(
                    icon = glyph,
                    title = title,
                    open = open,
                    onToggle = if (expandLabel != null && body.isNotEmpty()) ({ open = !open }) else null,
                    toggleLabel = expandLabel,
                    body = body,
                    onLink = onLink,
                ) {
                    if (mode == KitAgentCardMode.Receipt) AgentReceiptLine(receiptLabel, undoLabel, onUndo)
                    else KitText(
                        passedOverLabel.orEmpty(),
                        Modifier.testTag("kit-agent-card-receipt").semantics { liveRegion = LiveRegionMode.Polite },
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                    )
                }
            KitAgentCardMode.Unreadable -> AgentUnreadable(unreadableLabel ?: title, details, detailsLabel)
        }
    }
}

/**
 * kitRequestFrame (kit_request_shape.dart:54-240), Solid's look: the attention-tinted card with its attention line
 * and the 4 dp wash ring outside it while it asks, else a plain surface1 card with a hairline in the ring's place.
 */
@Composable
private fun AgentFrame(attention: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalKitColors.current
    val radius = FlutterKitTokens.CARD_RADIUS
    val ring = FlutterKitTokens.NEEDS_YOU_RING_WIDTH
    val shape = RoundedCornerShape(radius.dp)
    val face = if (attention) colors.attentionSurface.compositeOver(colors.surface1) else colors.surface1
    val line = if (attention) colors.attentionLine else colors.hairline
    val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, ownFill = face, color = if (attention) face else null))
    val card =
        Modifier.fillMaxWidth()
            .then(look.modifier)
            .border(kitHairline(), line, shape)
            .padding(Space.S4.dp)
    val around =
        if (attention)
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape((radius + ring).dp))
                .background(colors.attention.copy(alpha = FlutterKitTokens.NEEDS_YOU_RING_ALPHA))
                .padding(ring.dp)
        else Modifier.fillMaxWidth().padding(horizontal = ring.dp)
    Box(around) { Column(card, content = content) }
}

/** kitRequestHeading (kit_request_shape.dart:430-465) with kitRequestTile (:382-425). */
@Composable
private fun AgentHeading(icon: ImageVector, attention: Boolean, caption: String, title: String, announcement: String?) {
    val colors = LocalKitColors.current
    val said =
        if (announcement != null)
            Modifier.clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            }
        else Modifier
    Row(said, verticalAlignment = Alignment.Top) {
        val tint =
            if (attention)
                colors.attention.copy(alpha = FlutterKitTokens.MARK_TINT_ALPHA)
                    .compositeOver(colors.attentionSurface.compositeOver(colors.surface1))
            else colors.surface3
        Box(
            Modifier.size(FlutterKitTokens.REQUEST_TILE_SIZE.dp)
                .clip(RoundedCornerShape(FlutterKitTokens.REQUEST_TILE_RADIUS.dp))
                .background(tint)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            KitIcon(icon, size = KitIconSize.Small, tone = if (attention) TextTone.Attention else TextTone.Primary)
        }
        Spacer(Modifier.width(Space.S3.dp))
        Column(Modifier.weight(1f)) {
            KitText(caption, role = TypeRole.Caption, tone = if (attention) TextTone.Attention else TextTone.Secondary)
            Spacer(Modifier.height(Space.S1.dp))
            KitText(title, Modifier.semantics { heading() }, role = TypeRole.Headline, tone = TextTone.Primary)
        }
    }
}

/** kitAnsweredRow (kit_request_shape.dart:473-572): no card; the glyph, the title, the outcome under it. */
@Composable
private fun AgentAnsweredRow(
    icon: ImageVector,
    title: String,
    open: Boolean,
    onToggle: (() -> Unit)?,
    toggleLabel: String?,
    body: ImmutableList<KitCardNode>,
    onLink: (String) -> Unit,
    outcome: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val twoLines = LocalDensity.current.fontScale * 10 >= 13 // F: lib/ui/kit/kit_request_shape.dart:487
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = tokens.rowHeightOne.dp).padding(horizontal = Space.S4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitIcon(icon, size = KitIconSize.Small, tone = TextTone.Secondary)
            Spacer(Modifier.width(Space.S3.dp))
            val words: @Composable (Modifier) -> Unit = { m ->
                KitText(title, m, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = if (twoLines) 2 else 1) // F: lib/ui/kit/kit_request_shape.dart:494
            }
            if (onToggle != null) {
                // The title line is the toggle (48 dp tall, as Flutter's KitTappable); the outcome sits right under it
                // inside the same row target, so no line lies flush under a separate control (LAYOUT-RULES.md tight).
                KitTappable(
                    onClick = onToggle,
                    role = KitTappableRole.Row,
                    modifier = Modifier.weight(1f).kitEdge(KitEdge.Bleed).semantics {
                        toggleLabel?.let { contentDescription = it }
                        selected = open
                    },
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            words(Modifier.weight(1f))
                            KitIcon(if (open) KitIcons.chevronUp else KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary)
                        }
                        outcome()
                    }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    words(Modifier)
                    outcome()
                }
            }
        }
        if (onToggle != null) {
            KitReveal(visible = open) {
                KitCardBody(
                    body,
                    onLink,
                    Modifier.padding(
                        start = (Space.S4 + KitIconSize.Small.dp + Space.S3).dp,
                        top = Space.S2.dp,
                        end = Space.S4.dp,
                    ).clearAndSetSemantics {},
                )
            }
        }
    }
}

/**
 * The receipt of an answered card (kit_receipt.dart, confirmed with a label): the check, the act in the person's
 * words, then Undo while the host offers it. Native `KitReceipt` has no label or Undo yet (folded gap, evidence 21).
 */
@Composable
private fun AgentReceiptLine(label: String?, undoLabel: String?, onUndo: (() -> Unit)?) {
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.testTag("kit-agent-card-receipt").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitIcon(KitIcons.check, size = KitIconSize.Small, tone = TextTone.Success)
            Spacer(Modifier.width(Space.S2.dp))
            if (label != null) KitText(label, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        if (onUndo != null && undoLabel != null) {
            KitButton(undoLabel, onUndo, role = KitButtonRole.Tertiary, modifier = Modifier.testTag("kit-agent-card-undo"))
        }
    }
}

/** kit_agent_card.dart:245-286: one quiet line with the info glyph, the technical text under a Details fold. */
@Composable
private fun AgentUnreadable(line: String, details: String?, detailsLabel: String?) {
    val tokens = LocalKitTokens.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = tokens.rowHeightOne.dp), verticalAlignment = Alignment.CenterVertically) {
            KitIcon(KitIcons.info, size = KitIconSize.Small, tone = TextTone.Secondary)
            Spacer(Modifier.width(Space.S3.dp))
            KitText(
                line,
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
        if (!details.isNullOrEmpty()) {
            KitDetailsFold(
                label = detailsLabel ?: stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopy),
                text = LocalKitRedactor.current.details(details),
                modifier = Modifier.testTag("kit-agent-card-details"),
            )
        }
    }
}

/**
 * The body: one exhaustive `when` over the node kinds (a new kind fails compilation until it has a
 * part).
 */
@Composable
public fun KitCardBody(
    nodes: ImmutableList<KitCardNode>,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) return
    val colors = LocalKitColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        nodes.forEach { node ->
            when (node) {
                is KitCardNode.Text -> KitText(node.text, role = TypeRole.Body)
                is KitCardNode.KeyValue -> KitKeyValue(node.rows.map { (k, v) -> KitKeyValueRow(k, v) })
                is KitCardNode.Checklist ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        node.items.forEach { (text, done) ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (node.checks)
                                    KitIcon(
                                        if (done) KitIcons.checkboxChecked else KitIcons.checkboxEmpty,
                                        size = KitIconSize.Small,
                                        tone = if (done) TextTone.Success else TextTone.Secondary,
                                    )
                                else
                                    Box(Modifier.size(KitIconSize.Small.dp.dp), contentAlignment = Alignment.Center) {
                                        Box(
                                            Modifier.size(6.dp)
                                                .clip(KitShapeId.Circle.shape())
                                                .background(LocalKitColors.current.text2)
                                        )
                                    }
                                KitText(
                                    text,
                                    role = TypeRole.Body,
                                    tone =
                                        if (done && node.checks) TextTone.Secondary
                                        else TextTone.Primary,
                                )
                            }
                        }
                    }
                is KitCardNode.Table ->
                    Column(
                        Modifier.horizontalScroll(rememberScrollState()).semantics {
                            contentDescription = node.label
                        },
                        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                    ) {
                        TableRow(node.columns, header = true)
                        node.rows.forEach { TableRow(it, header = false) }
                    }
                is KitCardNode.Chart -> {
                    val max = node.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                    Column(
                        Modifier.semantics { contentDescription = node.summary },
                        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                    ) {
                        if (node.line && node.values.isNotEmpty()) {
                            val values = node.values.map { if (it.isFinite()) it else 0.0 }
                            val low = minOf(0.0, values.minOrNull() ?: 0.0)
                            val high = maxOf(low + 1.0, values.maxOrNull() ?: 0.0)
                            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                                val points = values.mapIndexed { i, v ->
                                    Offset(
                                        if (values.size == 1) size.width / 2
                                        else size.width * i / (values.size - 1),
                                        size.height * (1 - (v - low) / (high - low)).toFloat(),
                                    )
                                }
                                points.zipWithNext().forEach { (a, b) ->
                                    drawLine(colors.accent, a, b, strokeWidth = 2.dp.toPx())
                                }
                                points.forEach { drawCircle(colors.accent, 3.dp.toPx(), it) }
                            }
                        }
                        node.labels.forEachIndexed { i, label ->
                            val v = node.values.getOrElse(i) { 0.0 }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                            ) {
                                KitText(
                                    label,
                                    Modifier.weight(0.35f),
                                    role = TypeRole.Secondary,
                                    tone = TextTone.Secondary,
                                    maxLines = 1,
                                )
                                if (node.line)
                                    KitText(
                                        "$v${node.unit.orEmpty()}",
                                        Modifier.weight(0.65f),
                                        role = TypeRole.Secondary,
                                    )
                                else
                                    Box(Modifier.weight(0.65f)) {
                                        Box(
                                            Modifier.fillMaxWidth(
                                                    (v / max)
                                                        .coerceIn(0.0, 1.0)
                                                        .toFloat()
                                                        .coerceAtLeast(0.02f)
                                                )
                                                .height(Space.S3.dp)
                                                .clip(KitShapeId.Pill.shape())
                                                .background(colors.accent)
                                        )
                                    }
                            }
                        }
                    }
                }
                is KitCardNode.Code ->
                    KitCodeBlock(
                        node.text,
                        KitCodeKind.Code,
                        language = CodeLanguage.of(node.language),
                    )
                is KitCardNode.DiffStat ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        node.files.forEach { (path, added, removed) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                                KitText(
                                    path,
                                    Modifier.weight(1f),
                                    role = TypeRole.Secondary,
                                    maxLines = 1,
                                )
                                KitText(
                                    "+$added",
                                    role = TypeRole.Secondary,
                                    tone = TextTone.Success,
                                    kind = KitTextKind.Frame,
                                )
                                KitText(
                                    "−$removed",
                                    role = TypeRole.Secondary,
                                    tone = TextTone.Secondary,
                                    kind = KitTextKind.Frame,
                                )
                            }
                        }
                        node.more?.let {
                            KitText(
                                it,
                                role = TypeRole.Secondary,
                                tone = TextTone.Secondary,
                                kind = KitTextKind.Frame,
                            )
                        }
                    }
                is KitCardNode.Progress ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        Row {
                            KitText(node.label, Modifier.weight(1f), role = TypeRole.Secondary)
                            KitText(
                                node.valueLabel,
                                role = TypeRole.Secondary,
                                tone = TextTone.Secondary,
                                kind = KitTextKind.Frame,
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(Space.S1.dp).clip(KitShapeId.Pill.shape()).background(colors.surface2)) {
                            Box(Modifier.fillMaxWidth(node.value.toFloat().coerceIn(0f, 1f)).height(Space.S1.dp).background(colors.accent))
                        }
                    }
                is KitCardNode.Callout -> KitNotice(node.text, tone = node.tone, liveRegion = false)
                is KitCardNode.Link ->
                    KitButton(
                        "${node.label} · ${node.host}",
                        { onLink(node.url) },
                        role = KitButtonRole.Tertiary,
                        modifier = Modifier.testTag("kit-agent-card-link"),
                    )
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        cells.forEach {
            KitText(
                it,
                Modifier.widthIn(min = 64.dp, max = 200.dp),
                role = TypeRole.Secondary,
                tone = if (header) TextTone.Secondary else TextTone.Primary,
            )
        }
    }
}

/**
 * A choice ask (Flutter `agent_card_asks.dart`): every option a visible row. Single choice: one tap
 * answers (the Undo window covers a slip). Several: check rows and [sendLabel]. Controls wait while
 * [blockedReason] says why.
 */
@Composable
public fun KitCardChoice(
    options: ImmutableList<KitFormOption>,
    multi: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSend: (List<String>) -> Unit,
    sendLabel: String,
    chooseHint: String,
    modifier: Modifier = Modifier,
    blockedReason: String? = null,
) {
    val enabled = KitEnabled.of(blockedReason == null, blockedReason)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        options.forEach { o ->
            KitChoiceRow(
                title = o.label,
                selected = o.value in selected,
                supporting = o.description,
                mark = if (multi) KitChoiceMark.Check else KitChoiceMark.Radio,
                enabled = enabled,
                haptic = if (multi) null else KitHaptic.Send,
                modifier = Modifier.testTag("kit-agent-card-option-${o.value}"),
                onClick = { if (multi) onToggle(o.value) else onSend(listOf(o.value)) },
            )
        }
        if (multi) {
            val none = selected.isEmpty()
            KitButton(
                sendLabel,
                { onSend(options.map { it.value }.filter { it in selected }) },
                enabled = if (blockedReason != null) enabled else KitEnabled.of(!none, chooseHint),
                haptic = KitHaptic.Send,
                modifier = Modifier.testTag("kit-agent-card-send"),
            )
        }
    }
}

/**
 * A confirm ask: the cancel answer and the confirm answer side by side; danger tones the confirm
 * answer.
 */
@Composable
public fun KitCardConfirm(
    confirmLabel: String,
    cancelLabel: String,
    danger: Boolean,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    blockedReason: String? = null,
) {
    val enabled = KitEnabled.of(blockedReason == null, blockedReason)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitButton(
            cancelLabel,
            { onAnswer(false) },
            Modifier.weight(1f),
            role = KitButtonRole.Secondary,
            enabled = enabled,
            haptic = KitHaptic.Send,
        )
        KitButton(
            confirmLabel,
            { onAnswer(true) },
            Modifier.weight(1f),
            destructive = danger,
            enabled = enabled,
            haptic = KitHaptic.Send,
        )
    }
}

/** A form ask: the one form renderer, a note that the agent sees what is typed, and one Send. */
@Composable
public fun KitCardForm(
    fields: ImmutableList<KitFormField>,
    values: ImmutableMap<String, KitFormValue>,
    onChange: (String, KitFormValue) -> Unit,
    sendLabel: String,
    onSend: () -> Unit,
    note: String,
    modifier: Modifier = Modifier,
    sendBlockedReason: String? = null,
    blockedReason: String? = null,
) {
    val enabled = KitEnabled.of(blockedReason == null, blockedReason)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        KitForm(fields, values, onChange, enabled = enabled)
        KitText(
            note,
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
            kind = KitTextKind.Frame,
        )
        val reason = blockedReason ?: sendBlockedReason
        KitButton(
            sendLabel,
            onSend,
            enabled = KitEnabled.of(reason == null, reason),
            haptic = KitHaptic.Send,
            modifier = Modifier.testTag("kit-agent-card-send"),
        )
    }
}

/** A selected, local photo, held by its card until the person sends or removes it. */
public data class KitCardPhotoItem(
    val id: String,
    val name: String,
    val removeLabel: String,
    val bytes: ByteArray? = null,
)

/**
 * A photo ask: the purpose, the visible ways to add a photo, and Send; or one plain line when
 * photos cannot go.
 */
@Composable
public fun KitCardPhoto(
    purpose: String,
    countLabel: String,
    modifier: Modifier = Modifier,
    unavailable: String? = null,
    chooseLabel: String? = null,
    onChoose: (() -> Unit)? = null,
    sendLabel: String? = null,
    onSend: (() -> Unit)? = null,
    sendBlockedReason: String? = null,
    blockedReason: String? = null,
    items: ImmutableList<KitCardPhotoItem> = persistentListOf(),
    onRemove: ((String) -> Unit)? = null,
    takeLabel: String? = null,
    onTake: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitText(purpose, role = TypeRole.Body)
        if (unavailable != null) {
            KitText(
                unavailable,
                Modifier.testTag("kit-agent-card-photo-unavailable"),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            return@Column
        }
        KitText(
            countLabel,
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
            kind = KitTextKind.Frame,
        )
        val enabled = KitEnabled.of(blockedReason == null, blockedReason)
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                CardPhotoPreview(item)
                KitText(item.name, Modifier.weight(1f), role = TypeRole.Secondary)
                if (onRemove != null)
                    KitButton(
                        item.removeLabel,
                        { onRemove(item.id) },
                        role = KitButtonRole.Tertiary,
                        enabled = enabled,
                    )
            }
        }
        if (takeLabel != null && onTake != null)
            KitButton(takeLabel, onTake, role = KitButtonRole.Secondary, enabled = enabled)
        if (chooseLabel != null && onChoose != null)
            KitButton(chooseLabel, onChoose, role = KitButtonRole.Secondary, enabled = enabled)
        if (sendLabel != null && onSend != null) {
            val reason = blockedReason ?: sendBlockedReason
            KitButton(
                sendLabel,
                onSend,
                enabled = KitEnabled.of(reason == null, reason),
                haptic = KitHaptic.Send,
            )
        }
    }
}

/**
 * The list slot (plan 09 §13.7, 07 C40): a waiting card under its Conversations row, one line and a
 * way in. The row keeps the list's one primary; this is secondary.
 */
@Composable
public fun KitCardListSlot(
    eyebrow: String,
    title: String,
    openLabel: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Card.shape()
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.attentionSurface)
            .border(1.dp, colors.attentionLine, shape)
            .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)
            .testTag("kit-card-list-slot"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        Column(Modifier.weight(1f)) {
            KitText(
                eyebrow,
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
                maxLines = 1,
            )
            KitText(title, role = TypeRole.Body, maxLines = 2)
        }
        KitButton(openLabel, onOpen, role = KitButtonRole.Secondary)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun CardPhotoPreview(item: KitCardPhotoItem) {
    val bitmap =
        remember(item.bytes) {
            try {
                item.bytes?.decodeToImageBitmap()
            } catch (_: RuntimeException) {
                null
            }
        }
    if (bitmap != null)
        Image(BitmapPainter(bitmap), item.name, Modifier.height(64.dp).widthIn(max = 96.dp))
}
