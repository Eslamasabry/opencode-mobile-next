plugins { id("oc.kmp.feature") }

// :feature:agents (plan 07 §5 G, plan 15 S3-AGENTSUI). S3-TERMMIN adds the sign-in terminal (G03)
// only; the agents hub,
// sheet and account screens are S3-AGENTSUI's. The sign-in screen reads :engine:agents through the
// narrow ports in
// signin/SignInPorts.kt until :engine:agents compiles on the flat core contract (INTEGRATE-W2), and
// the terminal through
// the TerminalHost stand-in until :engine:project freezes it (plan 17 §2.6, contracts/s3.md).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:runtime"))
        implementation(project(":engine:integrations-catalog"))
        implementation(project(":engine:connection"))
        implementation(project(":engine:project-terminal"))
        implementation(
            project(":ui:kit-content")
        ) // KitTerminalView (.live + .accessible, plan 06 §7.7)
        implementation(project(":engine:agents"))
        implementation(project(":engine:settings"))
    }
}

// Order 49's golden cases render the real hub and account surfaces with immutable fixture state.
kotlin.sourceSets.getByName("desktopTest") {
    kotlin.srcDir("../../ui/gate/src/desktopMain/kotlin")
    resources.srcDir("../../ui/gate/src/desktopMain/resources")
    dependencies { implementation(libs.compose.desktop.currentos) }
}
tasks.withType<Test>().matching { it.name == "desktopTest" }.configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
