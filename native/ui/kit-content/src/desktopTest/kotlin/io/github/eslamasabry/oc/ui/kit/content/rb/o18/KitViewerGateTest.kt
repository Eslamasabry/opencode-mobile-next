package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewer
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerContent
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerKeys
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerSheet
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerSource
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerPdfPage
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import kotlinx.coroutines.awaitCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_viewer_golden_test.dart: `showKitViewer` opened from an empty page as a person
 * would from a file row — the full-height sheet on the 412 x 915 phone, the pushed page on the 1280 x 800 window — for
 * code, Markdown, image, PDF, SVG, delimited, binary, loading, error, truncated and finding, light and dark, plus the
 * code at text 2.0 at both sizes. The inputs are the Dart test's, typed as it has them.
 */
@OptIn(ExperimentalTestApi::class)
class KitViewerGateTest {
    // No blank lines (kit_viewer_golden_test.dart:24-25).
    private val dart = """
import 'package:flutter/widgets.dart';
/// Greets someone by name.
class Greeting extends StatelessWidget {
  const Greeting({super.key, required this.name});
  final String name;
  @override
  Widget build(BuildContext context) {
    final text = 'Hello, ${'$'}name. Welcome back to the project.';
    return Text(text);
  }
}
String shout(String value) => value.toUpperCase();
int count(List<String> items) {
  var total = 0;
  for (final item in items) {
    total += item.length;
  }
  return total;
}
""".trimStart('\n')

    private val readme = """
# Greeting

A small widget that says **hello** to the person using the app.

## Use it

1. Add `Greeting` to a screen.
2. Pass the person's `name`.
3. Run the tests with `flutter test`.

```dart
const Greeting(name: 'Sam');
```

Read [the widget guide](https://docs.flutter.dev/ui) for more.
""".trimStart('\n')

    private val svg = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 80" width="240" height="160">
  <rect x="4" y="4" width="112" height="72" rx="10" fill="#2f6f73"/>
  <circle cx="40" cy="40" r="20" fill="#f2c14e"/>
  <path d="M70 58 L88 26 L106 58 Z" fill="#e8eef0"/>
</svg>
""".trimStart('\n')

    private fun hsv(hue: Float, saturation: Float, value: Float): Color {
        val c = value * saturation
        val h = hue / 60f
        val x = c * (1 - kotlin.math.abs(h % 2 - 1))
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = value - c
        fun ch(v: Float) = kotlin.math.round((v + m) * 255).toInt()
        return Color(ch(r), ch(g), ch(b))
    }

    private fun draw(width: Int, height: Int, paint: (Canvas) -> Unit): ByteArray {
        val bitmap = ImageBitmap(width, height)
        paint(Canvas(bitmap))
        return Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    // The Dart test's deterministic bitmaps (kit_viewer_golden_test.dart:87-121).
    private val photo: ByteArray by lazy {
        draw(480, 320) { canvas ->
            canvas.drawRect(Rect(0f, 0f, 480f, 320f), Paint().apply { color = hsv(200f, .35f, .9f) })
            canvas.drawCircle(Offset(360f, 90f), 44f, Paint().apply { color = hsv(45f, .7f, 1f) })
            val hills = Path().apply {
                moveTo(0f, 320f); lineTo(0f, 220f); quadraticTo(120f, 150f, 240f, 220f)
                quadraticTo(360f, 290f, 480f, 200f); lineTo(480f, 320f); close()
            }
            canvas.drawPath(hills, Paint().apply { color = hsv(140f, .5f, .55f) })
        }
    }

    private fun page(lines: Int): ByteArray = draw(420, 594) { canvas ->
        canvas.drawRect(Rect(0f, 0f, 420f, 594f), Paint().apply { color = hsv(0f, 0f, 1f) })
        val ink = Paint().apply { color = hsv(0f, 0f, .72f) }
        canvas.drawRect(Rect(40f, 48f, 260f, 66f), ink)
        for (i in 0 until lines) canvas.drawRect(Rect(40f, 100f + i * 22f, 40f + 340f - (i % 4) * 36f, 108f + i * 22f), ink)
    }

    private val page1 by lazy { page(20) }
    private val page2 by lazy { page(12) }

    private fun table(): List<List<String>> = listOf(listOf("Name", "Role", "City", "Joined")) +
        (0 until 30).map { i ->
            listOf(
                "Person ${i + 1}",
                listOf("Engineer", "Designer", "Writer", "Researcher")[i % 4],
                listOf("Cairo", "Lisbon", "Osaka", "Toronto", "Nairobi")[i % 5],
                "2026-0${i % 9 + 1}-1${i % 9}",
            )
        }

    private val code get() = KitViewerSource(KitViewerContent.Code(dart, language = "dart"))

    private class Scene(val name: String, val path: String, val source: KitViewerSource, val primary: Boolean = true)

    private val scenes: Map<String, () -> Scene> = linkedMapOf(
        "code" to { Scene("greeting.dart", "/home/sam/app/lib/ui/greeting.dart", code) },
        "markdown" to { Scene("README.md", "/home/sam/app/README.md", KitViewerSource(KitViewerContent.Markdown(readme))) },
        "image" to { Scene("hills.png", "/home/sam/app/assets/hills.png", KitViewerSource(KitViewerContent.Image(photo))) },
        "pdf" to {
            Scene(
                "Report.pdf", "/home/sam/app/docs/Report.pdf",
                KitViewerSource(KitViewerContent.Pdf(2, { index, _ -> ViewerPdfPage(if (index == 0) page1 else page2, 420, 594) })),
            )
        },
        "svg" to { Scene("logo.svg", "/home/sam/app/assets/logo.svg", KitViewerSource(KitViewerContent.Svg(svg, original = svg))) },
        "delimited" to { Scene("people.csv", "/home/sam/app/data/people.csv", KitViewerSource(KitViewerContent.Delimited(table()))) },
        "binary" to {
            Scene(
                "release.zip", "/home/sam/app/build/release.zip",
                KitViewerSource(KitViewerContent.Binary(mimeType = "application/zip", byteLength = 2516582)), primary = false,
            )
        },
        "loading" to { Scene("server.log", "/home/sam/app/logs/server.log", KitViewerSource.load { awaitCancellation() }) },
        "error" to {
            Scene("server.log", "/home/sam/app/logs/server.log", KitViewerSource.load { error("The server stopped answering.") })
        },
        "truncated" to {
            Scene(
                "strings.dart", "/home/sam/app/lib/generated/strings.dart",
                KitViewerSource(
                    KitViewerContent.Code(
                        (1..80).joinToString("\n") { "const s$it = 'string $it';" },
                        language = "dart", truncated = true, totalLines = 5210,
                    ),
                ),
            )
        },
        "finding" to { Scene("greeting.dart", "/home/sam/app/lib/ui/greeting.dart", code) },
    )

    /** `showKitViewer`: the sheet on a compact window, the page from expanded up (kit_viewer.dart:60-90). */
    private fun opened(scene: Scene, wide: Boolean): @Composable () -> Unit = {
        val viewer: @Composable () -> Unit = {
            KitViewer(
                name = scene.name,
                source = scene.source,
                path = scene.path,
                primary = if (scene.primary) KitAction("Add to prompt", {}) else null,
                more = listOf(KitMenuItem("Copy path", {})),
                onClose = {},
                onOpenAll = {},
            )
        }
        if (wide) viewer() else KitViewerSheet(onDismiss = {}) { viewer() }
    }

    private val find: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {
        onNodeWithTag(KitViewerKeys.MORE).performClick()
        waitForIdle()
        onNodeWithText("Find in file").performClick()
        waitForIdle()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(KitViewerKeys.FIND))).performTextInput("name")
        mainClock.advanceTimeBy(1_000) // Dart pumps KitMotion.typingSettle before tapping Next
        waitForIdle()
        onNodeWithTag(KitViewerKeys.FIND_NEXT).performClick()
    }

    private fun shot(state: String, size: String) {
        val wide = size == "_1280x800"
        FlutterGoldenGate.check(
            "kit_viewer_$state$size",
            Frame.Screen,
            act = if (state == "finding") find else null,
            content = opened(scenes.getValue(state)(), wide),
        )
    }

    @Test fun code() { shot("code", ""); shot("code", "_1280x800") }
    @Test fun markdown() { shot("markdown", ""); shot("markdown", "_1280x800") }
    @Test fun image() { shot("image", ""); shot("image", "_1280x800") }
    @Test fun pdf() { shot("pdf", ""); shot("pdf", "_1280x800") }
    @Test fun svg() { shot("svg", ""); shot("svg", "_1280x800") }
    @Test fun delimited() { shot("delimited", ""); shot("delimited", "_1280x800") }
    @Test fun binary() { shot("binary", ""); shot("binary", "_1280x800") }
    @Test fun loading() { shot("loading", ""); shot("loading", "_1280x800") }
    @Test fun error() { shot("error", ""); shot("error", "_1280x800") }
    @Test fun truncated() { shot("truncated", ""); shot("truncated", "_1280x800") }
    @Test fun finding() { shot("finding", ""); shot("finding", "_1280x800") }

    @Test fun code_text2() {
        for (size in listOf("", "_1280x800")) {
            FlutterGoldenGate.check("kit_viewer_code_text2$size", Frame.Screen, content = opened(scenes.getValue("code")(), size.isNotEmpty()))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_viewer",
            caps = mapOf("KitViewer" to "fill", "showKitViewer" to "fill"),
            frame = Frame.Screen,
            content = { opened(scenes.getValue("code")(), wide = false)() },
        )
    }
}
