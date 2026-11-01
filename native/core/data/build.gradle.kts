plugins { id("oc.kmp.library") }

// :core:data (plan 05 §1, plan 03 §10.5). May depend on :core:common, :core:security and :core:model only (ADR §6.1).
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:security")) // Redactor is in public constructors (AeadSecretStore, AndroidDataStores)
    }
    sourceSets.getByName("androidHostTest").dependencies {
        implementation(kotlin("test-junit"))
        implementation(libs.junit4)
        implementation(libs.robolectric)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(kotlin("test-junit"))
    }
}
