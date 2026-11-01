import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Same Kotlin pin as plan/01 §8 (Kotlin 2.4.21).
    kotlin("jvm") version "2.4.21"
}

group = "io.github.eslamasabry.oc.tools"
version = "0.1.0"

kotlin {
    explicitApi()
    compilerOptions {
        // The code is meant to move into :android:migration (JDK 17 target), so stay on 17 bytecode.
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // No runtime dependencies on purpose: the readers must be copyable into :android:migration and
    // :core:data without dragging a JSON or XML library along.
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // The ledger cross-check reads the Flutter repo's own ledger file.
    systemProperty("oc.repoRoot", rootDir.resolve("../../..").canonicalPath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Writes a complete synthetic fixture set (prefs XML, DataStore pb, secure-storage XMLs, image-picker cache)
// to build/fixtures. The committed copies in src/test/resources/fixtures were made with this task.
tasks.register<JavaExec>("generateFixtures") {
    group = "verification"
    description = "Generate synthetic Flutter on-disk fixtures into build/fixtures"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGeneratorMain")
    args(layout.buildDirectory.dir("fixtures").get().asFile.absolutePath)
}
