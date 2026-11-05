package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** A visible, user-triggered clipboard read. The kit retains no clipboard text or credential. */
@Composable
public fun KitPasteButton(
    label: String,
    onPaste: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
    role: KitButtonRole = KitButtonRole.Secondary,
) {
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    KitButton(
        label,
        { onPaste(clipboard.getText()?.text) },
        modifier,
        role = role,
        icon = KitIcons.paste,
        enabled = enabled,
    )
}
