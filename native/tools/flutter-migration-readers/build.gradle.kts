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
    // The ledger cross-checks read the Flutter repo's own ledger file and docs/native/migration/.
    // The module runs both as a standalone build (rootDir = this folder) and inside native/ (rootDir = native/), so
    // the repo root is found from projectDir, which is native/tools/flutter-migration-readers in both: three up.
    systemProperty("oc.repoRoot", projectDir.resolve("../../..").canonicalPath)
    // Optional: a checkout of the released Flutter line (git worktree add <dir> origin/master). When given, the
    // store-ledger test rescans it instead of trusting only the committed snapshot.
    (providers.gradleProperty("flutterTree").orNull ?: System.getenv("OC_FLUTTER_TREE"))?.let {
        systemProperty("oc.flutterTree", file(it).canonicalPath)
    }
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

// Rescans a checkout of the released Flutter line and rewrites docs/native/migration/flutter-2.1.5-names.json.
//   git worktree add ../flutter-2.1.5 v2.1.5+63
//   ./gradlew scanFlutterTree -PflutterTree=../flutter-2.1.5 -PflutterCommit=$(git rev-parse 'v2.1.5+63^{commit}')
tasks.register<JavaExec>("scanFlutterTree") {
    group = "verification"
    description = "Rewrite the committed store-ledger snapshot from a Flutter checkout"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.eslamasabry.oc.migration.readers.ledger.FlutterStoreScannerMain")
    val tree = providers.gradleProperty("flutterTree")
    val commit = providers.gradleProperty("flutterCommit")
    val out = rootDir.resolve("../../../docs/native/migration/flutter-2.1.5-names.json").canonicalPath
    argumentProviders += CommandLineArgumentProvider {
        listOf(file(tree.get()).canonicalPath, out, "2.1.5+63", "v2.1.5+63", commit.get())
    }
}
