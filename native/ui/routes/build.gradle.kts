plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        api(libs.navigation3.runtime) // NavKey and EntryProviderScope are part of this module's API
        api(libs.savedstate) // SavedStateConfiguration is part of RouteSaving's API
    }
}
