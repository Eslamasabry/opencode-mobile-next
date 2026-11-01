package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.Spacing
import io.github.eslamasabry.oc.ui.kit.platform.KitHaptic
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/** Slice-0 placeholder kit parts. Their real signatures freeze in contracts/slice-1.md (docs/ux-system/kit-api). */
public enum class KitTextRole { Title, Body, Supporting }

@Composable
public fun KitText(text: String, role: KitTextRole = KitTextRole.Body, modifier: Modifier = Modifier) {
    val style =
        when (role) {
            KitTextRole.Title -> MaterialTheme.typography.headlineSmall
            KitTextRole.Body -> MaterialTheme.typography.bodyLarge
            KitTextRole.Supporting -> MaterialTheme.typography.bodyMedium
        }
    val color =
        if (role == KitTextRole.Supporting) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onBackground
    Text(text = text, style = style, color = color, modifier = modifier)
}

/** A screen frame: title row (with an optional back action), then content. No bottom bar (owner, 2026-10-20). */
@Composable
public fun KitScreen(
    title: String,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(Spacing.M.dp).widthIn(max = 840.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.M.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (onBack != null && backLabel != null) {
                    TextButton(onClick = onBack) { Text(backLabel) }
                }
                KitText(title, KitTextRole.Title, Modifier.semantics { heading() })
            }
            content()
        }
    }
}

/** The kit's primary action. Press feedback is the platform ripple; the haptic goes through KitPlatform. */
@Composable
public fun KitButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val platform = LocalKitPlatform.current
    Button(
        onClick = {
            platform.haptic(KitHaptic.Commit)
            onClick()
        },
        modifier = modifier,
    ) {
        Text(label)
    }
}
