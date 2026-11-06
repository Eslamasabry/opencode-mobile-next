plugins { id("oc.kmp.feature") }

// :feature:onboarding (07 §5 A01, A03; S2-CHATS): the opening gate's words and states, the root decision and first
// run. It reads no engine yet: the shell hooks the gate to `:engine:entry` through `OpeningWorld` (report §7).

kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(project(":ui:gate")) // order 40 renders onboarding states in their owning module
        implementation(libs.compose.desktop.currentos)
    }
}
apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))
tasks.withType<Test>().matching { it.name == "desktopTest" }.configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
