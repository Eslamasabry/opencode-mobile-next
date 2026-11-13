plugins { id("oc.kmp.library") }

// :protocol:paseo — the Paseo daemon client (plan 04 §8, WP-04.10). Depends on the frozen core contract only.
kotlin {
    sourceSets.commonMain.dependencies { api(project(":core:domain")) }
}

// Live-proof tests (desktopTest, JVM) talk to a running daemon when one is set.
tasks.withType<Test>().configureEach {
    // Live proof (PaseoLiveProofTest): set OC_PASEO_URL (and OC_PASEO_PASSWORD) to a scratch daemon; skipped otherwise.
    providers.environmentVariable("OC_PASEO_URL").orNull?.let { systemProperty("oc.paseo.url", it) }
    providers.environmentVariable("OC_PASEO_PASSWORD").orNull?.let { systemProperty("oc.paseo.password", it) }
    providers.environmentVariable("OC_PASEO_DIR").orNull?.let { systemProperty("oc.paseo.dir", it) }
}
