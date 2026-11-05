plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos)
    }
}
