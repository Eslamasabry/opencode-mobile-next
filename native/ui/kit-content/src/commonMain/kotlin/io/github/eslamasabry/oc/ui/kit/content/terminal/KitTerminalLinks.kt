package io.github.eslamasabry.oc.ui.kit.content.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** A visible way to open detected addresses, also usable without hitting a terminal grid cell. */
@Composable
public fun KitTerminalLinks(transcript: String, modifier: Modifier = Modifier, columns: Int = 80) {
    val host = LocalTerminalLinkHost.current ?: return
    val urls =
        remember(transcript, columns) {
            TerminalLinks.urls(
                TerminalText.strip(transcript.takeLast(100_000)),
                columns.coerceAtLeast(1),
            )
        }
    if (urls.isEmpty()) return
    var review by remember { mutableStateOf<TerminalLinkReview?>(null) }
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        urls.forEach { url ->
            val shownHost =
                url.substringAfter("://")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .take(160)
            KitButton(
                label = stringResource(Res.string.terminalParityOpenAddress, shownHost),
                role = KitButtonRole.Tertiary,
                onClick = { review = host.review(url) },
            )
        }
    }
    KitConfirm(
        spec =
            review?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.terminalLinkReviewTitle),
                    body =
                        stringResource(
                            if (it.insecure) Res.string.terminalLinkReviewInsecure
                            else Res.string.terminalLinkReviewBody,
                            it.host,
                        ),
                    confirmLabel = stringResource(Res.string.terminalLinkOpen),
                )
            },
        onConfirm = {
            val selected = review
            review = null
            selected?.confirm?.invoke()
        },
        onCancel = { review = null },
    )
}
