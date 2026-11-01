plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        // The adapter publishes core's GatewayEvent (Oc2CoreProjection); ADR §6.1 allows protocol → model/domain.
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
    }
    sourceSets.commonTest.dependencies { implementation(project(":core:testing")) }
}

// Fixture-backed tests (desktopTest, JVM) read native/fixtures/oc2 and the Flutter originals under test/fixtures.
val fixtureRoot: String = rootProject.layout.projectDirectory.dir("fixtures/oc2").asFile.absolutePath
val repoRoot: String = rootProject.layout.projectDirectory.asFile.parentFile.absolutePath
tasks.withType<Test>().configureEach {
    systemProperty("oc.fixtures.oc2", fixtureRoot)
    systemProperty("oc.repo.root", repoRoot)
    inputs.dir(fixtureRoot).withPropertyName("oc2Fixtures")
}
