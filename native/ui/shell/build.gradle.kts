plugins { id("oc.kmp.compose") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":ui:kit"))
        implementation(project(":ui:routes"))
        implementation(libs.navigation3.ui)
        implementation(libs.lifecycle.viewmodel.navigation3)
        implementation(libs.lifecycle.viewmodel.compose)
        implementation(libs.metrox.viewmodel.compose)
        implementation(libs.savedstate)
    }
}
