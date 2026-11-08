plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:integrations-catalog"))
    }
    // S28: the catalogue's ViewModel, row facts and screen run on the desktop (Skiko software raster).
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos)
    }
}

// S28: `-Poc.mcpCatalogShots=<dir>` writes the catalogue's PNGs there; otherwise nothing is written.
tasks.withType<Test>().configureEach {
    systemProperty(
        "oc.mcpCatalogShots",
        providers.gradleProperty("oc.mcpCatalogShots").orElse("").get(),
    )
}

apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))
