plugins { id("oc.kmp.feature") }

// :feature:phone (plan 07 §5 F, plan 11 §6–§8; plan 15 S3-PHONEUI). This phone, Running on this
// phone with the
// child-process-limit card, and Keep running. Android only at runtime: the shell registers these
// pages only when
// PlatformCapabilities.builtinLinux is true (07 §2.8); the code is common so its rules test on
// desktop.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:settings")) // first-start consent identities
        implementation(
            project(":engine:runtime")
        ) // RuntimeStatus, ProbeAnswer, StartProblem, ProcessBudget (public API)
    }
}

kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(project(":ui:gate")) // order 48 renders phone-owned screens through the golden-diff gate
        implementation(libs.compose.desktop.currentos)
    }
}
apply(from = rootProject.file("gradle/desktop-golden-fonts.gradle"))

tasks.withType<Test>().matching { it.name == "desktopTest" }.configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
