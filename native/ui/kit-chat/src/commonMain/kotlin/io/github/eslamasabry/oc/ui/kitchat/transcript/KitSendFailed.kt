package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold

/**
 * What a [KitSendFailed] keeps in its Details fold: its label, the copy action's words, the stable code and the
 * failure's own redacted details (the app's fixed words for which check refused, when it has them).
 */
public data class KitSendFailedDetails(
    val label: String,
    val copyLabel: String,
    val note: String,
    val text: Redacted? = null,
)

/**
 * Under a message that did not leave: what happened ([lines], plain words, empty ones skipped), the failure's code in
 * a Details fold ([details], optional) and the one way forward ([actionLabel]). Every piece stands on the
 * conversation's column, the sentences' own edge: the fold and the action used to be laid by the screen with no
 * gutter, so they started at the screen's edge under sentences that kept it (owner 2026-11-15).
 */
@Composable
public fun KitSendFailed(
    lines: List<String>,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    details: KitSendFailedDetails? = null,
    detailsTag: String = "kit-send-failed-details",
    actionTag: String = "kit-send-failed-action",
) {
    Column(modifier.then(column()), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        for (line in lines) {
            if (line.isEmpty()) continue
            BasicText(
                line,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDirection = TextDirection.Content,
                    ),
            )
        }
        details?.let {
            KitDetailsFold(
                label = it.label,
                copyLabel = it.copyLabel,
                notes = listOf(it.note),
                text = it.text,
                modifier = Modifier.testTag(detailsTag),
            )
        }
        KitButton(actionLabel, onAction, role = KitButtonRole.Tertiary, modifier = Modifier.testTag(actionTag))
    }
}
