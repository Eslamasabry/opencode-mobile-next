package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitCopyMask
import io.github.eslamasabry.oc.ui.kit.action.KitCopyText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButtonCopy
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopiedMasked
import org.jetbrains.compose.resources.stringResource

/**
 * The one redacting copy (Flutter `KitCopy.copy` in lib/ui/kit/kit_copy.dart, whose `redact` defaults to true): text
 * that came from the server (commands, tool input and output, reasoning, skill text, MCP addresses) is masked by the
 * app's one [Redactor] before it reaches the clipboard, so other apps never read a key, token or password from it.
 * Terminal selection and OSC 52 copy stay verbatim (reviewed in B-SEC-145).
 */
public object KitCopy {
    /** The redacting mask: `masked` is true when the Redactor changed anything. */
    public fun redacting(redactor: Redactor): KitCopyMask =
        KitCopyMask { text ->
            val safe = redactor.redact(text)
            KitCopyText(safe, masked = safe != text)
        }

    /** The text a redacting copy writes, for platform wiring that writes the clipboard itself. */
    public fun safeText(redactor: Redactor, text: String): KitCopyText = redacting(redactor).apply(text)
}

/** [KitCopy.redacting] with the composition's Redactor ([LocalKitRedactor]). */
@Composable
public fun rememberKitRedactingCopyMask(): KitCopyMask {
    val redactor = LocalKitRedactor.current
    return remember(redactor) { KitCopy.redacting(redactor) }
}

/** `rememberKitCopyAction` for server text: masked, and the copied state says so when something was hidden. */
@Composable
public fun rememberKitRedactedCopyAction(
    label: String,
    copiedLabel: String,
    text: () -> String,
    enabled: KitEnabled = KitEnabled.Enabled,
): KitAction =
    rememberKitCopyAction(
        label,
        copiedLabel,
        text,
        enabled,
        mask = rememberKitRedactingCopyMask(),
        maskedLabel = stringResource(Res.string.kitCopiedMasked),
    )

/** `KitIconButtonCopy` for server text: masked, and the copied name says so when something was hidden. */
@Composable
public fun KitRedactedCopyIconButton(text: () -> String, label: String, modifier: Modifier = Modifier) {
    KitIconButtonCopy(
        text = text,
        modifier = modifier,
        label = label,
        mask = rememberKitRedactingCopyMask(),
        maskedLabel = stringResource(Res.string.kitCopiedMasked),
    )
}
