plugins { id("oc.kmp.feature") }

// :feature:diagnostics (BUILD-DIAGNOSTICS-ATTENTION): Settings › Report a problem › Everything this phone kept. Port of
// lib/ui/screens/app_diagnostics_kept_screen.dart with its crash reports, exit history and kept errors sections. The
// engines come from :engine:diagnostics; :androidApp binds KeptDiagnosticsHost (DiagnosticsAttentionWiring.kt).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:diagnostics"))
        implementation(project(":ui:kit-content")) // searchable redacted report output
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
