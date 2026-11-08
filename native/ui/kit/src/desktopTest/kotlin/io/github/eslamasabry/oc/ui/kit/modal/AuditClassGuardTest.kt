package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateLayout
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateView
import io.github.eslamasabry.oc.ui.kit.state.KitPageState
import io.github.eslamasabry.oc.ui.kit.state.KitStartState
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards for the three kit classes the 2026-11-09 visual audit found (docs/native/qa/audit-2026-11-09), so none comes
 * back:
 *
 * - C01: no icon sits on a tile, box or circle in a confirm, dialog, sheet header or state. Each mark is drawn, then
 *   the strips just beside the glyph must be one flat colour: a plate behind the glyph would paint them.
 * - C06: an input dialog says its validation line once (under the field, never again under the button).
 * - C10: every destructive confirm declares its kind. `KitConfirmSpec.kind` has no default (the compiler holds that);
 *   this scan holds a confirm whose title or label deletes, removes, clears, discards, forgets, revokes, resets,
 *   disconnects, signs out, stops, undoes or declines to a non-Neutral kind, so it is never the accent primary.
 */
@OptIn(ExperimentalTestApi::class)
class AuditClassGuardTest {
    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(motion = MotionLevel.Off),
            platform = FakeKitPlatform(),
            localeTag = "en",
            content = content,
        )
    }

    private fun shows(tag: String, content: @Composable () -> Unit) =
        runDesktopComposeUiTest(412, 915) {
            setContent { Theme { KitSheetHost { Box(Modifier.fillMaxSize()) { content() } } } }
            waitForIdle()
            assertBare(tag)
        }

    /** Every node tagged [tag] is a bare glyph: the strips beside it, on both sides, are one flat colour. */
    private fun ComposeUiTest.assertBare(tag: String) {
        val nodes = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty(), "the mark '$tag' is drawn")
        val pixels = onRoot().captureToImage().toPixelMap()
        val strip = (STRIP_DP * density.density).roundToInt()
        nodes.forEach { node ->
            val glyph: Rect = node.boundsInRoot
            val top = glyph.top.roundToInt().coerceAtLeast(0)
            val bottom = glyph.bottom.roundToInt().coerceAtMost(pixels.height)
            listOf(
                    (glyph.left.roundToInt() - strip) until glyph.left.roundToInt(),
                    glyph.right.roundToInt() until (glyph.right.roundToInt() + strip),
                )
                .forEach { xs ->
                    val inside = xs.filter { it in 0 until pixels.width }
                    val colours = inside.flatMap { x -> (top until bottom).map { y -> pixels[x, y] } }
                    val first = colours.first()
                    val off = colours.count { !same(it, first) }
                    assertEquals(
                        0,
                        off,
                        "'$tag' at $glyph: the ${STRIP_DP} dp beside the glyph must be flat (no tile, box or " +
                            "circle behind an icon; audit 2026-11-09 C01)",
                    )
                }
        }
    }

    private fun same(a: Color, b: Color): Boolean =
        abs(a.red - b.red) < TOLERANCE && abs(a.green - b.green) < TOLERANCE && abs(a.blue - b.blue) < TOLERANCE

    @Test
    fun `B-KIT-015 every confirm kind heads with a bare glyph, no tile`() {
        KitConfirmKind.entries.forEach { kind ->
            shows(HEADER_MARK_TAG) {
                KitConfirm(KitConfirmSpec("Delete fox?", "Delete fox", kind, body = "It goes."), onConfirm = {}, onCancel = {})
            }
        }
    }

    @Test
    fun `B-KIT-015 a sheet header icon and an alert icon are bare glyphs`() {
        KitSheetTone.entries.forEach { tone ->
            shows(HEADER_MARK_TAG) {
                KitSheet(visible = true, onDismissRequest = {}, title = "Trust this certificate?", icon = KitIcons.warning, tone = tone) {}
            }
        }
        shows(HEADER_MARK_TAG) {
            KitAlert(visible = true, title = "Server moved", onDismissRequest = {}, body = "It answers elsewhere.", icon = KitIcons.info)
        }
    }

    @Test
    fun `B-KIT-034 every state view heads with a bare glyph, page and inline`() {
        KitStateKind.entries.filter { it != KitStateKind.Calm }.forEach { kind ->
            KitStateSize.entries.forEach { size ->
                shows("kit-state-icon") {
                    KitStateView(
                        kind,
                        "Nothing here yet",
                        body = "Words.",
                        size = size,
                        failure = if (kind == KitStateKind.Error) Failure.Network() else null,
                        primary = if (kind == KitStateKind.Missing) KitAction("Turn on", onClick = {}) else null,
                        prerequisite = false,
                    )
                }
            }
        }
    }

    @Test
    fun `B-KIT-034 page, inline and start states draw their glyph bare`() {
        shows("kit-state-icon") { KitPageState(KitStateKind.Empty, "No plans", icon = KitIcons.info) }
        KitInlineStateLayout.entries.forEach { layout ->
            shows("kit-state-icon") { KitInlineState("No runs", KitIcons.info, body = "Words.", layout = layout) }
        }
        shows("kit-state-icon") { KitInlineStateView("No files", KitIcons.info, body = "Words.") }
        shows("kit-state-icon") { KitStartState("Start a chat", KitIcons.add, body = "Words.") }
    }

    @Test
    fun `B-KIT-016 every input dialog says its validation line once`() {
        val dialogs: List<@Composable () -> Unit> =
            listOf(
                {
                    KitInputDialog(true, "Send back with a note", "Your comment", "Send back", validate = { if (it.isBlank()) "Type a comment first." else null }, onResult = {})
                },
                {
                    KitInputOperationDialog(true, "Send back with a note", "Your comment", "Send back", initial = "", onSubmit = {}, onDismissRequest = {}, validate = { if (it.isBlank()) "Type a comment first." else null })
                },
                {
                    KitInputFormDialog(rememberTextFieldState(), "Send back with a note", "Your comment", "Send back", "Cancel", onSave = {}, onDismissRequest = {}, validate = { if (it.isBlank()) "Type a comment first." else null })
                },
            )
        dialogs.forEach { dialog ->
            runDesktopComposeUiTest(412, 915) {
                setContent { Theme { KitSheetHost { dialog() } } }
                waitForIdle()
                onAllNodesWithText("Type a comment first.", useUnmergedTree = true).assertCountEquals(0)
                onNode(hasSetTextAction()).performTextReplacement("x")
                onNode(hasSetTextAction()).performTextReplacement("")
                onNodeWithText("Send back").performClick()
                waitForIdle()
                onAllNodesWithText("Type a comment first.", useUnmergedTree = true).assertCountEquals(1)
            }
        }
    }

    @Test
    fun `B-KIT-015 the destructive-confirm scan finds a delete drawn as the neutral primary`() {
        val planted =
            """
            KitConfirmSpec(
                title = stringResource(Res.string.scheduleDeleteNamedTitle, name),
                confirmLabel = words.delete,
                kind = KitConfirmKind.Neutral,
            )
            KitConfirmSpec(stringResource(Res.string.nlibRemoveQuestion), stringResource(Res.string.nlibRemoveAccount), KitConfirmKind.Neutral)
            KitConfirmSpec(stringResource(Res.string.nlibRemoveQuestion), stringResource(Res.string.nlibRemoveAccount), KitConfirmKind.Destructive)
            KitConfirmSpec(title = "Open this link?", confirmLabel = "Open link", kind = if (insecure) KitConfirmKind.Destructive else KitConfirmKind.Neutral)
            KitConfirmSpec(title = "Stop the server?", confirmLabel = "Stop server", kind = KitConfirmKind.Stop)
            """.trimIndent()
        assertEquals(listOf(1, 6), neutralDestructiveConfirms(planted))
    }

    @Test
    fun `B-KIT-015 every destructive confirm in the app declares a destructive kind`() {
        val root =
            generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile && File(it, "MODULE_OWNERS").isFile }
        val sources =
            listOf("ui", "feature", "androidApp", "desktopApp").flatMap { dir ->
                File(root, dir).walkTopDown()
                    .onEnter { it.name != "build" }
                    .filter { it.isFile && it.extension == "kt" && "/src/" in it.path && "Test/" !in it.path }
                    .toList()
            }
        assertTrue(sources.size > 50, "the scan found the sources (${sources.size} files under ${root.path})")
        val found = sources.flatMap { file -> neutralDestructiveConfirms(file.readText()).map { "${file.relativeTo(root)}:$it" } }
        assertEquals(
            emptyList(),
            found,
            "A confirm that deletes, removes, clears, stops or signs out passes KitConfirmKind.Destructive, Stop or " +
                "Discard, so it is drawn in the kit's destructive style, never the accent primary (audit 2026-11-09 C10).",
        )
    }

    @Test
    fun `B-KIT-015 no confirm, dialog, sheet or state part builds an icon tile`() {
        val root =
            generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile && File(it, "MODULE_OWNERS").isFile }
        val kit = File(root, "ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit")
        val parts = listOf("modal", "state").flatMap { File(kit, it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }
        assertTrue(parts.size > 10, "the scan found the modal and state parts")
        val tile = Regex("""\b(IconTile|KitSurfaceTile)\s*\(""")
        val found =
            parts.flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line -> if (tile.containsMatchIn(line)) "${file.name}:${i + 1}" else null }
            }
        assertEquals(emptyList(), found, "Draw the glyph bare (KitGlyph / KitIcon): no icon tiles in modals or states (C01).")
    }

    private companion object {
        /** How far beside the glyph the flat check looks: less than any gap between a mark and its words. */
        const val STRIP_DP = 6f
        const val TOLERANCE = 0.02f
    }
}

private val destructiveWords =
    // A capitalised verb anywhere ("scheduleDeleteNamedTitle", "Delete fox"), or a lower-case one that does not continue
    // a word ("words.delete"), so "desktopRelease" is not a stop.
    Regex(
        """(?:Delete|Remove|Clear|Discard|SignOut|Sign out|Forget|Revoke|Reset|Disconnect|Uninstall|Erase|Wipe|Stop|Undo|Revert|Decline)""" +
            """|(?<![A-Za-z])(?:delete|remove|clear|discard|sign.?out|forget|revoke|reset|disconnect|uninstall|erase|wipe|stop|undo|revert|decline)""",
    )

/**
 * The 1-based lines of `KitConfirmSpec(` calls whose title or confirm label names a destructive act while the kind is
 * Neutral (or missing). The kind is the `kind =` argument or the third positional one; a kind expression that can be
 * Destructive, Stop or Discard passes.
 */
internal fun neutralDestructiveConfirms(source: String): List<Int> {
    val found = mutableListOf<Int>()
    var from = 0
    while (true) {
        val start = source.indexOf("KitConfirmSpec(", from)
        if (start < 0) break
        from = start + 1
        if (source.substring(maxOf(0, start - 11), start) == "data class ") continue
        val args =
            topLevelArgs(source, start + "KitConfirmSpec(".length)?.map { it.replace(Regex("""//[^\n]*"""), "") } ?: continue
        val named = args.associateNotNull { arg -> Regex("""^\s*(\w+)\s*=([\s\S]*)$""").find(arg)?.let { it.groupValues[1] to it.groupValues[2] } }
        val positional = args.takeWhile { !Regex("""^\s*\w+\s*=[^=]""").containsMatchIn(it) }
        val title = named["title"] ?: positional.getOrNull(0).orEmpty()
        val label = named["confirmLabel"] ?: positional.getOrNull(1).orEmpty()
        val kind = named["kind"] ?: positional.getOrNull(2).orEmpty()
        val strong = listOf("Destructive", "Stop", "Discard").any { it in kind }
        if (!strong && destructiveWords.containsMatchIn(title + " " + label)) found += source.substring(0, start).count { it == '\n' } + 1
    }
    return found
}

/** The call's top-level arguments, from just after its opening parenthesis; strings and nested brackets are skipped. */
private fun topLevelArgs(source: String, open: Int): List<String>? {
    val args = mutableListOf<String>()
    var depth = 1
    var i = open
    var argStart = open
    while (i < source.length) {
        when (val c = source[i]) {
            '"' -> {
                i++
                while (i < source.length && source[i] != '"') {
                    if (source[i] == '\\') i++
                    i++
                }
            }
            '(', '[', '{' -> depth++
            ')', ']', '}' -> {
                depth--
                if (depth == 0) {
                    source.substring(argStart, i).takeIf { it.isNotBlank() }?.let(args::add)
                    return args
                }
            }
            ',' -> if (depth == 1) {
                args += source.substring(argStart, i)
                argStart = i + 1
            }
            else -> if (c == '\n' && depth < 1) return null
        }
        i++
    }
    return null
}

private inline fun <T, K, V> List<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> =
    mapNotNull(transform).toMap()
