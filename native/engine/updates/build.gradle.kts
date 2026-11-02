plugins { id("oc.kmp.library") }

// :engine:updates (unit P12-PLATFORM): the in-app update notice of the Flutter app (lib/update/), ported. GitHub
// release check every six hours, the ABI file pick, SHA256SUMS, signer / package / versionCode checks, resumable
// download and the install hand-off. Network, files and PackageInstaller are ports bound by :androidApp
// (graph/PlatformWiring.kt); no Android types here. Not in ModuleRules.engineOrder: no other engine depends on it.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
    }
}
