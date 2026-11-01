plugins {
    id("oc.kmp.compose")
    alias(libs.plugins.roborazzi) // P7: goldens recorded and verified through the plugin's tasks (plan 06 §15.3)
}

// Test-only artifacts the catalog has no entries for yet; versions come from the catalog's pins (P7 report, D-list).
val cmp = libs.versions.cmp.asProvider().get()
val roborazzi = libs.versions.roborazzi.get()

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":ui:words")) // S1-KITA2: kit-internal words from strings_kit.xml (plan 06 §13.5)
        api(project(":ui:design")) // tokens appear in kit signatures (KitHaptic, TypeRole, TextTone, KitShapeId)
        implementation(libs.compose.material3) // kit modules only (ExternalRules)
        implementation(libs.navigationevent.compose) // S1-KITA2: the topmost sheet or dialog owns Back (plan 06 §7.4)
    }
    sourceSets.getByName("androidHostTest").dependencies {
        implementation(libs.junit4)
        implementation(libs.robolectric)
        implementation("org.jetbrains.compose.ui:ui-test-junit4:$cmp")
        implementation("androidx.compose.ui:ui-test-manifest:$cmp") // ComponentActivity for the compose rule
        implementation("io.github.takahirom.roborazzi:roborazzi:$roborazzi")
        implementation("io.github.takahirom.roborazzi:roborazzi-compose:$roborazzi")
        implementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:$roborazzi")
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/screenshots"))
}

// Robolectric 4.17 on the JDK 21 launcher (audit #10): its FileDescriptor interceptor reads JDK internals, and AGP-KMP
// host tests do not add the opens that AGP's unit-test tasks add. Module-local for P7; belongs in oc.kmp.library.
tasks.withType<Test>().matching { it.name.contains("AndroidHostTest") }.configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}
