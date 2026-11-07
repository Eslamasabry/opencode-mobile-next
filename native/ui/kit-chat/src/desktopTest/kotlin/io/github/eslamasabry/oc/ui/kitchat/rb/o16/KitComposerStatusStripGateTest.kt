package io.github.eslamasabry.oc.ui.kitchat.rb.o16

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerChip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStatusStrip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStop
import io.github.eslamasabry.oc.ui.kitchat.composer.KitStopState
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import kotlin.test.Test

/**
 * test/goldens/kit/kit_composer_status_strip_golden_test.dart: the pause chip beside a long model label at 360 dp and
 * 2.0 text, dark only, English and Arabic (RTL), with no Stop, with Stop at rest and with Stop stuck (amber).
 */
class KitComposerStatusStripGateTest {

    private fun strip(ar: Boolean, stop: KitStopState?): @Composable () -> Unit = {
        KitComposerStatusStrip(
            hasChips = true,
            model = { KitComposerChip("Loading conversation selection…", onClick = {}) },
            stop = stop?.let { s ->
                {
                    KitComposerStop(
                        label = if (ar) "إيقاف" else "Stop",
                        targetLabel = if (ar) "إيقاف Claude Code" else "Stop Claude Code",
                        onStop = {},
                        state = s,
                    )
                }
            },
        ) {
            KitChip("Auto-approve paused", kind = KitChipKind.Plain, icon = KitIcons.pause)
        }
    }

    @Test fun composer_status_strip() {
        val dark = listOf("dark") // F: test/goldens/kit/kit_composer_status_strip_golden_test.dart:38,62 light: false
        for (ar in listOf(false, true)) {
            val lang = if (ar) "_ar" else ""
            FlutterGoldenGate.check("kit_composerstatusstrip_wrapped${lang}_text2_360x800", modes = dark, content = strip(ar, null))
            FlutterGoldenGate.check("kit_composerstatusstrip_stop${lang}_text2_360x800", modes = dark, content = strip(ar, KitStopState.Running))
            FlutterGoldenGate.check("kit_composerstatusstrip_stop_stuck${lang}_text2_360x800", modes = dark, content = strip(ar, KitStopState.Stuck))
        }
    }

    @Test fun composer_status_strip_matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_composer_status_strip",
            caps = mapOf("KitComposerStatusStrip" to "conversation"),
            content = strip(false, KitStopState.Running),
        )
    }
}
