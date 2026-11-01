plugins { id("oc.kmp.feature") }

// :feature:phone (plan 07 §5 F, plan 11 §6–§8; plan 15 S3-PHONEUI). This phone, Running on this phone with the
// child-process-limit card, and Keep running. Android only at runtime: the shell registers these pages only when
// PlatformCapabilities.builtinLinux is true (07 §2.8); the code is common so its rules test on desktop.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:runtime")) // RuntimeStatus, ProbeAnswer, StartProblem, ProcessBudget (public API)
    }
}
