package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.servers.ui.LocalServersField
import io.github.eslamasabry.oc.feature.servers.ui.ServersFieldKind
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Test-only host: the kit theme on the desktop platform, motion Off, and a stand-in input part bound to the field
 * spec until `KitField` exists (the production default draws no input; report D2). Tests find fields by their label.
 */
@Composable
fun ServersHost(
    dark: Boolean = false,
    localeTag: String = "en",
    /** Goldens pass false: they show the production field part, not the test stand-in. */
    inputs: Boolean = true,
    content: @Composable () -> Unit,
) {
    KitTheme(
        appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
        platform = DesktopKitPlatform(),
        localeTag = localeTag,
        stillFrame = true,
    ) {
        if (!inputs) return@KitTheme content()
        CompositionLocalProvider(
            LocalServersField provides { spec ->
                if (spec.kind == ServersFieldKind.Secret) {
                    var typed by remember { mutableStateOf("") }
                    BasicTextField(typed, { typed = it; spec.onValueChange(it) }, Modifier.testTag(spec.label))
                } else {
                    BasicTextField(spec.value, spec.onValueChange, Modifier.testTag(spec.label))
                }
            },
            content = content,
        )
    }
}

/** ViewModel work runs at once on the test thread, so a click's effect is visible to the next assertion. */
@OptIn(ExperimentalCoroutinesApi::class)
fun immediateScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())
