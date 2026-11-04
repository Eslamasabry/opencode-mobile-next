plugins { id("oc.kmp.library") }

// :engine:termuxhost (unit P12-BUILD-RUNTIME-C): the Termux host scripts and bridge rules (lib/termux/bridge.dart +
// scripts, runtime switch, recovery, clarity, default runtime; Termux is migrate-out only, plan 01 D7).
// Depends on core only, so it sits outside the ordered engine list (ADR §6.2). The script-running tests are in
// desktopTest (real bash/dash, like the Flutter tests).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:security"))
    }
}
