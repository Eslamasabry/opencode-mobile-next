// :android:platform (plan 11 §2, row `:android:platform`). Slice 1 holds exactly three files (plan 15 C15-3, S1-PLAT0):
// the copied NativeCrashStore, the network monitor (NetworkStatus) and basic DeviceFacts. Entry intents, pickers,
// permissions and share arrive with S2-PLATFORM.
plugins { id("oc.android.library") }

android {
    testOptions { unitTests.isReturnDefaultValues = false }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
