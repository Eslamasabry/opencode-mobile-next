plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model")) // audit BLOCKER 3: AddressRules.normalize returns ServerOrigin
    }
}
