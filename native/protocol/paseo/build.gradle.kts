plugins { id("oc.kmp.library") }

// :protocol:paseo — the Paseo daemon client (plan 04 §8, WP-04.10). Depends on the frozen core contract only.
kotlin {
    sourceSets.commonMain.dependencies { api(project(":core:domain")) }
}

// Fixture-backed and live-proof tests (desktopTest, JVM) read the Flutter fixtures and, when set, a running daemon.
val repoRoot: String = rootProject.layout.projectDirectory.asFile.parentFile.absolutePath
tasks.withType<Test>().configureEach {
    systemProperty("oc.repo.root", repoRoot)
    // Live proof (PaseoLiveProofTest): set OC_PASEO_URL (and OC_PASEO_PASSWORD) to a scratch daemon; skipped otherwise.
    providers.environmentVariable("OC_PASEO_URL").orNull?.let { systemProperty("oc.paseo.url", it) }
    providers.environmentVariable("OC_PASEO_PASSWORD").orNull?.let { systemProperty("oc.paseo.password", it) }
    providers.environmentVariable("OC_PASEO_DIR").orNull?.let { systemProperty("oc.paseo.dir", it) }
}
