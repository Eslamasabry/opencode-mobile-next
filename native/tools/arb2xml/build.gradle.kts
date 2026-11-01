plugins {
    kotlin("jvm") version "2.4.21"
    application
}

group = "io.github.eslamasabry.oc.tools"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.eslamasabry.oc.tools.arb2xml.MainKt")
    applicationName = "arb2xml"
}

tasks.test {
    useJUnitPlatform()
    // Tests read the real ARB files and Dart sources of this repository.
    systemProperty("arb2xml.repoRoot", rootDir.resolve("../../..").canonicalPath)
    systemProperty("arb2xml.toolDir", rootDir.canonicalPath)
    // ./gradlew test -PupdateGoldens=true rewrites src/test/resources/golden from the fixture.
    systemProperty("arb2xml.updateGoldens", providers.gradleProperty("updateGoldens").getOrElse("false"))
    testLogging { events("failed"); showStandardStreams = false; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.named<JavaExec>("run") {
    // `--args` paths are resolved from the folder gradle was started in.
    workingDir = project.gradle.startParameter.currentDir
}
