plugins { id("oc.kmp.library") }

// :engine:setupscripts (unit P12-BUILD-RUNTIME-C): the builtin setup shell scripts (lib/builtin/setup/setup_scripts.dart: download, apt install, combined checks), run under dash and bash in desktopTest.
// Depends on core only, so it sits outside the ordered engine list (ADR §6.2).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
    }
}
