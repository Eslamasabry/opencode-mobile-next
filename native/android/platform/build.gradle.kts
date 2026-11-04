// :android:platform (plan 11 §2, row `:android:platform`). Slice 1 (S1-PLAT0): the copied
// NativeCrashStore, the
// network monitor (NetworkStatus) and basic DeviceFacts. Slice 2 (S2-ENTRY, S2-PLATFORM):
// EntryIntentReader and the
// MainActivity entry capture, share text in and out, the Tailscale handoff, static shortcuts and
// ActivityResults.
plugins { id("oc.android.library") }

android {
    testOptions {
        unitTests.isReturnDefaultValues = false
        unitTests.isIncludeAndroidResources =
            true // StaticShortcutsTest reads the compiled shortcut XML and strings
    }
}

dependencies {
    api(project(":core:domain"))
    api(project(":engine:entry"))
    implementation(project(":engine:lifecycle"))
    implementation(libs.androidx.core.ktx)
    // ActivityResultRegistry and the contracts (plan 12 N-51: activity-compose for
    // :android:platform's ActivityResults).
    implementation(libs.androidx.activity.compose)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
