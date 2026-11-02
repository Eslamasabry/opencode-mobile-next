plugins { id("oc.kmp.feature") }

// P12-MIGRATION: the update gate (plan 05 §8, 07 §5 A01), moved here from :ui:shell (S2-GATEUI) and fed by
// :engine:migration. Words: strings_migration.xml in :ui:words.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:migration"))
    }
}
