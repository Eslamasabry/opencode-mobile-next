plugins { id("oc.kmp.feature") }

// :feature:phonesetup (unit P12-BUILD-RUNTIME-C): Set up OpenCode on this phone — the preflight, the real setup job
// (:engine:linuxsetup over android/linux SetupRunner, bound in androidApp graph/PhoneSetupWiring.kt), its progress,
// plain failure words with Details, Continue and Stop. Ported from lib/ui/screens/phone_setup/**.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:runtime"))
        implementation(project(":engine:linuxsetup"))
    }
}
