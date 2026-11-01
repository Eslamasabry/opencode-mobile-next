plugins { id("oc.kmp.library") }

// :engine:requests (plan 09 §5). S2-REQ owns the module and its permission / one-question files; S3-REQFORMS adds the
// form and multi-question files. If S2-REQ's build file lands first, keep S2-REQ's: this one only lists what the form
// files need (all of it is already below :engine:requests in ADR §6.2).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
    }
}
