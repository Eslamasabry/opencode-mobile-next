plugins { id("oc.kmp.library") }

// :engine:settings (engine order #1, ADR §6.2): the preferences facade over :core:data, the settings scope access,
// three-state consent reads and the teaching registry (plan 07 §5 I, plan 16 SI-22, plan 06 §11.1). Unit S1-ENG.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:data"))
    }
}
