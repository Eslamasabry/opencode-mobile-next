plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:integrations-catalog"))
        implementation(project(":engine:integrations-host"))
        implementation(project(":ui:kit-chat"))
        implementation(libs.kotlinx.collections.immutable)
    }
}
