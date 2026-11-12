plugins { id("oc.kmp.library") }

// Shared widget ingress (plan 11 §11.3) plus BUILD-DIAGNOSTICS-ATTENTION's attention engine: automatic-activity history,
// elsewhere attention, the profile monitor, live status, notification text and the widget snapshot.
// Attention also consumes the neutral all-server event stream.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:data")) // the edge dock registers its nickname key shape for the profile sweep
        implementation(project(":core:security"))
    }
}
