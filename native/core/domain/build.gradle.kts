plugins { id("oc.kmp.library") }

// `api`: gateway signatures expose common (Outcome, ids), model (value types) and security (HostClass).
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:security"))
    }
}
