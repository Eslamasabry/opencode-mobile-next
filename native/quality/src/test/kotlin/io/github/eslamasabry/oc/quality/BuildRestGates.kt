package io.github.eslamasabry.oc.quality

import java.io.File

/**
 * Source-scanning gates ported from the Flutter repo gates (BUILD-REST): `architecture_boundaries_test` (B-BUILD-001),
 * `kit_ratchet_test` / `kit_map_gate_test` (B-BUILD-023), `repository_hygiene_test` toolchain pins (B-BUILD-031) and
 * `ui_ledger_coverage_test` (B-BUILD-033). Each rule is a pure function over (path, text) so the planted tests feed it
 * the same way the live scan does.
 */
internal object BuildRestGates {
    data class Hit(val path: String, val rule: String, val line: Int)

    private val PRODUCTION_SETS = listOf("/src/commonMain/", "/src/androidMain/", "/src/desktopMain/", "/src/main/", "/src/jvmShared/")

    /** Production Kotlin under [layers] (e.g. "feature", "ui"), as paths relative to [root]. */
    fun production(root: File, vararg layers: String): Map<String, String> =
        layers.flatMap { layer ->
            File(root, layer).walkTopDown()
                .filter { it.isFile && it.extension == "kt" && "/build/" !in it.path && PRODUCTION_SETS.any { s -> s in it.path } }
                .toList()
        }.associate { it.relativeTo(root).path to it.readText() }

    private fun code(text: String): List<Pair<Int, String>> =
        text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null else (i + 1) to line
        }

    private fun scan(path: String, text: String, rule: String, pattern: Regex): List<Hit> =
        code(text).filter { (_, l) -> pattern.containsMatchIn(l) }.map { (n, _) -> Hit(path, rule, n) }

    // ---- B-BUILD-001: UI talks to the domain gateway only ----

    private val PROTOCOL_IMPORT = Regex("""^import io\.github\.eslamasabry\.oc\.protocol\.""")
    private val BACKEND_BRANCH = Regex("""(==|!=)\s*BackendKind\.|BackendKind\.\w+\s*->|\bis BackendKind\b|when\s*\([^)]*\bbackend\b[^)]*\)""")
    private val NOTIFY = Regex("""\.notify\(\s*\w""")

    /** A feature or UI file that imports a protocol client or branches on the server flavour. */
    fun uiBoundary(path: String, text: String): List<Hit> =
        scan(path, text, "protocol import", PROTOCOL_IMPORT) + scan(path, text, "flavour branch", BACKEND_BRANCH)

    /** A UI module whose build file depends on a protocol module. */
    fun uiBuildFile(path: String, text: String): List<Hit> = scan(path, text, "protocol dependency", Regex("""project\(":protocol:"""))

    /** A notification posted outside the reviewed posting paths. */
    fun notificationPost(path: String, text: String): List<Hit> = scan(path, text, "notification post", NOTIFY)

    /** The reviewed posting paths (path → reason). Shrinks only: an entry that no longer posts fails the gate. */
    val NOTIFICATION_POSTERS: Map<String, String> =
        mapOf(
            "android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/AlertShade.kt" to
                "the one alert shade: every conversation alert",
            "android/notifications/src/main/kotlin/io/github/eslamasabry/oc/android/notifications/ReplyWatch.kt" to
                "the shade's own reply progress for an alert it posted",
            "android/schedules/src/main/kotlin/io/github/eslamasabry/oc/android/schedules/ScheduleNotifications.kt" to
                "scheduled-run results, a separate channel the person can mute",
            "android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupService.kt" to
                "the foreground service's own ongoing notification (Android requires the service to post it)",
            "android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotification.kt" to
                "the built-in Linux status line of that same service",
            "android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeIdleReturnNotificationHost.kt" to
                "the idle-return notice of the same service",
            "androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt" to
                "Settings › Notifications › Send a test notification",
        )

    // ---- B-BUILD-023: kit-only UI rules ----

    private val UI_RULES: List<Pair<String, Regex>> =
        listOf(
            "colour literal" to Regex("""\bColor\(\s*0x|\bColor\(\s*red\s*="""),
            "motion literal" to Regex("""\btween\(|\bspring\(|\bkeyframes\s*\{|durationMillis\s*="""),
            "raw dialog, snackbar or sheet" to Regex("""\b(AlertDialog|Dialog|Snackbar|ModalBottomSheet|Popup)\("""),
            "left/right layout" to
                Regex("""Alignment\.(Top|Center|Bottom)(Left|Right)\b|Arrangement\.Absolute|AbsoluteAlignment|absolutePadding|absoluteOffset"""),
        )

    /** A feature file breaking the kit-only rules: colours and motion values live in the kit, start/end not left/right. */
    fun featureUi(path: String, text: String): List<Hit> = UI_RULES.flatMap { (rule, re) -> scan(path, text, rule, re) }

    private val KIT_URL = Regex("""LocalUriHandler|\.openUri\(|ACTION_VIEW|launchUrl""")

    /** A kit file that opens a URL itself (every link goes through the host's ExternalLinkPolicy → opener). */
    fun kitUrl(path: String, text: String): List<Hit> = scan(path, text, "kit opens a URL", KIT_URL)

    private val WIDTH_CAP = Regex("""widthIn\(\s*max\s*=\s*\d+""")

    /** A hard width cap written in a feature instead of the kit's frame. */
    fun widthCap(path: String, text: String): List<Hit> = scan(path, text, "hard width cap", WIDTH_CAP)

    /** Reviewed hard width caps (path → reason). Shrinks only. */
    val WIDTH_CAPS: Map<String, String> =
        mapOf(
            "feature/files/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/files/ui/FileShareSheet.kt" to
                "the share QR code's square cap (a code wider than 280 dp is not easier to scan)",
            "feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatTranscript.kt" to
                "the reading column of the transcript on a tablet (plan 07 readable measure)",
        )

    // ---- B-BUILD-033: every page has a home and every edge lands on one ----

    data class Route(val parent: String, val name: String) {
        override fun toString() = "$parent.$name"
    }

    private val SEALED_ROUTE = Regex("""sealed interface (\w+)\s*:\s*AppRoute\b""")
    private val DECL = Regex("""\b(?:data class|data object|object|class)\s+(\w+)""")

    /** The concrete destinations in the route files: a class or object that extends AppRoute or a sealed route family. */
    fun routes(files: Map<String, String>): List<Route> {
        val families = files.values.flatMap { t -> SEALED_ROUTE.findAll(t).map { it.groupValues[1] } }.toSet() + "AppRoute"
        return files.values.flatMap { t ->
            DECL.findAll(t).mapNotNull { m ->
                var i = m.range.last + 1
                if (i < t.length && t[i] == '(') {
                    var depth = 0
                    while (i < t.length) {
                        if (t[i] == '(') depth++ else if (t[i] == ')') { depth--; if (depth == 0) { i++; break } }
                        i++
                    }
                }
                val rest = t.substring(i).trimStart()
                if (!rest.startsWith(":")) return@mapNotNull null
                val parent = Regex("""^:\s*(\w+)""").find(rest)?.groupValues?.get(1) ?: return@mapNotNull null
                if (parent in families) Route(parent, m.groupValues[1]) else null
            }.toList()
        }
    }

    private val ENTRY = Regex("""entry<([\w.]+)>""")

    /** The destinations an EntryInstaller draws (simple names, `Parent.Name` or `Name`). */
    fun entries(files: Map<String, String>): Set<String> = files.values.flatMap { t -> ENTRY.findAll(t).map { it.groupValues[1] } }.toSet()

    fun hasEntry(route: Route, entries: Set<String>): Boolean = "$route" in entries || route.name in entries

    private val EDGE = Regex("""\b(?:open|replaceRoot|navigate)\(\s*(\w+Route)\.(\w+)""")

    /** Every `open(XRoute.Y…)` in [files]: the edges between pages. */
    fun edges(files: Map<String, String>): List<Pair<String, Route>> =
        files.flatMap { (path, t) -> EDGE.findAll(t).map { path to Route(it.groupValues[1], it.groupValues[2]) }.toList() }
}
