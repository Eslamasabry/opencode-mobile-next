package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFile
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFileStatus
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffLine
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffLineKind
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_diff_view_golden_test.dart: the diff filling a pushed page on `ground`
 * (kitGalleryShot, so [FlutterGoldenGate.Frame.Screen]), at 412x915 and 1280x800, plus the unified view at text 2.0.
 *
 * Mismatch: native [KitDiffView] draws one [DiffFile]; the Dart view takes the file list and draws its file header,
 * the phone's file picker and (wide) the file list pane. Each shot draws the file the Dart view opens (`initialFile`,
 * else the first) on its own.
 *
 * Mismatch: native has no "unlimited" full view: a full (reader) view is `maxLines > 12`, so the scenes without
 * `maxLines` pass [FULL]. Its line list is capped at 560 dp high instead of filling the page.
 *
 * Mismatch: native has no `showKitDiff` (the Dart full-screen diff sheet); the matrix measures [KitDiffView] alone.
 *
 * Mismatch: native gapLabel gets only the count, so every fold reads Dart's "between" words (`kitDiffFoldBetween`);
 * Dart says "lines above" / "lines below" at the file's ends.
 */
@OptIn(ExperimentalTestApi::class)
class KitDiffViewGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit = {
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) { it() }
    }

    // F: test/goldens/kit/kit_diff_view_golden_test.dart:26
    private val before =
        listOf(
            "import 'package:flutter/widgets.dart';",
            "",
            "class Greeting extends StatelessWidget {",
            "  const Greeting({super.key, required this.name});",
            "",
            "  final String name;",
            "",
            "  @override",
            "  Widget build(BuildContext context) {",
            "    final text = 'Hello, ' + name;",
            "    return Text(text);",
            "  }",
            "}",
            "",
            "String shout(String value) => value.toUpperCase();",
            "",
            "int count(List<String> items) {",
            "  var total = 0;",
            "  for (final item in items) {",
            "    total += item.length;",
            "  }",
            "  return total;",
            "}",
        ).joinToString("\n")

    // F: test/goldens/kit/kit_diff_view_golden_test.dart:51
    private val after =
        listOf(
            "import 'package:flutter/widgets.dart';",
            "",
            "class Greeting extends StatelessWidget {",
            "  const Greeting({super.key, required this.name, this.emphasis = false});",
            "",
            "  final String name;",
            "  final bool emphasis;",
            "",
            "  @override",
            "  Widget build(BuildContext context) {",
            "    final text = 'Welcome aboard, \$name';",
            "    return Text(text);",
            "  }",
            "}",
            "",
            "String shout(String value) => value.toUpperCase();",
            "",
            "int count(List<String> items) {",
            "  var total = 0;",
            "  for (final item in items) {",
            "    total += item.length;",
            "  }",
            "  return total + 1;",
            "}",
        ).joinToString("\n")

    // F: test/goldens/kit/kit_diff_view_golden_test.dart:77
    private fun files(): List<DiffFile> =
        listOf(
            DiffModel.fromBeforeAfter("lib/ui/greeting.dart", before, after),
            DiffModel.parseFilePatch(
                "README.md",
                "@@ -1,4 +1,4 @@\n" +
                    " # Greeting\n" +
                    "-Says hello.\n" +
                    "+Says welcome aboard.\n" +
                    " \n" +
                    " Run `flutter test` to check it.\n",
            ),
        )

    // F: test/goldens/kit/kit_diff_view_golden_test.dart:106
    private val jsonBefore =
        listOf(
            "{",
            "  \"name\": \"greeting\",",
            "  \"version\": \"1.2.0\",",
            "  \"private\": true,",
            "  \"scripts\": {",
            "    \"build\": \"tsc -p .\",",
            "    \"test\": \"jest --ci\"",
            "  },",
            "  \"files\": [\"dist\"]",
            "}",
        ).joinToString("\n")

    // F: test/goldens/kit/kit_diff_view_golden_test.dart:118
    private val jsonAfter =
        listOf(
            "{",
            "  \"name\": \"greeting\",",
            "  \"version\": \"1.3.0\",",
            "  \"private\": true,",
            "  \"scripts\": {",
            "    \"build\": \"tsc -p .\",",
            "    \"test\": \"jest --ci --coverage\",",
            "    \"lint\": \"eslint src\"",
            "  },",
            "  \"files\": [\"dist\"]",
            "}",
        ).joinToString("\n")

    /** The Dart `_page`: the view filling the page body on `ground` (the gate's Screen frame paints the ground). */
    private fun page(file: DiffFile, maxLines: Int = FULL, actions: Boolean = false): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            KitDiffView(
                file,
                maxLines = maxLines,
                hunkLabel = { _, _, start, end -> "Lines $start–$end" },
                gapLabel = { hidden -> if (hidden == 1) "… 1 line" else "… $hidden lines" },
                modifier = Modifier.fillMaxWidth(),
                onComment = if (actions) ({ _ -> }) else null, // Dart `readOnly: false` with onComment
                onAddToPrompt = if (actions) ({ _ -> }) else null, // and onAddToPrompt: the selecting scene only
            )
        }
    }

    private val unified: @Composable () -> Unit get() = page(files()[0])

    private fun shots(state: String, act: (ComposeUiTest.() -> Unit)? = null, content: @Composable () -> Unit) {
        for (suffix in listOf("", "_1280x800")) {
            FlutterGoldenGate.check(
                "kit_diff_view_$state$suffix",
                frame = FlutterGoldenGate.Frame.Screen,
                wrap = wrap,
                act = act,
                content = content,
            )
        }
    }

    @Test fun unified_and_json() {
        shots("unified", content = unified)
        // F: test/goldens/kit/kit_diff_view_golden_test.dart:94
        shots("json", content = page(DiffModel.fromBeforeAfter("package.json", jsonBefore, jsonAfter)))
        for (suffix in listOf("", "_1280x800")) {
            FlutterGoldenGate.check(
                "kit_diff_view_unified_text2$suffix",
                frame = FlutterGoldenGate.Frame.Screen,
                wrap = wrap,
                content = unified,
            )
        }
    }

    /**
     * Dart drags 300 ms from line 3 to line 5 of the new side (`kit-diff-line-0-current-3` → `-5`). Native selects a
     * range from the gutter: a tap on new line 3, then on new line 5. Each of those rows is context, so its old number
     * cell comes first and the new one second.
     *
     * Mismatch: native has no drag selection on the lines; the gutter taps stand in for it. The selection bar is
     * native's (count, Copy lines, Comment, Add to prompt, Clear selection), not Dart's menu.
     */
    @Test fun selecting() {
        shots("selecting", act = {
            onAllNodes(hasText(" 3") and hasClickAction())[1].performClick()
            waitForIdle()
            onAllNodes(hasText(" 5") and hasClickAction())[1].performClick()
        }, content = page(files()[0], actions = true))
    }

    /**
     * F: test/goldens/kit/kit_diff_view_golden_test.dart:142 (`initialFile: 1`, the binary file open).
     *
     * Mismatch: native draws nothing for a binary file (Dart: "Binary file · not shown"), and has no "Renamed from"
     * line (Dart shows it in the wide file list and the phone's picker).
     */
    @Test fun binary_renamed() {
        val renamed =
            DiffFile(
                path = "lib/ui/greeting_card.dart",
                segments = persistentListOf(DiffLine("class GreetingCard {}", DiffLineKind.Context, oldNo = 1, newNo = 1)),
                added = 0,
                removed = 0,
                status = DiffFileStatus.Renamed,
                oldPath = "lib/ui/card.dart",
            )
        val binary = DiffFile(path = "assets/logo.png", segments = persistentListOf(), added = 0, removed = 0, binary = true)
        shots("binary_renamed", content = page(listOf(renamed, binary)[1]))
    }

    /**
     * F: test/goldens/kit/kit_diff_view_golden_test.dart:172 (3,200 added lines, `maxLines: 400`, `onOpenAll`).
     *
     * Mismatch: native has no "Showing 400 of 3200 lines" notice and no Open all action; it only cuts at 400 lines.
     */
    @Test fun too_big() {
        val strings = (1..3200).joinToString("\n") { "const s$it = 'string $it';" } // F: test/goldens/kit/kit_diff_view_golden_test.dart:178
        shots("too_big", content = page(DiffModel.fromBeforeAfter("lib/generated/strings.dart", null, strings), maxLines = 400))
    }

    /**
     * Mismatch: native [KitDiffView] has no loading, empty or error state (Dart: a loading placeholder;
     * KitStateView "No changes" with the checks icon; KitStateView.error "Couldn't load the changes" with the body
     * "The server stopped answering." and Try again). Each shot draws the view of an empty file, which is what the
     * native part shows with no lines.
     */
    @Test fun loading_empty_error() {
        val none = DiffModel.fromBeforeAfter("", "", "")
        shots("loading", content = page(none))
        shots("empty", content = page(none))
        shots("error", content = page(none))
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_diff_view",
            caps = mapOf("KitDiffView" to "fill", "showKitDiff" to "fill"),
            frame = FlutterGoldenGate.Frame.Screen,
            wrap = wrap,
            content = unified,
        )
    }

    private companion object {
        /** Native's full (reader) view starts above the inline cap of 12 lines; this stands for Dart's "no cap". */
        const val FULL = Int.MAX_VALUE
    }
}
