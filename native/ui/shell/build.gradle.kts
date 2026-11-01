plugins {
    id("oc.kmp.compose")
    alias(libs.plugins.roborazzi) // S2-GATEUI: gate goldens (plan 05 W7), the kit's P7 recipe
}

// Test-only artifacts the catalog has no entries for yet; versions come from the catalog's pins (as in :ui:kit).
val cmp = libs.versions.cmp.asProvider().get()
val roborazzi = libs.versions.roborazzi.get()

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":ui:kit"))
        implementation(project(":ui:routes"))
        implementation(project(":ui:words")) // S2-GATEUI: strings_migration (plan 05 §8.2)
        implementation(libs.navigation3.ui)
        implementation(libs.lifecycle.viewmodel.navigation3)
        implementation(libs.lifecycle.viewmodel.compose)
        implementation(libs.metrox.viewmodel.compose)
        implementation(libs.savedstate)
        implementation(libs.navigationevent.compose) // P2: the back gesture's transitionState
    }
}

// P2: Robolectric host test of the Android animator-scale source (ADR §7.2: Android-only behaviour).
kotlin.sourceSets.getByName("androidHostTest").dependencies {
    implementation(libs.junit4)
    implementation(libs.robolectric)
    // S2-GATEUI: gate goldens, the same set as :ui:kit's P7 goldens.
    implementation("org.jetbrains.compose.ui:ui-test-junit4:$cmp")
    implementation("androidx.compose.ui:ui-test-manifest:$cmp")
    implementation("io.github.takahirom.roborazzi:roborazzi:$roborazzi")
    implementation("io.github.takahirom.roborazzi:roborazzi-compose:$roborazzi")
    implementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:$roborazzi")
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/screenshots"))
}

// Robolectric 4.17 (SDK 36) on the JDK 21 launcher reads jdk.internal.access.SharedSecrets ("Failed to interact with
// raw FileDescriptor internals" otherwise). Belongs in the convention once P7 settles Robolectric for every module.
tasks.withType<Test>().configureEach {
    if (name.contains("AndroidHostTest")) {
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            // S2-GATEUI: native graphics (Roborazzi) needs the same opens as :ui:kit's goldens.
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
        )
    }
}
