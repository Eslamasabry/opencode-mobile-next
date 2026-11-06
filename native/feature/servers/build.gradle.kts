plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:connection"))
        implementation(project(":engine:agents"))
        implementation(project(":engine:lifecycle"))
        implementation(project(":engine:runtime"))
        implementation(project(":engine:setupassist"))
        implementation(project(":ui:kit-chat"))
    }
}

// Order 45 compares the real server screens against the frozen Flutter scenes (the :ui:gate module).
kotlin.sourceSets.getByName("desktopTest").dependencies {
    implementation(project(":ui:gate"))
    implementation(libs.compose.desktop.currentos)
}
tasks.withType<Test>().matching { it.name == "desktopTest" }.configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
