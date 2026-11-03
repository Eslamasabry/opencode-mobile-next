// :android:webview (unit PREVIEW-QR): the one place the app builds an Android WebView (K-SEC-9: `KitWebPreview.kt`),
// for the in-app preview of a dev server. Plain Android, no Compose and no project dependencies: the screen
// (`:feature:preview`) reaches it through the app wiring, which hands it the egress rules as a [PreviewGuard].
plugins { id("oc.android.library") }

android {
    // Matches :android:camera: core-style JVM 17 bytecode (oc.android.library leaves 11).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // P12-FIX-PREVIEWQR: the pure navigation decisions (PreviewNavigationTest), plain JVM.
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
}
