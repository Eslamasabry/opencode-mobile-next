plugins { id("oc.kmp.feature") }

// :feature:setup (unit BUILD-RUNTIME-B). "Set up OpenCode on this phone": the start page and the live progress of the
// real native setup job (Pause, Continue after a failure, a plain failure line with Details). Ported from
// lib/ui/screens/phone_setup/phone_setup_{start,progress}_screen.dart over :engine:runtime's SetupEngine; the Android
// binding is androidApp's graph/SetupWiring.kt.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:runtime")) // SetupProgress, SetupComponent, SetupProgressRules (public API)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
