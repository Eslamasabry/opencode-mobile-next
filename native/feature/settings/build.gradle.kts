plugins { id("oc.kmp.feature") }

// :feature:settings (plan 07 §5 I, plan 18 §6; unit SETTINGS-UI): the Settings hub with its five
// groups, the
// Appearance, Voice, Notifications, Updates, About, Privacy and "What we collect" pages, arranged
// from kit parts.
// The analytics page reads :engine:analytics. Not yet a dependency: :engine:settings. On 2026-11-01
// it does not
// compile against the flat core Failure contract (StorageProblem, DartJson; INTEGRATE-W2 reconciles
// it), and units may
// depend only on modules that compile. Personal choices, voice, notifications and updates go
// through this module's
// own ports (presentation/SettingsPorts.kt); the Preferences adapter is the first follow-up
// (SETTINGS-UI report §6).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:analytics"))
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(
            libs.compose.desktop.currentos
        ) // skiko natives for the desktop UI tests and goldens
    }
}

// Goldens (desktop, Skiko software raster): `-Poc.recordGoldens=true` writes them, otherwise they
// are compared.
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
