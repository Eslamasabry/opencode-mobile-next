plugins { id("oc.kmp.library") }

// :engine:lifecycle (unit P12-BUILD-RUNTIME-C): the restart lifecycle report and deletion drains (lib/state/lifecycle_report.dart, builtin owner and recovery-policy drains, manage-space measuring, the this-phone mark).
// Depends on core only, so it sits outside the ordered engine list (ADR §6.2).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
    }
}
