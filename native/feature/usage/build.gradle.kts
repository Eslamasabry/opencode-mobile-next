plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:integrations-usage"))
        implementation(
            project(":engine:usage")
        ) // Calendar ranges in the device's own time zone (B-PROTO-122).
        // KitLimitBar (pace marker) and KitTrendLine (cost chart), B-159/B-160.
        implementation(project(":ui:kit-content"))
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
