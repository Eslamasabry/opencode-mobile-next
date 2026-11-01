plugins { id("oc.kmp.library") }

// :engine:diagnostics (engine order #2, ADR §6.2): the redacted diagnostics ring, crash categories and the crash store
// contract, the exit classifier (MemoryLimiter:AnonSwap, plan 11 §7.3) and the analytics port (plan 18). Unit S1-ENG.
// :engine:analytics is reached through DiagnosticsSignals until ModuleRules.engineOrder lists "analytics" (A18-1).
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        implementation(project(":core:security"))
    }
}
