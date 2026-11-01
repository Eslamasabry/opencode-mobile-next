package io.github.eslamasabry.oc.quality.sec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** P9-style proofs for the plan 16 §2.3 gates: each fails on a planted violation and passes the plan's own text. */
class SecGatesPlantedTest {
    private fun ids(findings: List<Finding>) = findings.map { it.id }

    // ---- NetworkSecurityConfigTest
    private val nscPlan = """
        <network-security-config>
            <base-config cleartextTrafficPermitted="true">
                <certificateTransparency enabled="true" />
            </base-config>
        </network-security-config>
    """.trimIndent()

    @Test
    fun `NetworkSecurityConfigTest passes the plan's file, with and without the CT opt-in`() {
        assertClean(NetworkSecurityConfig.check(nscPlan), "plan 16 §1.7 file")
        assertClean(NetworkSecurityConfig.check("""<network-security-config><base-config cleartextTrafficPermitted="true"/></network-security-config>"""), "pre-P16-2 file")
    }

    @Test
    fun `NetworkSecurityConfigTest fails on planted user trust, bundled CA, a domain config and a pin set`() {
        val planted =
            listOf(
                nscPlan.replace("<certificateTransparency enabled=\"true\" />", "<trust-anchors><certificates src=\"user\"/></trust-anchors>"),
                nscPlan.replace("<certificateTransparency enabled=\"true\" />", "<trust-anchors><certificates src=\"@raw/ca\"/></trust-anchors>"),
                nscPlan.replace("</network-security-config>", "<domain-config><domain>example.com</domain><pin-set/></domain-config></network-security-config>"),
                nscPlan.replace("<certificateTransparency enabled=\"true\" />", "<certificateTransparency enabled=\"false\" />"),
                nscPlan.replace("cleartextTrafficPermitted=\"true\"", "cleartextTrafficPermitted=\"true\" overridePins=\"true\""),
                nscPlan.replace("</network-security-config>", "<debug-overrides><trust-anchors><certificates src=\"user\"/></trust-anchors></debug-overrides></network-security-config>"),
                """<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><network-security-config>&e;</network-security-config>""",
            )
        planted.forEachIndexed { i, xml -> assertTrue(ids(NetworkSecurityConfig.check(xml)).contains("NSC-SHAPE"), "planted NSC #$i passed") }
    }

    @Test
    fun `NetworkSecurityConfigTest fails when the manifest does not point at the file`() {
        val t = Planted()
            .file(NetworkSecurityConfig.PATH, nscPlan)
            .file(ExportedSurface.MANIFEST, """<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:allowBackup="false"/></manifest>""")
        assertEquals(listOf("NSC-MANIFEST"), ids(NetworkSecurityConfig.checkTree(t.root)))
    }

    // ---- FileProviderPathsTest
    private val pathsPlan = """
        <paths>
            <external-path name="shared_storage" path="." />
            <files-path name="camera" path="camera-capture-v1/" />
            <cache-path name="share" path="share/" />
        </paths>
    """.trimIndent()

    @Test
    fun `FileProviderPathsTest passes the plan's three roots`() = assertClean(FileProviderPaths.check(pathsPlan), "plan 16 §1.8.3 file")

    @Test
    fun `FileProviderPathsTest fails on planted root-path, whole filesDir, a missing root and a duplicate`() {
        val planted =
            listOf(
                pathsPlan.replace("</paths>", "<root-path name=\"all\" path=\"\" /></paths>"),
                pathsPlan.replace("path=\"camera-capture-v1/\"", "path=\".\""),
                pathsPlan.replace("<cache-path name=\"share\" path=\"share/\" />", "<cache-path name=\"share\" path=\"/\" />"),
                pathsPlan.replace("<cache-path name=\"share\" path=\"share/\" />", ""),
                pathsPlan.replace("</paths>", "<external-files-path name=\"x\" path=\".\" /></paths>"),
                pathsPlan.replace("</paths>", "<cache-path name=\"share\" path=\"share/\" /></paths>"),
            )
        planted.forEachIndexed { i, xml -> assertTrue(ids(FileProviderPaths.check(xml)).contains("FP-SHAPE"), "planted paths #$i passed") }
    }

    // ---- ExportedSurfaceTest
    private fun manifest(components: String, app: String = """android:allowBackup="false"""") = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application $app>
                $components
            </application>
        </manifest>
    """.trimIndent()

    private val mainOk = """
        <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop" android:taskAffinity="">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity>
        <service android:name=".AttentionTileService" android:exported="true" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE" />
        <receiver android:name=".AttentionWidget" android:exported="true">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
        </receiver>
        <receiver android:name=".ScheduledPromptReceiver" android:exported="false" />
        <provider android:name="androidx.core.content.FileProvider" android:authorities="${'$'}{applicationId}.localfiles" android:exported="false" android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/local_file_paths" />
        </provider>
    """

    @Test
    fun `ExportedSurfaceTest passes a section 1_8_1 shaped manifest`() = assertClean(ExportedSurface.invariants(manifest(mainOk)), "§1.8.1 manifest")

    @Test
    fun `ExportedSurfaceTest fails on planted exported receiver, exported provider, hijackable main activity and debuggable`() {
        val cases =
            mapOf(
                "SURFACE-EXPORTED" to manifest(mainOk.replace(".ScheduledPromptReceiver\" android:exported=\"false\"", ".ScheduledPromptReceiver\" android:exported=\"true\"")),
                "SURFACE-PROVIDER" to manifest(mainOk.replace("android:authorities=\"\${applicationId}.localfiles\" android:exported=\"false\"", "android:authorities=\"\${applicationId}.localfiles\" android:exported=\"true\"")),
                "SURFACE-MAIN-TASKAFFINITY" to manifest(mainOk.replace(" android:taskAffinity=\"\"", "")),
                "SURFACE-MAIN-LAUNCHMODE" to manifest(mainOk.replace("singleTop", "standard")),
                "SURFACE-EXPLICIT" to manifest(mainOk.replace("<receiver android:name=\".AttentionWidget\" android:exported=\"true\">", "<receiver android:name=\".AttentionWidget\">")),
                "SURFACE-DEBUGGABLE" to manifest(mainOk, """android:allowBackup="false" android:debuggable="true""""),
                "SURFACE-BACKUP" to manifest(mainOk, """android:allowBackup="true""""),
                "SURFACE-FILEPROVIDER" to manifest(mainOk.replace("android:grantUriPermissions=\"true\"", "android:grantUriPermissions=\"false\"")),
                "SURFACE-QUERY-ALL" to manifest(mainOk).replace("<application", "<uses-permission android:name=\"android.permission.QUERY_ALL_PACKAGES\" /><application"),
            )
        cases.forEach { (id, xml) -> assertTrue(id in ids(ExportedSurface.invariants(xml)), "planted $id passed: ${ExportedSurface.invariants(xml)}") }
    }

    @Test
    fun `ExportedSurfaceTest fails when a component or an intent filter changes without the snapshot`() {
        val t = Planted().file(ExportedSurface.MANIFEST, manifest(mainOk))
        val snapshot = ExportedSurface.render(manifest(mainOk))
        assertClean(ExportedSurface.checkTree(t.root, snapshot), "unchanged manifest")
        val widened = manifest(mainOk.replace("</intent-filter>\n        </activity>", "</intent-filter><intent-filter><action android:name=\"android.intent.action.VIEW\" /><data android:scheme=\"https\" /></intent-filter>\n        </activity>"))
        Planted().file(ExportedSurface.MANIFEST, widened).let { w ->
            assertTrue("SURFACE-SNAPSHOT" in ids(ExportedSurface.checkTree(w.root, snapshot)), "a new intent filter slipped past the snapshot")
        }
    }

    @Test
    fun `sec-pending tolerates a listed finding and fails a listed id that holds now`() {
        val f = listOf(Finding("SURFACE-MAIN-TASKAFFINITY", "x"))
        assertClean(Pending.apply(f, setOf("SURFACE-MAIN-TASKAFFINITY"), ExportedSurface.IDS), "listed finding")
        assertEquals(1, Pending.apply(emptyList(), setOf("SURFACE-MAIN-TASKAFFINITY"), ExportedSurface.IDS).size)
        assertEquals(1, Pending.apply(f, emptySet(), ExportedSurface.IDS).size)
    }

    // ---- TlsFloorTest
    @Test
    fun `TlsFloorTest fails on a planted OkHttp below 5_5_1 in the catalog, a script and a constraint, and on a bundled CA`() {
        assertClean(TlsFloor.checkCatalog("[versions]\nokhttp = \"5.5.1\"\n[libraries]\n"), "5.5.1")
        assertClean(TlsFloor.checkCatalog("[versions]\nokhttp = \"5.6.0\"\n[libraries]\n"), "5.6.0")
        assertEquals(listOf("TLS-CATALOG"), ids(TlsFloor.checkCatalog("[versions]\nokhttp = \"5.5.0\"\n[libraries]\n")))
        assertEquals(listOf("TLS-CATALOG"), ids(TlsFloor.checkCatalog("[versions]\nokhttp = \"5.5.1-alpha1\"\n")))
        assertEquals(listOf("TLS-CATALOG"), ids(TlsFloor.checkCatalog("[versions]\nkotlin = \"2.4.21\"\n[libraries]\n")))
        assertEquals(
            listOf("TLS-CATALOG"),
            ids(TlsFloor.checkCatalog("[versions]\nokhttp = \"5.5.1\"\nok4 = \"4.12.0\"\n[libraries]\nold = { module = \"com.squareup.okhttp3:okhttp\", version.ref = \"ok4\" }\n")),
        )
        val scripts =
            listOf(
                SecFile("core/net-okhttp/build.gradle.kts", "dependencies { implementation(\"com.squareup.okhttp3:okhttp:4.12.0\") }"),
                SecFile("androidApp/build.gradle.kts", "configurations.all { resolutionStrategy { dependencySubstitution { } } }\ndependencies { implementation(\"com.squareup.okhttp3:okhttp\") { version { strictly(\"5.4.0\") } } }"),
                SecFile("core/net/build.gradle.kts", "dependencies { implementation(\"com.squareup.okhttp3:okhttp:5.5.1\") }"),
            )
        assertEquals(listOf("TLS-SCRIPT", "TLS-SCRIPT"), ids(TlsFloor.checkBuildScripts(scripts)))
        assertEquals(
            listOf("TLS-BUNDLED-CA", "TLS-BUNDLED-CA"),
            ids(TlsFloor.checkBundledTrust(listOf("androidApp/src/main/res/raw/corp_ca.pem", "core/net-okhttp/src/main/resources/trust.bks", "spikes/netp/src/test/resources/server.p12", "androidApp/src/main/res/xml/x.xml"))),
        )
    }

    // ---- QaHooksAbsentTest
    @Test
    fun `QaHooksAbsentTest fails on a planted stableRelease with src qa wired into main`() {
        val wired = SecFile("androidApp/build.gradle.kts", """
            android {
                sourceSets {
                    getByName("main") { kotlin.srcDir("src/qa/kotlin") }
                    named("stableQa") { kotlin.srcDir("src/qa/kotlin") }
                }
            }
            android.sourceSets.getByName("release").kotlin.srcDir("src/qa/kotlin")
            android.sourceSets.getByName("stableQa").res.srcDir("src/qa/res")
        """.trimIndent())
        assertEquals(listOf("QA-WIRED", "QA-WIRED"), ids(QaHooks.checkBuildScripts(listOf(wired))), "main and release wired, stableQa fine")
    }

    @Test
    fun `QaHooksAbsentTest fails on a planted qa class, qa marker and qa version name in production`() {
        val t = Planted()
            .file("androidApp/src/qa/kotlin/p/QaHooksFileReader.kt", "class QaHooksFileReader\nclass QaFixtureServer")
            .file("androidApp/src/main/kotlin/p/Graph.kt", "val r = QaHooksFileReader()\nval s = QaFixtureServer()\nval f = \"qa-hooks.json\"")
            .file("engine/updates/src/commonMain/kotlin/p/Feed.kt", "val emu = \"http://10.0.2.2:4096\"")
            .file("androidApp/src/qa/kotlin/p/QaOnly.kt", "val ok = \"http://10.0.2.2:4096\" + QaHooksFileReader()")
        val findings = QaHooks.checkSources(t.files(), "versionCode=10001\nversionName=3.0.0-qa\n")
        assertEquals(listOf("QA-CLASS", "QA-CLASS", "QA-MARKER", "QA-MARKER", "QA-VERSION"), ids(findings).sorted(), findings.toString())
        assertClean(QaHooks.checkSources(Planted().file("core/model/src/commonMain/kotlin/p/A.kt", "val a = 1").files(), "versionName=3.0.0\n"), "clean tree")
    }

    // ---- LintTableTest
    private val lintOk = buildString {
        append("<lint>\n")
        LintTable.ERRORS.forEach { append("  <issue id=\"$it\" severity=\"error\" />\n") }
        append("  <!-- Kotlin's TrustGateInterceptor is the cleartext boundary (04 §4.6, B-PROTO-139) -->\n")
        append("  <issue id=\"InsecureBaseConfiguration\">\n    <ignore path=\"src/main/res/xml/network_security_config.xml\" />\n  </issue>\n</lint>\n")
    }

    @Test
    fun `LintTableTest passes the full table and fails on a planted downgrade, a missing id and a widened suppression`() {
        assertClean(LintTable.checkLintXml(lintOk), "full table")
        val planted =
            listOf(
                lintOk.replace("<issue id=\"TrustAllX509TrustManager\" severity=\"error\" />", "<issue id=\"TrustAllX509TrustManager\" severity=\"warning\" />"),
                lintOk.replace("<issue id=\"AddJavascriptInterface\" severity=\"error\" />", ""),
                lintOk.replace("<ignore path=\"src/main/res/xml/network_security_config.xml\" />", "<ignore path=\"src/main/res/xml/\" />"),
                lintOk.replace("<issue id=\"InsecureBaseConfiguration\">", "<issue id=\"InsecureBaseConfiguration\" severity=\"ignore\">"),
                lintOk.replace("TrustGateInterceptor is the cleartext boundary", "fine"),
                lintOk.replace("<issue id=\"AllowBackup\" severity=\"error\" />", "<issue id=\"AllowBackup\" severity=\"error\"><ignore path=\"src/main/AndroidManifest.xml\" /></issue>"),
                lintOk.replace("<lint>", "<lint><issue id=\"all\" severity=\"ignore\" />"),
            )
        planted.forEachIndexed { i, xml -> assertTrue(LintTable.checkLintXml(xml).isNotEmpty(), "planted lint.xml #$i passed") }
    }

    @Test
    fun `LintTableTest fails on a planted convention that drops an id, adds a baseline or stops aborting`() {
        val ok = "object LintPolicy { val SECURITY_ERRORS = setOf(${LintTable.ERRORS.joinToString { "\"$it\"" }})\n fun Lint.oc() { abortOnError = true } }"
        assertClean(LintTable.checkConvention(ok), "full convention")
        assertTrue(LintTable.checkConvention(ok.replace("\"GetInstance\", ", "").replace(", \"GetInstance\"", "")).isNotEmpty(), "dropped id passed")
        assertTrue(LintTable.checkConvention(ok.replace("abortOnError = true", "abortOnError = false")).isNotEmpty(), "abortOnError false passed")
        assertTrue(LintTable.checkConvention(ok.replace("abortOnError = true", "abortOnError = true; baseline = file(\"b.xml\")")).isNotEmpty(), "baseline passed")
    }
}
