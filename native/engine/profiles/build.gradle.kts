plugins { id("oc.kmp.library") }

// :engine:profiles (engine order #3, ADR §6.2): the profile store with trust and pins, the persisted profile DTO and
// the backwards deletion transaction over ProfileScope (plan 05 §3.5, plan 16 SI-5, Astra M1). Unit S1-ENG.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:data"))
        implementation(project(":core:security"))
    }
}
