import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

plugins { id("oc.android.application") }

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        val requestedAgentFloor = providers.gradleProperty("ocQaAgentInstallMinFreeBytes").orNull
        val agentInstallFloor =
            requestedAgentFloor?.toLongOrNull()
                ?: if (requestedAgentFloor?.matches(Regex("[+]?[0-9]+")) == true) Long.MAX_VALUE
                else 0L
        buildConfigField("long", "AGENT_INSTALL_MIN_FREE_BYTES", "${agentInstallFloor}L")
    }
    // Slice-0 proofs P3 and start-up (native/proof-p3-2026-11-01): a release build that the shell
    // may profile, signed
    // with the debug key so Macrobenchmark can install it. R8 on, like the release the plan ships
    // (ADR §9.9).
    buildTypes {
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
        create("switch") {
            dimension = "channel"
        }
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
            ":feature:servers",
            ":core:data",
            ":core:net",
            ":protocol:oc2",
            ":engine:profiles",
            ":engine:composer",
            ":engine:media",
            ":android:media",
            ":engine:agents",
            ":engine:delivery",
            ":engine:requests",
            ":ui:kit-chat",
            ":ui:kit-content",
            ":feature:settings",
            ":feature:onboarding",
            ":engine:settings",
            ":engine:analytics",
            ":engine:diagnostics",
            ":android:notifications",
            ":android:background", // BUILD-ANDROID-BG-PLATFORM: live mode, pause, sign-in lease
            ":android:migration",
            ":android:termux",
            ":engine:migration",
            ":feature:gate",
            ":protocol:paseo",
            ":feature:agents",
            ":engine:runtime",
            ":feature:phone",
            ":feature:linux",
            ":engine:git",
            ":feature:git",
            ":engine:updates", // P12-PLATFORM: in-app update notice
            ":feature:updates", // P12-PLATFORM: Updates page
            ":feature:background", // BUILD-ANDROID-BG-PLATFORM: Settings › Background
            ":android:tiles", // P12-PLATFORM: QS tiles, shortcuts, share, update installer
            ":android:widgets", // P12-PLATFORM: home-screen widgets
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
            ":feature:diagnostics", // BUILD-DIAGNOSTICS-ATTENTION
            ":android:linux", // P12-FIX-LINUX-VOICE: the real built-in Linux (proot runtime + its
            // native libraries)
            ":feature:devservices", // BUILD-RUNTIME-A: Development services (saved commands, dev
                                    // servers found)
            ":engine:integrations-usage", // P12-BUILD-INTEGRATIONS-B: Usage › Plans engine
            ":feature:plans", // P12-BUILD-INTEGRATIONS-B: Usage › Plans
        )
        .forEach { implementation(project(it)) }
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    // getString outside composition: LinuxWiring's job notice words, SettingsWiring's
    // shareAppMessage; P6's proof too.
    implementation(libs.compose.resources)
    debugImplementation(libs.compose.foundation) // P6 proof screen (debug only)
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
    // P3 stream proof screen (benchmark build type only)
    listOf(":feature:chat", ":ui:kit-chat").forEach { "benchmarkImplementation"(project(it)) }
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
            ZipFile(apk).use { zip ->
                if (zip.getEntry("assets/aiteam-engine-manifest.json") == null)
                    problems += "${apk.name}: assets/aiteam-engine-manifest.json missing"
                for (abi in listOf("arm64-v8a", "x86_64")) for (name in required) {
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

androidComponents.onVariants { variant ->
    if (variant.flavorName !in setOf("preview", "switch")) return@onVariants
    val name = variant.name.replaceFirstChar { it.uppercaseChar() }
    val verify =
        tasks.register<VerifyNativeLibraries>("verify${name}NativeLibraries") {
            group = "verification"
            description =
                "Fails when the APK lacks the built-in Linux or sherpa-onnx native libraries (P12-FIX-LINUX-VOICE)."
            apkDir.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
        }
    tasks.matching { it.name == "assemble$name" }.configureEach { finalizedBy(verify) }
}
