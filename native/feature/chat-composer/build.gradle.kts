plugins { id("oc.kmp.feature") }

// :feature:chat-composer (plan 09 §10, plan 15 S3-COMPUI): the composer UI that arranges kit parts over the composer
// engine. Attachments are S3-MEDIAATT's files; voice is S5's (the mic is a slot here).
//
// Not yet a dependency: :engine:composer. On 2026-11-01 it does not compile against the flat core Failure contract
// (it still names StorageProblem; INTEGRATE-W2 reconciles it), and units may depend only on modules that compile.
// The presentation layer reaches the engines through its own port, ComposerPort; the adapter over Drafts,
// DraftAutosave, PromptHistoryNavigation and PromptDelivery is the first follow-up (S3-COMPUI report §6).
kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests and goldens
    }
}

// Goldens (desktop, Skiko software raster): `-Poc.recordGoldens=true` writes them, otherwise they are compared.
tasks.withType<Test>().configureEach {
    systemProperty("oc.goldens.dir", layout.projectDirectory.dir("src/desktopTest/goldens").asFile.absolutePath)
    systemProperty("oc.goldens.record", providers.gradleProperty("oc.recordGoldens").orElse("false").get())
}
