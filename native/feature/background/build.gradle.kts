plugins { id("oc.kmp.feature") }

// :feature:background (unit BUILD-ANDROID-BG-PLATFORM): Settings › Notifications › Background — the "Stay connected in
// the background" switch, its battery row, the live status row with Restart, Android's time-limit notice and the pause
// notice with Resume. Port of the background section of lib/ui/screens/settings/notifications_settings_screen.dart and
// lib/ui/widgets/background_pause_notice.dart. BackgroundLive is bound by :androidApp (graph/BackgroundWiring.kt) over
// :android:background.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.datetime)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
