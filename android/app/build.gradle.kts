import java.io.FileInputStream
import java.util.Properties
import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android Gradle plugin.
    id("dev.flutter.flutter-gradle-plugin")
}

val ocPreview = (project.findProperty("ocPreview") as String?) == "true"
// Explicit test-build opt-in only; production builds retain normal R8 rules.
val ocStableEngineQa = (project.findProperty("ocStableEngineQa") as String?) == "true"
// Test-only release AOT smoke entry point and separate instrumentation runner.
val ocBd9Smoke = (project.findProperty("ocBd9Smoke") as String?) == "true"
val ocBb8Smoke = (project.findProperty("ocBb8Smoke") as String?) == "true"
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.isFile) {
    FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
}

android {
    namespace = "io.github.eslamasabry.opencode_mobile"
    // flutter_secure_storage 11 ships AAR metadata that requires API 37;
    // Flutter 3.47 still defaults to 36. Pin explicitly until Flutter's
    // default catches up, then drop this back to flutter.compileSdkVersion.
    compileSdk = 37
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "io.github.eslamasabry.opencode_mobile"
        // The built-in Linux and AI Team engine use process APIs from Android 8.
        minSdk = maxOf(flutter.minSdkVersion, 26)
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        testInstrumentationRunner = "io.github.eslamasabry.opencode_mobile." +
            (if (ocBb8Smoke) "Bb8DeviceSmoke" else if (ocBd9Smoke) "Bd9DeviceSmoke" else "PhoneEngineAcceptance")
        // A preview build installs beside the stable app instead of over it
        // (`flutter build apk --android-project-arg=ocPreview=true`): its own
        // package, name, data and built-in Ubuntu, so trying a new version
        // never needs uninstalling the one that holds the person's servers.
        // Projects in Termux are shared by both, since neither owns them.
        if (ocPreview) applicationIdSuffix = ".preview"
        manifestPlaceholders["appLabel"] =
            if (ocPreview) "OpenCode Preview" else "OpenCode Mobile"
        manifestPlaceholders["appShortcuts"] =
            if (ocPreview) "@xml/shortcuts_preview" else "@xml/shortcuts"
    }

    // Shorebird's pinned embedding ships release engine jars. This runner is
    // test-only; the stable journey additionally requires explicit runtime opt-in.
    testBuildType = "release"

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = keystoreProperties.getProperty("storeFile")?.let(::file)
            storePassword = keystoreProperties.getProperty("storePassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Release instrumentation shares the target's Kotlin/native ABI.
            // R8 prototype rewrites otherwise break test-APK calls into it.
            if (ocPreview || ocStableEngineQa || ocBd9Smoke) proguardFiles("phone-engine-instrumentation.pro")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

android {
    // proot and its loader ship as native libraries and must exist as real
    // files in the app's native library folder: the one place this app may
    // run programs from (BuiltinLinux.kt).
    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Runtime attestation hashes these exact staged executables. AGP's
            // release strip step must not rewrite them after manifest creation.
            keepDebugSymbols += "**/libaiteam_*.so"
        }
    }
}

// Check the APK, rather than only the staging directory: stripping, ABI
// filtering, or packaging changes must fail the build before delivery.
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val capitalizedName = variant.name.replaceFirstChar { it.uppercaseChar() }
    val packagedApks = variant.artifacts.get(SingleArtifact.APK)
    val checker = rootProject.file("../tool/qa/verify_phone_engine_apk.py")
    val manifest = file("src/main/assets/aiteam-engine-manifest.json")
    val abiFilters = android.defaultConfig.ndk.abiFilters.toList().sorted()
    val verifyBundle = tasks.register<Exec>("verify${capitalizedName}PhoneEngineApk") {
        group = "verification"
        description = "Verify packaged AI Team executable hashes against the runtime manifest."
        inputs.file(checker)
        inputs.file(manifest)
        inputs.dir(rootProject.file("../engine/phone/src"))
        inputs.file(rootProject.file("../engine/phone/Cargo.toml"))
        inputs.file(rootProject.file("../engine/phone/Cargo.lock"))
        inputs.dir(packagedApks)
        commandLine(
            listOf("python3", checker.absolutePath, "--manifest", manifest.absolutePath,
                "--source-root", rootProject.file("..").absolutePath,
                "--apk-dir", packagedApks.get().asFile.absolutePath) +
                abiFilters.flatMap { listOf("--abi", it) }
        )
    }
    tasks.matching { it.name == "assemble$capitalizedName" }.configureEach {
        dependsOn(verifyBundle)
    }
}

dependencies {
    // Flutter 3.47 excludes dev plugins from release configurations. The
    // explicit AOT smoke needs the native result bridge in this test build.
    if (ocBd9Smoke) add("releaseImplementation", project(":integration_test"))
    // ShortcutManagerCompat for the pinned-session launcher shortcuts
    // (PinnedSessionShortcuts.kt); same major line the Flutter embedding
    // already pulls in transitively, pinned so the compile classpath is
    // explicit rather than inherited.
    implementation("androidx.core:core:1.13.1")
    // Reads the Ubuntu Base tarball for the built-in Linux (BuiltinLinux.kt).
    implementation("org.apache.commons:commons-compress:1.27.1")
    // The local terminal's PTY (LocalTerminal.kt): Termux's terminal-emulator
    // library, Apache 2.0 (NOTICE). Only this module: termux-app itself and
    // termux-shared are GPLv3 and must not be used.
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.3")
}

repositories {
    // JitPack builds the Termux terminal libraries from their release tags.
    // Limited to that group so no other dependency can resolve from it.
    exclusiveContent {
        forRepository { maven("https://jitpack.io") }
        filter { includeGroup("com.github.termux.termux-app") }
    }
}
