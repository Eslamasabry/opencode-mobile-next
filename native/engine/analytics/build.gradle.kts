plugins { id("oc.kmp.library") }

// The analytics core (owner decision 2026-11-01; plan 16 §5, plan 15 S4-ANALYTICS / C15-9). Depends on core only:
// every engine may emit into it once ModuleRules.engineOrder lists it (catalog request in docs/native/units/S1-ANALYTICS.md).
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        // B-190: the problem report starts from the app's own Redactor (KitRedact in Flutter).
        api(project(":core:security"))
        // B-180: Flutter 2.1.5's consent and crash-offer answers are read in place from the adopted prefs store.
        api(project(":core:data"))
    }
}
