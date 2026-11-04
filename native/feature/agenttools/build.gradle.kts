plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":ui:kit-content"))
        implementation(project(":engine:integrations-usage"))
    }
}
