plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":ui:kit-chat"))
        implementation(project(":engine:git"))
        implementation(
            project(":engine:agents")
        ) // ChatFeed and the return brief (17 §1.6, 07 §5 B15; S2-CHATS)
        implementation(project(":engine:attention"))
        implementation(project(":engine:settings"))
    }
}

kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos)
        // ChatsGateTest renders the real Conversations screen through the golden-diff gate (test-only module).
        implementation(project(":ui:gate"))
        // E05-S21: HomeBackExitScreenTest drives Back through a navigation-event dispatcher, as Android's input does.
        implementation(libs.navigationevent.compose)
    }
}
// E20-S09 desktop goldens (Skiko software raster): `-Poc.recordGoldens=true` writes them, otherwise they are compared.
tasks.withType<Test>().configureEach {
    systemProperty(
        "oc.goldens.dir",
        layout.projectDirectory.dir("src/desktopTest/goldens").asFile.absolutePath,
    )
    systemProperty(
        "oc.goldens.record",
        providers.gradleProperty("oc.recordGoldens").orElse("false").get(),
    )
}

apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))
