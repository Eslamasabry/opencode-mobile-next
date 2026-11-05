package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.state.kitNeedsYouSpan
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Flutter server_rows.dart:156-190: one wrapping supporting span, state before server kind. */
@Composable
public fun kitServerSupporting(
    kind: String,
    connected: Boolean = false,
    waiting: Int = 0,
    running: Int = 0,
    secret: String? = null,
    queued: Int = 0,
): AnnotatedString {
    val colors = LocalKitColors.current
    val label =
        LocalKitTypography.current.style(TypeRole.Label).copy(color = colors.text1).toSpanStyle()
    val current = if (connected) stringResource(Res.string.serverRowConnected) else null
    val needsYou = if (!connected && waiting > 0) kitNeedsYouSpan(waiting) else null
    val working =
        if (running == 1) stringResource(Res.string.otherServerWorking__eq1)
        else if (running > 0) pluralStringResource(Res.plurals.otherServerWorking, running, running)
        else null
    val queue =
        if (queued == 1) stringResource(Res.string.serverRowQueuedWaiting__eq1)
        else if (queued > 0)
            pluralStringResource(Res.plurals.serverRowQueuedWaiting, queued, queued)
        else null
    val disconnected =
        if (!connected && waiting <= 0 && running <= 0)
            stringResource(Res.string.settingsV3NotConnected)
        else null
    return buildAnnotatedString {
        if (current != null)
            withStyle(label.copy(color = colors.accent)) {
                append(current)
                append(" · ")
            }
        needsYou?.let { append(it) }
        for (word in listOfNotNull(working, secret, queue)) withStyle(label) {
            append(word)
            append(" · ")
        }
        disconnected?.let {
            append(it)
            append(" · ")
        }
        append(kind)
    }
}
