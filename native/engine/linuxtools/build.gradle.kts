plugins { id("oc.kmp.library") }

// :engine:linuxtools (unit P12-BUILD-RUNTIME-C): the phone Linux tools (lib/termux/processes.dart, storage.dart, termux_folders.dart, lib/builtin/builtin_folders.dart, lib/domain/shared_storage_path.dart).
// Depends on core only, so it sits outside the ordered engine list (ADR §6.2).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
    }
}
