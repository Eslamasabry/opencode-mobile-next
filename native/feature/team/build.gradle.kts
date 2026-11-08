plugins { id("oc.kmp.feature") }

// :feature:team (owner 2026-11-08: AI Team before the switch; unit s23-team). The AI Team pages arranged from kit
// parts over :engine:team: the team page (projects), one project, its board. Layout and words follow Flutter
// lib/ui/screens/team/projects/*; the app wiring supplies TeamHost (the phone engine's loopback read).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:team"))
        implementation(project(":ui:kit-chat")) // the person's notes in a task's conversation (KitPromptBubble)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
