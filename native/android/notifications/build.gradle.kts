// :android:notifications (unit NOTIFY; plan 11 §10, §9.3; 16 WP-16.8; 05 §7.9). The notification half of
// :android:background: frozen channel ids, alert rendering and its buttons, the shade-action body, the status row with
// Live Updates, and the reply-watch service body. Every OS-visible class (receivers, services) stays a thin shell in
// :androidApp at its frozen FQCN and forwards here (plan 11 §2.2).
plugins { id("oc.android.library") }

android {
    testOptions {
        unitTests.isReturnDefaultValues = false
        // The tests read this module's own strings (seven languages) and plurals.
        unitTests.isIncludeAndroidResources = true
    }
}

// Robolectric's SDK 36 runtime (PromotableStatusTest) reaches into java.io FileDescriptor internals on JDK 21.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
