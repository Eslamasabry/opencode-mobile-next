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
    implementation(libs.androidx.core.ktx)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}
