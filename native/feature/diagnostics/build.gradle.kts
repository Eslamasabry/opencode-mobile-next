plugins { id("oc.kmp.feature") }

// :feature:diagnostics (BUILD-DIAGNOSTICS-ATTENTION): the stale-failure prompt and the report headlines. Its "Everything
// this phone kept" page was removed by R1 (2026-11-09); KeptDiagnosticsHost stays until its :androidApp binding
// (DiagnosticsAttentionWiring.kt) can go. The engines come from :engine:diagnostics.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:diagnostics"))
        implementation(project(":ui:kit-content")) // searchable redacted report output
        // E19-S03: the updater's read side (AppUpdateState) for "Report this failure" on an older version.
        implementation(project(":engine:updates"))
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
