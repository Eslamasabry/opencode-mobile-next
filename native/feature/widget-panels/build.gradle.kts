plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.getByName("desktopTest") {
        kotlin.srcDir("../../ui/gate/src/desktopMain/kotlin")
        resources.srcDir("../../ui/gate/src/desktopMain/resources")
    }
    sourceSets.getByName("desktopTest").dependencies {
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
