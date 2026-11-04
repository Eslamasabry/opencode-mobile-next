plugins { id("oc.kmp.feature") }

// :feature:linux (plan 07 §5, plan 11 §8; parity unit P12-LINUX). The Linux manager of the phone's own Ubuntu:
// what listens (ports with preview), storage and safe cleanups, packages (install, remove, update) as one long job,
// repair and reset with plain confirmations. Common code over the narrow LinuxHost port; the Android binding is
// androidApp's graph/LinuxWiring.kt. Ported from lib/builtin/linux_manager/** and lib/ui/screens/linux_manager/**.

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:runtime"))
    }
}
