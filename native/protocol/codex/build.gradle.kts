plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies { api(project(":core:domain")) }
}
