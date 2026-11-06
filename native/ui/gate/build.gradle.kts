plugins { id("oc.kmp.compose") }

// The golden-diff gate (docs/design/native-ui-rebuild/GOLDEN-STANDARD.md §8.2): desktopMain code that renders a kit
// scene and compares it with the frozen Flutter picture. Test-only (ModuleRules): kits and features take it as a
// desktopTest dependency (`implementation(project(":ui:gate"))`); no build file borrows its source folder (E02-S01,
// guarded by :quality's TestSourceRulesTest).
kotlin {
    sourceSets.getByName("desktopMain").dependencies {
        implementation(project(":ui:kit"))
        implementation(project(":ui:design"))
        implementation(libs.compose.ui.test) // runDesktopComposeUiTest, captureToImage
    }
}
