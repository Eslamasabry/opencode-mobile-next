package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.analytics.DeviceClass
import io.github.eslamasabry.oc.engine.analytics.NeverCollected
import io.github.eslamasabry.oc.engine.analytics.health.BreadcrumbWords
import io.github.eslamasabry.oc.engine.analytics.health.HealthWords
import io.github.eslamasabry.oc.engine.analytics.health.PerfSchema
import io.github.eslamasabry.oc.engine.analytics.health.healthKeyPatterns
import io.github.eslamasabry.oc.engine.analytics.health.healthKeyWords
import io.github.eslamasabry.oc.engine.analytics.health.healthPropertyPatterns
import io.github.eslamasabry.oc.engine.analytics.health.healthPropertyWords
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiField
import io.github.eslamasabry.oc.engine.analytics.umami.UmamiFieldGroup
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
        /** The fields only our own server gets, read from the code's one field list (`UmamiField`). */
        val ownServer: Set<String> = UmamiField.wires,
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
        // The fields our own server may get (UmamiField): their names and every fixed word they take are names too.
        words += UmamiField.wires
        words += UmamiField.entries.flatMap { it.allowedWords.orEmpty() }
        words += setOf("breadcrumbs", "problem_report") // crash-side lines that are not events
        return SendableNames(
            events = productSchema,
            traces = traces,
            properties = healthPropertyWords.keys + healthPropertyPatterns.keys,
            keys = healthKeyWords.keys + healthKeyPatterns.keys + vendorKeys,
            failures = failures,
            words = words,
            ownServer = UmamiField.wires,
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
        for (name in code.events.keys + code.properties + code.keys + code.ownServer) {
            if (name !in listed) out += "What we collect does not list the name `$name` (contractEvents)"
        }
        for (name in listed - structural) {
            if (name !in code.events && name !in code.properties && name !in code.keys && name !in code.ownServer) {
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

        // 3. The in-app policy: every name, every event's own fields, and no ghost names.
        for (name in code.events.keys + code.properties + code.keys + code.failures) {
            if (!has(n.policy, name)) out += "The in-app policy does not name `$name`"
        }
        for (name in code.allFields) if (!has(n.policy, name)) out += "The in-app policy does not name the field `$name`"
        for ((event, fields) in code.events) {
            if (fields.isEmpty()) continue
            val scope = policyEventScope(n.policy, event)
            for (field in fields.keys) {
                if (!has(scope, field)) out += "The in-app policy `$event` entry does not name `$field`"
            }
        }
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
        "it" to ("ogni mese" to Regex("(?i)giornalier[oa]|quotidian[oa]|ogni giorno")),
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
        val device = UmamiDevice("en", "browser", 16, DeviceClass.Phone)
        val source = UmamiSource("website", UmamiBuild("3.0.1", "10081"), device)
        for ((name, fields) in productSchema) {
            val event = umamiEvent(name, fields.mapValues { it.value.first() }, umamiPages.getValue("conversations"),
                source, 1000)!!
            val added = event.payload.data.keys - fields.keys
            assertEquals(setOf("version", "build", "os_major", "device_class"), added, name)
        }
        val wiringPath = "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/" +
            "telemetry/Telemetry.kt"
        val wiring = File(repo, wiringPath).readText()
        assertTrue("UsageTelemetryVendor" in wiring && "UmamiSender(" in wiring, "the notice covers the sender that the app actually builds")
        assertTrue("BuildConfig.VERSION_NAME" in wiring && "BuildConfig.VERSION_CODE" in wiring, "version facts come from this build")
        val firebase = File(repo, "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/telemetry/FirebaseTelemetryVendor.kt").readText()
        assertTrue("setUserProperty(\"app_build\", null)" in firebase, "usage off clears the Google build property")
    }

    @Test
    fun `B-SEC-217 guard - the telemetry wiring reads no device fact beyond the Android major and the class`() {
        val wiring = File(repo, "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/telemetry/Telemetry.kt").readText()
        val device = wiring.substringAfter("val device = {").substringBefore("val configured")
        val allowed = setOf("Build.VERSION.RELEASE", "smallestScreenWidthDp", "Locale.getDefault")
        for (word in listOf(
            "displayMetrics", "widthPixels", "heightPixels", "densityDpi", "Build.MODEL", "Build.MANUFACTURER",
            "Build.BRAND", "Build.DEVICE", "Build.PRODUCT", "Build.HARDWARE", "Build.FINGERPRINT", "Build.SERIAL",
            "Build.ID", "SDK_INT",
            "TimeZone", "ANDROID_ID", "Settings.Secure", "advertising", "telephony", "Display",
        )) assertTrue(word !in device, "the Umami device may not read $word")
        assertTrue(allowed.all { it in device }, "the device reads the Android major, the width class and the language")
    }

    @Test
    fun `B-SEC-218 the notices name the three device fields for our server and no screen size for it`() {
        val md = File(repo, "PRIVACY.md").readText()
        val umami = md.substringAfter("Umami receives the same listed events").substringBefore("The private queue")
        val facts = listOf("Android main version number", "device class (phone or tablet)", "build number",
            "no screen size or resolution")
        for (fact in facts) {
            assertTrue(fact in umami, "PRIVACY.md Umami part must name $fact")
        }
        val saysResolution = Regex("(?i)and\\s+screen\\s+resolution").find(umami) != null
        assertTrue(!saysResolution, "PRIVACY.md must not say Umami gets the resolution")
        assertTrue("no screen size or resolution" in umami, "PRIVACY.md says the screen size is not sent to Umami")
        val en = File(repo, "native/ui/words/src/commonMain/composeResources/values")
        val policy = xmlString(File(en, "strings_native_privacy.xml"), "nativeBundledPrivacy")
        val detail = xmlString(File(en, "strings_s28_privacy.xml"), "nativeTelemetryUsageDetail")
        val device = xmlString(File(en, "strings_s28_privacy.xml"), "collectDevice")
        for ((name, text) in listOf("policy" to policy, "usage detail" to detail)) {
            val namesFields = "main version number" in text && "phone or a tablet" in text
            assertTrue(namesFields, "$name names the major and the class")
            assertTrue("not the screen size" in text, "$name says the screen size is not sent to Umami")
        }
        val googleOnly = "Google Analytics also receives, on its own, a random install ID, the phone model" in device &&
            "never the screen size" in device
        assertTrue(googleOnly, "the page puts the screen size on Google's side only")
        for (tag in AppLanguage.entries.mapNotNull { it.tag }.filter { it != "en" }) {
            val dir = File(en.parentFile, "values-$tag")
            val translated = xmlString(File(dir, "strings_native_privacy.xml"), "nativeBundledPrivacy")
            val umamiPart = translated.substringAfter("Umami").substringBefore("256")
            assertTrue("16" in umamiPart, "$tag policy gives the Android main version example for Umami")
            val line = xmlString(File(dir, "strings_s28_privacy.xml"), "collectDevice")
            assertTrue("Umami" in line && "Google Analytics" in line, "$tag page puts the screen size on Google's side")
        }
        val claimed =
            NeverCollected.entries.any { it.name in setOf("ScreenSize", "DeviceModel", "TimeZone", "Location") }
        assertTrue(!claimed, "the never-list cannot claim what Google Analytics receives on its own")
    }

    @Test
    fun `B-SEC-219 every field our own server may get is named in PRIVACY, the policy and the page`() {
        val md = File(repo, "PRIVACY.md").readText()
        val umami = md.substringAfter("Umami receives the same listed events").substringBefore("The private queue")
        val words = File(repo, "native/ui/words/src/commonMain/composeResources")
        for (tag in AppLanguage.entries.mapNotNull { it.tag }) {
            val dir = File(words, if (tag == "en") "values" else "values-$tag")
            val policy = xmlString(File(dir, "strings_native_privacy.xml"), "nativeBundledPrivacy")
            val page = UmamiFieldGroup.entries.joinToString(" ") { group ->
                xmlString(File(dir, "strings_s28_privacy.xml"), "collectOwn" + group.name)
            }
            for (field in UmamiField.entries) {
                assertTrue("`${field.wire}`" in umami, "PRIVACY.md names `${field.wire}` for Umami")
                assertTrue(has(policy, field.wire), "$tag: the bundled policy names ${field.wire}")
                assertTrue(has(page, field.wire), "$tag: What we collect must name ${field.wire} (own server)")
            }
        }
        val label = xmlString(File(words, "values/strings_s28_privacy.xml"), "collectOwnLabel")
        assertTrue("Only to our own server" in label)
    }

    @Test
    fun `B-SEC-227 our own server gets a random install ID and the phone model, and the notices say so`() {
        val md = File(repo, "PRIVACY.md").readText()
        val umami = md.substringAfter("Umami receives the same listed events").substringBefore("The private queue")
        for (fact in listOf("`install_id`", "`phone_model`", "deleted when you switch usage off", "count installs",
            "not Google's ID")) {
            assertTrue(fact in umami, "PRIVACY.md Umami part must say $fact")
        }
        assertTrue("device or installation ID" !in md, "PRIVACY.md no longer says our server gets no installation ID")
        val en = File(repo, "native/ui/words/src/commonMain/composeResources/values")
        val rules = xmlString(File(en, "strings_s28_privacy.xml"), "collectRules")
        val saysOwnId = "none of those" !in rules && "install ID" in rules
        assertTrue(saysOwnId, "the page says our server gets its own install ID")
        val install = xmlString(File(en, "strings_s28_privacy.xml"), "collectOwnInstall")
        val installOk = "install_id" in install && "switch them off" in install && "not Google" in install
        assertTrue(installOk, "the install line")
        val umamiDir = "native/engine/analytics/src/jvmShared/kotlin/io/github/eslamasabry/oc/engine/analytics/umami/"
        val sender = File(repo, umamiDir + "UmamiSender.kt").readText()
        assertTrue("installId?.on(false)" in sender, "switching usage off deletes the install id")
        val id = File(repo, umamiDir + "UmamiInstallId.kt").readText()
        assertTrue("if (!enabled) return null" in id && "file.delete()" in id, "no id exists while the switch is off")
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
            val stallWords = listOf("cause", "thinking_silent", "tool_running", "retrying", "stream_dead_refetched",
                "stream_silent", "unknown", "app_exit", "exit_reason", "foreground")
            for (word in stallWords)
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

    private fun assertBothVoicePaths(where: String, text: String) {
        val ok = "huggingface.co" in text && "Android 12" in text
        assertTrue(ok, "$where must name both voice paths")
    }

    @Test
    fun `B-SEC-213 privacy policy, What we collect and voice settings name both voice paths truthfully`() {
        val mdText = File(repo, "PRIVACY.md").readText()
        val md = section(mdText, "## Microphone and local voice input", "## Optional assistant on this phone")
        assertBothVoicePaths("PRIVACY.md voice section", md)
        for (fact in listOf("never leaves the phone", "EXTRA_PREFER_OFFLINE", "privacy terms apply")) {
            assertTrue(fact in md, "PRIVACY.md voice section must name '$fact'")
        }
        val wiringPath = "native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/VoiceWiring.kt"
        val wiring = File(repo, wiringPath).readText()
        assertTrue("SpeechRecognizer.createOnDeviceSpeechRecognizer(" in wiring, "uses on-device recognizer")
        assertTrue("RecognizerIntent.EXTRA_PREFER_OFFLINE" in wiring, "sets EXTRA_PREFER_OFFLINE")
        assertTrue("SpeechRecognizer.createSpeechRecognizer(" !in wiring, "never uses default recognizer")
        val kdocDir = "native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/dictation"
        val kdoc = File(repo, "$kdocDir/OnDeviceSpeech.kt").readText()
        assertTrue("audio never leaves the phone" !in kdoc && "Android guarantees" !in kdoc, "KDoc truthful")
        val resources = File(repo, "native/ui/words/src/commonMain/composeResources")
        for (tag in AppLanguage.entries.mapNotNull { it.tag }) {
            val locale = File(resources, if (tag == "en") "values" else "values-$tag")
            val policy = xmlString(File(locale, "strings_native_privacy.xml"), "nativeBundledPrivacy")
            val never = xmlString(File(locale, "strings_s28_privacy.xml"), "collectNever")
            val voice = xmlString(File(locale, "strings_voice_native.xml"), "voiceNativePhoneEngine")
            assertBothVoicePaths("${locale.name} policy", policy)
            assertBothVoicePaths("${locale.name} never", never)
            assertTrue("Android 12" in voice, "${locale.name}: voice")
            assertTrue("createOnDeviceSpeechRecognizer" !in voice && "createOnDeviceSpeechRecognizer" !in never,
                "${locale.name}: no code identifier in the words people read")
            for (overclaim in voiceOverclaims) assertTrue(overclaim !in voice, "${locale.name}: $overclaim")
        }
    }

    @Test
    fun `B-SETUP-013 What we collect and the bundled policy name the start fail kinds in every offered language`() {
        val startFailKinds = listOf("exited", "refused", "password", "withdrawn", "port_busy")
        val resources = File(repo, "native/ui/words/src/commonMain/composeResources")
        for (tag in AppLanguage.entries.mapNotNull { it.tag }) {
            val locale = File(resources, if (tag == "en") "values" else "values-$tag")
            val page = xmlString(File(locale, "strings_s28_privacy.xml"), "collectPhoneSetup")
            val policy = xmlString(File(locale, "strings_native_privacy.xml"), "nativeBundledPrivacy")
            for (word in startFailKinds) {
                assertTrue(has(page, word), "$tag What we collect (collectPhoneSetup) must name $word")
                assertTrue(has(policy, word), "$tag bundled policy must name $word")
            }
        }
    }

    private fun privacyMdFieldViolations(): List<String> {
        val md = File(repo, "PRIVACY.md").readText()
        val telemetry = section(
            md,
            "## Optional crash reports, usage and app health",
            "## Data stored on your device",
        )
        val rows = tableRows(section(telemetry, "### Usage and app health", "### Performance"))
        return productSchema.mapNotNull { (event, fields) ->
            val row = rows[event]
            if (row == null) {
                "PRIVACY.md has no row for `$event`"
            } else {
                val missing = fields.keys.filterNot { "`$it`" in row }
                missing.takeIf { it.isNotEmpty() }?.let { "PRIVACY.md `$event` row missing $it" }
            }
        }
    }

    @Test
    fun `B-SEC-228 each event's fields are named on that event's own notice line`() {
        val failures = privacyMdFieldViolations().toMutableList()
        val offered = AppLanguage.entries.mapNotNull { it.tag }
        assertEquals(firstMessageEnding.keys, offered.toSet(), "every offered language has an ending check")
        val journeyFields = productSchema.getValue("journey_milestone").keys
        val resources = File(repo, "native/ui/words/src/commonMain/composeResources")
        for (tag in offered) {
            val dir = File(resources, if (tag == "en") "values" else "values-$tag")
            val ending = firstMessageEnding.getValue(tag)
            val collect = xmlString(File(dir, "strings_s28_privacy.xml"), "collectJourney")
            val policy = xmlString(File(dir, "strings_native_privacy.xml"), "nativeBundledPrivacy")
            collectNoticeViolation("${dir.name} collectJourney", tag, collect, ending)
                ?.let { failures += it }
            val policyEntry = balancedEventEntry(policy, "journey_milestone")
            policyJourneyViolation("${dir.name} nativeBundledPrivacy", tag, policyEntry, journeyFields, ending)
                ?.let { failures += it }
        }
        assertEquals(emptyList(), failures, "event notice line mismatches:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `B-SEC-228 guard catches journey_milestone missing outcome and fail_kind even when other events name them`() {
        val notices = liveNotices()
        val oldEntry = balancedEventEntry(notices.policy, "journey_milestone")!!
        val stripped = notices.policy.replace(
            oldEntry,
            "journey_milestone (step and after_range: a first-use step and how long after you first agreed)",
        )
        val found = violations(liveCode(), Notices(notices.privacyMd, stripped, notices.contract))
        assertTrue(found.any { "journey_milestone" in it && "outcome" in it }, found.toString())
        assertTrue(found.any { "journey_milestone" in it && "fail_kind" in it }, found.toString())
    }
}

/** Owner-approved English wording (2026-11-26) required verbatim in What we collect and the bundled policy. */
private const val APPROVED_ENDING_LINE =
    "plus how your first message ended (a reply, a stop, an error kind or a refused send), " +
        "as kind words only, never its text. Each step is counted once per install."

/** Localized plain-words phrase for how the first message ended, checked in every offered language. */
private val firstMessageEnding = mapOf(
    "en" to Regex("(?i)how your first message ended"),
    "ar" to Regex("كيف انتهت رسالتك الأولى"),
    "es" to Regex("(?i)cómo terminó tu primer mensaje"),
    "fr" to Regex("(?i)comment s[’']est terminé votre premier message"),
    "id" to Regex("(?i)bagaimana pesan pertama Anda berakhir"),
    "it" to Regex("(?i)come si è concluso il tuo primo messaggio"),
    "ja" to Regex("最初のメッセージがどう終わったか"),
    "pt" to Regex("(?i)como sua primeira mensagem terminou"),
    "ru" to Regex("(?i)чем закончилось ваше первое сообщение"),
    "vi" to Regex("(?i)cách tin nhắn đầu tiên của bạn kết thúc"),
    "zh" to Regex("你的第一条消息如何结束"),
)

private fun hasWord(text: String, word: String): Boolean =
    Regex("""(?<![A-Za-z0-9_])${Regex.escape(word)}(?![A-Za-z0-9_])""").containsMatchIn(text)

private fun balancedEventEntry(text: String, event: String): String? {
    val pattern = """(?<![A-Za-z0-9_])${Regex.escape(event)}(?![A-Za-z0-9_])\s*[(\uFF08]"""
    val open = Regex(pattern).find(text) ?: return null
    var depth = 0
    var end = -1
    for (i in open.range.last until text.length) {
        val c = text[i]
        if (c == '(' || c == '\uFF08') depth++ else if (c == ')' || c == '\uFF09') depth--
        if (depth == 0) {
            end = i
            break
        }
    }
    return if (end >= 0) text.substring(open.range.first, end + 1) else null
}

private fun policyEventScope(policy: String, event: String): String {
    val parts = mutableListOf<String>()
    balancedEventEntry(policy, event)?.let { parts += it }
    for (para in policy.split("\\n\\n")) {
        if (!para.startsWith("- ") && hasWord(para, event)) parts += para
    }
    return parts.joinToString("\n")
}

private fun collectNoticeViolation(
    where: String,
    tag: String,
    text: String,
    ending: Regex,
): String? {
    val parts = mutableListOf<String>()
    if (tag == "en" && APPROVED_ENDING_LINE !in text) parts += "missing approved ending line"
    if (!ending.containsMatchIn(text)) parts += "missing first-message ending"
    val rawTokens = listOf("first_prompt_ended", "after_range", "fail_kind").filter { hasWord(text, it) }
    if (rawTokens.isNotEmpty()) parts += "raw wire names in plain copy $rawTokens"
    return parts.takeIf { it.isNotEmpty() }?.let { "$where: ${it.joinToString(", ")}" }
}

private fun policyJourneyViolation(
    where: String,
    tag: String,
    entry: String?,
    fields: Set<String>,
    ending: Regex,
): String? {
    if (entry == null) return "$where: missing journey_milestone entry"
    val missingFields = fields.filterNot { hasWord(entry, it) }
    val parts = mutableListOf<String>()
    if (missingFields.isNotEmpty()) parts += "missing fields $missingFields"
    if (tag == "en" && APPROVED_ENDING_LINE !in entry) parts += "missing approved ending line"
    if (!ending.containsMatchIn(entry)) parts += "missing first-message ending"
    return parts.takeIf { it.isNotEmpty() }?.let { "$where: ${it.joinToString(", ")}" }
}
