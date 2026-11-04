plugins { id("oc.kmp.library") }

// Attention consumes the neutral all-server event stream; widget ingress remains shared.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
    }
}
