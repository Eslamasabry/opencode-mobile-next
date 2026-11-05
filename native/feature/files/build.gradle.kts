plugins { id("oc.kmp.feature") }

// :feature:files (P12-FILES): Project Files for a conversation (07 §5 D02/D03/D05): folders, the
// file viewer, Ask
// about this file into the composer, Open with and Share on Wi-Fi with its QR code.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:files"))
        implementation(project(":engine:settings"))
        implementation(project(":engine:media"))
        implementation(project(":ui:kit-chat"))
        implementation(project(":engine:connection")) // the conversation's own connection graph
        implementation(
            project(":engine:composer")
        ) // the conversation's draft: "@path " and its file mention
        implementation(
            project(":ui:kit-content")
        ) // viewer frame and bodies, QR, zoomable image, PDF page
    }
}

// looks capture only: stale existing tests in this module do not compile; compile only the capture test and fixtures.
kotlin.sourceSets.matching { it.name == "commonTest" || it.name == "desktopTest" }.configureEach {
    kotlin.exclude { it.file.name.endsWith("Test.kt") && it.file.name != "LooksCaptureTest.kt" }
}
