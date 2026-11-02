// :android:tiles: the Quick Settings tiles (AttentionTileService, PauseTileService) copied from the Flutter app with
// their class names frozen, plus the launcher-shortcut, launch-extra and share-intake helpers MainActivity calls.
// See README.md for what :androidApp wires.
plugins { id("oc.android.library") }

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = false
    }
}

dependencies {
    // The Android half of the in-app update notice (UpdateFeed / UpdatePlatform / UpdateStore, unit P12-PLATFORM).
    implementation(project(":engine:updates"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
