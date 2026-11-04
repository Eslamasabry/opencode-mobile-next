plugins { id("oc.kmp.feature") }

// P12-PROMPTS: Saved prompts (the composer engine's PromptShelf), the server's slash commands and
// the agent's task
// list, each opened from a conversation. Text goes back to that conversation's composer through
// :ui:kit-chat's
// ComposerInbox.
kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos)
    }
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:composer"))
        implementation(project(":engine:integrations-catalog"))
        implementation(project(":engine:connection"))
        implementation(project(":ui:kit-chat"))
        implementation(libs.kotlinx.collections.immutable)
    }
}
