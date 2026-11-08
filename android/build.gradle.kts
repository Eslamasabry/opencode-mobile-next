import com.android.build.api.dsl.LibraryExtension

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// F-Droid build (see android/app/build.gradle.kts): refuse Google's proprietary
// SDKs outright. A dependency that pulls one in fails here with its name,
// instead of reaching the APK and F-Droid's scanner.
if ((project.findProperty("ocFdroid") as String?) == "true") {
    val banned = listOf("com.google.android.gms", "com.google.mlkit", "com.google.firebase")
    allprojects {
        configurations.configureEach {
            resolutionStrategy.eachDependency {
                if (banned.any { requested.group == it || requested.group.startsWith("$it.") }) {
                    throw GradleException(
                        "F-Droid build: ${requested.group}:${requested.name} is a proprietary SDK " +
                            "(configuration ${this@configureEach.name} of ${project.path}). " +
                            "Run tool/fdroid/prebuild.dart first.",
                    )
                }
            }
        }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)

    // flutter_secure_storage contains optional biometric code, but this app
    // deliberately uses its default non-biometric AndroidOptions. The library
    // lints that unreachable path without the consuming app's configuration.
    // Keep this exception limited to that dependency and that one detector.
    if (project.name == "flutter_secure_storage") {
        plugins.withId("com.android.library") {
            extensions.configure<LibraryExtension> {
                lint {
                    disable += "MissingPermission"
                }
            }
        }
    }
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
