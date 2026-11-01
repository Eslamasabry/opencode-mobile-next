package io.github.eslamasabry.oc.quality.sec

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** A gate finding: a stable id (so `sec-pending.txt` can name it) and the reason. */
data class Finding(val id: String, val message: String) {
    override fun toString(): String = "$id: $message"
}

/**
 * Pending items (plan 03's freeze-pending pattern): a finding whose id is listed is tolerated until the owning slice
 * lands; a listed id that no longer fires fails, so the list can only shrink.
 */
object Pending {
    fun apply(findings: List<Finding>, pending: Set<String>, ids: Set<String>): List<String> {
        val open = findings.filterNot { it.id in pending }.map { it.toString() }
        val stale = pending.filter { it in ids && findings.none { f -> f.id == it } }.map { "$it is listed in sec-pending.txt but holds now: remove it" }
        return open + stale
    }
}

internal object Xml {
    const val ANDROID = "http://schemas.android.com/apk/res/android"

    fun parse(text: String): Element {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        // no DTDs, no external entities (plan 16 §3.1 T3: XML without DTD)
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        f.setFeature("http://xml.org/sax/features/external-general-entities", false)
        f.isExpandEntityReferences = false
        return f.newDocumentBuilder().parse(text.byteInputStream()).documentElement
    }

    fun Element.children(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    /** Attributes as `prefix:name` → value (android: prefix kept so a test reads like the XML). */
    fun Element.attrs(): Map<String, String> =
        (0 until attributes.length).map { attributes.item(it) }
            .filterNot { it.nodeName.startsWith("xmlns") }
            .associate { (if (it.namespaceURI == ANDROID) "android:${it.localName}" else it.nodeName) to it.nodeValue }

    fun Element.tag(): String = localName ?: tagName
}

// --------------------------------------------------------------------------------------------------------------------
/** `NetworkSecurityConfigTest` (plan 16 §1.7 rule 4, 04 §4.7): exactly the base-config element, system trust only. */
object NetworkSecurityConfig {
    const val PATH = "androidApp/src/main/res/xml/network_security_config.xml"
    val IDS = setOf("NSC-ABSENT", "NSC-SHAPE", "NSC-MANIFEST")

    fun check(xml: String): List<Finding> {
        val bad = mutableListOf<Finding>()
        val root = runCatching { Xml.parse(xml) }.getOrElse { return listOf(Finding("NSC-SHAPE", "does not parse")) }
        with(Xml) {
            if (root.tag() != "network-security-config" || root.attrs().isNotEmpty()) bad += Finding("NSC-SHAPE", "root must be a bare <network-security-config>")
            val kids = root.children()
            if (kids.size != 1 || kids[0].tag() != "base-config") {
                bad += Finding("NSC-SHAPE", "only one <base-config> is allowed, found ${kids.map { it.tag() }}")
            }
            kids.filter { it.tag() == "base-config" }.forEach { base ->
                if (base.attrs() != mapOf("cleartextTrafficPermitted" to "true")) {
                    bad += Finding("NSC-SHAPE", "<base-config> must carry exactly cleartextTrafficPermitted=\"true\", found ${base.attrs()}")
                }
                val inner = base.children()
                val ok = inner.isEmpty() || (inner.size == 1 && inner[0].tag() == "certificateTransparency" && inner[0].attrs() == mapOf("enabled" to "true"))
                if (!ok) bad += Finding("NSC-SHAPE", "<base-config> may hold only <certificateTransparency enabled=\"true\"/> (no trust-anchors, certificates, pins), found ${inner.map { it.tag() + it.attrs() }}")
            }
        }
        return bad
    }

    /** The live tree: the file exists, has exactly that shape, and the manifest points at it. */
    fun checkTree(root: File): List<Finding> {
        val file = File(root, PATH)
        if (!file.isFile) return listOf(Finding("NSC-ABSENT", "$PATH does not exist yet (04 §4.7 / 16 §1.7)"))
        val manifest = File(root, ExportedSurface.MANIFEST).readText()
        val pointed = Regex("""android:networkSecurityConfig\s*=\s*"@xml/network_security_config"""").containsMatchIn(manifest)
        return check(file.readText()) + if (pointed) emptyList() else listOf(Finding("NSC-MANIFEST", "the manifest does not set android:networkSecurityConfig=\"@xml/network_security_config\""))
    }
}

// --------------------------------------------------------------------------------------------------------------------
/** `FileProviderPathsTest` (plan 16 §1.8.3): exactly the three roots, exactly as written. */
object FileProviderPaths {
    const val PATH = "androidApp/src/main/res/xml/local_file_paths.xml"
    val IDS = setOf("FP-ABSENT", "FP-SHAPE")

    private val expected =
        setOf(
            "external-path" to mapOf("name" to "shared_storage", "path" to "."),
            "files-path" to mapOf("name" to "camera", "path" to "camera-capture-v1/"),
            "cache-path" to mapOf("name" to "share", "path" to "share/"),
        )

    fun check(xml: String): List<Finding> {
        val root = runCatching { Xml.parse(xml) }.getOrElse { return listOf(Finding("FP-SHAPE", "does not parse")) }
        return with(Xml) {
            val found = root.children().map { it.tag() to it.attrs() }
            buildList {
                if (root.tag() != "paths") add(Finding("FP-SHAPE", "root must be <paths>"))
                found.filter { it !in expected }.forEach { add(Finding("FP-SHAPE", "root not allowed: <${it.first} ${it.second}>")) }
                expected.filter { it !in found }.forEach { add(Finding("FP-SHAPE", "missing root <${it.first} ${it.second}>")) }
                if (found.size != found.toSet().size) add(Finding("FP-SHAPE", "a root is declared twice"))
            }
        }
    }

    fun checkTree(root: File): List<Finding> {
        val file = File(root, PATH)
        return if (!file.isFile) listOf(Finding("FP-ABSENT", "$PATH does not exist yet (16 §1.8.3)")) else check(file.readText())
    }
}

// --------------------------------------------------------------------------------------------------------------------
/**
 * `ExportedSurfaceTest` (plan 16 §1.8.1) on the source manifests: a canonical line per component compared with the
 * reviewed snapshot `exported-surface.txt`, plus the hardening invariants of §1.8.1. The merged-manifest form
 * (`verifyExportedSurface`, library components included) is a build-logic task on `:androidApp` (open, see report).
 */
object ExportedSurface {
    const val MANIFEST = "androidApp/src/main/AndroidManifest.xml"
    const val PKG = "io.github.eslamasabry.opencode_mobile"
    val IDS = setOf("SURFACE-SNAPSHOT", "SURFACE-EXPLICIT", "SURFACE-EXPORTED", "SURFACE-PROVIDER", "SURFACE-MAIN-LAUNCHMODE",
        "SURFACE-MAIN-TASKAFFINITY", "SURFACE-BACKUP", "SURFACE-DEBUGGABLE", "SURFACE-QUERY-ALL", "SURFACE-FILEPROVIDER")

    private val kinds = setOf("activity", "activity-alias", "service", "receiver", "provider")
    private val exportedByName = setOf(".MainActivity", ".ManageSpaceActivity", ".WidgetConfigureActivity")
    private val systemOnlyPermissions =
        setOf("android.permission.BIND_QUICK_SETTINGS_TILE", "android.permission.BIND_JOB_SERVICE", "android.permission.DUMP")

    private fun short(name: String) = if (name.startsWith("$PKG.")) name.removePrefix(PKG) else name

    /** One sorted, canonical line per component. */
    fun render(manifest: String): List<String> =
        with(Xml) {
            val app = Xml.parse(manifest).children().firstOrNull { it.tag() == "application" } ?: return emptyList()
            app.children().filter { it.tag() in kinds }.map { c ->
                val a = c.attrs()
                val filters =
                    c.children().filter { it.tag() == "intent-filter" }.joinToString("") { f ->
                        val parts = f.children().map { e -> "${e.tag()}=" + e.attrs().entries.sortedBy { it.key }.joinToString(",") { "${it.key.removePrefix("android:")}:${it.value}" } }.sorted()
                        " filter(${parts.joinToString(" ")})"
                    }
                listOfNotNull(
                    c.tag(),
                    short(a["android:name"] ?: "?"),
                    "exported=${a["android:exported"] ?: "unset"}",
                    a["android:permission"]?.let { "permission=$it" },
                    a["android:taskAffinity"]?.let { "taskAffinity=\"$it\"" },
                    a["android:launchMode"]?.let { "launchMode=$it" },
                    a["android:process"]?.let { "process=$it" },
                    a["android:isolatedProcess"]?.let { "isolatedProcess=$it" },
                    a["android:grantUriPermissions"]?.let { "grantUriPermissions=$it" },
                ).joinToString(" ") + filters
            }.sorted()
        }

    fun invariants(manifest: String): List<Finding> =
        with(Xml) {
            val root = Xml.parse(manifest)
            val app = root.children().firstOrNull { it.tag() == "application" } ?: return listOf(Finding("SURFACE-BACKUP", "no <application>"))
            val out = mutableListOf<Finding>()
            val appAttrs = app.attrs()
            if (appAttrs["android:allowBackup"] != "false") out += Finding("SURFACE-BACKUP", "<application> must set allowBackup=\"false\"")
            if ("android:debuggable" in appAttrs) out += Finding("SURFACE-DEBUGGABLE", "android:debuggable must never be set in a manifest")
            root.children().filter { it.tag() == "uses-permission" && it.attrs()["android:name"] == "android.permission.QUERY_ALL_PACKAGES" }
                .forEach { _ -> out += Finding("SURFACE-QUERY-ALL", "QUERY_ALL_PACKAGES is never requested") }
            app.children().filter { it.tag() in kinds }.forEach { c ->
                val a = c.attrs()
                val name = short(a["android:name"] ?: "?")
                val hasFilter = c.children().any { it.tag() == "intent-filter" }
                val exported = a["android:exported"]
                if (hasFilter && exported == null) out += Finding("SURFACE-EXPLICIT", "$name has an intent-filter but no explicit android:exported")
                if (exported == "true") {
                    val actions = c.children().filter { it.tag() == "intent-filter" }.flatMap { f -> f.children().filter { it.tag() == "action" }.map { it.attrs()["android:name"] } }
                    val widget = c.tag() == "receiver" && actions.isNotEmpty() && actions.all { it == "android.appwidget.action.APPWIDGET_UPDATE" }
                    val guarded = a["android:permission"] in systemOnlyPermissions
                    when {
                        c.tag() == "provider" -> out += Finding("SURFACE-PROVIDER", "$name: a provider is never exported")
                        name in exportedByName || widget || guarded -> Unit
                        else -> out += Finding("SURFACE-EXPORTED", "$name is exported but is not in plan 16 §1.8.1's exported list")
                    }
                }
                if (name == ".MainActivity") {
                    if (a["android:launchMode"] != "singleTop") out += Finding("SURFACE-MAIN-LAUNCHMODE", ".MainActivity must be launchMode=\"singleTop\"")
                    if (a["android:taskAffinity"] != "") out += Finding("SURFACE-MAIN-TASKAFFINITY", ".MainActivity must set taskAffinity=\"\" (16 §1.8.1)")
                }
                if (a["android:name"] == "androidx.core.content.FileProvider") {
                    val meta = c.children().firstOrNull { it.tag() == "meta-data" }?.attrs()
                    val ok = exported == "false" && a["android:grantUriPermissions"] == "true" &&
                        a["android:authorities"] == "\${applicationId}.localfiles" &&
                        meta?.get("android:name") == "android.support.FILE_PROVIDER_PATHS" && meta["android:resource"] == "@xml/local_file_paths"
                    if (!ok) out += Finding("SURFACE-FILEPROVIDER", "FileProvider must be \${applicationId}.localfiles, not exported, grantUriPermissions, paths @xml/local_file_paths")
                }
            }
            out
        }

    fun checkTree(root: File, snapshot: List<String>): List<Finding> {
        val manifest = File(root, MANIFEST).readText()
        val actual = render(manifest)
        val diff = buildList {
            (actual - snapshot.toSet()).forEach { add(Finding("SURFACE-SNAPSHOT", "not in exported-surface.txt (a change needs a CR16 row): $it")) }
            (snapshot - actual.toSet()).forEach { add(Finding("SURFACE-SNAPSHOT", "in exported-surface.txt but not in the manifest: $it")) }
        }
        return diff + invariants(manifest)
    }
}

// --------------------------------------------------------------------------------------------------------------------
/**
 * `TlsFloorTest` (plan 16 §1.7, SI-10, TI-1): OkHttp ≥ 5.5.1 in the catalog and in every literal coordinate, no
 * downgrade constraint, and no bundled CA or key store in any product source set. The resolved-graph form
 * (`verifyOkHttpFloor` on `stableReleaseRuntimeClasspath`) is a build-logic task (open, see report).
 */
object TlsFloor {
    val FLOOR = listOf(5, 5, 1)
    private val caFile = Regex("""\.(pem|crt|cer|der|bks|p12|pfx|jks|keystore)$""", RegexOption.IGNORE_CASE)

    /** -1, 0, 1. A pre-release (`5.5.1-alpha`) sorts below its release. */
    fun compare(version: String, floor: List<Int> = FLOOR): Int {
        val core = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(core.size, floor.size)) {
            val c = core.getOrElse(i) { 0 }.compareTo(floor.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return if ('-' in version) -1 else 0
    }

    fun checkCatalog(toml: String): List<Finding> {
        val versions = toml.substringAfter("[versions]").substringBefore("\n[")
        val refs = Regex("""module\s*=\s*"com\.squareup\.okhttp3:[^"]+"\s*,\s*version\.ref\s*=\s*"([^"]+)"""").findAll(toml).map { it.groupValues[1] }.toSet()
        val inline = Regex("""module\s*=\s*"com\.squareup\.okhttp3:[^"]+"\s*,\s*version\s*=\s*"([^"]+)"""").findAll(toml).map { it.groupValues[1] }
        val keyVersions = (refs + "okhttp").mapNotNull { key ->
            Regex("""(?m)^\s*${Regex.escape(key)}\s*=\s*"([^"]+)"""").find(versions)?.groupValues?.get(1)?.let { key to it }
        }
        return buildList {
            if (keyVersions.none { it.first == "okhttp" }) add(Finding("TLS-CATALOG", "the catalog has no okhttp version"))
            keyVersions.filter { compare(it.second) < 0 }.forEach { add(Finding("TLS-CATALOG", "${it.first} = ${it.second} is below OkHttp 5.5.1")) }
            inline.filter { compare(it) < 0 }.forEach { add(Finding("TLS-CATALOG", "an okhttp3 library pins $it, below 5.5.1")) }
        }
    }

    fun checkBuildScripts(scripts: List<SecFile>): List<Finding> =
        scripts.flatMap { f ->
            val code = KotlinText.stripComments(f.text)
            // a templated version ("…:${'$'}okhttp") comes from the catalog, which checkCatalog covers
            Regex(""""com\.squareup\.okhttp3:[\w-]+:([^"$]+)"""").findAll(code).filter { compare(it.groupValues[1]) < 0 }
                .map { Finding("TLS-SCRIPT", "${f.rel} pins okhttp3 ${it.groupValues[1]}, below 5.5.1") }.toList() +
                Regex("""okhttp3[^\n]*\b(strictly|prefer|require)\s*\(\s*"([^"]+)"""").findAll(code).filter { compare(it.groupValues[2].trimStart('[', '(')) < 0 }
                    .map { Finding("TLS-SCRIPT", "${f.rel} constrains okhttp3 to ${it.groupValues[2]}") }.toList()
        }

    /** No CA bundle, trust store or key store file inside any product module's src/ (TI rule 1: never bundle a CA). */
    fun checkBundledTrust(paths: List<String>): List<Finding> =
        paths.filter { p -> SecFile(p, "").isProductModule && "/src/" in p && caFile.containsMatchIn(p) }
            .map { Finding("TLS-BUNDLED-CA", "$it bundles a certificate or key store") }
}

// --------------------------------------------------------------------------------------------------------------------
/**
 * `QaHooksAbsentTest` (plan 16 §2.3, SI-17, 14 §4.4) at source level: no build script wires `src/qa` into a non-qa
 * source set, no production code names a class from a `src/qa/` source set, and no production file carries the qa
 * markers. The APK form (`verifyQaHooksAbsent`, dexdump on stableRelease) is a build-logic task (open, see report).
 */
object QaHooks {
    /** At least these three never ship (14 §4.4), even before their `src/qa/` files exist. */
    val ALWAYS = setOf("QaHooksFileReader", "EgressRecorder", "QaFixtureAnalyticsSender")

    fun checkBuildScripts(scripts: List<SecFile>): List<Finding> =
        scripts.flatMap { f ->
            val code = f.code
            Regex("""src/qa\b""").findAll(code).mapNotNull { m ->
                // the nearest enclosing source set name before the srcDir call
                val before = code.substring(0, m.range.first)
                val sameLine = Regex("""(?:getByName|named|maybeCreate|create)\s*\(\s*"(\w+)"\s*\)""").findAll(before.substringAfterLast('\n')).lastOrNull()?.groupValues?.get(1)
                val owner = sameLine ?: enclosingNames(before).firstOrNull { it !in setOf("", "kotlin", "java", "res", "resources", "assets", "manifest", "srcDir", "srcDirs", "apply", "also", "let", "run", "with", "dependencies", "sourceSets", "android", "kotlinOptions") }
                if (owner != null && owner.contains("qa", ignoreCase = true)) null
                else Finding("QA-WIRED", "${f.rel}:${f.lineOf(m.range.first)} wires src/qa into ${owner ?: "an unnamed source set"}")
            }.toList()
        }

    /** Names of the blocks still open at the end of [before], innermost first. */
    private fun enclosingNames(before: String): List<String> {
        val stack = ArrayDeque<String>()
        val token = Regex("""(?:getByName|named|maybeCreate|create)\s*\(\s*"(\w+)"\s*\)\s*(?:\.\s*\w+\s*)?\{|(\w+)\s*\{|\{|\}""")
        for (m in token.findAll(before)) {
            when {
                m.value == "}" -> if (stack.isNotEmpty()) stack.removeLast()
                m.value == "{" -> stack.addLast("")
                else -> stack.addLast(m.groupValues[1].ifEmpty { m.groupValues[2] })
            }
        }
        return stack.reversed()
    }

    fun qaClassNames(files: List<SecFile>): Set<String> =
        files.filter { it.isKotlin && it.isQaSource }
            .flatMap { f -> Regex("""\b(?:class|object|interface)\s+([A-Z]\w*)""").findAll(f.code).map { it.groupValues[1] } }
            .toSet() + ALWAYS

    fun checkSources(files: List<SecFile>, versionProperties: String): List<Finding> {
        val names = qaClassNames(files)
        val nameRegex = Regex("""\b(${names.joinToString("|") { Regex.escape(it) }})\b""")
        val prod = files.filter { it.isProduction }
        return buildList {
            prod.filter { it.isKotlin }.forEach { f ->
                nameRegex.findAll(f.code).forEach { add(Finding("QA-CLASS", "${f.rel}:${f.lineOf(it.range.first)} names qa-only ${it.value}")) }
            }
            prod.forEach { f ->
                Regex("""qa-hooks\.json|\b10\.0\.2\.2\b""").findAll(f.code).forEach { add(Finding("QA-MARKER", "${f.rel}:${f.lineOf(it.range.first)} carries ${it.value}")) }
                if (f.isXml && Regex("""android:debuggable\s*=\s*"true"""").containsMatchIn(f.code)) add(Finding("QA-DEBUGGABLE", "${f.rel} sets android:debuggable"))
            }
            Regex("""(?m)^\s*versionName\s*=\s*(\S+)""").find(versionProperties)?.groupValues?.get(1)?.let { v ->
                if (v.contains("-qa", ignoreCase = true)) add(Finding("QA-VERSION", "versionName $v carries -qa"))
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------------------------
/** The Android lint severity table of plan 16 §2.2: these ids are errors in `androidApp/lint.xml` and the conventions. */
object LintTable {
    val ERRORS: Set<String> =
        setOf(
            "TrustAllX509TrustManager", "BadHostnameVerifier", "AllowAllHostnameVerifier", "SSLCertificateSocketFactoryCreateSocket",
            "SSLCertificateSocketFactoryGetInsecure", "WebViewClientOnReceivedSslError", "AddJavascriptInterface",
            "SetJavaScriptEnabled", "UnspecifiedImmutableFlag", "MutableImplicitPendingIntent", "UnsafeImplicitIntentLaunch",
            "UnsafeIntentLaunch", "ExportedContentProvider", "ExportedReceiver", "ExportedService", "ExportedPreferenceActivity",
            "GrantAllUris", "WorldReadableFiles", "WorldWriteableFiles", "HardcodedDebugMode", "SecureRandom", "TrulyRandom",
            "GetInstance", "UnsafeDynamicallyLoadedCode", "UnsafeNativeCodeLocation", "PackageManagerGetSignatures",
            "AcceptsUserCertificates", "DataExtractionRules", "AllowBackup", "UnprotectedSMSBroadcastReceiver",
        )
    const val NSC_SUPPRESSED = "InsecureBaseConfiguration"

    fun checkLintXml(xml: String): List<Finding> {
        val root = runCatching { Xml.parse(xml) }.getOrElse { return listOf(Finding("LINT-XML", "lint.xml does not parse")) }
        return with(Xml) {
            val issues = root.children().filter { it.tag() == "issue" }
            val severity = issues.associate { it.attrs()["id"] to it.attrs()["severity"] }
            buildList {
                ERRORS.filter { severity[it] != "error" }.forEach { add(Finding("LINT-XML", "$it must be severity=\"error\" (found ${severity[it]})")) }
                issues.filter { it.attrs()["id"] == "all" }.forEach { _ -> add(Finding("LINT-XML", "an id=\"all\" override would hide the table")) }
                // the one suppression: InsecureBaseConfiguration, for network_security_config.xml only, with its reason
                val nsc = issues.filter { it.attrs()["id"] == NSC_SUPPRESSED }
                val ignores = nsc.flatMap { it.children() }.filter { it.tag() == "ignore" }.map { it.attrs()["path"] ?: it.attrs()["regexp"] ?: "" }
                if (nsc.size != 1 || nsc[0].attrs()["severity"] == "ignore" || ignores != listOf("src/main/res/xml/network_security_config.xml")) {
                    add(Finding("LINT-XML", "$NSC_SUPPRESSED must be ignored only for src/main/res/xml/network_security_config.xml"))
                }
                if (!xml.contains("TrustGateInterceptor is the cleartext boundary")) add(Finding("LINT-XML", "the $NSC_SUPPRESSED suppression lost its reason"))
                issues.filter { it.attrs()["id"] in ERRORS && it.children().any { c -> c.tag() == "ignore" } }
                    .forEach { add(Finding("LINT-XML", "${it.attrs()["id"]} may not be ignored by path in lint.xml (suppress in code with a reason)")) }
            }
        }
    }

    /** The convention source (`LintPolicy.kt` in build-logic) names exactly the table. */
    fun checkConvention(kotlin: String): List<Finding> {
        val block = KotlinText.stripComments(kotlin).substringAfter("SECURITY_ERRORS", "").substringBefore(")")
        val ids = Regex(""""(\w+)"""").findAll(block).map { it.groupValues[1] }.toSet()
        return buildList {
            (ERRORS - ids).forEach { add(Finding("LINT-CONVENTION", "LintPolicy.SECURITY_ERRORS misses $it")) }
            (ids - ERRORS).forEach { add(Finding("LINT-CONVENTION", "LintPolicy.SECURITY_ERRORS has $it, which plan 16 §2.2 does not list")) }
            if (!Regex("""abortOnError\s*=\s*true""").containsMatchIn(kotlin)) add(Finding("LINT-CONVENTION", "abortOnError must be true"))
            if (Regex("""\bbaseline\s*=""").containsMatchIn(KotlinText.stripComments(kotlin))) add(Finding("LINT-CONVENTION", "no lint baseline (03 §19.5)"))
        }
    }
}
