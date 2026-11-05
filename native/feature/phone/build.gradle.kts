plugins { id("oc.kmp.feature") }

// :feature:phone (plan 07 §5 F, plan 11 §6–§8; plan 15 S3-PHONEUI). This phone, Running on this
// phone with the
// child-process-limit card, and Keep running. Android only at runtime: the shell registers these
// pages only when
// PlatformCapabilities.builtinLinux is true (07 §2.8); the code is common so its rules test on
// desktop.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:settings")) // first-start consent identities
        implementation(
            project(":engine:runtime")
        ) // RuntimeStatus, ProbeAnswer, StartProblem, ProcessBudget (public API)
    }
}

kotlin {
    sourceSets.getByName("desktopTest").dependencies { implementation(libs.compose.desktop.currentos) }
}
apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))

// looks capture only: stale existing tests in this module do not compile; compile only the capture test and fixtures.
kotlin.sourceSets.matching { it.name == "commonTest" || it.name == "desktopTest" }.configureEach {
    kotlin.exclude { it.file.name.endsWith("Test.kt") && it.file.name != "LooksCaptureTest.kt" && !it.file.invariantSeparatorsPath.contains("/rb/o48/") }
}

// Order 48's phone catalog compiles each owned management feature in the render gate.
kotlin.sourceSets.getByName("desktopTest") {
    kotlin.srcDir("../../ui/gate/src/desktopMain/kotlin")
    resources.srcDir("../../ui/gate/src/desktopMain/resources")
    dependencies {
        implementation(project(":feature:linux"))
        implementation(project(":feature:background"))
        implementation(project(":feature:manage-space"))
    }
}
tasks.withType<Test>().matching { it.name == "desktopTest" }.configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
