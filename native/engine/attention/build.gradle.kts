plugins { id("oc.kmp.library") }

// Shared widget ingress (plan 11 §11.3) plus BUILD-DIAGNOSTICS-ATTENTION's attention engine: automatic-activity history,
// elsewhere attention, the profile monitor, live status, notification text and the widget snapshot.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        implementation(project(":core:security"))
    }
}
