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
        implementation(
            project(":ui:kit-content")
        ) // KitTerminalView (.live + .accessible, plan 06 §7.7)
        implementation(project(":engine:agents"))
    }
}
