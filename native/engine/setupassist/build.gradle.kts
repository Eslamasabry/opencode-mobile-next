plugins { id("oc.kmp.library") }

// :engine:setupassist (unit P12-BUILD-RUNTIME-C): the setup assistant (lib/domain/setup_*, lib/state/setup_*,
// lib/api2/setup_config_adapter.dart: OC2 config reads, MCP registry, planner, controller, audit store).
// Depends on core only, so it sits outside the ordered engine list (ADR §6.2).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        api(project(":core:security"))
        api(project(":core:data"))
        api(libs.kotlinx.serialization.json)
    }
}
