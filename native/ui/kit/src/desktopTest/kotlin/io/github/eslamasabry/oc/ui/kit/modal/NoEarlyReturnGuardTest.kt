package io.github.eslamasabry.oc.ui.kit.modal

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guard for the field crash `ArrayIndexOutOfBoundsException` at `IntStack.peek2` (GapComposer.endToMarker): a
 * composable content lambda left half way with a labelled return (`return@Column`, `return@CompositionLocalProvider`,
 * `return@KitSheet`) makes the Compose compiler unwind the open groups by marker, and when lazy prefetch composes the
 * row in pieces that unwinding ran past the end of its stack. Production code draws with if / else instead.
 */
class NoEarlyReturnGuardTest {
    @Test
    fun `C20 the scan finds a labelled return out of a composable content block`() {
        val planted =
            """
            Column {
                if (a) return@Column
                if (b) return@Column // a comment, still code before it
                // return@Column in a comment is not code
                val x = remember { calc { return@calc 1 } }
                items(list) { if (it == null) return@items }
                KitSheet(visible = true) { shown ?: return@KitSheet }
                LazyColumn { if (empty) return@LazyColumn }
                list.forEach { if (it == 0) return@forEach }
            }
            """.trimIndent()
        assertEquals(
            listOf(2 to "Column", 3 to "Column", 6 to "items", 7 to "KitSheet"),
            composableContentReturns(planted),
        )
    }

    @Test
    fun `C20 no production composable leaves a content block with a labelled return`() {
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
        val found =
            sources.flatMap { file ->
                val name = file.name
                composableContentReturns(file.readText())
                    .filterNot { (_, label) -> (name to label) in ALLOWED }
                    .map { (line, label) -> "${file.relativeTo(root)}:$line return@$label" }
            }
        assertEquals(
            emptyList(),
            found,
            "A labelled return out of a composable content block (Column, Row, Box, item, KitSheet, KitScreen ...) " +
                "makes the Compose compiler unwind the open groups by marker, which crashed a list row composed in " +
                "pieces by lazy prefetch (IntStack.peek2 in GapComposer.endToMarker). Write if / else around the " +
                "rest of the block instead: `if (x == null) { ... } else { rest }`, or lift the condition above " +
                "the block. If the label is not a composable lambda, add the file and label to ALLOWED with the reason.",
        )
    }

    private companion object {
        /** File and label pairs that are not composable content, each with why. */
        val ALLOWED: Set<Pair<String, String>> =
            setOf(
                // KitLiquidBackdrop's trailing lambda is a draw lambda (DrawScope.(LayoutCoordinates) -> Unit), not composable.
                "KitLiquidBody.kt" to "KitLiquidBackdrop",
                "KitLiquidGlass.kt" to "KitLiquidBackdrop",
                "KitGlassLookPicker.kt" to "KitLiquidBackdrop",
                // KitConfirm's onConfirm is a click callback, not a composable lambda.
                "ChatInboxSlot.kt" to "KitConfirm",
            )
    }
}

private val CONTENT_LABEL =
    Regex(
        "return@(Column|Row|Box|BoxWithConstraints|CompositionLocalProvider|item|items|itemsIndexed|stickyHeader|" +
            "AnimatedVisibility|AnimatedContent|Crossfade|key|FlowRow|Surface|Kit[A-Z][A-Za-z0-9]*)\\b",
    )

/** Line numbers (1-based) and labels of the labelled returns in [source] whose label is a composable content block. */
internal fun composableContentReturns(source: String): List<Pair<Int, String>> =
    source.lines().mapIndexedNotNull { index, line ->
        val code = line.substringBefore("//")
        if (code.trimStart().startsWith("*") || code.trimStart().startsWith("/*")) return@mapIndexedNotNull null
        CONTENT_LABEL.find(code)?.let { (index + 1) to it.groupValues[1] }
    }
