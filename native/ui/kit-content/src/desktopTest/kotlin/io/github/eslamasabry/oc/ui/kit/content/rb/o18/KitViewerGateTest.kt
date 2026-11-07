package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerContent
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerModal
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerSource
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerPdfPage
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import kotlinx.coroutines.awaitCancellation
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * test/goldens/kit/kit_viewer_golden_test.dart: the viewer opened through showKitViewer (the sheet at 412 x 915, the
 * page at 1280 x 800) in each declared state, light and dark, and the code state at text 2.0.
 */
class KitViewerGateTest {
    private companion object {
        // No blank lines (kit_viewer_golden_test.dart:24-46).
        const val DART = """import 'package:flutter/widgets.dart';
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
"""

        const val README = """# Greeting

A small widget that says **hello** to the person using the app.

## Use it

1. Add `Greeting` to a screen.
2. Pass the person's `name`.
3. Run the tests with `flutter test`.

```dart
const Greeting(name: 'Sam');
```

Read [the widget guide](https://docs.flutter.dev/ui) for more.
"""

        const val SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 80" width="240" height="160">
  <rect x="4" y="4" width="112" height="72" rx="10" fill="#2f6f73"/>
  <circle cx="40" cy="40" r="20" fill="#f2c14e"/>
  <path d="M70 58 L88 26 L106 58 Z" fill="#e8eef0"/>
</svg>
"""

        fun hsv(hue: Float, saturation: Float, value: Float): Color = Color(Color.HSBtoRGB(hue / 360f, saturation, value))

        fun draw(width: Int, height: Int, paint: (java.awt.Graphics2D) -> Unit): ByteArray {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paint(g)
            g.dispose()
            return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        }

        /** The illustration (kit_viewer_golden_test.dart:93-111). */
        val photo: ByteArray by lazy {
            draw(480, 320) { g ->
                g.color = hsv(200f, .35f, .9f)
                g.fillRect(0, 0, 480, 320)
                g.color = hsv(45f, .7f, 1f)
                g.fill(java.awt.geom.Ellipse2D.Double(360.0 - 44, 90.0 - 44, 88.0, 88.0))
                val hills = Path2D.Double().apply {
                    moveTo(0.0, 320.0)
                    lineTo(0.0, 220.0)
                    quadTo(120.0, 150.0, 240.0, 220.0)
                    quadTo(360.0, 290.0, 480.0, 200.0)
                    lineTo(480.0, 320.0)
                    closePath()
                }
                g.color = hsv(140f, .5f, .55f)
                g.fill(hills)
            }
        }

        /** A PDF page of [lines] grey bars (kit_viewer_golden_test.dart:112-123). */
        fun page(lines: Int): ByteArray =
            draw(420, 594) { g ->
                g.color = hsv(0f, 0f, 1f)
                g.fillRect(0, 0, 420, 594)
                g.color = hsv(0f, 0f, .72f)
                g.fill(java.awt.geom.Rectangle2D.Double(40.0, 48.0, 220.0, 18.0))
                for (i in 0 until lines) {
                    val width = 340.0 - (i % 4) * 36
                    g.fill(java.awt.geom.Rectangle2D.Double(40.0, 100 + i * 22.0, width, 8.0))
                }
            }

        val page1: ByteArray by lazy { page(20) }
        val page2: ByteArray by lazy { page(12) }

        fun table(): List<List<String>> = buildList {
            add(listOf("Name", "Role", "City", "Joined"))
            for (i in 0 until 30) {
                add(
                    listOf(
                        "Person ${i + 1}",
                        listOf("Engineer", "Designer", "Writer", "Researcher")[i % 4],
                        listOf("Cairo", "Lisbon", "Osaka", "Toronto", "Nairobi")[i % 5],
                        "2026-0${i % 9 + 1}-1${i % 9}",
                    ),
                )
            }
        }
    }

    private val code = KitViewerSource(KitViewerContent.Code(DART, language = "dart"))

    /** Opens the viewer as a person would from a file row (`_open`). */
    private fun open(
        name: String,
        source: KitViewerSource,
        path: String? = null,
        primary: Boolean = true,
        load: (suspend () -> KitViewerContent)? = null,
    ): @Composable () -> Unit = {
        KitViewerModal(
            visible = true,
            onDismissRequest = {},
            name = name,
            source = source,
            path = path,
            primary = if (primary) KitAction("Add to prompt", onClick = {}) else null,
            more = listOf(KitMenuItem("Copy path", {}, KitIcons.copy)),
            onOpenAll = {},
            load = load,
        )
    }

    private val states: Map<String, @Composable () -> Unit> by lazy {
        val loading: suspend () -> KitViewerContent = { awaitCancellation() }
        val failing: suspend () -> KitViewerContent = { error("The server stopped answering.") }
        mapOf(
            "code" to open("greeting.dart", code, "/home/sam/app/lib/ui/greeting.dart"),
            "markdown" to open("README.md", KitViewerSource(KitViewerContent.Markdown(README)), "/home/sam/app/README.md"),
            "image" to open("hills.png", KitViewerSource(KitViewerContent.Image(photo)), "/home/sam/app/assets/hills.png"),
            "pdf" to open(
                "Report.pdf",
                KitViewerSource(
                    KitViewerContent.Pdf(pageCount = 2, renderPage = { index, _ -> ViewerPdfPage(if (index == 0) page1 else page2, 420, 594) }),
                ),
                "/home/sam/app/docs/Report.pdf",
            ),
            "svg" to open("logo.svg", KitViewerSource(KitViewerContent.Svg(SVG, original = SVG)), "/home/sam/app/assets/logo.svg"),
            "delimited" to open("people.csv", KitViewerSource(KitViewerContent.Delimited(table())), "/home/sam/app/data/people.csv"),
            "binary" to open(
                "release.zip",
                KitViewerSource(KitViewerContent.Binary(mimeType = "application/zip", byteLength = 2516582)),
                "/home/sam/app/build/release.zip",
                primary = false,
            ),
            "loading" to open("server.log", KitViewerSource.load(loading), "/home/sam/app/logs/server.log", load = loading),
            "error" to open("server.log", KitViewerSource.load(failing), "/home/sam/app/logs/server.log", load = failing),
            "truncated" to open(
                "strings.dart",
                KitViewerSource(
                    KitViewerContent.Code(
                        (1..80).joinToString("\n") { "const s$it = 'string $it';" },
                        language = "dart",
                        truncated = true,
                        totalLines = 5210,
                    ),
                ),
                "/home/sam/app/lib/generated/strings.dart",
            ),
            "finding" to open("greeting.dart", code, "/home/sam/app/lib/ui/greeting.dart"),
        )
    }

    /** The finding scene's taps: More, Find in file, type "name", Next (kit_viewer_golden_test.dart:298-310). */
    private val finding: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {
        onNodeWithContentDescription("More").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Find in file").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("name")
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        // A tap, as the Dart test's tester.tap: no mouse left hovering over the button (no hover wash, no tooltip).
        onNodeWithContentDescription("Next match").performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun shot(state: String, size: String = "") {
        val name = "kit_viewer_$state$size"
        FlutterGoldenGate.check(name, Frame.Screen, act = if (state == "finding") finding else null) { states.getValue(state)() }
    }

    @Test fun kit_viewer_code() = shot("code")
    @Test fun kit_viewer_code_1280x800() = shot("code", "_1280x800")
    @Test fun kit_viewer_markdown() = shot("markdown")
    @Test fun kit_viewer_markdown_1280x800() = shot("markdown", "_1280x800")
    @Test fun kit_viewer_image() = shot("image")
    @Test fun kit_viewer_image_1280x800() = shot("image", "_1280x800")
    @Test fun kit_viewer_pdf() = shot("pdf")
    @Test fun kit_viewer_pdf_1280x800() = shot("pdf", "_1280x800")
    @Test fun kit_viewer_svg() = shot("svg")
    @Test fun kit_viewer_svg_1280x800() = shot("svg", "_1280x800")
    @Test fun kit_viewer_delimited() = shot("delimited")
    @Test fun kit_viewer_delimited_1280x800() = shot("delimited", "_1280x800")
    @Test fun kit_viewer_binary() = shot("binary")
    @Test fun kit_viewer_binary_1280x800() = shot("binary", "_1280x800")
    @Test fun kit_viewer_loading() = shot("loading")
    @Test fun kit_viewer_loading_1280x800() = shot("loading", "_1280x800")
    @Test fun kit_viewer_error() = shot("error")
    @Test fun kit_viewer_error_1280x800() = shot("error", "_1280x800")
    @Test fun kit_viewer_truncated() = shot("truncated")
    @Test fun kit_viewer_truncated_1280x800() = shot("truncated", "_1280x800")
    @Test fun kit_viewer_finding() = shot("finding")
    @Test fun kit_viewer_finding_1280x800() = shot("finding", "_1280x800")
    @Test fun kit_viewer_code_text2() = shot("code", "_text2")
    @Test fun kit_viewer_code_text2_1280x800() = shot("code", "_text2_1280x800")

    @Test
    fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_viewer",
            caps = mapOf("KitViewer" to "fill", "showKitViewer" to "fill"),
            frame = Frame.Screen,
        ) { states.getValue("code")() }
    }

    @Test
    fun matrixBodies() {
        FlutterGoldenGate.matrix("part-kit_viewer_bodies", frame = Frame.Screen) { states.getValue("delimited")() }
    }
}
