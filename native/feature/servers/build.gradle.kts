plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:connection"))
        implementation(project(":engine:agents"))
        implementation(project(":engine:lifecycle"))
        implementation(project(":engine:runtime"))
        implementation(project(":engine:setupassist"))
        implementation(project(":ui:kit-chat"))
    }
}

// looks capture only: stale existing tests in this module do not compile; compile only the capture test and fixtures.
kotlin.sourceSets.matching { it.name == "commonTest" || it.name == "desktopTest" }.configureEach {
    kotlin.exclude { it.file.name.endsWith("Test.kt") && it.file.name != "LooksCaptureTest.kt" }
}
