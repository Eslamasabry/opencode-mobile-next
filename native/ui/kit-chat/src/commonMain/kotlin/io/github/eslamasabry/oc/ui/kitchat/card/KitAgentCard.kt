package io.github.eslamasabry.oc.ui.kitchat.card

import androidx.compose.animation.animateContentSize
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

// Agent cards (plan 09 §13.7, Flutter `KitAgentCard` + agent_card_nodes/asks/form). Kit-local models only: the chat
// feature maps the cards engine onto these and passes every word already written (no copy in the kit). Every agent
// string is bidi-isolated by reading text (TextDirection.Content); links are inert until tapped and then go to the
// caller, who routes them through the app's external-link policy.

/** Reading cap of a card (plan 09 §13.7): one shape at 720 dp. */
private val CARD_MAX_WIDTH: Dp = 720.dp

/** One body node, drawn by [KitCardBody]. */
@Immutable
public sealed interface KitCardNode {
    public data class Text(val text: String) : KitCardNode

    public data class KeyValue(val rows: ImmutableList<Pair<String, String>>) : KitCardNode

    public data class Checklist(val items: ImmutableList<Pair<String, Boolean>>, val checks: Boolean) : KitCardNode

    public data class Table(val columns: ImmutableList<String>, val rows: ImmutableList<ImmutableList<String>>, val label: String) : KitCardNode

    /** A chart as labelled value bars; [summary] is its spoken description (B-CHAT-054). */
    public data class Chart(val labels: ImmutableList<String>, val values: ImmutableList<Double>, val unit: String?, val summary: String) : KitCardNode

    public data class Code(val language: String?, val text: String) : KitCardNode

    public data class DiffStat(val files: ImmutableList<Triple<String, Long, Long>>, val more: String?) : KitCardNode

    public data class Progress(val label: String, val value: Double, val valueLabel: String) : KitCardNode

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
 * The agent card (plan 09 §13.7): eyebrow ("Claude Code asks"), title and body, then the [ask] slot; a card with an
 * ask wears the needs-you tone, a report is neutral. A receipt collapses to [receiptLabel] with [onUndo] while the
 * answer is held, and with [expandLabel] opens the body read-only. The size change runs on the quick motion (150 ms;
 * none under reduced motion). Lives in the reply, never inside a folded work line.
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
) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Card.shape()
    val asks = mode == KitAgentCardMode.Ask
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .widthIn(max = CARD_MAX_WIDTH)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .then(if (asks) Modifier.background(colors.attentionSurface).border(1.dp, colors.attentionLine, shape) else Modifier.border(1.dp, colors.hairline, shape))
            .animateContentSize(KitMotion.specs.quick())
            .padding(Space.S4.dp)
            .testTag("kit-agent-card"),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitText(eyebrow, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
        KitText(
            title,
            Modifier.semantics {
                heading()
                if (announcement != null) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announcement
                }
            },
            role = TypeRole.Headline,
            tone = TextTone.Primary,
            kind = KitTextKind.Reading,
        )
        when (mode) {
            KitAgentCardMode.Unreadable -> {
                if (details != null && detailsLabel != null) {
                    var shown by remember { mutableStateOf(false) }
                    KitButton(detailsLabel, { shown = !shown }, role = KitButtonRole.Tertiary)
                    if (shown) KitText(details, Modifier.testTag("kit-agent-card-details"), role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
            }
            KitAgentCardMode.Receipt, KitAgentCardMode.PassedOver -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    val line = if (mode == KitAgentCardMode.Receipt) receiptLabel else passedOverLabel
                    if (line != null) {
                        KitText(
                            line,
                            Modifier.weight(1f).testTag("kit-agent-card-receipt").semantics { liveRegion = LiveRegionMode.Polite },
                            role = TypeRole.Body,
                            tone = if (mode == KitAgentCardMode.Receipt) TextTone.Primary else TextTone.Secondary,
                        )
                    }
                    if (onUndo != null && undoLabel != null) {
                        KitButton(undoLabel, onUndo, role = KitButtonRole.Tertiary, modifier = Modifier.testTag("kit-agent-card-undo"))
                    } else if (expandLabel != null && body.isNotEmpty()) {
                        KitButton(expandLabel, { open = !open }, role = KitButtonRole.Tertiary)
                    }
                }
                if (open && onUndo == null) KitCardBody(body, onLink)
            }
            KitAgentCardMode.Ask, KitAgentCardMode.Report -> {
                KitCardBody(body, onLink)
                ask?.invoke()
            }
        }
        if (notice != null) KitNotice(notice, Modifier.testTag("kit-agent-card-notice"), tone = noticeTone)
    }
}

/** The body: one exhaustive `when` over the node kinds (a new kind fails compilation until it has a part). */
@Composable
public fun KitCardBody(nodes: ImmutableList<KitCardNode>, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    if (nodes.isEmpty()) return
    val colors = LocalKitColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        nodes.forEach { node ->
            when (node) {
                is KitCardNode.Text -> KitText(node.text, role = TypeRole.Body)
                is KitCardNode.KeyValue ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        node.rows.forEach { (k, v) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                                KitText(k, Modifier.weight(0.4f), role = TypeRole.Secondary, tone = TextTone.Secondary)
                                KitText(v, Modifier.weight(0.6f), role = TypeRole.Body)
                            }
                        }
                    }
                is KitCardNode.Checklist ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        node.items.forEach { (text, done) ->
                            val mark = if (!node.checks) "•" else if (done) "☑" else "☐"
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                                KitText(mark, role = TypeRole.Body, tone = TextTone.Secondary, kind = KitTextKind.Frame)
                                KitText(text, role = TypeRole.Body, tone = if (done && node.checks) TextTone.Secondary else TextTone.Primary)
                            }
                        }
                    }
                is KitCardNode.Table ->
                    Column(
                        Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = node.label },
                        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                    ) {
                        TableRow(node.columns, header = true)
                        node.rows.forEach { TableRow(it, header = false) }
                    }
                is KitCardNode.Chart -> {
                    val max = node.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                    Column(Modifier.semantics { contentDescription = node.summary }, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        node.labels.forEachIndexed { i, label ->
                            val v = node.values.getOrElse(i) { 0.0 }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                                KitText(label, Modifier.weight(0.35f), role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 1)
                                Box(Modifier.weight(0.65f)) {
                                    Box(
                                        Modifier.fillMaxWidth((v / max).coerceIn(0.0, 1.0).toFloat().coerceAtLeast(0.02f))
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
                    KitCodeBlock(node.text, KitCodeKind.Code, language = CodeLanguage.of(node.language))
                is KitCardNode.DiffStat ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        node.files.forEach { (path, added, removed) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                                KitText(path, Modifier.weight(1f), role = TypeRole.Secondary, maxLines = 1)
                                KitText("+$added", role = TypeRole.Secondary, tone = TextTone.Success, kind = KitTextKind.Frame)
                                KitText("−$removed", role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
                            }
                        }
                        node.more?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame) }
                    }
                is KitCardNode.Progress ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        Row {
                            KitText(node.label, Modifier.weight(1f), role = TypeRole.Secondary)
                            KitText(node.valueLabel, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
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
 * A choice ask (Flutter `agent_card_asks.dart`): every option a visible row. Single choice: one tap answers (the Undo
 * window covers a slip). Several: check rows and [sendLabel]. Controls wait while [blockedReason] says why.
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

/** A confirm ask: the cancel answer and the confirm answer side by side; danger tones the confirm answer. */
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
        KitButton(cancelLabel, { onAnswer(false) }, Modifier.weight(1f), role = KitButtonRole.Secondary, enabled = enabled, haptic = KitHaptic.Send)
        KitButton(confirmLabel, { onAnswer(true) }, Modifier.weight(1f), destructive = danger, enabled = enabled, haptic = KitHaptic.Send)
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
        KitText(note, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        val reason = blockedReason ?: sendBlockedReason
        KitButton(sendLabel, onSend, enabled = KitEnabled.of(reason == null, reason), haptic = KitHaptic.Send, modifier = Modifier.testTag("kit-agent-card-send"))
    }
}

/** A photo ask: the purpose, the visible ways to add a photo, and Send; or one plain line when photos cannot go. */
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
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitText(purpose, role = TypeRole.Body)
        if (unavailable != null) {
            KitText(unavailable, Modifier.testTag("kit-agent-card-photo-unavailable"), role = TypeRole.Secondary, tone = TextTone.Secondary)
            return@Column
        }
        KitText(countLabel, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        val enabled = KitEnabled.of(blockedReason == null, blockedReason)
        if (chooseLabel != null && onChoose != null) KitButton(chooseLabel, onChoose, role = KitButtonRole.Secondary, enabled = enabled)
        if (sendLabel != null && onSend != null) {
            val reason = blockedReason ?: sendBlockedReason
            KitButton(sendLabel, onSend, enabled = KitEnabled.of(reason == null, reason), haptic = KitHaptic.Send)
        }
    }
}

/**
 * The list slot (plan 09 §13.7, 07 C40): a waiting card under its Conversations row, one line and a way in. The row
 * keeps the list's one primary; this is secondary.
 */
@Composable
public fun KitCardListSlot(eyebrow: String, title: String, openLabel: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
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
            KitText(eyebrow, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
            KitText(title, role = TypeRole.Body, maxLines = 2)
        }
        KitButton(openLabel, onOpen, role = KitButtonRole.Secondary)
    }
}
