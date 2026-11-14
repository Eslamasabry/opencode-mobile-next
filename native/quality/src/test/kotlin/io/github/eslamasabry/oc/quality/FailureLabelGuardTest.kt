package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A release build renames every class (R8), so a class name read at run time is noise on screen: the owner's Details
 * fold of "Couldn't open this conversation" printed `un5` (`Failure$Refused`) and dropped which refusal it was. Screens
 * and the problem report name a failure with `Failure.label()` (`Refused · StaleScope`), which is written in
 * `:core:common` and survives R8. This rule keeps class names and raw `toString()` of a failure out of every screen
 * (`feature/`, `ui/`) and out of the report code (`engine/diagnostics`, `engine/analytics/.../health`).
 */
class FailureLabelGuardTest {
    private val root = File(System.getProperty("oc.root"))

    /** Files that may read a class name, each with its reason. */
    private val allowed: Map<String, String> =
        mapOf(
            // A desktop-only gate tool's own console line about a thrown test error, never shown in the app.
            "FlutterGoldenGate.kt" to "a desktop gate's console output",
            // Maps a crash's JVM class to a fixed category word; the name itself is never printed.
            "CrashStore.kt" to "class name only matched against a whitelist",
        )

    private val raw =
        Regex("""::class\.(simpleName|qualifiedName)\b|\bjavaClass\.(simpleName|name)\b|\bfailure\??\.toString\(\)""")

    private fun scanned(path: String): Boolean =
        path.startsWith("feature/") || path.startsWith("ui/") || path.startsWith("engine/diagnostics/") ||
            (path.startsWith("engine/analytics/") && "/health/" in path)

    internal fun hits(path: String, text: String): List<Int> =
        if (!scanned(path) || path.substringAfterLast('/') in allowed) emptyList()
        else
            text.lines().mapIndexedNotNull { i, raw0 ->
                val line = raw0.substringBefore("//").trim()
                if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null
                else if (raw.containsMatchIn(line)) i + 1 else null
            }

    @Test
    fun `FAIL-LABEL no screen or report code prints a class name or a raw failure`() {
        val files = BuildRestGates.production(root, "feature", "ui", "engine")
        assertTrue(files.size > 500, "scanned ${files.size} files")
        val bad = files.flatMap { (p, t) -> hits(p, t).map { "$p:$it" } }
        assertTrue(
            bad.isEmpty(),
            "name a failure with Failure.label() (core/common FailureLabel.kt), never a class name:\n" + bad.joinToString("\n"),
        )
    }

    @Test
    fun `FAIL-LABEL planted class names are caught in screens and the report, comments and other engines are not`() {
        val planted =
            """
            val a = failure::class.simpleName
            // failure::class.simpleName in a comment
            val b = f::class.qualifiedName
            val c = failure.toString()
            val ok = failure.label()
            val d = error.javaClass.simpleName
            """.trimIndent()
        assertEquals(listOf(1, 3, 4, 6), hits("feature/chat/src/commonMain/x/ChatStates.kt", planted))
        assertEquals(listOf(1, 3, 4, 6), hits("engine/analytics/src/commonMain/x/health/Report.kt", planted))
        assertEquals(emptyList(), hits("engine/team/src/commonMain/x/TeamEngine.kt", planted))
        assertEquals(emptyList(), hits("ui/gate/src/desktopMain/x/FlutterGoldenGate.kt", planted))
    }
}
