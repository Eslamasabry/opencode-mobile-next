plugins { id("oc.kmp.feature") }

// :feature:updates: the Updates page (Settings › This app › Updates), the one-line update notice and the offer sheet
// the Settings gear opens while a release waits. Port of lib/ui/screens/settings/updates_screen.dart,
// lib/update/app_update_notice.dart and lib/ui/kit/kit_update_sheet.dart. AppUpdates comes from :engine:updates and is
// bound app-wide by :androidApp (README.md).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:updates"))
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
