plugins { id("oc.kmp.compose") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":ui:design"))
        implementation(libs.compose.material3) // kit modules only (ExternalRules)
    }
}
