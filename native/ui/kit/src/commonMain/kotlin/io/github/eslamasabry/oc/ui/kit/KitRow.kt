package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowMotion
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowSource
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/**
 * A tappable list row (slice-0 placeholder of the conversation row). With [growKey], the page opened from it grows out
 * of this row and shrinks back into it ([io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage], plan 08 §5.4).
 * Motion: Transform (Full only); press is the platform ripple.
 */
@Composable
public fun KitRow(
    title: String,
    supporting: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    growKey: Any? = null,
) {
    val platform = LocalKitPlatform.current
    Surface(
        onClick = {
            platform.haptic(KitHaptic.Select)
            onClick()
        },
        shape = RoundedCornerShape(KitGrowMotion.ROW_CORNER),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth().kitGrowSource(growKey),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp + Space.S1.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            KitText(title, KitTextRole.Body)
            if (supporting != null) KitText(supporting, KitTextRole.Supporting)
        }
    }
}
