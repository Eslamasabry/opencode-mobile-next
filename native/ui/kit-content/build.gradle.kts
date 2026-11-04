plugins {
    id("oc.kmp.compose")
    alias(
        libs.plugins.roborazzi
    ) // goldens recorded and verified through the plugin's tasks (plan 06 §15.3)
}

// Content kit (plan 06 §3.1, KN3): QR, zoom, viewer frames and bodies, the PDF page. No copy
// outside
// composeResources; labels a host owns come in as parameters.
//
// Test-only artifacts the catalog has no entries for yet; versions come from the catalog's pins or
// from plan 10 §2.1
// (zxing core 3.5.4, test configurations only). See docs/native/units/S3-KCCONTENT.md "catalog
// requests".
val cmp = libs.versions.cmp.asProvider().get()
val roborazzi = libs.versions.roborazzi.get()
val zxingCore = "com.google.zxing:core:3.5.4"

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":ui:design"))
        implementation(project(":ui:kit"))
        implementation(project(":ui:words")) // kit-internal words (strings_kit.xml), plan 06 §16.1
        implementation("io.coil-kt.coil3:coil-compose:3.6.3")
        implementation("io.coil-kt.coil3:coil-svg:3.6.3")
        implementation(libs.compose.material3) // kit modules only (ExternalRules)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(
            project(":core:security")
        ) // B-XPLAT-011: terminal lines masked by the app's one redactor
        implementation(
            zxingCore
        ) // QR readability: every drawn code is decoded by ZXing (plan 10 §14.1)
    }
    sourceSets.getByName("androidHostTest").dependencies {
        implementation(libs.junit4)
        implementation(libs.robolectric)
        implementation(zxingCore)
        implementation("org.jetbrains.compose.ui:ui-test-junit4:$cmp")
        implementation(
            "androidx.compose.ui:ui-test-manifest:$cmp"
        ) // ComponentActivity for the compose rule
        implementation("io.github.takahirom.roborazzi:roborazzi:$roborazzi")
        implementation("io.github.takahirom.roborazzi:roborazzi-compose:$roborazzi")
        implementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:$roborazzi")
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/screenshots"))
}

// Robolectric 4.17 on the JDK 21 launcher (audit #10); same opens as :ui:kit (P7 report, D-list).
tasks
    .withType<Test>()
    .matching { it.name.contains("AndroidHostTest") }
    .configureEach {
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
        )
    }
