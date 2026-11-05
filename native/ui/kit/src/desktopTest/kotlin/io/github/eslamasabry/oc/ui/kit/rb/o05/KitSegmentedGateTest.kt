package io.github.eslamasabry.oc.ui.kit.rb.o05

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_segmented_golden_test.dart. */
class KitSegmentedGateTest {
    private fun default(ar: Boolean = false): @Composable () -> Unit = {
        KitSegmented(
            segments = listOf(
                KitSegment("today", if (ar) "اليوم" else "Today"),
                KitSegment("week", if (ar) "الأسبوع" else "Week"),
                KitSegment("month", if (ar) "الشهر" else "Month"),
            ),
            selected = "week",
            semanticsLabel = if (ar) "النطاق الزمني" else "Time range",
            onChange = {},
        )
    }

    private val stacked: @Composable () -> Unit = {
        KitSegmented(
            segments = listOf(
                KitSegment("session", "Only this conversation"),
                KitSegment("server", "Every conversation on this computer"),
            ),
            selected = "session",
            semanticsLabel = "Permission scope",
            onChange = {},
        )
    }

    private val tall: @Composable () -> Unit = {
        KitSegmented(
            segments = listOf(
                KitSegment("auto", "Auto", KitIcons.systemTheme),
                KitSegment("light", "Light", KitIcons.lightMode),
                KitSegment("dark", "Dark", KitIcons.darkMode),
                KitSegment("saver", "Saver", KitIcons.leaf),
            ),
            selected = "dark",
            semanticsLabel = "Light or dark",
            tall = true,
            onChange = {},
        )
    }

    private val counts: @Composable () -> Unit = {
        KitSegmented(
            segments = listOf(
                KitSegment("all", "All"),
                KitSegment("needsYou", "Needs you", count = 2),
                KitSegment("done", "Done"),
            ),
            selected = "needsYou",
            semanticsLabel = "Filter",
            onChange = {},
        )
    }

    private val segmentDisabled: @Composable () -> Unit = {
        KitSegmented(
            segments = listOf(
                KitSegment("chat", "Chat"),
                KitSegment("voice", "Voice"),
                KitSegment("camera", "Camera", enabled = KitEnabled.Disabled("Chosen at install · reinstall to change")),
            ),
            selected = "chat",
            semanticsLabel = "Mode",
            onChange = {},
        )
    }

    private val disabled: @Composable () -> Unit = {
        KitSegmented(
            segments = listOf(KitSegment("mine", "Mine"), KitSegment("team", "Team"), KitSegment("workspace", "Workspace")),
            selected = "mine",
            semanticsLabel = "Scope",
            enabled = KitEnabled.Disabled("Set by your workspace admin"),
            onChange = {},
        )
    }

    @Test fun scenes() {
        FlutterGoldenGate.check("kit_segmented_default", content = default())
        FlutterGoldenGate.check("kit_segmented_with_counts", content = counts)
        FlutterGoldenGate.check("kit_segmented_segment_disabled", content = segmentDisabled)
        FlutterGoldenGate.check("kit_segmented_disabled", content = disabled)
        FlutterGoldenGate.check("kit_segmented_tall", content = tall)
        FlutterGoldenGate.check("kit_segmented_stacked", content = stacked)
    }

    @Test fun sizes() {
        for (s in listOf("_360x800", "_800x1280", "_1280x800", "_1600x1000", "_915x412")) {
            FlutterGoldenGate.check("kit_segmented_default$s", content = default())
        }
    }

    @Test fun scaled() {
        for (s in listOf("", "_1280x800")) {
            FlutterGoldenGate.check("kit_segmented_default_text2$s", content = default())
            FlutterGoldenGate.check("kit_segmented_default_ar$s", textScale = 1.3f, content = default(ar = true))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_segmented", caps = mapOf("KitSegmented" to "reading")) { default()() }
    }
}
