package io.github.eslamasabry.oc.ui.kitchat.rb.o21

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrost
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostRules
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostSaturation
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * test/goldens/kit/kit_frost_golden_test.dart `_scene`: the composer pane at top 140 over words and a code block. The
 * desktop platform has no blur (standard §5.3); the gate gives the pane the platform blur an Android 31+ phone has, so
 * the comparison is of the glass itself.
 */
class KitFrostGateTest {
    private class BlurringPlatform(base: KitPlatform = DesktopKitPlatform()) : KitPlatform by base {
        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect =
            FrostPolicy.blurRadiusPx(spec.sigmaDp, density).let { BlurEffect(it, it, TileMode.Clamp) }
    }

    @Composable
    private fun scene(strength: FrostStrength = FrostStrength.Balanced) {
        val state = rememberKitFrostState()
        val colors = LocalKitColors.current
        val line: @Composable (Int) -> Unit = { i ->
            KitText("The focus ring on the TV grid is cut off, line $i.", Modifier.padding(vertical = Space.S2.dp))
        }
        Box(Modifier.fillMaxWidth().padding(LocalKitTokens.current.gutter.dp)) {
            Column(Modifier.fillMaxWidth().kitFrostSource(state).clearAndSetSemantics {}) {
                for (i in 0 until 4) line(i)
                KitCodeBlock(
                    source = "- padding: EdgeInsets.all(12),\n+ padding: EdgeInsets.all(15),",
                    kind = KitCodeKind.Code,
                    copyLabel = "Copy",
                    onCopy = {},
                )
                for (i in 4 until 8) line(i)
            }
            KitFrost(
                role = FrostRole.Composer,
                strength = strength,
                shown = true,
                tint = colors.surface2,
                state = state,
                modifier = Modifier.fillMaxWidth().offset(y = 140.dp),
                radius = LocalKitTokens.current.composerRadius.dp,
            ) {
                KitText("Steer while it works…", Modifier.padding(Space.S4.dp), role = TypeRole.Body)
            }
        }
    }

    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { c ->
        CompositionLocalProvider(LocalKitPlatform provides BlurringPlatform(), LocalKitRedactor provides Redactor()) {
            FlutterCapture(c)
        }
    }

    private fun shot(name: String, strength: FrostStrength = FrostStrength.Balanced) =
        FlutterGoldenGate.check(wrap = wrap, name = name) { scene(strength) }

    @Test fun kit_frost_default() {
        for (size in listOf("", "_360x800", "_800x1280", "_1280x800", "_1600x1000")) shot("kit_frost_default$size")
    }
    @Test fun kit_frost_default_text2() { shot("kit_frost_default_text2"); shot("kit_frost_default_text2_1280x800") }
    @Test fun kit_frost_weak() { shot("kit_frost_weak", FrostStrength.Weak) }
    @Test fun kit_frost_strong() { shot("kit_frost_strong", FrostStrength.Strong) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_frost",
            caps = mapOf("KitFrost" to "wrapper"),
            wrap = { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { c() } },
        ) { scene() }
    }

    /** kit_frost.dart:166,222-232,388-391: the fade, the saturation matrix, the tint at each end of the fade. */
    @Test fun rules() {
        assertEquals(180, KitFrostRules.FADE_MS)
        val m = KitFrostSaturation.matrix(1.4f)
        assertEquals(0.2126f * -0.4f + 1.4f, m[0], 1e-6f)
        assertEquals(0.7152f * -0.4f, m[1], 1e-6f)
        assertEquals(1f, m[18], 0f)
        assertEquals(0.70f, KitFrostRules.tintAlpha(FrostRole.Composer, FrostStrength.Balanced, dark = false, t = 1f), 1e-6f)
        assertEquals(0.60f, KitFrostRules.tintAlpha(FrostRole.Composer, FrostStrength.Balanced, dark = true, t = 1f), 1e-6f)
        assertEquals(0f, KitFrostRules.tintAlpha(FrostRole.Composer, FrostStrength.Strong, dark = false, t = 0f), 0f)
        assertEquals(1f, KitFrostRules.tintAlpha(FrostRole.ChatHeader, FrostStrength.Strong, dark = false, t = 0f), 0f)
        assertEquals(0.85f, KitFrostRules.tintAlpha(FrostRole.ChatHeader, FrostStrength.Strong, dark = false, t = 1f), 1e-6f)
    }
}
