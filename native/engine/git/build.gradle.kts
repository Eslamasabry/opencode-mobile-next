plugins { id("oc.kmp.library") }

// :engine:git (unit P12-GIT): the Git page model of lib/domain/git + lib/state/git_* — one POSIX sh
// script per
// action through a GitShell (an OpenCode 2 server's POST /api/shell), the typed parsers, the GitHub
// device flow and
// the per-profile GitHub sign-in. Depends on core only, so it sits outside the ordered engine list
// (ADR §6.2).
val ktor: String = libs.versions.ktor.get()

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
        implementation(project(":core:security"))
        implementation(project(":core:data"))
        api(project(":core:net"))
    }
    sourceSets.commonTest.dependencies {
        implementation("io.ktor:ktor-client-mock:$ktor")
    }
}
