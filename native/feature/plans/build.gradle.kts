plugins { id("oc.kmp.feature") }

// :feature:plans (unit P12-BUILD-INTEGRATIONS-B). Usage › Plans arranged from kit parts over
// :engine:integrations-usage. The app wiring supplies the engine fed by the real sources (PlansSource).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:integrations-usage"))
        implementation(libs.kotlinx.collections.immutable)
        implementation(libs.kotlinx.datetime)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos) // skiko natives for the desktop UI tests
    }
}
