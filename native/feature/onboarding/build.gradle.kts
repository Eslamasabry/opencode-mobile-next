plugins { id("oc.kmp.feature") }

// :feature:onboarding (07 §5 A01, A03; S2-CHATS): the opening gate's words and states, the root decision and first
// run. It reads no engine yet: the shell hooks the gate to `:engine:entry` through `OpeningWorld` (report §7).

kotlin {
    sourceSets.getByName("desktopTest").dependencies { implementation(libs.compose.desktop.currentos) }
}
apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))

// Order 40 hosts the setup galleries; dependencies are test-only, never feature navigation edges.
kotlin.sourceSets.getByName("desktopTest") {
    kotlin.srcDir("../../ui/gate/src/desktopMain/kotlin")
    resources.srcDir("../../ui/gate/src/desktopMain/resources")
    dependencies {
        implementation(project(":feature:setup"))
        implementation(project(":feature:phonesetup"))
        implementation(project(":engine:runtime"))
    }
}
tasks.withType<Test>().matching { it.name == "desktopTest" }.configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
