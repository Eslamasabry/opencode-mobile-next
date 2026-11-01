plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model")) // audit BLOCKER 3: AddressRules.normalize returns ServerOrigin
    }
}
