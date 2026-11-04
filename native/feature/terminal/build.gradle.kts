plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:runtime"))
        implementation(project(":engine:project-terminal"))
        implementation(project(":ui:kit-content"))
    }
}
