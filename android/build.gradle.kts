import com.android.build.api.dsl.LibraryExtension

allprojects {
    repositories {
        google()
        mavenCentral()
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
