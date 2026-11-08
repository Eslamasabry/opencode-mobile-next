plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
        implementation(project(":core:security"))
        // E00-S03: the Flutter chat keys are read in place from the adopted preferences file.
        implementation(project(":core:data"))
        // B-190: conn_attempt and the conn_connect Performance trace (consent-gated in :engine:analytics).
        implementation(project(":engine:analytics"))
    }
}
