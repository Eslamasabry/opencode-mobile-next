package io.github.eslamasabry.oc.ui.kit.content

import androidx.compose.foundation.Indication
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.coroutines.flow.MutableStateFlow

/** A debug platform for content-kit tests (the kit's own fake moves to `:ui:testing` later). */
class ContentFakePlatform(override val isDebug: Boolean = true) : KitPlatform {
    override val name: String = "fake"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic) {}

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
}

/** The QR fixture payloads (plan 10 §14.1: Wi-Fi URLs and handoff links up to 1,000 chars). */
object QrFixtures {
    val payloads: List<String> =
        buildList {
            add("https://example.com")
            add("http://example.invalid:8765/share/3f9a")
            add("WIFI:T:WPA;S:Home;P:correct horse;;")
            add("opencode://pair?host=pc.example.invalid&port=4096&code=ABCD-1234")
            add("مرحبا بالعالم") // UTF-8 bytes, Arabic
            add("emoji 🙂 and ü")
            add("1234567890")
            add("A")
            for (i in 1..42) {
                val len = i * 24 - 8 // 16 … 1000
                val base = "https://handoff.example.invalid/s/${i}?t="
                add(base + "x".repeat((len - base.length).coerceAtLeast(1)).mapIndexed { k, c -> if (k % 7 == 0) 'a' + (k % 26) else c }.joinToString(""))
            }
        }
}
