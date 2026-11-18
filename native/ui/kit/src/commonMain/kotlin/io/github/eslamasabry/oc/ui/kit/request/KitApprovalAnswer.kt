package io.github.eslamasabry.oc.ui.kit.request

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled

/** The two answers to an approval (plain values). */
public enum class KitApproval { AllowOnce, Deny }

/**
 * One answer of an approval, drawn the one way everywhere it is asked (B-457): "Allow once" is the filled
 * (Primary) button, "Deny" the outlined (Secondary) one, so Home's card and the chat's card never differ in emphasis.
 * The caller lays the two out (side by side, wrapped or stacked) and brings the words; [quiet] lowers "Allow once" to
 * the outlined button for a look that fills nothing else (the chat card's Signal look keeps its own).
 *
 * States: enabled, disabled with a reason, working
 * Text: frame (Button)
 * Cap: Control (the button's own)
 * Motion: the button's own press; [haptic] plays on the tap
 */
@Composable
public fun KitApprovalAnswer(
    answer: KitApproval,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
    working: Boolean = false,
    haptic: KitHaptic? = null,
    semanticsLabel: String? = null,
    expand: Boolean = true,
    quiet: Boolean = false,
) {
    KitButton(
        label,
        onClick,
        modifier,
        role = if (answer == KitApproval.AllowOnce && !quiet) KitButtonRole.Primary else KitButtonRole.Secondary,
        semanticsLabel = semanticsLabel,
        enabled = enabled,
        working = working,
        haptic = haptic,
        expand = expand,
    )
}
