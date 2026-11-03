package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The large-text overflow matrix (Flutter `test/text_scale_overflow_test.dart` G6, B-KIT-060): every public kit part
 * has a scene in [kitOverflowScenes], every scene names only real parts, and at the largest text size on a 320 dp
 * phone no scene draws outside its window, except the recorded [ceiling], which may only shrink.
 */
@OptIn(ExperimentalTestApi::class)
class KitOverflowMatrixTest {
    /** Scenes allowed to reach past the window today, with what does. Recorded 2026-11-03; never add to it. */
    private val ceiling: Map<String, String> = emptyMap()

    private val kitSources: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "ui/kit/src/commonMain/kotlin") }
        .plus(generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "src/commonMain/kotlin") })
        .first { File(it, "io/github/eslamasabry/oc/ui/kit/KitText.kt").isFile }

    /** Public composable functions named `Kit…`: the parts a feature can place. */
    private fun manifest(): Set<String> {
        val part = Regex("""@Composable\s+(?:@\w+(?:\([^)]*\))?\s+)*public fun (?:<[^>]+>\s*)?(Kit[A-Z]\w*)\s*\(""")
        return kitSources.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> part.findAll(f.readText()).map { it.groupValues[1] } }.toSet()
    }

    @Test
    fun `B-KIT-060 every kit part has an overflow scene and every scene a part`() {
        val parts = manifest()
        assertTrue(parts.size > 60, "the manifest read the kit: ${parts.size} parts")
        val covered = kitOverflowScenes.flatMap { it.parts }.toSet()
        val missing = (parts - covered).sorted()
        val stale = (covered - parts).sorted()
        assertEquals(emptyList(), missing, "kit parts with no scene in KitOverflowScenes.kt (add one at its end)")
        assertEquals(emptyList(), stale, "scenes naming parts the kit no longer has")
        val ids = kitOverflowScenes.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate scene ids")
    }

    @Test
    fun `B-KIT-060 the manifest finds generic, annotated and plain parts and skips internal ones`() {
        val dir = kotlin.io.path.createTempDirectory("kit-manifest").toFile()
        try {
            File(dir, "io/github/eslamasabry/oc/ui/kit").mkdirs()
            File(dir, "io/github/eslamasabry/oc/ui/kit/KitText.kt").writeText(
                """
                @Composable
                public fun KitPlain(text: String) {}
                @Composable
                @NonRestartableComposable
                public fun KitAnnotated() {}
                @Composable
                public fun <T> KitGeneric(value: T) {}
                @Composable
                internal fun KitInternal() {}
                public fun KitNotComposable(): Int = 1
                """.trimIndent(),
            )
            val part = Regex("""@Composable\s+(?:@\w+(?:\([^)]*\))?\s+)*public fun (?:<[^>]+>\s*)?(Kit[A-Z]\w*)\s*\(""")
            val found = part.findAll(File(dir, "io/github/eslamasabry/oc/ui/kit/KitText.kt").readText()).map { it.groupValues[1] }.toSet()
            assertEquals(setOf("KitPlain", "KitAnnotated", "KitGeneric"), found)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `B-KIT-060 at the largest text no scene draws outside a 320 dp phone, and the ceiling only shrinks`() =
        runComposeUiTest {
            val owner = object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = NavigationEventDispatcher()
            }
            val shown = mutableStateOf(kitOverflowScenes.first())
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 2f),
                    LocalNavigationEventDispatcherOwner provides owner,
                    LocalKitSecretSink provides KitSecretSink {},
                ) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        InWindow(320, 1600) { shown.value.content() }
                    }
                }
            }
            val overflowing = mutableMapOf<String, String>()
            for (scene in kitOverflowScenes) {
                shown.value = scene
                waitForIdle()
                val out = outside(onNodeWithTag("window", useUnmergedTree = true).fetchSemanticsNode())
                if (out.isNotEmpty()) overflowing[scene.id] = out.take(3).joinToString("; ")
            }
            val worse = overflowing.keys - ceiling.keys
            assertTrue(worse.isEmpty(), "These scenes overflow at the largest text; fix the part: " + worse.associateWith { overflowing[it] })
            val fixed = ceiling.keys - overflowing.keys
            assertTrue(fixed.isEmpty(), "These scenes no longer overflow; remove them from the ceiling: $fixed")
            assertTrue((ceiling.keys - kitOverflowScenes.map { it.id }.toSet()).isEmpty(), "the ceiling names unknown scenes")
        }

    /** Every node under [window] that reaches past its left or right edge. */
    private fun outside(window: SemanticsNode): List<String> {
        val edge = window.boundsInRoot
        val out = mutableListOf<String>()
        fun visit(node: SemanticsNode) {
            val r = node.boundsInRoot
            if (r.width > 0 && (r.left < edge.left - 0.5f || r.right > edge.right + 0.5f)) out += "node ${node.id} $r"
            node.children.forEach(::visit)
        }
        window.children.forEach(::visit)
        return out
    }

    @Test
    fun `B-KIT-060 the overflow check catches a part wider than the phone`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    InWindow(320, 400) {
                        androidx.compose.foundation.layout.Row(
                            androidx.compose.ui.Modifier.wrapContentWidth(unbounded = true, align = androidx.compose.ui.Alignment.Start),
                        ) { KitText("A line that will not wrap past the phone", maxLines = 1); KitButton("Send now please", onClick = {}) }
                    }
                }
            }
        }
        assertTrue(outside(onNodeWithTag("window", useUnmergedTree = true).fetchSemanticsNode()).isNotEmpty())
    }
}
