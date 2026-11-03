package io.github.eslamasabry.oc.feature.projects

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
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

/** Plan 06 §6's fake platform (kit's own copy is test-internal there). */
internal class TestKitPlatform : KitPlatform {
    override val name: String = "test"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())
    override val isDebug: Boolean = true

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic) {}

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
}

@Composable
internal fun Themed(content: @Composable () -> Unit) {
    KitTheme(
        appearance = KitAppearance(motion = MotionLevel.Off),
        platform = TestKitPlatform(),
        localeTag = "en",
        stillFrame = true,
        content = content,
    )
}
