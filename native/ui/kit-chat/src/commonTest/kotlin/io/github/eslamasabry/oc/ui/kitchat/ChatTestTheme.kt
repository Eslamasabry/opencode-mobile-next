package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.coroutines.flow.MutableStateFlow

/** A fixed, silent platform for kit-chat tests (the kit's `FakeKitPlatform` is test-private to `:ui:kit`). */
class ChatFakePlatform : KitPlatform {
    override val name: String = "fake"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())
    override val isDebug: Boolean = true

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic) = Unit

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
}

/**
 * Every kit-chat test draws inside the real [KitTheme] (Graphite roles, Geist type) plus the chat's [LocalKitRedactor].
 * [platform] is the fake by default; the Robolectric goldens pass the Android one.
 */
@Composable
fun ChatTestTheme(
    dark: Boolean = false,
    localeTag: String = "en",
    redactor: Redactor = Redactor(),
    platform: KitPlatform = ChatFakePlatform(),
    content: @Composable () -> Unit,
) {
    KitTheme(
        appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
        platform = platform,
        localeTag = localeTag,
        stillFrame = true,
    ) {
        CompositionLocalProvider(LocalKitRedactor provides redactor, content = content)
    }
}
