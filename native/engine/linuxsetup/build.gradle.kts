plugins { id("oc.kmp.library") }

// :engine:linuxsetup (unit P12-BUILD-RUNTIME-C): the builtin Linux setup engine's portable rules (lib/builtin/setup:
// components, progress, job record, failures, preflight, inventory, finish, termux host params).
// Depends on core only, so it sits outside the ordered engine list (ADR §6.2).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:security"))
    }
}
