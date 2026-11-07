package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test
import java.awt.Color as AwtColor

/**
 * Every scene of test/goldens/kit/kit_markdown_golden_test.dart (a reply, a streaming fence, the secondary role, a
 * validated path link, the empty reply; 412x915 and 1280x800, the reply also at text 2.0) and of
 * test/goldens/kit/kit_markdown_images_golden_test.dart (pictures in a reply, in Arabic, inline, and with no file
 * reader). Each is a pushed page (kitGalleryShot, so [FlutterGoldenGate.Frame.Screen]) with the Dart `_onGround`
 * frame: scrolling, the gutter all round, top-start, at most 700 dp wide.
 *
 * The `table` scene of the Dart gallery has no golden in 14.gate.tsv, so it has no check here.
 */
class KitMarkdownGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit = {
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) { it() }
    }

    // F: test/goldens/kit/kit_markdown_golden_test.dart:44
    private val reply =
        listOf(
            "## Reconnect after the phone sleeps",
            "",
            "The controller now **refetches** the session instead of replaying events.",
            "See [the protocol notes](https://opencode.ai/docs) for why.",
            "",
            "- `ConnectionController.resume()` reopens the stream",
            "- Missed deltas are *never* replayed",
            "- The session list refreshes once",
            "",
            "> Deltas and tool progress never replay after a reconnect.",
            "",
            "```dart",
            "await controller.resume();",
            "final sessions = await gateway.listSessions();",
            "```",
            "",
        ).joinToString("\n")

    // F: test/goldens/kit/kit_markdown_golden_test.dart:70
    private val streaming =
        listOf(
            "Here is the fix, still arriving:",
            "",
            "```kotlin",
            "class ShareReceiver : BroadcastReceiver() {",
            "    override fun onReceive(context: Context, intent: Intent) {",
            "        val text = intent.getStringExtra(Intent.EXTRA_TEXT)",
        ).joinToString("\n")

    // F: test/goldens/kit/kit_markdown_golden_test.dart:78
    private val secondary =
        listOf(
            "Checked the **three** call sites; only `chat_screen.dart` reads the old flag.",
            "The others take it from the gateway.",
        ).joinToString("\n")

    // F: test/goldens/kit/kit_markdown_golden_test.dart:82
    private val pathLink =
        "The screenshot is saved at `/tmp/opencode/shots/home.png` and the " +
            "test lives in `test/kit/kit_markdown_test.dart:42`."

    private val inlineData =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

    // F: test/goldens/kit/kit_markdown_images_golden_test.dart:46
    private val picturesReply =
        listOf(
            "I captured the three screens you asked about:",
            "",
            "![Image](/tmp/shots/home.png)",
            "![Image](/tmp/shots/settings.png)",
            "![Image](/tmp/shots/chat.png)",
            "",
            "The architecture diagram comes from the docs, so it is only named:",
            "",
            "![Architecture](https://docs.example.com/architecture.png)",
            "",
            "This one is inline data, which cannot be opened:",
            "",
            "![Inline pixel]($inlineData)",
            "",
        ).joinToString("\n")

    // F: test/goldens/kit/kit_markdown_images_golden_test.dart:62
    private val picturesReplyArabic =
        listOf(
            "التقطتُ الشاشات الثلاث التي طلبتها:",
            "",
            "![Image](/tmp/shots/home.png)",
            "![Image](/tmp/shots/settings.png)",
            "![Image](/tmp/shots/chat.png)",
            "",
            "مخطط البنية من الوثائق، لذا يُذكر اسمه فقط:",
            "",
            "![Architecture](https://docs.example.com/architecture.png)",
            "",
            "وهذه بيانات مضمّنة لا يمكن فتحها:",
            "",
            "![]($inlineData)",
            "",
        ).joinToString("\n")

    // F: test/goldens/kit/kit_markdown_images_golden_test.dart:78
    private val picturesInline =
        "Compare ![the old layout](/tmp/shots/home.png) with ![the new one](/tmp/shots/settings.png), " +
            "then read ![](https://example.com/spec.png) before you merge."

    // F: test/goldens/kit/kit_markdown_images_golden_test.dart:81
    private val picturesUnread =
        listOf(
            "The host has no way to read these, so they are named:",
            "",
            "![Image](/tmp/shots/home.png)",
            "![Home settings](/tmp/shots/settings.png)",
            "![Image](/tmp/shots/chat.png)",
        ).joinToString("\n")

    /** F: test/goldens/kit/kit_markdown_images_golden_test.dart:88, the stand-in screenshots' gradient colours. */
    private val shotColors =
        mapOf(
            "/tmp/shots/home.png" to (0xFF1E3A8A.toInt() to 0xFF60A5FA.toInt()),
            "/tmp/shots/settings.png" to (0xFF14532D.toInt() to 0xFF4ADE80.toInt()),
            "/tmp/shots/chat.png" to (0xFF581C87.toInt() to 0xFFC084FC.toInt()),
        )

    /** Dart `_screenshot`: a 240x180 gradient with a header bar and three rows, as PNG bytes. */
    private fun screenshot(top: Int, bottom: Int): ByteArray {
        val width = 240 // F: test/goldens/kit/kit_markdown_images_golden_test.dart:97
        val height = 180 // F: test/goldens/kit/kit_markdown_images_golden_test.dart:98
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = GradientPaint(0f, 0f, AwtColor(top, true), 0f, height.toFloat(), AwtColor(bottom, true))
        g.fillRect(0, 0, width, height)
        g.color = AwtColor(0xCCFFFFFF.toInt(), true) // F: test/goldens/kit/kit_markdown_images_golden_test.dart:109
        g.fillRoundRect(16, 16, 120, 18, 18, 18) // F: test/goldens/kit/kit_markdown_images_golden_test.dart:112-113
        g.color = AwtColor(0x55FFFFFF, true) // F: test/goldens/kit/kit_markdown_images_golden_test.dart:123
        for (row in 0 until 3) {
            g.fillRoundRect(16, 54 + row * 38, width - 32, 28, 16, 16) // F: test/goldens/kit/kit_markdown_images_golden_test.dart:120-121
        }
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private val shots: Map<String, ByteArray> by lazy { shotColors.mapValues { (_, c) -> screenshot(c.first, c.second) } }

    /** Dart `KitMarkdownFileLinks(validate: _readable, open: (_) {})`: every path is readable. */
    private val pathActions =
        KitMarkdownActions(
            onLink = {},
            onPath = { _, _ -> },
            confirmedPaths = persistentSetOf("/tmp/opencode/shots/home.png", "test/kit/kit_markdown_test.dart"),
        )

    private val reader: suspend (String) -> ByteArray? = { path -> shots[path] }

    /** Dart `_host(pictures)`: the screenshots validate and open; [read] false is a host with no file reader. */
    private fun pictureActions(read: Boolean) =
        KitMarkdownActions(
            onLink = {},
            confirmedPaths = persistentSetOf(*shotColors.keys.toTypedArray()),
            onPicture = {},
            openFilePictures = true,
            readPicture = reader.takeIf { read },
        )

    /** Dart `_onGround`: the gutter all round, top-start, capped at `KitLayout.paneDetailMaxWidth`. */
    private fun onGround(text: String, actions: KitMarkdownActions = KitMarkdownActions(onLink = {})): @Composable () -> Unit = {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FlutterKitTokens.GUTTER.dp), // F: test/goldens/kit/kit_markdown_golden_test.dart:28-29
            horizontalAlignment = Alignment.Start,
        ) {
            KitMarkdown(
                KitMdSource.blocks("md", text, streaming = false),
                actions,
                Modifier.widthIn(max = 700.dp), // F: test/goldens/kit/kit_markdown_golden_test.dart:34
            )
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, wrap = wrap, content = content)
    }

    private fun sizes(state: String, content: @Composable () -> Unit) {
        for (suffix in listOf("", "_1280x800")) shot("kit_markdown_$state$suffix", content)
    }

    @Test fun reply() {
        sizes("default", onGround(reply))
        shot("kit_markdown_default_text2", onGround(reply))
        sizes("streaming", onGround(streaming))
    }

    /**
     * Mismatch: native [KitMarkdown] has no text role (Dart `role: KitTextRole.secondary` draws the words in the
     * secondary tone and size); it draws the body role.
     *
     * Mismatch: native confirms paths from a known set ([KitMarkdownActions.confirmedPaths]); Dart validates each path
     * through the host (`validate`, here always true). Both paths are in the set, so both draw as links.
     */
    @Test fun roles_paths_empty() {
        sizes("secondary", onGround(secondary))
        sizes("path_link", onGround(pathLink, pathActions))
        sizes("empty", onGround(""))
    }

    @Test fun pictures() {
        shot("kit_markdown_images_reply", onGround(picturesReply, pictureActions(read = true)))
        shot("kit_markdown_images_reply_ar", onGround(picturesReplyArabic, pictureActions(read = true)))
        shot("kit_markdown_images_inline", onGround(picturesInline, pictureActions(read = true)))
        shot("kit_markdown_images_unread", onGround(picturesUnread, pictureActions(read = false)))
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_markdown",
            caps = mapOf("KitMarkdown" to "conversation"),
            frame = FlutterGoldenGate.Frame.Screen,
            wrap = wrap,
            content = onGround(reply),
        )
    }
}
