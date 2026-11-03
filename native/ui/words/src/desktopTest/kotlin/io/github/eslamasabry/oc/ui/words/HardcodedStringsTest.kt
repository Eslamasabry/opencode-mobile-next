package io.github.eslamasabry.oc.ui.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ratchet against words written straight into screens and kit parts (Flutter `test/l10n_coverage_test.dart`,
 * B-L10N-007). Every person-visible word comes from `:ui:words`; this scan counts the string literals a translator
 * would need in Compose text slots of every feature and kit module, per file, and fails when a file holds more than
 * [baseline] allows. Move a literal into a `strings_*.xml` and lower (or drop) its entry; never raise one.
 */
class HardcodedStringsTest {
    /** Recorded 2026-11-03. Only decrease these numbers. */
    private val baseline: Map<String, Int> = mapOf(
        // Seam descriptions (why a timer exists) a developer reads, not screen copy; they belong in KDoc.
        "feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/presentation/LinuxSeams.kt" to 1,
        "feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/PhoneTimers.kt" to 2,
    )

    @Test
    fun `B-L10N-007 the detector finds literal words in text slots and ignores ids, symbols and comments`() {
        assertEquals(1, HardcodedStrings.count("""KitText("Send")"""))
        assertEquals(1, HardcodedStrings.count("""Text("Send now")"""))
        assertEquals(1, HardcodedStrings.count("Text(\n    \"Send\",\n)"))
        assertEquals(1, HardcodedStrings.count("""KitButton(label = "Ask anything", onClick = {})"""))
        assertEquals(1, HardcodedStrings.count("""Modifier.semantics { contentDescription = "Attach" }"""))
        assertEquals(1, HardcodedStrings.count("""KitRow(title = "Open settings")"""))
        assertEquals(0, HardcodedStrings.count("""KitText("·")"""), "no letters")
        assertEquals(0, HardcodedStrings.count("""KitText("Ctrl+Enter")"""), "shortcut")
        assertEquals(0, HardcodedStrings.count("""KitText("anthropic/claude")"""), "id")
        assertEquals(0, HardcodedStrings.count("""KitText("${'$'}count")"""), "interpolation only")
        assertEquals(1, HardcodedStrings.count("""KitText("${'$'}count files")"""))
        assertEquals(0, HardcodedStrings.count("""KitText("+${'$'}{d.added}  −${'$'}{d.removed}")"""), "numbers only")
        assertEquals(1, HardcodedStrings.count("""KitText("${'$'}{d.added} added")"""))
        assertEquals(0, HardcodedStrings.count("""// KitText("comment")"""))
        assertEquals(0, HardcodedStrings.count(""" * KitText("in KDoc")"""))
        assertEquals(0, HardcodedStrings.count("""KitText(stringResource(Res.string.kitComposerSend))"""))
        assertEquals(0, HardcodedStrings.count("""animateColorAsState(c, spec, label = "kit-button-fill")"""), "debug label")
        assertEquals(0, HardcodedStrings.count("""rememberInfiniteTransition(label = "sweep")"""), "debug label")
    }

    @Test
    fun `B-L10N-007 no feature or kit file gained hardcoded words, and the recorded list only shrinks`() {
        val root = NativeTree.root
        val current = HardcodedStrings.scan(root)
        assertTrue(current.isNotEmpty() || baseline.isEmpty(), "the scan read no sources from $root")
        val regressions = current.filter { (path, n) -> n > (baseline[path] ?: 0) }
            .map { (path, n) -> "$path: $n hardcoded strings, baseline ${baseline[path] ?: 0}" }
        assertTrue(regressions.isEmpty(), "Move these words into :ui:words:\n" + regressions.joinToString("\n"))
        val improved = baseline.filter { (path, n) -> (current[path] ?: 0) < n }
            .map { (path, n) -> "$path: ${current[path] ?: 0} (baseline $n)" }
        assertTrue(improved.isEmpty(), "Lower the baseline in HardcodedStringsTest:\n" + improved.joinToString("\n"))
        assertTrue(HardcodedStrings.sources(root).size > 200, "the scan must cover every feature and kit module")
    }
}

/** The scanner itself (test-only: it reads the source tree). */
internal object HardcodedStrings {
    private const val LITERAL = """"((?:[^"\\\n]|\\.)*)""""
    private val textCall = Regex("""\b(?:Text|BasicText|KitText|KitFrameText)\(\s*$LITERAL""")
    private val namedSlot = Regex(
        """\b(?:text|label|title|subtitle|contentDescription|stateDescription|placeholder|hint|helper|supporting|""" +
            """reason|message|tooltip|confirmLabel|cancelLabel|actionLabel)\s*=\s*$LITERAL""",
    )
    private val letter = Regex("[A-Za-z]")
    private val identifierLike = Regex("""^\S*[/._+:]\S*$""")
    private val debugLabel = Regex("""^[a-z0-9]+(?:-[a-z0-9]+)*$""")
    private val interpolationOnly = Regex("""^\$\{?[A-Za-z_][A-Za-z0-9_.]*}?$""")
    private val interpolation = Regex("""\$(?:\{[^}]*}|[A-Za-z_][A-Za-z0-9_]*)""")

    fun translatable(literal: String): Boolean {
        if (!letter.containsMatchIn(literal.replace(interpolation, ""))) return false
        if (identifierLike.matches(literal) || debugLabel.matches(literal)) return false
        return !interpolationOnly.matches(literal)
    }

    private fun stripComments(source: String): String = source.lineSequence()
        .filterNot { line -> line.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") } }
        .joinToString("\n")

    fun count(source: String): Int {
        val code = stripComments(source)
        return (textCall.findAll(code) + namedSlot.findAll(code)).count { translatable(it.groupValues[1]) }
    }

    /** Main sources of every feature module and every kit module (`ui/kit*`). */
    fun sources(root: File): List<File> {
        val modules = (File(root, "feature").listFiles().orEmpty().toList() +
            File(root, "ui").listFiles().orEmpty().filter { it.name.startsWith("kit") })
        return modules.flatMap { module ->
            File(module, "src").listFiles().orEmpty()
                .filter { it.isDirectory && it.name.endsWith("Main") }
                .flatMap { set -> set.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        }.sortedBy { it.path }
    }

    fun scan(root: File): Map<String, Int> = sources(root)
        .associate { it.relativeTo(root).invariantSeparatorsPath to count(it.readText()) }
        .filterValues { it > 0 }
}

/** The `native/` directory, found from the test's working directory (the module's own folder under Gradle). */
internal object NativeTree {
    val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile && File(it, "ui/words").isDirectory }
    }

    val words: File get() = File(root, "ui/words/src/commonMain/composeResources")
}
