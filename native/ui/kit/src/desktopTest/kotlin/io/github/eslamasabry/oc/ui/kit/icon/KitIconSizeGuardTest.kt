package io.github.eslamasabry.oc.ui.kit.icon

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P-101: KitIcon draws its font glyph at the size it is given. A host that squeezes it with `Modifier.size` (or
 * `requiredSize`, `width`, `height`) gets a glyph cropped on the end and bottom edges: the failed mark showed half a
 * circle, the stopped mark half a ring. The size goes through `size = KitIconSize.X`; this check holds every source
 * file under `ui/` and `feature/` to that.
 */
@OptIn(ExperimentalTestApi::class)
class KitIconSizeGuardTest {
    @Test
    fun `B-KIT-101 the tiny size is 16 dp and the glyph box is exactly that`() {
        assertEquals(16, KitIconSize.Tiny.dp)
        runDesktopComposeUiTest(200, 200) {
            setContent { KitTheme(platform = FakeKitPlatform(), localeTag = "en") { KitIcon(KitIcons.error, Modifier.testTag("icon"), size = KitIconSize.Tiny) } }
            val bounds = onNodeWithTag("icon").getBoundsInRoot()
            assertEquals(16.dp, bounds.right - bounds.left)
            assertEquals(16.dp, bounds.bottom - bounds.top)
        }
    }

    @Test
    fun `B-KIT-101 the guard finds a KitIcon sized by its modifier`() {
        val planted =
            """
            KitIcon(KitIcons.warning, Modifier.size(iconSize), tone = TextTone.Secondary)
            KitStatusIcon(
                status,
                modifier = Modifier.padding(2.dp).requiredSize(16.dp),
            )
            KitIcon(mark, Modifier.padding(start = 4.dp), size = KitIconSize.Tiny)
            Box(Modifier.size(16.dp)) { KitIcon(mark, size = KitIconSize.Tiny) }
            KitIcon(KitIcons.add, Modifier.width(12.dp))
            """.trimIndent()
        assertEquals(listOf(1, 2, 8), kitIconsSizedByModifier(planted))
    }

    @Test
    fun `B-KIT-101 no screen or kit part sizes a KitIcon with a modifier`() {
        val root = generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile && File(it, "MODULE_OWNERS").isFile }
        val sources =
            listOf("ui", "feature").flatMap { dir ->
                File(root, dir).walkTopDown()
                    .onEnter { it.name != "build" }
                    .filter { it.isFile && it.extension == "kt" && "/src/" in it.path && "Test/" !in it.path }
                    .toList()
            }
        assertTrue(sources.size > 50, "the scan found the sources (${sources.size} files under ${root.path})")
        val found =
            sources.flatMap { file ->
                kitIconsSizedByModifier(file.readText()).map { "${file.relativeTo(root)}:$it" }
            }
        assertEquals(
            emptyList(),
            found,
            "Pass KitIcon's size = KitIconSize.X instead of Modifier.size: a sized modifier crops the glyph (P-101).",
        )
    }
}

private val iconCall = Regex("""\bKit(?:Status)?Icon\(""")
private val sizing = Regex("""\.(?:size|requiredSize|width|height|requiredWidth|requiredHeight|sizeIn)\(""")

/** The 1-based lines where a `KitIcon(` or `KitStatusIcon(` call carries a sizing modifier in its own arguments. */
internal fun kitIconsSizedByModifier(source: String): List<Int> =
    iconCall.findAll(source).mapNotNull { match ->
        val open = match.range.last
        var depth = 0
        var end = open
        while (end < source.length) {
            when (source[end]) {
                '(' -> depth++
                ')' -> if (--depth == 0) break
            }
            end++
        }
        val args = source.substring(open + 1, end.coerceAtMost(source.length))
        // Only the call's own arguments: a lambda's body is not this icon's.
        val own = args.substringBefore('{')
        if ("Modifier" in own && sizing.containsMatchIn(own)) source.substring(0, match.range.first).count { it == '\n' } + 1
        else null
    }.toList()
