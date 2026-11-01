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
    ).forEach { testImplementation(project(it)) }
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
