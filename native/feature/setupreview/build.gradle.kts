plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.collections.immutable)
    }
}
