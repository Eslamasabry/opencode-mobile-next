plugins { id("oc.kmp.feature") }

// :feature:computer (unit BUILD-PROTOCOLS): one Paseo computer's tools page, Settings › Agents › the computer: update
// the agent helper there (B-AGENT-167) and import Claude Code conversations from its project (B-AGENT-174). Port of
// the Flutter host-update row/sheet and ClaudeImportScreen. ComputerTools is bound by :androidApp (ProtocolsWiring).
kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
