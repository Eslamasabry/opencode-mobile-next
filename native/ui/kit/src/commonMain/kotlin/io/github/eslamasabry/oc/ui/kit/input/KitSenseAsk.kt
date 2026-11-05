package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceTile
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** What an agent asks the phone for (Dart `KitSenseKind`). */
public enum class KitSenseKind { Photo, File, Voice }

/** One thing the person picked, shown with a way to take it back (Dart `KitSenseItem`). */
public data class KitSenseItem(
    val id: String,
    val name: String,
    val removeLabel: String,
    val detail: String? = null,
)

/** One way to get something from the phone: "Take photo", "Choose file" (Dart `KitSenseAction`). */
public data class KitSenseAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
)

/**
 * An agent asking for a photo, a file or a voice note from the phone (Dart `KitSenseAsk`): a purpose line, the actions
 * that get one, the items picked so far with a remove control each, and a Send. With items at [max] the actions are
 * disabled with [maxReachedLabel] beneath them; with no items Send is disabled with [sendDisabledReason]. While
 * [sending] Send shows its working look and every control ignores input.
 *
 * States: default, disabled, working
 * Text: reading
 * Cap: Conversation
 * Motion: none
 * Hidden: none
 * Replaces: KitSenseAsk (lib/ui/kit/kit_sense_ask.dart)
 */
@Composable
public fun KitSenseAsk(
    kind: KitSenseKind,
    purpose: String,
    actions: List<KitSenseAction>,
    items: List<KitSenseItem>,
    sendLabel: String,
    onSend: (() -> Unit)?,
    modifier: Modifier = Modifier,
    max: Int = 4, // F: lib/ui/kit/kit_sense_ask.dart:86
    countLabel: String? = null,
    maxReachedLabel: String? = null,
    sendDisabledReason: String? = null,
    sending: Boolean = false,
    secondary: Boolean = false,
    onRemove: ((String) -> Unit)? = null,
) {
    val full = items.size >= max
    val canSend = items.isNotEmpty() && onSend != null && !sending
    val send = KitAction(
        label = sendLabel,
        onClick = if (canSend) onSend else null,
        enabled = if (canSend || sending) KitEnabled.Enabled else KitEnabled.Disabled(sendDisabledReason ?: sendLabel),
        working = sending,
    )
    Column(
        modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth().testTag("kit-sense-ask"),
    ) {
        KitText(purpose, role = TypeRole.Body, tone = TextTone.Primary)
        Spacer3()
        actions.forEach { action ->
            val off = full || sending
            KitButton(
                KitAction(
                    label = action.label,
                    onClick = if (off) null else action.onClick,
                    enabled = if (off) KitEnabled.Disabled(action.label) else KitEnabled.Enabled,
                    icon = action.icon,
                ),
                Modifier.fillMaxWidth(),
                KitButtonRole.Secondary,
            )
            Spacer(Space.S2)
        }
        if (full && maxReachedLabel != null) {
            KitText(
                maxReachedLabel,
                Modifier.padding(bottom = Space.S2.dp),
                TypeRole.Secondary,
                TextTone.Secondary,
            )
        }
        if (items.isNotEmpty()) {
            if (countLabel != null) {
                KitText(countLabel, Modifier.padding(bottom = Space.S1.dp), TypeRole.Caption, TextTone.Secondary)
            }
            items.forEach { SenseItem(kind, it, sending, onRemove) }
            Spacer(Space.S2)
        }
        KitActionBlock(primary = if (secondary) null else send, secondary = if (secondary) send else null)
    }
}

@Composable
private fun Spacer3() = Spacer(Space.S3)

@Composable
private fun Spacer(height: Int) {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = height.dp))
}

@Composable
private fun SenseItem(kind: KitSenseKind, item: KitSenseItem, sending: Boolean, onRemove: ((String) -> Unit)?) {
    val glyph = when (kind) {
        KitSenseKind.Photo -> KitIcons.image
        KitSenseKind.File -> KitIcons.file
        KitSenseKind.Voice -> KitIcons.mic
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = Space.S1.dp).testTag("kit-sense-item-${item.id}"),
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitSurfaceTile(glyph, Modifier.clearAndSetSemantics {})
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            KitText(item.name, role = TypeRole.RowTitle, tone = TextTone.Primary)
            if (item.detail != null) KitText(item.detail, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        val off = sending || onRemove == null
        KitIconButton(
            icon = KitIcons.close,
            label = item.removeLabel,
            onClick = { onRemove?.invoke(item.id) },
            enabled = if (off) KitEnabled.Disabled(item.removeLabel) else KitEnabled.Enabled,
        )
    }
}
