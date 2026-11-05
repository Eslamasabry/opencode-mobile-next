plugins { id("oc.kmp.feature") }

// :feature:onboarding (07 §5 A01, A03; S2-CHATS): the opening gate's words and states, the root decision and first
// run. It reads no engine yet: the shell hooks the gate to `:engine:entry` through `OpeningWorld` (report §7).

kotlin {
    sourceSets.getByName("desktopTest").dependencies { implementation(libs.compose.desktop.currentos) }
}
apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))
