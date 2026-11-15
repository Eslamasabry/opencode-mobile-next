package io.github.eslamasabry.oc.quality

/**
 * K20, kit surfaces (docs/designs/glass-in-the-kit/SURFACES-SPEC.md §5): a kit part states what it is and gets its face
 * from `kitSurfaceLook`; it does not paint its own pane. This counts, per kit file, the code lines that still paint a
 * surface by hand: a fill of `surface1-3` or `ground` (also as `.copy(...)`), an own `.border(...)`, an own shadow, a
 * `drawRect`/`drawRoundRect` in a surface colour, and a glass face built outside the resolver. Lines matching an entry
 * of the allowlist (`quality/config/kit-surfaces-allow.txt`, `path | line regex | reason`) are not counted. Pure
 * functions over (path, text), as [MotionGates], so the planted tests feed them the same way.
 *
 * What it does not see: a fill passed through a local value (`val fill = colors.surface2` … `.background(fill)`), the
 * attention colours (`attentionSurface`, `attentionFill`), and the kit's own edge helpers (`kitHairlineBorder`,
 * `exactEdge`). The count is a floor, not the whole debt.
 */
internal object KitSurfaces {
    /** The kit modules scanned (paths relative to `native/`). */
    val MODULES: List<String> = listOf("ui/kit", "ui/kit-chat", "ui/kit-content")

    private val SETS = listOf("/src/commonMain/", "/src/androidMain/", "/src/desktopMain/")

    /** A production kit source the rule reads: not a test, not the glass engine under `effects/`. */
    fun inScope(path: String): Boolean {
        val p = "/" + path.replace('\\', '/')
        return p.endsWith(".kt") && "/build/" !in p && "/effects/" !in p && "Test" !in p &&
            MODULES.any { p.startsWith("/$it/") } && SETS.any { it in p }
    }

    private const val SURFACE = """(?:\bcolors|\broles|\bc|\.current)\.(?:surface[123]|ground)\b"""

    // An argument list with up to two levels of nested parentheses: `.background(if (on) colors.surface3 else x)`.
    private const val ARGS = """(?:[^()]|\((?:[^()]|\([^()]*\))*\))*"""

    /** What a hand-painted surface looks like on one code line (comments stripped). */
    val PAINTS: List<Pair<String, Regex>> =
        listOf(
            "surface fill" to Regex("""\.background\($ARGS$SURFACE"""),
            "own border" to Regex("""\.border\("""),
            "own shadow" to Regex("""\.shadow\(|\bdropShadow\("""),
            "surface drawn" to Regex("""\bdraw(?:Round)?Rect\(\s*(?:color\s*=\s*)?$SURFACE"""),
            "glass face outside the resolver" to
                Regex(
                    """(?<!fun )(?<!fun Modifier\.)\b(?:kitLiquidFace|kitGlassSheet|kitBackdropVeil|kitFrostPane|kitSurfaceGlass)\(|""" +
                        """\bstate\.spec\.resolve\(""",
                ),
        )

    data class Hit(val path: String, val line: Int, val code: String)

    private fun code(text: String): List<Pair<Int, String>> =
        text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null else (i + 1) to line
        }

    /** Every code line of [text] that paints a surface by hand, allowed or not. */
    fun paints(path: String, text: String): List<Hit> =
        code(text).filter { (_, l) -> PAINTS.any { (_, p) -> p.containsMatchIn(l) } }.map { (n, l) -> Hit(path, n, l) }

    /** One allowlist line: a kit file, a regex its painting line matches, and why it may paint. */
    data class Allow(val path: String, val line: Regex, val reason: String, val source: String)

    /** Parses the allowlist; a malformed line (not three ` | ` fields, or a blank reason) is returned as an error. */
    fun parseAllow(text: String): Pair<List<Allow>, List<String>> {
        val entries = mutableListOf<Allow>()
        val errors = mutableListOf<String>()
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { raw ->
            val parts = raw.split(" | ").map { it.trim() }
            if (parts.size != 3 || parts.any { it.isEmpty() }) {
                errors += "needs `path | line regex | reason`, all three filled: $raw"
            } else {
                entries += Allow(parts[0], Regex(parts[1]), parts[2], raw)
            }
        }
        return entries to errors
    }

    /** The result of a scan: counted lines per file, and the allowlist entries no painting line matches any more. */
    data class Count(val counted: Map<String, List<Hit>>, val stale: List<Allow>) {
        val perFile: Map<String, Int> get() = counted.mapValues { it.value.size }.filterValues { it > 0 }
    }

    /** Counts [files] (path → text) against [allow]: an allowed line is not counted; an entry that allows nothing is stale. */
    fun count(files: Map<String, String>, allow: List<Allow>): Count {
        val used = mutableSetOf<Allow>()
        val counted =
            files.filterKeys { inScope(it) }.mapValues { (path, text) ->
                paints(path, text).filter { hit ->
                    val by = allow.filter { it.path == path && it.line.containsMatchIn(hit.code) }
                    used += by
                    by.isEmpty()
                }
            }
        return Count(counted.filterValues { it.isNotEmpty() }, allow.filter { it !in used })
    }

    /**
     * K20b, own faces (glass-truth 2026-11-16): the voice pill, the side sheet, the undo bar, the toast and their kind
     * painted their own outer face and never asked the style, so Glass drew an opaque Solid card among its glass. A kit
     * file that paints a surface by hand (a counted K20 line) must also ask a style path Glass answers ([ASKS]:
     * `kitSurfaceLook` or a wrapper of it, `kitStyledFace`, a liquid face, the modal glass face); `kitDressedLook` alone
     * does not count, it answers only for Brutal and Paper. A file that may keep its own face (content inside another
     * pane, a state layer, an engine primitive) is listed with its reason in `quality/config/kit-own-face-allow.txt`.
     */
    val ASKS: Regex =
        Regex("""\b\w*[sS]urfaceLook\(|\bkitStyledFace\(|\bkitLiquid\w*Face\(|\bkitModalGlassFace\(|\bkitGlassSheet\(|\bKitSurface(?:Panel|Inset|Tile)?\(""")

    /** Whether a code line of [text] asks the style for a face Glass answers ([ASKS]). */
    fun asksTheStyle(text: String): Boolean = code(text).any { (_, l) -> ASKS.containsMatchIn(l) }

    /** One own-face allowlist line: a kit file that may paint its own face, and why. */
    data class OwnFace(val path: String, val reason: String, val source: String)

    /** Parses the own-face allowlist (`path | reason`); a line without both fields is returned as an error. */
    fun parseOwnFaces(text: String): Pair<List<OwnFace>, List<String>> {
        val entries = mutableListOf<OwnFace>()
        val errors = mutableListOf<String>()
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { raw ->
            val parts = raw.split(" | ").map { it.trim() }
            if (parts.size != 2 || parts.any { it.isEmpty() }) errors += "needs `path | reason`, both filled: $raw" else entries += OwnFace(parts[0], parts[1], raw)
        }
        return entries to errors
    }

    /** K20b's result: files that paint by hand and never ask the style, and allowlist entries no such file needs any more. */
    data class OwnFaces(val unasked: List<String>, val stale: List<OwnFace>)

    /** The files of [files] with a counted K20 line ([allow] applied) and no [ASKS] call, against the own-face list [own]. */
    fun ownFaces(files: Map<String, String>, allow: List<Allow>, own: List<OwnFace>): OwnFaces {
        val painting = count(files, allow).counted.keys
        val unasked = painting.filter { !asksTheStyle(files.getValue(it)) }.toSet()
        val listed = own.map { it.path }.toSet()
        return OwnFaces(unasked.filter { it !in listed }.sorted(), own.filter { it.path !in unasked })
    }

    /** The baseline file's `path<TAB>count` lines. */
    fun parseBaseline(text: String): Map<String, Int> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.associate { line ->
            val (path, n) = line.split('\t').map { it.trim() }
            path to n.toInt()
        }

    fun writeBaseline(perFile: Map<String, Int>): String =
        "# K20-kit-surfaces: hand-painted surface lines per kit file (KitSurfaces.kt; allowlist kit-surfaces-allow.txt).\n" +
            "# A count may only fall: move a paint behind kitSurfaceLook, then lower its number (or delete the line at 0).\n" +
            "# Never raise a number or add a file by hand. Total: ${perFile.values.sum()}\n" +
            perFile.toSortedMap().entries.joinToString("") { "${it.key}\t${it.value}\n" }
}
