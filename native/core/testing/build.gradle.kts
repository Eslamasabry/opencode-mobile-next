plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
        api(libs.kotlinx.coroutines.test)
        api(libs.turbine)
    }
}
