plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:integrations-usage"))
        implementation(
            project(":engine:usage")
        ) // Calendar ranges in the device's own time zone (B-PROTO-122).
        // KitLimitBar (pace marker) and KitTrendLine (cost chart), B-159/B-160.
        implementation(project(":ui:kit-content"))
        // LocalKitRedactor: the Details "Account" value passes the app's one redactor (Flutter PR #136).
        implementation(project(":ui:kit-chat"))
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(project(":ui:gate")) // order 50 renders the usage page against Flutter's goldens
    }
}

// Goldens for Usage › Limits and Cost (desktop, Skiko software raster): `-Poc.recordGoldens=true` writes them,
// otherwise they are compared. Files: src/desktopTest/goldens/<scene>-<variant>.png.
tasks.withType<Test>().configureEach {
    systemProperty(
        "oc.goldens.dir",
        layout.projectDirectory.dir("src/desktopTest/goldens").asFile.absolutePath,
    )
    systemProperty(
        "oc.goldens.record",
        providers.gradleProperty("oc.recordGoldens").orElse("false").get(),
    )
}

// Keep the Linux desktop golden fallback identical to the recording environment.
apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))
