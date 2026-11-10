plugins {
    id("oc.jvm.library")
    alias(libs.plugins.metro) // the graph smoke test builds one test graph (plan 03 §13.8)
    alias(libs.plugins.compose.compiler) // DesktopShellUiTest calls composables
}

dependencies {
    testImplementation(libs.navigationevent.compose)
    testImplementation(project(":engine:requests"))
    testImplementation(project(":engine:delivery"))
    testImplementation(libs.konsist)
    testImplementation(project(":desktopApp")) // desktop graph smoke
    testImplementation(project(":feature:projects")) // DesktopAppGraph extends its IsolatedTaskBindings
    // The smoke graph provides what only the app graphs bind (ContextUsageHistory, ScheduleEngine, SessionNotes).
    listOf(":engine:settings", ":engine:schedules", ":engine:agents").forEach { testImplementation(project(it)) }
    listOf(
        ":core:common", ":core:security", ":core:model", ":core:domain", ":core:testing",
        ":engine:connection", ":engine:transcript", ":ui:routes", ":ui:kit", ":ui:shell", ":feature:chats",
        ":ui:kit-chat", ":feature:chat", // P3: the headless transcript stream test (K1 forbids test imports in features)
    ).forEach { testImplementation(project(it)) }
    testImplementation(libs.compose.foundation)
    testImplementation(libs.metrox.viewmodel)
    testImplementation(libs.lifecycle.viewmodel)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test) // headless desktop UI test of the shared shell
    testImplementation(libs.compose.ui)
    testImplementation(libs.kotlinx.coroutines.swing)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    // Konsist parses every production file into one test worker; the default 512 MB heap ran out as the tree grew
    // (OutOfMemoryError in the Kotlin parser, release gates 2026-11-10).
    maxHeapSize = "2g"
    // Konsist and ModuleOwnersTest read the source tree; make them rerun when it changes.
    inputs.files(fileTree(rootDir) { include("**/src/**/*.kt", "**/build.gradle.kts", "MODULE_OWNERS"); exclude("**/build/**", "build-logic/**") })
        .withPropertyName("sources").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("oc.root", rootDir.absolutePath)
    // E00-S13: T2's component-freeze step checks the merged same-package release manifest ("required", or a path).
    inputs.files(fileTree(rootDir) {
        include("**/src/**/AndroidManifest.xml", "**/src/**/res/xml/*.xml", "quality/config/component-freeze/**")
        exclude("**/build/**")
    }).withPropertyName("freezeSources").withPathSensitivity(PathSensitivity.RELATIVE)
    providers.gradleProperty("oc.mergedManifest").orNull?.let {
        systemProperty("oc.mergedManifest", it)
        outputs.upToDateWhen { false } // the merged manifest lives under build/: always re-read it
    }
}

// E02-S11c: a module whose unit-test sources stop compiling fails the gate. T1 runs only the touched modules' tests, so
// a change to a shared API could leave another module's tests uncompilable until T2. `:quality:test` runs at every
// T1 and T2 (component-freeze), so it depends on compiling every module's unit-test sources:
//   - desktop/JVM: compileTestKotlinDesktop (KMP), compileTestKotlin (JVM modules, :desktopApp);
//   - Android: compileAndroidHostTest (AGP-KMP host tests) and compile<Variant>DebugUnitTestKotlin (Android modules,
//     debug variants only), when an Android SDK is configured (ANDROID_HOME, ANDROID_SDK_ROOT or local.properties
//     sdk.dir). Without one only the desktop/JVM half runs and the task says so; -Poc.testCompileGuard=all forces the
//     Android half (it then fails without an SDK), =jvm skips it.
// Compiling is incremental and build-cached, so after the first run this costs only what changed.
val testCompileGuard = providers.gradleProperty("oc.testCompileGuard").orElse("auto")
val androidSdkConfigured =
    providers.environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .map { true }
        .orElse(
            providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
                .map { text -> text.lineSequence().any { it.trim().startsWith("sdk.dir=") } }
                .orElse(false),
        )
val compileAndroidTests =
    testCompileGuard.zip(androidSdkConfigured) { mode, sdk ->
        when (mode) {
            "all" -> true
            "jvm" -> false
            "auto" -> sdk
            else -> error("oc.testCompileGuard is auto, all or jvm (got \"$mode\")")
        }
    }
val jvmTestCompiles = setOf("compileTestKotlinDesktop", "compileTestKotlin")
val androidTestCompile = Regex("compileAndroidHostTest|compile\\w*DebugUnitTestKotlin")
val compileAllTestSources =
    tasks.register("compileAllTestSources") {
        group = "verification"
        description = "Compiles every module's unit-test sources, so a module whose tests no longer compile fails the gate."
        val android = compileAndroidTests.get()
        rootProject.subprojects.filter { it != project }.forEach { other ->
            // By name, never `tasks.matching { }`: matching creates every task of the module to ask its name, and
            // AGP 9.5 has one (generate<Variant>ComposePreviewRunfiles) that refuses to be created in :androidApp,
            // which stopped this gate at configuration (release dry run 2026-11-10).
            dependsOn(
                other.provider {
                    other.tasks.names
                        .filter { it in jvmTestCompiles || (android && androidTestCompile.matches(it)) }
                        .map { other.tasks.named(it) }
                },
            )
        }
        val note = if (android) "desktop, JVM and Android" else "desktop and JVM only (no Android SDK, or oc.testCompileGuard=jvm)"
        doLast { logger.lifecycle("test sources compiled: $note") }
    }
tasks.test { dependsOn(compileAllTestSources) }

// S1-SECGATES (plan 16 §2): the K-SEC rules and the security gates. A separate suite that compiles no project module,
// so it runs while the rest of the tree is being reconciled: `./gradlew :quality:secTest`.
testing {
    suites {
        register<JvmTestSuite>("secTest") {
            useJUnitJupiter()
            dependencies {
                implementation(libs.konsist)
                implementation("org.jetbrains.kotlin:kotlin-test-junit5")
                implementation(libs.compose.runtime) // the module applies the Compose compiler, which needs its runtime
            }
            targets.all {
                testTask.configure {
                    inputs.files(fileTree(rootDir) {
                        include("**/src/**/*.kt", "**/src/**/*.xml", "**/build.gradle.kts", "build-logic/**/*.kt", "quality/config/**",
                            "gradle/libs.versions.toml", "version.properties", "androidApp/lint.xml")
                        exclude("**/build/**")
                    }).withPropertyName("secSources").withPathSensitivity(PathSensitivity.RELATIVE)
                    systemProperty("oc.root", rootDir.absolutePath)
                    providers.gradleProperty("oc.leakBase").orNull?.let { systemProperty("oc.leakBase", it) }
                    providers.gradleProperty("oc.leakMode").orNull?.let { systemProperty("oc.leakMode", it) }
                    val leakRun = providers.gradleProperty("oc.leakBase").isPresent // a leak-gate run always reruns
                    outputs.upToDateWhen { !leakRun }
                }
            }
        }
    }
}
tasks.named("check") { dependsOn("secTest") }
