plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(project(":ui:gate"))
        implementation(libs.compose.desktop.currentos)
    }
}

apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))

tasks
    .withType<Test>()
    .matching { it.name == "desktopTest" }
    .configureEach {
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
