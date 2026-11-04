package io.github.eslamasabry.oc.ui.kitchat.form
import io.github.eslamasabry.oc.ui.kit.state.KitRequestSurface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The form sheet's body (plan 09 §5.4–§5.5, map CHAT-GENUI-47/48): [title], an optional [description], the fields
 * ([content], usually [KitForm]) scrolling, and the answers pinned under them — Send and the quiet Decline. While the
 * form cannot be sent [blockedReason] says why, under Send (STATE-8), and Send is disabled with that reason. [error]
 * shows a refused answer above the answers (400 keeps the form open). [sending] ("Sending your answers") shows Send
 * working and is the reason Decline waits.
 *
 * Height is the host sheet's call (content height for ≤ 4 declared fields, full height otherwise: the engine's
 * `FormRules.prefersFullHeight`); this body fills what it is given and keeps the answers in sight.
 */
@Composable
public fun KitFormSheetBody(
    title: String,
    description: String?,
    sendLabel: String,
    declineLabel: String,
    onSend: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    blockedReason: String? = null,
    error: String? = null,
    sending: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(
                title,
                Modifier.semantics { heading() },
                role = TypeRole.Headline,
                tone = TextTone.Primary,
                kind = KitTextKind.Reading,
            )
            description?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Reading) }
            content()
        }
        error?.let { KitErrorNotice(it) }
        if (blockedReason != null && sending == null) {
            KitText(
                blockedReason,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
            KitButton(
                label = declineLabel,
                onClick = onDecline,
                modifier = Modifier.weight(1f),
                role = KitButtonRole.Tertiary,
                enabled = sending?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled,
            )
            KitButton(
                label = sendLabel,
                onClick = onSend,
                modifier = Modifier.weight(1f),
                enabled = blockedReason?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled,
                working = sending != null,
            )
        }
    }
}

/**
 * The form's card in the chat (map CHAT-GENUI-48): the needs-you look, [title], [summary] ("3 questions"), and Answer,
 * which opens the sheet — a form is never presented by itself (keyboard safety). [status] says where an answer is
 * ("Sending your answers", "Not confirmed yet"); [error] shows a refused answer's reason above Answer again.
 */
@Composable
public fun KitFormCard(
    title: String,
    summary: String?,
    answerLabel: String,
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    error: String? = null,
) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Card.toShape()
    KitRequestSurface(modifier.widthIn(max = 700.dp).fillMaxWidth()) {
        KitText(
            title,
            Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            role = TypeRole.Headline,
            tone = TextTone.Primary,
            kind = KitTextKind.Reading,
        )
        summary?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Reading) }
        error?.let { KitErrorNotice(it) }
        if (status != null) {
            KitText(
                status,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
            )
        } else {
            KitButton(answerLabel, onAnswer, Modifier.fillMaxWidth())
        }
    }
}
