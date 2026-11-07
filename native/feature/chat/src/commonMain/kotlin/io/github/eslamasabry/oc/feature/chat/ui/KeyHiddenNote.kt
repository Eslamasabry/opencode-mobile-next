package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusContribution
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import kotlinx.coroutines.delay

/** How long the "a key was hidden" line stays (the kit's tip hold); the person can dismiss it sooner. */
private const val KEY_HIDDEN_HOLD_MS = 4000L

/**
 * E15-S06: a copy that the app's redactor changed says so where it can be seen, not only to a screen reader. One plain
 * line in the window's status slot for each such copy ([copyId] grows with each copy; [masked] = the redactor hid
 * something), gone after a short hold, at once on dismiss, or when a later copy hid nothing. The slot folds it in and
 * out (and draws it instantly under reduced motion).
 */
@Composable
internal fun KeyHiddenNote(copyId: Long?, masked: Boolean, message: String, dismissLabel: String) {
    // The copy's id can go back to null as soon as the clipboard is written: remember the masked copy itself.
    var noted by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(copyId, masked) { if (copyId != null) noted = if (masked) copyId else null }
    LaunchedEffect(noted) {
        if (noted != null) {
            delay(KEY_HIDDEN_HOLD_MS)
            noted = null
        }
    }
    KitStatusContribution(
        noted?.let {
            KitStatus(
                KitStatusKind.Info,
                message,
                id = "chat-key-hidden-$it",
                onDismiss = { noted = null },
                dismissLabel = dismissLabel,
            )
        }
    )
}
