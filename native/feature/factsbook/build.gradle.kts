plugins { id("oc.kmp.feature") }

// :feature:factsbook (s23-factsbook): FactsBook, the memory pages (F: lib/ui/screens/factsbook/*), arranged from kit
// parts over :engine:factsbook. Copy is the converted Flutter `fb*` strings in :ui:words (en + ar). The app wiring
// supplies the engine, the connected profile and the developer switch through FactsBookAccess.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:factsbook"))
        implementation(libs.kotlinx.collections.immutable)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
