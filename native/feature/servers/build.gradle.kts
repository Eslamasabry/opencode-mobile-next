plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:connection"))
        implementation(project(":engine:lifecycle"))
        implementation(project(":engine:runtime"))
        implementation(project(":ui:kit-chat"))
    }
}
