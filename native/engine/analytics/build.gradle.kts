plugins { id("oc.kmp.library") }

// The analytics core (owner decision 2026-11-01; plan 16 §5, plan 15 S4-ANALYTICS / C15-9). Depends on core only:
// every engine may emit into it once ModuleRules.engineOrder lists it (catalog request in docs/native/units/S1-ANALYTICS.md).
kotlin {
    sourceSets.androidMain { kotlin.srcDir("src/jvmShared/kotlin") }
    sourceSets.getByName("desktopMain") { kotlin.srcDir("src/jvmShared/kotlin") }
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        implementation(project(":core:net"))
        // B-190: the problem report starts from the app's own Redactor (KitRedact in Flutter).
        api(project(":core:security"))
        // B-180: Flutter 2.1.5's consent and crash-offer answers are read in place from the adopted prefs store.
        api(project(":core:data"))
    }
}

// An explicit, one-shot desktop smoke session; ordinary tests never contact the collection service.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    val smoke = providers.gradleProperty("oc.umamiSmoke").getOrElse("false")
    systemProperty("oc.umamiSmoke", smoke)
    if (smoke == "true") testLogging.showStandardStreams = true
}
