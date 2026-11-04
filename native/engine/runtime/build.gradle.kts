plugins { id("oc.kmp.library") }

// :engine:runtime (plan 11 §5.2, §7, §8; plan 15 S3-RTENG). The phone runtime's portable rules:
// heat and battery tiers,
// the process budget, and the phone server's lifecycle rules. Pure Kotlin over ports; the Android
// bindings are S3-RTWIRE.
// Depends on :core:common only, so it stays buildable while the rest of the core contract moves.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
    }
}
