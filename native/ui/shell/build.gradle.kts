plugins { id("oc.kmp.compose") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":ui:kit"))
        implementation(project(":ui:words"))
        implementation(project(":ui:routes"))
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
}

// Robolectric 4.17 (SDK 36) on the JDK 21 launcher reads jdk.internal.access.SharedSecrets ("Failed to interact with
// raw FileDescriptor internals" otherwise). Belongs in the convention once P7 settles Robolectric for every module.
tasks.withType<Test>().configureEach {
    if (name.contains("AndroidHostTest")) jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
