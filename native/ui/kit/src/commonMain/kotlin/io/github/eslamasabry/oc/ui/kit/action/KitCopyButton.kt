package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlinx.coroutines.delay

/** Flutter kit_buttons.dart:898-945: copy on tap, then the temporary check and Copied label. */
@Composable
public fun KitCopyButton(
    label: String,
    copiedLabel: String,
    text: () -> String,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
    mask: KitCopyMask = KitCopyVerbatim,
    maskedLabel: String = copiedLabel,
) {
    KitButton(
        rememberKitCopyAction(label, copiedLabel, text, enabled, mask, maskedLabel),
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        role = KitButtonRole.Tertiary,
    )
}

/**
 * The same copy behavior for a pinned sheet action, without moving that action into the body. [mask] prepares the text
 * just before the write; when it masked something the copied state reads [maskedLabel] instead of [copiedLabel].
 */
@Composable
public fun rememberKitCopyAction(
    label: String,
    copiedLabel: String,
    text: () -> String,
    enabled: KitEnabled = KitEnabled.Enabled,
    mask: KitCopyMask = KitCopyVerbatim,
    maskedLabel: String = copiedLabel,
): KitAction {
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var copies by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    var masked by remember { mutableStateOf(false) }
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(MotionTokens.COPIED_HOLD.toLong())
            copied = false
        }
    }
    return KitAction(
        if (!copied) label else if (masked) maskedLabel else copiedLabel,
        {
            val out = mask.apply(text())
            clipboard.setText(AnnotatedString(out.text))
            masked = out.masked
            copies++
            copied = true
        },
        icon = if (copied) KitIcons.check else KitIcons.copy,
        enabled = enabled,
    )
}
