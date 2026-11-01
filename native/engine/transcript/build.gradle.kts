plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
        implementation(project(":engine:connection")) // above transcript in ADR §6.2
    }
}
