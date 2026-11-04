package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.tokens.Space

/** A contained colour preview: no system-bar changes, preference writes or density scaling. */
@Composable
public fun KitThemePreview(pack: PackId, dark: Boolean, content: @Composable () -> Unit) {
    val roles = remember(pack, dark) { ThemeResolver.roles(pack, null, dark, false, null) }
    val colors = remember(roles) { roles.toKitColors() }
    CompositionLocalProvider(LocalKitRoles provides roles, LocalKitColors provides colors) {
        MaterialTheme(colorScheme = MaterialBridge.scheme(roles)) {
            Box(Modifier.background(colors.ground).padding(Space.S4.dp)) { content() }
        }
    }
}
