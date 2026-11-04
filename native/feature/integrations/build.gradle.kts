plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:integrations-catalog"))
        implementation(libs.kotlinx.collections.immutable)
    }
}
