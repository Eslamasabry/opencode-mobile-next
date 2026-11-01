plugins { id("oc.kmp.library") }

// `api`: model types expose Failure, ids and JsonObject in their public signatures.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(libs.kotlinx.serialization.json)
    }
}
