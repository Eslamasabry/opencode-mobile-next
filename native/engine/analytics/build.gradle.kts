plugins { id("oc.kmp.library") }

// The analytics core (owner decision 2026-11-01; plan 16 §5, plan 15 S4-ANALYTICS / C15-9). Depends on core only:
// every engine may emit into it once ModuleRules.engineOrder lists it (catalog request in docs/native/units/S1-ANALYTICS.md).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
    }
}
