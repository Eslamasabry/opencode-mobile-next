package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.analytics.health.BreadcrumbWords
import io.github.eslamasabry.oc.engine.analytics.health.HealthWords
import io.github.eslamasabry.oc.engine.analytics.health.PerfSchema
import io.github.eslamasabry.oc.engine.analytics.health.healthKeyPatterns
import io.github.eslamasabry.oc.engine.analytics.health.healthKeyWords
import io.github.eslamasabry.oc.engine.analytics.health.healthPropertyPatterns
import io.github.eslamasabry.oc.engine.analytics.health.healthPropertyWords
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import io.github.eslamasabry.oc.feature.settings.presentation.HealthContract
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard that keeps the privacy wording true: what the code can send, what "What we collect" lists
 * ([HealthContract.contractEvents]), what the in-app policy says (`strings_native_privacy.xml`) and what PRIVACY.md
 * tables say are one list. It reads the live allow-lists (`productSchema`, the health keys and properties,
 * `PerfSchema`, `BreadcrumbWords`, the handled-failure names and the vendor's custom keys found in the sources), so
 * nobody has to remember to update a copy. It fails both ways: something the code can send that a notice does not
 * name, and something a notice names that the code cannot send.
 */
class PrivacyNoticeSyncTest {
    /** Everything the code can send, by name. */
    internal class SendableNames(
        /** event -> field -> every value the field may take (`productSchema`). */
        val events: Map<String, Map<String, Set<String>>>,
        /** trace -> attribute -> values; every `linux_setup_<step>` is one entry, `linux_setup_<step>`. */
        val traces: Map<String, Map<String, Set<String>>>,
        /** The user properties (section D). */
        val properties: Set<String>,
        /** The custom keys (section A), including the ones the vendor sets itself. */
        val keys: Set<String>,
        /** The names of handled failures (Crashlytics non-fatals). */
        val failures: Set<String>,
        /** Every other word a notice may use: breadcrumb parts, exit reasons and the like. */
        val words: Set<String>,
    ) {
        val allFields: Set<String> get() = events.values.flatMap { it.keys }.toSet() + traces.values.flatMap { it.keys }
        val allValues: Set<String> get() = events.values.flatMap { f -> f.values.flatten() }.toSet() + traces.values.flatMap { f -> f.values.flatten() } + words
        val allNames: Set<String> get() = events.keys + traces.keys + properties + keys + failures
    }

    /** The texts that have to agree with the code. */
    internal class Notices(val privacyMd: String, val policy: String, val contract: Map<String, List<String>>)

    // ---- the live code ----

    private val repo: File = findRepo()

    private fun findRepo(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null && !(File(d, "PRIVACY.md").isFile && File(d, "native").isDirectory)) d = d.parentFile
        return checkNotNull(d) { "repository root not found from ${System.getProperty("user.dir")}" }
    }

    private fun sourceFiles(): List<File> =
        File(repo, "native").walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && it.name != ".git" && it.name != "third_party" }
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val p = f.path.replace('\\', '/')
                "/src/main/" in p || "/src/commonMain/" in p || "/src/androidMain/" in p
            }
            .toList()

    private fun liveCode(): SendableNames {
        val sources = sourceFiles().map { it.readText() }
        val failure = Regex("""(?:reportFailure|recordFailure)\(\s*"([a-z][a-z0-9_]*)"""")
        val vendorKey = Regex("""setCustomKey\(\s*"([a-z][a-z0-9_]*)"""")
        val failures = sources.flatMap { s -> failure.findAll(s).map { it.groupValues[1] }.toList() }.toSet()
        val vendorKeys = sources.flatMap { s -> vendorKey.findAll(s).map { it.groupValues[1] }.toList() }.toSet()

        val traces = LinkedHashMap<String, Map<String, Set<String>>>()
        for (name in PerfSchema.names) {
            val key = if (name.startsWith(PerfSchema.LINUX_SETUP_PREFIX)) "${PerfSchema.LINUX_SETUP_PREFIX}<step>" else name
            traces[key] = PerfSchema.allowed(name).orEmpty()
        }
        val words = LinkedHashSet<String>()
        words += healthKeyWords.values.flatten()
        words += healthPropertyWords.values.flatten()
        words += HealthWords.exitReasons
        words += BreadcrumbWords.plain.flatMap { it.split('.') }
        words += BreadcrumbWords.settingGroups
        words += BreadcrumbWords.named.keys.flatMap { it.split('.') }
        words += BreadcrumbWords.named.values.flatten()
        words += "linux_setup" // the prefix of `linux_setup_<step>`
        words += setOf("breadcrumbs", "problem_report") // crash-side lines that are not events
        return SendableNames(
            events = productSchema,
            traces = traces,
            properties = healthPropertyWords.keys + healthPropertyPatterns.keys,
            keys = healthKeyWords.keys + healthKeyPatterns.keys + vendorKeys,
            failures = failures,
            words = words,
        )
    }

    private fun liveNotices(): Notices {
        val xml = File(repo, "native/ui/words/src/commonMain/composeResources/values/strings_native_privacy.xml").readText()
        val policy = Regex("""<string name="nativeBundledPrivacy">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)!!.groupValues[1]
        return Notices(
            privacyMd = File(repo, "PRIVACY.md").readText(),
            policy = policy,
            contract = HealthContract.contractEvents.mapKeys { it.key.name },
        )
    }

    // ---- the check ----

    private val snake = Regex("""(?<![A-Za-z0-9_])[a-z][a-z0-9]*(?:_[a-z0-9]+)+(?![A-Za-z0-9_<>])""")

    private fun has(text: String, word: String): Boolean =
        Regex("""(?<![A-Za-z0-9_])${Regex.escape(word)}(?![A-Za-z0-9_])""").containsMatchIn(text)

    private fun section(text: String, from: String, to: String): String {
        val a = text.indexOf(from)
        require(a >= 0) { "PRIVACY.md has no \"$from\"" }
        val b = text.indexOf(to, a + from.length).let { if (it < 0) text.length else it }
        return text.substring(a, b)
    }

    private fun tableRows(text: String): Map<String, String> =
        text.lines().filter { it.startsWith("| `") }.associate { line ->
            val cells = line.trim().trim('|').split('|', limit = 2)
            cells[0].trim().trim('`') to line
        }

    /** Every disagreement, by name; empty when the notices and the code say the same. */
    internal fun violations(code: SendableNames, n: Notices): List<String> {
        val out = mutableListOf<String>()

        // 1. "What we collect" (HealthContract.contractEvents) both ways.
        val listed = n.contract.values.flatten().toSet()
        val structural = setOf("crash", "breadcrumbs", "problem_report")
        for (name in code.events.keys + code.properties + code.keys) {
            if (name !in listed) out += "What we collect does not list the name `$name` (contractEvents)"
        }
        for (name in listed - structural) {
            if (name !in code.events && name !in code.properties && name !in code.keys) {
                out += "What we collect lists `$name`, which the code cannot send"
            }
        }

        // 2. PRIVACY.md: the telemetry part.
        val md = n.privacyMd
        val telemetry = section(md, "## Optional crash reports, usage and app health", "## Data stored on your device")
        val usage = section(telemetry, "### Usage and app health", "### Performance")
        val perf = section(telemetry, "### Performance", "### Problem report")
        val crash = section(telemetry, "### Crash reports", "### Usage and app health")
        val eventRows = tableRows(usage)
        val traceRows = tableRows(perf)

        for ((event, fields) in code.events) {
            val row = eventRows[event]
            if (row == null) {
                out += "PRIVACY.md has no row for the event `$event`"
                continue
            }
            for ((field, values) in fields) {
                if (!row.contains("`$field`")) out += "PRIVACY.md `$event` does not name the field `$field`"
                for (v in values) if (!has(row, v)) out += "PRIVACY.md `$event`.`$field` does not list the value `$v`"
            }
            val allowed = fields.keys + code.events.keys + code.allValues
            for (tick in Regex("`([^`]+)`").findAll(row.substringAfter('|').substringAfter('|')).map { it.groupValues[1] }) {
                if (tick !in allowed) out += "PRIVACY.md `$event` names `$tick`, which the code cannot send there"
            }
        }
        for (event in eventRows.keys - code.events.keys) {
            out += "PRIVACY.md has a usage row for `$event`, which the code cannot send"
        }
        for ((trace, attrs) in code.traces) {
            val row = traceRows[trace]
            if (row == null) {
                out += "PRIVACY.md has no row for the trace `$trace`"
                continue
            }
            for ((field, values) in attrs) {
                if (!row.contains("`$field`")) out += "PRIVACY.md trace `$trace` does not name `$field`"
                for (v in values) if (!has(row, v)) out += "PRIVACY.md trace `$trace`.`$field` does not list `$v`"
            }
        }
        for (trace in traceRows.keys - code.traces.keys) out += "PRIVACY.md has a trace row for `$trace`, which the code cannot send"
        for (p in code.properties) if (!has(usage, p)) out += "PRIVACY.md does not name the device property `$p`"
        for (k in code.keys) if (!has(crash, k)) out += "PRIVACY.md does not name the crash key `$k`"
        for (f in code.failures) if (!has(crash, f)) out += "PRIVACY.md does not name the handled failure `$f`"
        for (v in HealthWords.buildKinds) if (!has(crash, v)) out += "PRIVACY.md does not list the build kind `$v`"

        val known = code.allNames + code.allFields + code.allValues
        for (token in snake.findAll(telemetry).map { it.value }.toSet()) {
            if (token !in known) out += "PRIVACY.md names `$token`, which the code cannot send"
        }

        // 3. The in-app policy: every name; every word that looks like a name is a real one.
        for (name in code.events.keys + code.properties + code.keys + code.failures) {
            if (!has(n.policy, name)) out += "The in-app policy does not name `$name`"
        }
        for (name in code.allFields) if (!has(n.policy, name)) out += "The in-app policy does not name the field `$name`"
        for (token in snake.findAll(n.policy).map { it.value }.toSet()) {
            if (token !in known) out += "The in-app policy names `$token`, which the code cannot send"
        }
        return out.distinct()
    }

    // ---- the tests ----

    @Test
    fun `B-SEC-207 every name the code can send is in the notices and every notice name can be sent`() {
        val found = violations(liveCode(), liveNotices())
        assertEquals(emptyList(), found, "the privacy wording and the code disagree:\n" + found.joinToString("\n"))
    }

    @Test
    fun `B-SEC-208 What we collect covers every product event of the code`() {
        val listed = HealthContract.contractEvents.values.flatten().toSet()
        assertEquals(emptySet(), ProductEvent.entries.map { it.wire }.toSet() - listed)
        assertEquals(emptySet(), ProductEvent.entries.map { it.wire }.toSet() - productSchema.keys)
    }

    @Test
    fun `B-SEC-209 an event the code can send but no notice names fails the guard`() {
        val code = liveCode()
        val planted =
            SendableNames(
                events = code.events + ("planted_event" to mapOf("planted_field" to setOf("planted_word"))),
                traces = code.traces,
                properties = code.properties,
                keys = code.keys,
                failures = code.failures,
                words = code.words,
            )
        val found = violations(planted, liveNotices())
        assertTrue(found.any { "planted_event" in it }, found.toString())
        assertTrue(found.any { "planted_field" in it }, found.toString())
    }

    @Test
    fun `B-SEC-210 a notice entry the code cannot send fails the guard`() {
        val notices = liveNotices()
        val row = "| `planted_ghost` | `kind`: cold or warm |\n"
        val md = notices.privacyMd.replace("| `memory_pressure` |", row + "| `memory_pressure` |")
        assertTrue(md != notices.privacyMd, "the planted row found no place")
        val ghostMd = violations(liveCode(), Notices(md, notices.policy, notices.contract))
        assertTrue(ghostMd.any { "planted_ghost" in it }, ghostMd.toString())

        val ghostPolicy = violations(liveCode(), Notices(notices.privacyMd, notices.policy + " planted_ghost_event", notices.contract))
        assertTrue(ghostPolicy.any { "planted_ghost_event" in it }, ghostPolicy.toString())

        val ghostList = violations(liveCode(), Notices(notices.privacyMd, notices.policy, notices.contract + ("Ghost" to listOf("planted_ghost"))))
        assertTrue(ghostList.any { "planted_ghost" in it }, ghostList.toString())
    }

    @Test
    fun `B-SEC-211 a changed bucket the notice does not list fails the guard`() {
        val code = liveCode()
        val widened =
            SendableNames(
                events = code.events + ("jank" to (code.events.getValue("jank") + ("late_pct_range" to (code.events.getValue("jank").getValue("late_pct_range") + "99-100")))),
                traces = code.traces,
                properties = code.properties,
                keys = code.keys,
                failures = code.failures,
                words = code.words,
            )
        val found = violations(widened, liveNotices())
        assertTrue(found.any { "99-100" in it }, found.toString())
    }
}
