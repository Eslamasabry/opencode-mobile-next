import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins { id("oc.android.application") }

// Telemetry vendor adapter (Crashlytics, Analytics, Performance SDK). The private Firebase config is never in git: the
// Google Services and Crashlytics plugins are applied only when it exists (never the Performance plugin). OC_GOOGLE_SERVICES_DIR (Gradle property or environment, default
// ~/.config/oc-app/firebase) holds google-services-<applicationId>.json; the matching file is copied next to each flavor
// at configure time (both copies are git-ignored). Without it the build works and the app reports through the no-op sink.
run {
    val dir =
        File(
            providers.gradleProperty("OC_GOOGLE_SERVICES_DIR").orNull
                ?: System.getenv("OC_GOOGLE_SERVICES_DIR")
                ?: "${System.getProperty("user.home")}/.config/oc-app/firebase"
        )
    val flavorIds =
        mapOf(
            "preview" to "io.github.eslamasabry.opencode_mobile.native",
            "switch" to "io.github.eslamasabry.opencode_mobile",
        )
    var found = false
    for ((flavor, id) in flavorIds) {
        val source = File(dir, "google-services-$id.json")
        val target = file("src/$flavor/google-services.json")
        if (source.isFile) {
            target.parentFile.mkdirs()
            source.copyTo(target, overwrite = true)
            found = true
        } else {
            target.delete()
        }
    }
    if (found) {
        apply(plugin = "com.google.gms.google-services")
        apply(plugin = "com.google.firebase.crashlytics")
    } else {
        logger.lifecycle("No Firebase config in $dir: telemetry is a no-op in this build.")
    }
}

/** E01-S01: what the release signing file says. Values are never logged; only which keys are missing. */
class ReleaseSigning(val source: File, val storeFile: File?, val storePassword: String?, val keyAlias: String?, val keyPassword: String?) {
    val missing: List<String> =
        listOfNotNull(
            "storeFile".takeIf { storeFile == null },
            "storePassword".takeIf { storePassword.isNullOrEmpty() },
            "keyAlias".takeIf { keyAlias.isNullOrEmpty() },
            "keyPassword".takeIf { keyPassword.isNullOrEmpty() },
        )
    val present: Boolean get() = source.isFile
    val complete: Boolean get() = present && missing.isEmpty() && storeFile?.isFile == true
}

val releaseSigning: ReleaseSigning = run {
    val repo = rootDir.parentFile
    val named =
        providers.environmentVariable("OC_KEY_PROPERTIES").orNull?.takeIf { it.isNotBlank() }
            ?: providers.gradleProperty("OC_KEY_PROPERTIES").orNull?.takeIf { it.isNotBlank() }
    val source: File = if (named != null) File(named) else File(repo, "android/key.properties")
    val props = Properties()
    if (source.isFile) source.inputStream().use(props::load)
    val storePath: String = props.getProperty("storeFile").orEmpty().trim()
    val store: File? =
        when {
            storePath.isEmpty() -> null
            File(storePath).isAbsolute -> File(storePath)
            else -> File(repo, "android/app/$storePath")
        }
    ReleaseSigning(
        source,
        store,
        props.getProperty("storePassword"),
        props.getProperty("keyAlias"),
        props.getProperty("keyPassword"),
    )
}
val releaseDebugSigned: Boolean = providers.gradleProperty("ocReleaseDebugSigned").orNull == "true"
val releaseRequested: Boolean = gradle.startParameter.taskNames.any { it.substringAfterLast(':').contains("Release") }
val abiSplits: Boolean = providers.gradleProperty("ocAbiSplits").orNull?.let { it == "true" } ?: releaseRequested

when {
    releaseDebugSigned ->
        logger.lifecycle(
            "Release signing: DEBUG KEY (ocReleaseDebugSigned=true). A build-shape proof only; the identity gate " +
                "refuses this APK and it must never be installed over the Flutter app."
        )
    releaseSigning.complete -> logger.info("Release signing: the lineage key from ${releaseSigning.source}.")
    releaseSigning.present ->
        logger.lifecycle(
            "Release signing skipped: ${releaseSigning.source} is incomplete (" +
                (releaseSigning.missing.ifEmpty { listOf("storeFile is not a file") }).joinToString() +
                "). The release APK is left unsigned."
        )
    releaseRequested ->
        logger.lifecycle(
            "Release signing skipped: no ${releaseSigning.source} (or OC_KEY_PROPERTIES). The release APK is left " +
                "unsigned; sign it with apksigner and run tool/release/identity_gate.sh before delivery."
        )
}

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        val requestedAgentFloor = providers.gradleProperty("ocQaAgentInstallMinFreeBytes").orNull
        val agentInstallFloor =
            requestedAgentFloor?.toLongOrNull()
                ?: if (requestedAgentFloor?.matches(Regex("[+]?[0-9]+")) == true) Long.MAX_VALUE
                else 0L
        buildConfigField("long", "AGENT_INSTALL_MIN_FREE_BYTES", "${agentInstallFloor}L")
        buildConfigField("int", "PHONE_SERVER_PORT", "4098")
    }
    // E01-S01: the release signer. The same file and the same four keys the Flutter app reads
    // (android/app/build.gradle.kts: <repo>/android/key.properties with storeFile, storePassword, keyAlias, keyPassword;
    // a relative storeFile resolves against <repo>/android/app as Flutter's file() does). OC_KEY_PROPERTIES (environment
    // or Gradle property) names another file of that shape, so CI can keep it outside the repository. Without a
    // complete file the release stays unsigned (it is signed afterwards with apksigner and checked by the identity
    // gate). -PocReleaseDebugSigned=true signs it with this machine's debug key for a build-shape proof only: the
    // identity gate refuses that APK, so it can never be delivered.
    signingConfigs {
        if (releaseSigning.complete) {
            create("lineage") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
            }
        }
    }
    // E01-S05/E01-S08: ABI splits for release builds (arm64-v8a, x86_64, universal). AGP gives every split the
    // variant's versionCode; verifySwitchReleaseIdentity proves it. Splits are off for debug builds so the everyday
    // `assembleDebug` keeps one APK; -PocAbiSplits=true|false overrides.
    splits {
        abi {
            isEnable = abiSplits
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    // Slice-0 proofs P3 and start-up (native/proof-p3-2026-11-01): a release build that the shell
    // may profile, signed
    // with the debug key so Macrobenchmark can install it. R8 on, like the release the plan ships
    // (ADR §9.9).
    buildTypes {
        // E01-S08: the shipped release shape. R8 (shrink, optimize, obfuscate) and resource shrinking, with the keep
        // rules in release-rules.pro (serialization, Metro, workers, widget providers, frozen components, JNI).
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "release-rules.pro")
            signingConfig =
                when {
                    releaseDebugSigned -> signingConfigs.getByName("debug")
                    releaseSigning.complete -> signingConfigs.getByName("lineage")
                    else -> null
                }
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "benchmark-rules.pro",
            )
        }
        // Migration proof (docs/native/qa/migration-proof-2026-11-03): the release build with
        // :android:migration's
        // `qa` StepInterrupter (files/native/qa-kill-after, qa-delay-ms, qa-free-bytes) for the J2
        // kill points.
        // Emulator/lab only: signed with the build machine's debug key, never delivered.
        create("qa") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // Unchanged by E01-S08: the migration proof's qa build stays un-shrunk, as before.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    // Slice-0 P5 + P12-MIGRATION: the same-package build. `switch` has no applicationIdSuffix, so
    // it is
    // io.github.eslamasabry.opencode_mobile and installs OVER the Flutter app (adb install -r) when
    // signed with the
    // same key; on first launch MigrationWiring runs the day-1 migration behind the update gate.
    // See
    // docs/native/units/P12-MIGRATION.md "Building the same-package APK" for the signing step.
    // P12-FIX-LINUX-VOICE, as the Flutter app (android/app/build.gradle.kts): proot, its loader and
    // the phone engine are
    // run as programs from the native library folder, so the libraries must be extracted real files
    // (legacy packaging).
    // The phone engine's hashes are checked at run time, so the strip step must leave libaiteam_*
    // untouched.
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libaiteam_*.so"
            // The Kotlin API loads only libsherpa-onnx-jni.so + libonnxruntime.so (plan 10 §2.3).
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }
    productFlavors {
        getByName("preview") { buildConfigField("int", "PHONE_SERVER_PORT", "4100") }
        create("switch") {
            dimension = "channel"
        }
        // Dev loop (tool/dev/README.md): `installDevDebug` puts "OpenCode Dev" beside the real and preview apps. Own
        // package (own data, starts clean), its own keystore (devSigning below), debug build type only. src/dev
        // retargets the launcher shortcuts, tints the icon and turns off what registers outside the app (widgets,
        // Quick Settings tiles, the opencode-mobile:// link filter, the test-report broadcast name).
        create("dev") {
            dimension = "channel"
            applicationIdSuffix = ".native.dev"
            versionNameSuffix = "-dev"
            buildConfigField("int", "PHONE_SERVER_PORT", "4102")
        }
    }
}

// Dev loop signing: a properties file (storeFile, storePassword, keyAlias, keyPassword) named by `oc.devSigning` in
// local.properties, the Gradle property oc.devSigning or OC_DEV_SIGNING. Never the machine's debug key: on the PC
// that key is a real lineage, so without the file the dev build stops instead of falling back to it.
val devSigning: Properties? = run {
    val local = Properties()
    rootDir.resolve("local.properties").takeIf { it.isFile }?.inputStream()?.use(local::load)
    val named =
        providers.gradleProperty("oc.devSigning").orNull
            ?: providers.environmentVariable("OC_DEV_SIGNING").orNull
            ?: local.getProperty("oc.devSigning")
    named?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use(::load) }
    }
}
devSigning?.let { p ->
    android.signingConfigs.create("dev") {
        storeFile = File(p.getProperty("storeFile"))
        storePassword = p.getProperty("storePassword")
        keyAlias = p.getProperty("keyAlias")
        keyPassword = p.getProperty("keyPassword")
        enableV4Signing = true // the .idsig `adb install --incremental` streams from
    }
}
androidComponents.beforeVariants(androidComponents.selector().withFlavor("channel" to "dev")) { variant ->
    variant.enable = variant.buildType == "debug" // the loop needs devDebug only
    // Its sources are the preview's; tests and lint run on preview/switch, so `test*`/`connected*` do no extra work.
    variant.hostTests.values.forEach { it.enable = false }
    variant.deviceTests.values.forEach { it.enable = false }
}
androidComponents.onVariants(androidComponents.selector().withFlavor("channel" to "dev")) { variant ->
    val dev = android.signingConfigs.findByName("dev")
    if (dev != null) {
        variant.signingConfig.setConfig(dev)
    } else {
        val refuse =
            tasks.register("refuseDevWithoutDevKey") {
                doLast {
                    throw GradleException(
                        "No dev keystore: set oc.devSigning in native/local.properties (tool/dev/README.md). The dev " +
                            "build never falls back to this machine's debug key."
                    )
                }
            }
        tasks.matching { it.name == "preDevDebugBuild" }.configureEach { dependsOn(refuse) }
    }
}

dependencies {
    // One line per shipped module (plan 03 §20.1: the coordinator adds each at merge). Metro merges
    // every
    // contribution on this classpath into AndroidAppGraph.
    listOf(
            ":core:common",
            ":core:security",
            ":core:model",
            ":core:domain",
            ":engine:connection",
            ":engine:transcript",
            ":ui:design",
            ":ui:words",
            ":ui:routes",
            ":ui:kit",
            ":ui:shell",
            ":feature:chats",
            ":feature:chat",
            ":feature:chat-composer",
            ":feature:servers",
            ":feature:setupassistant",
            ":core:data",
            ":core:net",
            ":protocol:oc2",
            ":engine:profiles",
            ":engine:composer",
            ":engine:media",
            ":android:media",
            ":android:platform", // server editor: fixed-package Tailscale handoff and package
            // visibility
            ":engine:agents",
            ":engine:delivery",
            ":engine:requests",
            ":engine:attention", // Native watched-server Inbox and explicit monitoring consent.
            ":ui:kit-chat",
            ":ui:kit-content",
            ":feature:settings",
            ":feature:capabilities",
            ":feature:onboarding",
            ":engine:settings",
            ":engine:analytics",
            ":engine:diagnostics",
            ":android:platform", // SEC: the adopted category-only native crash handler/store
            ":android:notifications",
            ":android:platform", // ANDR: real default-network tracker for consented monitors
            ":android:background", // BUILD-ANDROID-BG-PLATFORM: live mode, pause, sign-in lease
            ":android:migration",
            ":engine:migration",
            ":feature:gate",
            ":protocol:paseo",
            ":protocol:codex", // BUILD-PROTOCOLS: Codex app-server backend
            ":feature:computer", // BUILD-PROTOCOLS: Settings › Agents › a computer (update, import)
            ":feature:agents",
            ":engine:runtime",
            ":feature:phone",
            ":feature:linux",
            ":feature:setup", // BUILD-RUNTIME-B: Set up OpenCode on this phone
            ":engine:git",
            ":feature:git",
            ":engine:updates", // P12-PLATFORM: in-app update notice
            ":feature:updates", // P12-PLATFORM: Updates page
            ":feature:background", // BUILD-ANDROID-BG-PLATFORM: Settings › Background
            ":android:platform", // Android share chooser and activity ports
            ":engine:entry", // B-ANDR-072: exact-shape opencode-mobile:// link parser
            ":android:tiles", // P12-PLATFORM: QS tiles, shortcuts, share, update installer
            ":android:widgets", // P12-PLATFORM: home-screen widgets
            ":android:platform",
            ":feature:preview", // PREVIEW-QR
            ":android:webview", // PREVIEW-QR
            ":android:camera", // PREVIEW-QR (pairing scanner)
            ":feature:prompts",
            ":engine:schedules",
            ":android:schedules",
            ":feature:schedules",
            ":engine:cards", // P12-CHAT-CARDS
            ":engine:files",
            ":feature:files",
            ":feature:search",
            ":feature:usage",
            ":feature:projects",
            ":engine:review",
            ":feature:review",
            ":engine:voice",
            ":android:voice",
            ":feature:voice",
            ":android:linux", // P12-FIX-LINUX-VOICE: the real built-in Linux (proot runtime + its
            // native libraries)
            ":engine:linuxsetup", // P12-BUILD-RUNTIME-C: the phone setup engine
            ":engine:setupscripts", // P12-BUILD-RUNTIME-C: the setup prelude the job scripts run
            // under
            ":engine:lifecycle", // P12-BUILD-RUNTIME-C: this-phone mark and lifecycle rules
            ":feature:phonesetup", // P12-BUILD-RUNTIME-C: Set up OpenCode on this phone
            ":feature:diagnostics", // BUILD-DIAGNOSTICS-ATTENTION
            ":feature:devservices", // BUILD-RUNTIME-A: Development services (saved commands, dev
            // servers found)
            ":engine:integrations-host",
            ":engine:integrations-catalog",
            ":engine:integrations-quota",
            ":engine:integrations-usage", // P12-BUILD-INTEGRATIONS-B: Usage › Plans engine
            ":engine:setupassist",
            ":feature:integrations",
            ":engine:project-terminal",
            ":android:terminal",
            ":feature:terminal",
            ":feature:plans", // P12-BUILD-INTEGRATIONS-B: Usage › Plans
            ":engine:integrations-catalog",
            ":feature:integrations-catalog",
            ":feature:setupreview",
            ":feature:agenttools",
            ":feature:widgetactions",
            ":feature:monitor",
            ":protocol:codex",
            ":feature:codexprobe",
            ":feature:handoff",
            ":android:terminal",
            ":engine:integrations-quota",
            ":engine:setupassist",
            ":engine:factsbook", // s23-factsbook: FactsBook memory pages
            ":feature:factsbook",
            ":engine:team", // s23-team: AI Team
            ":feature:team",
        )
        .forEach { implementation(project(it)) }
    implementation(platform(libs.firebase.bom))
    // B-180/B-190: :androidApp only; see telemetry/FirebaseTelemetryVendor. Performance is the SDK alone (never the plugin).
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    // getString outside composition: LinuxWiring's job notice words, SettingsWiring's
    // shareAppMessage; P6's proof too.
    implementation(libs.compose.resources)
    // IncomingShareNotice (main) lays out with Box; debug and benchmark had it, release did not compile (B-133).
    implementation(libs.compose.foundation)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.io.core) // Real app-private draft payload vault.
    implementation(
        libs.camerax.core
    ) // PREVIEW-QR: the scanner wiring hands CameraX's SurfaceRequest to a TextureView.
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(
        project(":core:testing")
    ) // P12-CHAT-BUGS: FakeServerGateway for the B1 paging test
    implementation(
        libs.androidx.profileinstaller
    ) // installs a baseline profile on sideloaded builds (start-up proof)
    // A library brings Fragment 1.1/1.2 in transitively; registerForActivityResult needs >= 1.3.0 or results can reach
    // the wrong caller (lint InvalidFragmentVersionForActivityResult, fatal on release-style builds).
    implementation(libs.androidx.fragment)
    // P3 stream proof screen (benchmark build type only)
    listOf(":feature:chat", ":ui:kit-chat").forEach { "benchmarkImplementation"(project(it)) }
    // Memory hygiene (audit gap 5): leak detection in the dev-flavour debug build only, never in release, preview or
    // switch. Apache-2.0, no network. Its provider installs itself; StrictMode joins in src/devDebug.
    "benchmarkImplementation"(libs.compose.foundation)
    "benchmarkImplementation"(libs.kotlinx.serialization.json)
}

// P12-FIX-LINUX-VOICE ship gate: every preview and switch APK must carry the built-in Linux and the
// offline speech
// engine, for both 64-bit ABIs, as extracted, 16 KB-aligned ELF files. A missing library (a dropped
// module, a lost
// jniLibs folder, no tools/vendor/fetch.sh) fails `assemble` instead of shipping an app that says
// "not on this phone".
abstract class VerifyNativeLibraries : DefaultTask() {
    @get:InputFiles abstract val apkDir: DirectoryProperty

    @TaskAction
    fun verify() {
        val required =
            listOf(
                "libproot.so",
                "libproot-loader.so",
                "libtalloc.so",
                "libandroid-shmem.so",
                "libaiteam_engine.so",
                "libaiteam_sandbox.so",
                "libaiteam_boundary_probe.so",
                "libsherpa-onnx-jni.so",
                "libonnxruntime.so",
            )
        val apks = apkDir.get().asFile.walkTopDown().filter { it.extension == "apk" }.toList()
        check(apks.isNotEmpty()) { "No APK to check in ${apkDir.get().asFile}" }
        val problems = mutableListOf<String>()
        for (apk in apks) {
            // E01-S08: a per-ABI split carries only its own ABI; the universal APK (or an unsplit one) carries both.
            val abis = listOf("arm64-v8a", "x86_64").filter { "-$it-" in apk.name }.ifEmpty { listOf("arm64-v8a", "x86_64") }
            ZipFile(apk).use { zip ->
                if (zip.getEntry("assets/aiteam-engine-manifest.json") == null)
                    problems += "${apk.name}: assets/aiteam-engine-manifest.json missing"
                for (abi in abis) for (name in required) {
                    val entry = zip.getEntry("lib/$abi/$name")
                    if (entry == null) {
                        problems += "${apk.name}: lib/$abi/$name missing"
                        continue
                    }
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val align = minLoadAlign(bytes)
                    if (align == null || align < 0x4000 || align % 0x4000 != 0L)
                        problems +=
                            "${apk.name}: lib/$abi/$name LOAD align ${align?.let { "0x" + it.toString(16) }} (needs 16 KB)"
                }
            }
        }
        check(problems.isEmpty()) {
            "Native libraries missing or misaligned:\n" + problems.joinToString("\n")
        }
    }

    /**
     * The smallest p_align of the PT_LOAD segments of a 64-bit little-endian ELF, or null when it
     * is not one.
     */
    private fun minLoadAlign(b: ByteArray): Long? {
        if (
            b.size < 64 ||
                b[0] != 0x7f.toByte() ||
                b[1] != 'E'.code.toByte() ||
                b[4] != 2.toByte() ||
                b[5] != 1.toByte()
        )
            return null
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val phoff = buf.getLong(0x20).toInt()
        val phentsize = buf.getShort(0x36).toInt() and 0xffff
        val phnum = buf.getShort(0x38).toInt() and 0xffff
        return (0 until phnum)
            .mapNotNull { i ->
                val at = phoff + i * phentsize
                if (at + 56 > b.size) null
                else if (buf.getInt(at) == 1) buf.getLong(at + 48) else null
            }
            .minOrNull()
    }
}

// B-145: installing over Flutter 2.1.4 must keep All-files access. Android resets a storage grant when the update
// newly requests a wider scope (requestLegacyExternalStorage) or stops requesting the permission, so the MERGED
// manifest (library manifests included) keeps 2.1.4's shape: MANAGE_EXTERNAL_STORAGE requested without a cap, and no
// legacy-storage attribute (2.1.4 set neither). StorageAccessManifestTest holds the same rules on the source manifests.
abstract class VerifyStorageManifest : DefaultTask() {
    @get:InputFile abstract val manifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val ns = "http://schemas.android.com/apk/res/android"
        val doc =
            javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest.get().asFile)
        val problems = mutableListOf<String>()
        val permissions = doc.getElementsByTagName("uses-permission")
        val manage =
            (0 until permissions.length)
                .map { permissions.item(it) as org.w3c.dom.Element }
                .filter { it.getAttributeNS(ns, "name") == "android.permission.MANAGE_EXTERNAL_STORAGE" }
        if (manage.isEmpty()) problems += "MANAGE_EXTERNAL_STORAGE is not requested"
        if (manage.any { it.hasAttributeNS(ns, "maxSdkVersion") }) problems += "MANAGE_EXTERNAL_STORAGE is capped"
        val app = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        for (attr in listOf("requestLegacyExternalStorage", "preserveLegacyExternalStorage")) {
            if (app.hasAttributeNS(ns, attr)) problems += "application sets android:$attr (2.1.4 did not)"
        }
        check(problems.isEmpty()) {
            "Merged manifest would reset All-files access on the update (B-145):\n" + problems.joinToString("\n")
        }
    }
}

// B-180/B-190: the MERGED manifest (the Firebase SDKs merge in their own permissions and flags) keeps every collection
// "enabled false" until consent, never "deactivated" (a deactivated flag can never be turned on at run time; Flutter
// 2.1.5 shipped Performance that way), and asks for no advertising ID or Android ad services. TelemetryManifestTest
// holds the same rules on the source manifest.
abstract class VerifyTelemetryManifest : DefaultTask() {
    @get:InputFile abstract val manifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val ns = "http://schemas.android.com/apk/res/android"
        val doc =
            javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest.get().asFile)
        fun elements(tag: String) =
            doc.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as org.w3c.dom.Element } }
        val problems = mutableListOf<String>()
        val permissions = elements("uses-permission").map { it.getAttributeNS(ns, "name") }
        for (p in permissions) if ("AD_ID" in p || "ADSERVICES" in p) problems += "requests $p"
        if (elements("uses-library").any { it.getAttributeNS(ns, "name") == "android.ext.adservices" })
            problems += "uses the android.ext.adservices library"
        val meta = elements("meta-data").associate { it.getAttributeNS(ns, "name") to it.getAttributeNS(ns, "value") }
        for (key in meta.keys.filter { it.endsWith("_deactivated") }) problems += "$key is set (consent could never turn it on)"
        for (flag in
            listOf(
                "firebase_crashlytics_collection_enabled",
                "firebase_analytics_collection_enabled",
                "firebase_performance_collection_enabled",
                "google_analytics_adid_collection_enabled",
                "google_analytics_default_allow_ad_storage",
                "google_analytics_default_allow_ad_user_data",
                "google_analytics_default_allow_ad_personalization_signals",
            )) {
            if (meta[flag] != "false") problems += "$flag is ${meta[flag]} (must be false until consent)"
        }
        check(problems.isEmpty()) { "Merged manifest breaks the telemetry consent rules (B-180):\n" + problems.joinToString("\n") }
    }
}

androidComponents.onVariants { variant ->
    if (variant.flavorName !in setOf("preview", "switch")) return@onVariants
    val name = variant.name.replaceFirstChar { it.uppercaseChar() }
    val telemetryManifest =
        tasks.register<VerifyTelemetryManifest>("verify${name}TelemetryManifest") {
            group = "verification"
            description = "Fails when the merged manifest could collect before consent or read an ad ID (B-180/B-190)."
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
        }
    tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(telemetryManifest) }
    val storage =
        tasks.register<VerifyStorageManifest>("verify${name}StorageManifest") {
            group = "verification"
            description = "Fails when the merged manifest would reset All-files access on the update (B-145)."
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
        }
    tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(storage) }
    val verify =
        tasks.register<VerifyNativeLibraries>("verify${name}NativeLibraries") {
            group = "verification"
            description =
                "Fails when the APK lacks the built-in Linux or sherpa-onnx native libraries (P12-FIX-LINUX-VOICE)."
            apkDir.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
        }
    tasks.matching { it.name == "assemble$name" }.configureEach { finalizedBy(verify) }
}

// E01-S01: the switch release must be the Flutter app's package with a release version name, at or above the first
// native release number, and every APK (each ABI split and the universal one) must carry the same versionCode, or
// the phones holding one APK could not update to another. Checked from the variant's outputs before packaging.
abstract class VerifyReleaseIdentity : DefaultTask() {
    @get:Input abstract val applicationId: Property<String>
    @get:Input abstract val versionCodes: ListProperty<Int>
    @get:Input abstract val versionNames: ListProperty<String>

    @TaskAction
    fun verify() {
        val problems = mutableListOf<String>()
        if (applicationId.get() != SWITCH_PACKAGE)
            problems += "package is ${applicationId.get()}, expected $SWITCH_PACKAGE"
        val codes = versionCodes.get().distinct()
        if (codes.size != 1) problems += "the APKs carry different versionCodes $codes (every split needs the same one)"
        if (codes.any { it < FIRST_RELEASE_CODE }) problems += "versionCode ${codes.min()} is below $FIRST_RELEASE_CODE"
        val names = versionNames.get().distinct()
        if (names.size != 1 || !Regex("""[3-9]\.\d+\.\d+|\d{2,}\.\d+\.\d+""").matches(names.single()))
            problems += "versionName $names is not one plain release version (3.0.0 or later, no suffix)"
        check(problems.isEmpty()) { "The switch release identity is wrong (E01-S01):\n" + problems.joinToString("\n") }
    }

    companion object {
        const val SWITCH_PACKAGE = "io.github.eslamasabry.opencode_mobile"
        const val FIRST_RELEASE_CODE = 10010
    }
}

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    // E01-S08 / release-identity.md (D-10 pending: 64-bit only): libraries bring 32-bit copies (armeabi-v7a, x86) that
    // would let a 32-bit phone install a universal APK whose built-in Linux cannot run there. Release ships 64-bit only.
    variant.packaging.jniLibs.excludes.addAll("lib/armeabi-v7a/**", "lib/x86/**")
    if (variant.flavorName != "switch") return@onVariants
    val identity =
        tasks.register<VerifyReleaseIdentity>("verifySwitchReleaseIdentity") {
            group = "verification"
            description = "Fails when the switch release is not the Flutter package at a release version (E01-S01)."
            applicationId.set(variant.applicationId)
            for (output in variant.outputs) {
                versionCodes.add(output.versionCode.map { it ?: -1 })
                versionNames.add(output.versionName.map { it ?: "" })
            }
        }
    tasks.matching { it.name == "assembleSwitchRelease" || it.name == "packageSwitchRelease" }.configureEach {
        dependsOn(identity)
    }
}

// A production release may only keep one of the two existing populations' signing lineages.
// Release builds that Gradle leaves unsigned (signed afterwards with apksigner, as the migration
// proof and the build server do) are checked by the signer comparison at delivery instead. The
// values are captured at configuration time so the task stays configuration-cache safe.
val verifyProductionLineage by tasks.registering {
    val signing = android.buildTypes.getByName("release").signingConfig
    val storeFile = signing?.storeFile
    val storeType = signing?.storeType ?: "JKS"
    val storePassword = signing?.storePassword
    val keyAlias = signing?.keyAlias
    val debugSigned = releaseDebugSigned
    doLast {
        if (debugSigned) {
            logger.lifecycle("Release is DEBUG-signed (ocReleaseDebugSigned=true): a build-shape proof, never delivered.")
            return@doLast
        }
        if (storeFile == null) {
            logger.lifecycle(
                "Release is unsigned here; its signer is compared with the installed lineage at delivery."
            )
            return@doLast
        }
        val store = KeyStore.getInstance(storeType)
        storeFile.inputStream().use { store.load(it, storePassword?.toCharArray()) }
        val certificate =
            store.getCertificate(keyAlias) ?: error("Production signing certificate is missing.")
        val digest =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") {
                "%02X".format(it.toInt() and 255)
            }
        check(
            digest in
                setOf(
                    "1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C",
                    "842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C",
                )
        ) {
            "The production signing certificate must keep the existing installed lineage."
        }
    }
}

tasks.configureEach {
    if (name.startsWith("package") && name.endsWith("Release")) dependsOn(verifyProductionLineage)
}

// The per-app language list (Android 13+ system settings) is generated, not hand-kept: a language is listed only when it
// has at least 95 % of the English keys (the same rule as LanguageCoverage.offered in :ui:words; :tools:strings
// LanguageGateTest pins both). Below that the app reads English as a whole.
abstract class GenerateLocaleConfig : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val words: DirectoryProperty

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun run() {
        val root = words.get().asFile
        fun names(folder: File): Set<String> =
            folder.listFiles().orEmpty().filter { it.name.startsWith("strings") && it.extension == "xml" }
                .flatMap { f -> Regex("""<(?:string|plurals)\s+name="([^"]+)""").findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSet()
        val source = names(File(root, "values"))
        val tags = root.listFiles().orEmpty().filter { it.isDirectory && Regex("values-[a-z]{2,3}").matches(it.name) }
            .map { it.name.removePrefix("values-") }
            .filter { names(File(root, "values-$it")).intersect(source).size * 100 >= 95 * source.size }.sorted()
        val xml = File(out.get().asFile, "xml/locales_config.xml")
        xml.parentFile.mkdirs()
        xml.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- Generated: languages at 95% of English or more. -->\n" +
                "<locale-config xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                (listOf("en") + tags).joinToString("") { "    <locale android:name=\"$it\" />\n" } + "</locale-config>\n",
        )
    }
}

val localeConfig = tasks.register<GenerateLocaleConfig>("generateLocaleConfig") {
    words.set(rootProject.layout.projectDirectory.dir("ui/words/src/commonMain/composeResources"))
    out.set(layout.buildDirectory.dir("generated/localeConfig/res"))
}
androidComponents.onVariants { variant -> variant.sources.res?.addGeneratedSourceDirectory(localeConfig, GenerateLocaleConfig::out) }

// LeakCanary (audit gap 5): the dev-flavour debug variant only, never release, preview or switch. The variant's
// configuration appears after the flavours are resolved, so the dependency is added when it does.
val leakCanary = "com.squareup.leakcanary:leakcanary-android:${libs.versions.leakcanary.get()}"
configurations.matching { it.name == "devDebugImplementation" }.configureEach {
    project.dependencies.add(name, leakCanary)
}
