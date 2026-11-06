package io.github.eslamasabry.oc.feature.servers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform

/**
 * The kit theme as the app hosts a server screen: English, a still frame, and the secret sink the app's redactor
 * provides (KitField(Secret) refuses to draw without one). Tests drop the typed secrets.
 */
@Composable
internal fun ServersTestTheme(content: @Composable () -> Unit) =
    KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}, content = content)
    }
