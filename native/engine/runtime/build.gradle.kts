plugins { id("oc.kmp.library") }

// :engine:runtime (plan 11 §5.2, §7, §8; plan 15 S3-RTENG). The phone runtime's portable rules: heat
// and battery tiers, the process budget, the phone server's lifecycle rules and the remote project
// terminal owner. Pure Kotlin over ports; the Android bindings are S3-RTWIRE. Depends on core
// modules only (:core:domain/:core:model for the terminal gateway contract).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:domain"))
        implementation(project(":core:model"))
    }
}
