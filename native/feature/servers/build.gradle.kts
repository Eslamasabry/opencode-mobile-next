plugins {
    id("oc.kmp.feature")
    alias(libs.plugins.roborazzi) // S2-SERVERS goldens: `:feature:servers:recordRoborazziDesktop` / `verifyRoborazziDesktop`
}

// Test-only artifact the catalog has no entry for yet; the version comes from the catalog's pin (same as :ui:kit, P7).
// Feature modules get no androidHostTest source set (oc.kmp.library adds it only to platform modules), so the screen
// goldens use Roborazzi's Compose Desktop capture; the Robolectric copies join when :ui:testing exists (report D1).
val roborazzi = libs.versions.roborazzi.get()

kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation("io.github.takahirom.roborazzi:roborazzi-compose-desktop:$roborazzi")
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/screenshots"))
}
