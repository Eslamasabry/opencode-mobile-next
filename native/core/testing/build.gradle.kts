plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(libs.kotlinx.coroutines.test)
        api(libs.turbine)
        implementation(kotlin("test"))
    }
}
