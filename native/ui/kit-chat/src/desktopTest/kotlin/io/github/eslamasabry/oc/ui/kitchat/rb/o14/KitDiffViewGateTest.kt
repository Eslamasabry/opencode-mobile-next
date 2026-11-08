package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFile
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffPosition
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
 * Mismatch: native [KitDiffView] draws one [DiffFile]; the Dart view takes the file list. Each shot draws the file the
 * Dart view opens (`initialFile`, else the first) with its [KitDiffPosition] ("· 1 of 2", "Change 1 of 5"); the wide
 * file list pane (1280x800) is not drawn.
 *
 * Mismatch: native has no "unlimited" full view: a full (reader) view is `maxLines > 12`, so the scenes without
 * `maxLines` pass [FULL]. In a bounded page the lines fill it, as in Dart.
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
    private fun page(file: DiffFile, maxLines: Int = FULL, actions: Boolean = false, position: KitDiffPosition? = null): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            KitDiffView(
                file,
                maxLines = maxLines,
                hunkLabel = { _, _, start, end -> "Lines $start–$end" },
                gapLabel = { hidden -> if (hidden == 1) "… 1 line" else "… $hidden lines" },
                modifier = Modifier.fillMaxWidth(),
                onComment = if (actions) ({ _ -> }) else null, // Dart `readOnly: false` with onComment
                onAddToPrompt = if (actions) ({ _ -> }) else null, // and onAddToPrompt: the selecting scene only
                position = position,
            )
        }
    }

    /** greeting.dart, the first of two files: four changes of the five across both (README.md has one). */
    private val pairPosition = KitDiffPosition(fileIndex = 0, fileCount = 2, changeOffset = 0, changeTotal = 5)

    private val unified: @Composable () -> Unit get() = page(files()[0], position = pairPosition)

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
     * Mismatch: native has no drag selection on the lines; the gutter taps stand in for it.
     */
    @Test fun selecting() {
        shots("selecting", act = {
            onAllNodes(hasText(" 3") and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))[1].performTouchInput { click() }
            waitForIdle()
            onAllNodes(hasText(" 5") and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))[1].performTouchInput { click() }
        }, content = page(files()[0], actions = true, position = pairPosition))
    }

    /**
     * F: test/goldens/kit/kit_diff_view_golden_test.dart:142 (`initialFile: 1`, the binary file open).
     *
     * Mismatch: no "Renamed from" line (Dart shows it in the wide file list and the phone's picker).
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
        shots("binary_renamed", content = page(listOf(renamed, binary)[1], position = KitDiffPosition(fileIndex = 1, fileCount = 2, changeTotal = 0)))
    }

    /**
     * F: test/goldens/kit/kit_diff_view_golden_test.dart:172 (3,200 added lines, `maxLines: 400`, `onOpenAll`).
     *
     */
    @Test fun too_big() {
        val strings = (1..3200).joinToString("\n") { "const s$it = 'string $it';" } // F: test/goldens/kit/kit_diff_view_golden_test.dart:178
        shots("too_big") {
            Box(Modifier.fillMaxSize()) {
                KitDiffView(
                    DiffModel.fromBeforeAfter("lib/generated/strings.dart", null, strings),
                    maxLines = 400,
                    hunkLabel = { _, _, start, end -> "Lines $start–$end" },
                    gapLabel = { hidden -> if (hidden == 1) "… 1 line" else "… $hidden lines" },
                    modifier = Modifier.fillMaxWidth(),
                    onOpenAll = {},
                )
            }
        }
    }

    /**
     * F: test/goldens/kit/kit_diff_view_golden_test.dart: `loading: true`; no files ("No changes", the checks glyph);
     * `error: 'The server stopped answering.'` with Try again.
     */
    @Test fun loading_empty_error() {
        val none = DiffFile(path = "", segments = persistentListOf(), added = 0, removed = 0)
        val labels: Pair<(Int, Int, Int, Int) -> String, (Int) -> String> = Pair({ _, _, s, e -> "Lines $s–$e" }, { "… $it lines" })
        shots("empty") { Box(Modifier.fillMaxSize()) { KitDiffView(none, FULL, labels.first, labels.second, Modifier.fillMaxWidth()) } }
        shots("error") {
            Box(Modifier.fillMaxSize()) {
                KitDiffView(none, FULL, labels.first, labels.second, Modifier.fillMaxWidth(), error = "The server stopped answering.", onRetry = {})
            }
        }
        shots("loading") { Box(Modifier.fillMaxSize()) { KitDiffView(none, FULL, labels.first, labels.second, Modifier.fillMaxWidth(), loading = true) } }
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
