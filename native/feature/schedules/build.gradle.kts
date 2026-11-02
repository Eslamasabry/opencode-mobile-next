plugins { id("oc.kmp.feature") }

// :feature:schedules (plan 07 §5 K: K01 list, K02 editor; unit P12-SCHEDULES). Scheduled prompts arranged from kit
// parts over :engine:schedules. The app wiring supplies the server facts (capability, recent project, models) through
// ScheduleServers.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:schedules"))
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
