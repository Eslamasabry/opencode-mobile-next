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
import io.github.eslamasabry.oc.engine.analytics.umami.umamiPages
import io.github.eslamasabry.oc.engine.analytics.umami.umamiEvent
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiBuild
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiDevice
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiSource
import io.github.eslamasabry.oc.feature.settings.presentation.HealthContract
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
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

    /** Receiver brands are deliberately untranslated in every offered locale. */
    internal fun recipientViolations(text: String): List<String> =
        listOf("Google", "Umami", "Railway").filterNot(text::contains)

    @Test
    fun `B-SEC-207 every offered privacy locale names both senders and self hosting`() {
        val resources = File(repo, "native/ui/words/src/commonMain/composeResources")
        for (page in listOf(
            "native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/PrivacyPages.kt",
            "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ContinuityWiring.kt",
        )) {
            val source = File(repo, page).readText()
            assertTrue("Res.string.nativeTelemetryUsageDetail" in source, "the actual Settings and consent UI use the current notice")
            assertTrue("Res.string.telemetryUsageDetail" !in source, "the frozen Google-only notice is not shown")
        }
        val consent = File(repo, "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ContinuityWiring.kt").readText()
        assertTrue("Res.string.nativeTelemetryConsentBody" in consent, "the first consent question must name the current receivers")
        assertTrue("Res.string.telemetryConsentBody" !in consent, "the frozen Google-only consent question is not shown")
        val offered = AppLanguage.entries.mapNotNull { it.tag }
        val code = liveCode()
        for (tag in offered) {
            val locale = File(resources, if (tag == "en") "values" else "values-$tag")
            assertTrue(File(locale, "strings_s28_privacy.xml").isFile, "$tag offers translated disclosure copy")
            assertTrue(File(locale, "strings_native_privacy.xml").isFile, "$tag offers a translated policy")
            val detail = File(locale, "strings_s28_privacy.xml").readText()
            val policy = File(locale, "strings_native_privacy.xml").readText()
            for (key in listOf("collectRules", "nativeTelemetryUsageDetail", "nativeTelemetryConsentBody")) {
                val match = Regex("""<string name="$key">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(detail)
                assertTrue(match != null, "${locale.name}: $key is translated")
                val text = match.groupValues[1]
                assertEquals(emptyList(), recipientViolations(text), "${locale.name}: $key names the receivers")
            }
            assertEquals(emptyList(), recipientViolations(policy), "${locale.name}: policy receivers")
            assertTrue("app_build" in policy, "${locale.name}: policy build property")
            for (name in code.events.keys + code.properties + code.keys + code.failures + code.allFields) {
                assertTrue(has(policy, name), "${locale.name}: the translated policy must name $name")
            }
        }
        assertEquals(emptyList(), recipientViolations(File(repo, "PRIVACY.md").readText()))
        assertTrue("Umami" in recipientViolations("Google hosted with Railway"), "omitting the actual sender must fail")
        assertTrue("Railway" in recipientViolations("Google and Umami"), "omitting the host must fail")
    }

    /** The phrase each offered language uses for "every month", and the words that would say a day instead. */
    private val monthly = mapOf(
        "en" to ("every month" to Regex("(?i)daily|every day|each day")),
        "ar" to ("كل شهر" to Regex("يومي|كل يوم")),
        "es" to ("cada mes" to Regex("(?i)diari[oa]|cada día")),
        "fr" to ("chaque mois" to Regex("(?i)quotidien|journalier|chaque jour")),
        "id" to ("setiap bulan" to Regex("(?i)harian|setiap hari")),
        "ja" to ("毎月" to Regex("毎日|日ごと")),
        "pt" to ("cada mês" to Regex("(?i)diári[oa]|cada dia")),
        "ru" to ("каждый месяц" to Regex("(?i)ежедневн|каждый день|суточн")),
        "vi" to ("mỗi tháng" to Regex("(?i)hằng ngày|hàng ngày|mỗi ngày")),
        "zh" to ("每月" to Regex("每日|每天")),
    )

    @Test
    fun `B-SEC-210 every offered language says the anonymous visitor hash changes every month and never daily`() {
        val resources = File(repo, "native/ui/words/src/commonMain/composeResources")
        val offered = AppLanguage.entries.mapNotNull { it.tag }
        assertEquals(monthly.keys, offered.toSet(), "every offered language has its month phrase in this guard")
        for (tag in offered) {
            val (month, day) = monthly.getValue(tag)
            val locale = File(resources, if (tag == "en") "values" else "values-$tag")
            val detail = File(locale, "strings_s28_privacy.xml").readText()
            val policy = File(locale, "strings_native_privacy.xml").readText()
            fun text(source: String, key: String) =
                Regex("""<string name="$key">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(source)!!.groupValues[1]
            for ((source, key) in listOf(detail to "nativeTelemetryUsageDetail", detail to "collectRules", policy to "nativeBundledPrivacy")) {
                val words = text(source, key)
                assertTrue(month in words, "${locale.name}: $key says the visitor hash changes \"$month\"")
                assertTrue(day.find(words) == null, "${locale.name}: $key must not say the hash changes by the day")
            }
            assertTrue(day.find(text(detail, "nativeTelemetryConsentBody")) == null, "${locale.name}: consent sentence")
        }
        val md = File(repo, "PRIVACY.md").readText()
        assertTrue("changes every month" in md, "PRIVACY.md says the hash changes every month")
        assertTrue(Regex("(?i)daily visitor|daily hash").find(md) == null, "PRIVACY.md must not say daily")
    }

    @Test
    fun `B-SEC-212 the site FAQ and every store description name both receivers and the monthly hash and never deny analytics`() {
        val site = File(repo, "docs/site/index.html").readText()
        val stores = File(repo, "fastlane/metadata/android").listFiles { f -> f.isDirectory }!!
            .associate { it.name to File(it, "full_description.txt").readText() }
        assertTrue(stores.keys.containsAll(listOf("en-US", "ar")), "the store languages this guard knows: ${stores.keys}")
        val texts = stores.mapKeys { "store ${it.key}" } + ("site" to site)
        for ((where, text) in texts) {
            assertEquals(emptyList(), recipientViolations(text.replace("OpenCode team, Anthropic, OpenAI or Google", "")), "$where names the receivers")
            assertTrue(Regex("(?i)daily visitor|daily hash").find(text) == null, "$where must not say daily")
        }
        assertTrue("changes every month" in site, "site says the hash changes every month")
        assertTrue("changes every month" in stores.getValue("en-US"), "English store text says the hash changes every month")
        assertTrue("كل شهر" in stores.getValue("ar"), "Arabic store text says the hash changes every month")
        assertTrue(Regex("لا تحليلات|no analytics", RegexOption.IGNORE_CASE).find(stores.getValue("ar") + stores.getValue("en-US") + site) == null,
            "no store or site text may deny the analytics that the app can send")
        // "The app talks only to the servers you choose" is false: usage reports, update checks and downloads go elsewhere.
        // Only the claim about conversations and code is true.
        val sentence = "Your conversations and code go only to the servers you choose."
        val arabic = "لا تذهب محادثاتك وشيفرتك إلا إلى الخوادم التي تختارها."
        val talksOnly = Regex(
            "(?i)(app|it)\\s+(talks|connects|communicates)\\s+(only\\s+)?(to|with)\\s+(the\\s+)?servers\\s+you\\s+choose" +
                "|connects\\s+only\\s+to" + "|talks\\s+only\\s+to" +
                "|لا يتحدث التطبيق إلا|يتحدث التطبيق مع" +
                "|solo (habla|se conecta)|ne communique qu|hanya (berkomunikasi|terhubung)|só se (comunica|conecta)|apenas se (comunica|conecta)" +
                "|общается только|chỉ giao tiếp|chỉ kết nối với|只与|只会与|仅与",
        )
        val claimFiles = listOf("README.md", "llms.txt", "marketing/launch-2026-10/POSTS.md", "docs/site/index.html") +
            File(repo, "fastlane/metadata/android").listFiles { f -> f.isDirectory }!!.map { "fastlane/metadata/android/${it.name}/full_description.txt" } +
            File(repo, "native/ui/words/src/commonMain/composeResources").listFiles { f -> f.isDirectory }!!.flatMap { d ->
                d.listFiles { f -> f.name.endsWith("privacy.xml") }!!.map { "native/ui/words/src/commonMain/composeResources/${d.name}/${it.name}" }
            } + "PRIVACY.md"
        for (path in claimFiles) {
            val text = File(repo, path).readText()
            assertTrue(talksOnly.find(text) == null, "$path must not say the app only talks to the servers you choose")
        }
        assertTrue(sentence in File(repo, "README.md").readText() && sentence in stores.getValue("en-US") && sentence in site, "the true sentence is used in README, the English store text and the site")
        assertTrue(arabic in stores.getValue("ar"), "the Arabic store text uses the true sentence")
        // Public launch drafts and docs must not deny analytics either (the app can send usage and crash reports once switched on).
        val denial = Regex(
            "(?i)\\bno\\s+(ads,?\\s+|ads\\s+or\\s+)?(analytics|tracking|telemetry)|analytics\\s+or\\s+accounts|ads,\\s+analytics|automatic\\s+crash\\s+upload",
        )
        val marketing = File(repo, "marketing/launch-2026-10").listFiles { f -> f.name.endsWith(".md") }!!
            .map { "marketing/launch-2026-10/${it.name}" }
            // IZZYONDROID.md rows 50 and 70 are the dated findings of an earlier build's audit, kept as history.
            .filter { !it.endsWith("IZZYONDROID.md") }
        for (path in marketing + listOf("README.md", "llms.txt", "docs/site/index.html")) {
            assertTrue(denial.find(File(repo, path).readText()) == null, "$path must not say the app has no analytics or no automatic crash upload")
        }
        val izzy = File(repo, "marketing/launch-2026-10/IZZYONDROID.md").readLines()[121]
        assertTrue(denial.find(izzy) == null, "the IZZYONDROID.md submission blurb must not deny analytics")
        assertTrue("Umami" in File(repo, "README.md").readText(), "README names the Umami receiver beside Google")
    }

    @Test
    fun `B-SEC-207 the new sender adds only documented build metadata to every event`() {
        val source = UmamiSource("website", UmamiBuild("3.0.1", "10081"), UmamiDevice("en", "1080x2340", "browser"))
        for ((name, fields) in productSchema) {
            val event = umamiEvent(name, fields.mapValues { it.value.first() }, umamiPages.getValue("conversations"),
                source, 1000)!!
            assertEquals(setOf("version", "build"), event.payload.data.keys - fields.keys, name)
        }
        val wiring = File(repo, "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/telemetry/Telemetry.kt").readText()
        assertTrue("UsageTelemetryVendor" in wiring && "UmamiSender(" in wiring, "the notice covers the sender that the app actually builds")
        assertTrue("BuildConfig.VERSION_NAME" in wiring && "BuildConfig.VERSION_CODE" in wiring, "version facts come from this build")
        val firebase = File(repo, "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/telemetry/FirebaseTelemetryVendor.kt").readText()
        assertTrue("setUserProperty(\"app_build\", null)" in firebase, "usage off clears the Google build property")
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
    @Test
    fun `B-435 every offered policy names stall cause and previous exit event fields`() {
        val resources = File(repo, "native/ui/words/src/commonMain/composeResources")
        for (tag in AppLanguage.entries.mapNotNull { it.tag }) {
            val locale = File(resources, if (tag == "en") "values" else "values-$tag")
            val policy = File(locale, "strings_native_privacy.xml").readText()
            val rows = File(locale, "strings_s28_privacy.xml").readText()
            for (field in listOf("cause", "app_exit", "exit_reason", "foreground"))
                assertTrue(has(rows, field), "$tag What we collect must name $field")
            for (word in listOf("cause", "thinking_silent", "tool_running", "retrying", "stream_dead_refetched", "stream_silent", "unknown", "app_exit", "exit_reason", "foreground"))
                assertTrue(has(policy, word), "$tag policy must name $word")
        }
        assertTrue(HealthContract.contractEvents.values.flatten().contains("app_exit"), "What we collect names the event")
    }

    private val voiceOverclaims = listOf(
        "so the audio stays on this phone",
        "so recorded audio stays on the phone",
        "فيبقى الصوت على هذا الهاتف",
        "el audio se queda en este teléfono",
        "l'audio reste sur ce téléphone",
        "audio tetap di ponsel ini",
        "外に出ることはありません",
        "o áudio fica neste celular",
        "аудио остаётся на этом телефоне",
        "âm thanh vẫn ở trên điện thoại này",
        "音频保留在这部手机上",
    )

    private fun xmlString(file: File, key: String): String =
        Regex("""<string name="$key">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(file.readText())!!.groupValues[1]

    @Test
    fun `B-SEC-213 privacy policy, What we collect and voice settings name both voice paths truthfully in every offered language`() {
        val md = section(File(repo, "PRIVACY.md").readText(), "## Microphone and local voice input", "## Optional read-aloud")
        val mdFacts = listOf(
            "huggingface.co",
            "never leaves the phone",
            "createOnDeviceSpeechRecognizer",
            "Android 12",
            "EXTRA_PREFER_OFFLINE",
            "privacy terms apply",
        )
        for (fact in mdFacts) {
            assertTrue(fact in md, "PRIVACY.md voice section must name '$fact'")
        }
        val wiring = File(repo, "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/VoiceWiring.kt").readText()
        assertTrue("SpeechRecognizer.createOnDeviceSpeechRecognizer(" in wiring, "fallback voice uses createOnDeviceSpeechRecognizer")
        assertTrue("RecognizerIntent.EXTRA_PREFER_OFFLINE" in wiring, "fallback voice sets EXTRA_PREFER_OFFLINE")
        assertTrue("SpeechRecognizer.createSpeechRecognizer(" !in wiring, "fallback voice never uses the default speech recognizer")
        val kdocPath = "native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/dictation/OnDeviceSpeech.kt"
        val kdoc = File(repo, kdocPath).readText()
        assertTrue("so recorded audio stays on the phone" !in kdoc, "OnDeviceSpeech KDoc must not overclaim for the system fallback")
        val resources = File(repo, "native/ui/words/src/commonMain/composeResources")
        for (tag in AppLanguage.entries.mapNotNull { it.tag }) {
            val locale = File(resources, if (tag == "en") "values" else "values-$tag")
            val policy = xmlString(File(locale, "strings_native_privacy.xml"), "nativeBundledPrivacy")
            val never = xmlString(File(locale, "strings_s28_privacy.xml"), "collectNever")
            val voice = xmlString(File(locale, "strings_voice_native.xml"), "voiceNativePhoneEngine")
            assertTrue("huggingface.co" in policy && "createOnDeviceSpeechRecognizer" in policy && "Android 12" in policy,
                "${locale.name}: nativeBundledPrivacy must name both voice paths")
            assertTrue("huggingface.co" in never && "createOnDeviceSpeechRecognizer" in never && "Android 12" in never,
                "${locale.name}: collectNever on What we collect must name both voice paths")
            assertTrue("createOnDeviceSpeechRecognizer" in voice && "Android 12" in voice,
                "${locale.name}: voiceNativePhoneEngine must name Android's on-device recognizer")
            for (overclaim in voiceOverclaims) {
                assertTrue(overclaim !in voice, "${locale.name}: voiceNativePhoneEngine must not overclaim '$overclaim'")
            }
        }
    }

}
