plugins {
    id("oc.jvm.library")
    alias(libs.plugins.metro) // the graph smoke test builds one test graph (plan 03 §13.8)
    alias(libs.plugins.compose.compiler) // DesktopShellUiTest calls composables
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(project(":desktopApp")) // desktop graph smoke
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
    // Konsist and ModuleOwnersTest read the source tree; make them rerun when it changes.
    inputs.files(fileTree(rootDir) { include("**/src/**/*.kt", "**/build.gradle.kts", "MODULE_OWNERS"); exclude("**/build/**", "build-logic/**") })
        .withPropertyName("sources").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("oc.root", rootDir.absolutePath)
}

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
