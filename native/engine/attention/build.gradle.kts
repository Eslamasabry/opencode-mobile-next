plugins { id("oc.kmp.library") }

// Shared widget ingress only (plan 11 §11.3). The attention engine's runners remain a later unit.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
    }
}
