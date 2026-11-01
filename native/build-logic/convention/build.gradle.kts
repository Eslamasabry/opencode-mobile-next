plugins { `kotlin-dsl` }

// Metro 1.5.1's Gradle plugin is published for JVM 21 (Gradle attribute check), so build-logic and the Gradle daemon
// run on JDK 21. Module bytecode still targets 17 (ADR §8) through the toolchain in each convention plugin.
kotlin { jvmToolchain(21) }

dependencies {
    compileOnly(libs.gradle.android)
    compileOnly(libs.gradle.kotlin)
    compileOnly(libs.gradle.compose.compiler)
    compileOnly(libs.gradle.compose)
    compileOnly(libs.gradle.metro)
    testImplementation(kotlin("test"))          // ModuleRulesTest: the ADR §6.1 table as test vectors
}


gradlePlugin {
    plugins {
        register("ocKmpLibrary") { id = "oc.kmp.library"; implementationClass = "oc.buildlogic.OcKmpLibraryPlugin" }
        register("ocKmpCompose") { id = "oc.kmp.compose"; implementationClass = "oc.buildlogic.OcKmpComposePlugin" }
        register("ocKmpFeature") { id = "oc.kmp.feature"; implementationClass = "oc.buildlogic.OcKmpFeaturePlugin" }
        register("ocJvmLibrary") { id = "oc.jvm.library"; implementationClass = "oc.buildlogic.OcJvmLibraryPlugin" }
        register("ocAndroidLibrary") { id = "oc.android.library"; implementationClass = "oc.buildlogic.OcAndroidLibraryPlugin" }
        register("ocAndroidApplication") { id = "oc.android.application"; implementationClass = "oc.buildlogic.OcAndroidApplicationPlugin" }
        register("ocDesktopApplication") { id = "oc.desktop.application"; implementationClass = "oc.buildlogic.OcDesktopApplicationPlugin" }
    }
}

tasks.test { useJUnitPlatform() }
dependencies { testRuntimeOnly("org.junit.platform:junit-platform-launcher") }
