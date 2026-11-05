package io.github.eslamasabry.oc.ui.kitchat.rb.o01

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.turn.KitSince
import io.github.eslamasabry.oc.ui.kitchat.turn.KitSincePhase
import io.github.eslamasabry.oc.ui.kitchat.turn.KitSinceTicks
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Every scene of test/goldens/kit/kit_since_golden_test.dart: a KitText line driven by KitSince in its phases. */
class KitSinceGateTest {
    private fun line(offset: Duration?, ticks: KitSinceTicks = KitSinceTicks.None, waiting: String): @Composable () -> Unit = {
        Box(Modifier.padding(horizontal = 20.dp)) {
            KitSince(since = offset?.let { Clock.System.now() - it }, ticks = ticks) { status ->
                val text = when (status.phase) {
                    KitSincePhase.Idle -> ""
                    KitSincePhase.Waiting -> "Connecting…"
                    KitSincePhase.Slow -> if (ticks == KitSinceTicks.Minutes) waiting else "Still waiting after 8 s"
                }
                KitText(text, role = TypeRole.Secondary)
            }
        }
    }

    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { CompositionLocalProvider(LocalKitRedactor provides Redactor()) { it() } }
    private val minutesEn = "Waiting 4 min"
    private val minutesAr = "جارٍ الانتظار منذ 4 دقائق"

    private fun shot(name: String, textScale: Float? = null, content: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, wrap = wrap, textScale = textScale, content = content)
    }

    @Test fun connecting() = shot("kit_since_connecting", content = line(Duration.ZERO, waiting = minutesEn))

    @Test fun slow_every_window() {
        for (size in listOf("", "_360x800", "_800x1280", "_1280x800", "_1600x1000")) shot("kit_since_slow$size", content = line(16.seconds, waiting = minutesEn))
    }

    @Test fun minutes_default() = shot("kit_since_minutes", content = line(4.minutes, KitSinceTicks.Minutes, minutesEn))

    @Test fun minutes_text2_and_ar() {
        for (size in listOf("", "_1280x800")) {
            shot("kit_since_minutes_text2$size", content = line(4.minutes, KitSinceTicks.Minutes, minutesEn))
            shot("kit_since_minutes_ar$size", 1.3f, line(4.minutes, KitSinceTicks.Minutes, minutesAr))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_since", caps = mapOf("KitSince" to "wrapper"), wrap = wrap, content = line(16.seconds, waiting = minutesEn))
    }
}
