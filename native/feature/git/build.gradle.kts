plugins { id("oc.kmp.feature") }

// P12-GIT (plan 07 D09, B-NAV-020..023): the Git page of a conversation's project — status,
// per-file diff, branches,
// save (commit), get latest (pull), send (push) through the server's command endpoint, and GitHub
// device sign-in.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:analytics")) // feature_used for a Git action that worked
        implementation(project(":engine:git"))
        implementation(project(":engine:connection"))
        implementation(project(":engine:delivery")) // Ask the agent to fix the conflicts: the chat's send pipeline
        implementation(project(":ui:kit-chat")) // KitDiffView for one file's patch
        implementation(libs.kotlinx.collections.immutable)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos)
    }
}
