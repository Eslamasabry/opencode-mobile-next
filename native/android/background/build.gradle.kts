// :android:background (plan 11 §2.1): live mode (the opted-in background connection), its durable pause receipt and
// pause notice, the sign-in foreground lease and its owner registry, and the first-reply notification offer. Ported
// from the Flutter app's lib/background/live_background.dart, signin_foreground.dart, lib/domain/background_pause.dart,
// agent_sign_in_foreground.dart, lib/state/background_pause_notice.dart, first_reply_notify_offer.dart and the
// Kotlin BackgroundPauseStore/BackgroundPauseReceipt. The notification half lives in :android:notifications; the
// BackgroundConnectionService shell stays in :androidApp.
plugins { id("oc.android.library") }

android {
    // :core:common's inline attempt {} is JVM 17 bytecode (same seam as :android:tiles).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = false
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
