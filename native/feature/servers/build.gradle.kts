plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:connection"))
    }
}

// UI-PARITY captures (ParityCapture, desktop): `-Poc.parityOut=<dir>` writes one PNG per screen state and frame.
tasks.withType<Test>().configureEach {
    systemProperty("oc.parity.out", providers.gradleProperty("oc.parityOut").orElse("").get())
}

// The same pinned font inventory as the other desktop goldens.
apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))
