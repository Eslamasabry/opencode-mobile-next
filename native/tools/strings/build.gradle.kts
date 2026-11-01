// :tools:strings — the ARB → CMP strings converter and the drift check (plan 06 §13.3, §13.8). A JVM tool: it
// ships nothing and no module may depend on it (ModuleRules: tools depend on nothing).
plugins {
    id("oc.jvm.library")
    application
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass = "io.github.eslamasabry.oc.tools.strings.MainKt"
    applicationName = "strings"
}

// native/ → the repository root (the Flutter lib/ and lib/l10n/ the converter reads).
val nativeRoot: File = rootDir
val repoRoot: File = rootDir.parentFile

tasks.test {
    // The real-data tests read the ARB files, the Dart sources, the Android res and the committed :ui:words XML.
    systemProperty("strings.repoRoot", repoRoot.canonicalPath)
    systemProperty("strings.nativeRoot", nativeRoot.canonicalPath)
    systemProperty("strings.toolDir", projectDir.canonicalPath)
    // ./gradlew :tools:strings:test -PupdateGoldens=true rewrites src/test/resources/golden from the fixture.
    systemProperty("strings.updateGoldens", providers.gradleProperty("updateGoldens").getOrElse("false"))
    maxHeapSize = "2g"
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named<JavaExec>("run") {
    // `--args` paths are resolved from the folder Gradle was started in.
    workingDir = gradle.startParameter.currentDir
}
