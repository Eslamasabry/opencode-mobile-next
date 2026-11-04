plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:net"))
        implementation(project(":core:model"))
        implementation(project(":core:security"))
    }
}
