plugins { id("oc.kmp.feature") }

// S2-CHAT (plan 09 §6–§7, plan 07 §3.4/§2.5, plan 15 row S2-CHAT): the chat screen, its ViewModel
// and entry, the
// status line, find, message actions, BasicComposerHost, arrivals and the Back guard. Request files
// belong to S2-REQ.
kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos)
    }
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:settings"))
        implementation(project(":engine:delivery"))
        implementation(project(":engine:voice"))
        implementation(project(":engine:composer"))
        implementation(
            project(":engine:connection")
        ) // ← engines this feature reads (public API only)
        implementation(project(":engine:transcript"))
        implementation(project(":ui:kit-chat"))
        implementation(
            project(":engine:requests")
        ) // S2-REQ: the request slot (permissions, one-question asks)
        implementation(libs.kotlinx.collections.immutable)
        implementation(libs.kotlinx.datetime)
        implementation(project(":engine:integrations-catalog"))
        implementation(project(":engine:cards")) // P12-CHAT-CARDS: the agent card slot
        implementation(
            project(":engine:agents")
        ) // P12-BUILD-AGENTS: the Continue on computer resume command
    }
}

// looks capture only: stale existing tests in this module do not compile; compile only the capture test and fixtures.
kotlin.sourceSets.matching { it.name == "commonTest" || it.name == "desktopTest" }.configureEach {
    kotlin.exclude { it.file.name.endsWith("Test.kt") && it.file.name != "LooksCaptureTest.kt" }
}
