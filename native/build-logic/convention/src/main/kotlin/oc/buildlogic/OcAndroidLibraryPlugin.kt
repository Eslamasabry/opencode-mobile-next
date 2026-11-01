package oc.buildlogic

import com.android.build.api.dsl.LibraryExtension
import oc.buildlogic.LintPolicy.ocLint
import oc.buildlogic.Sdk.ocCompileSdk
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/** `oc.android.library`: com.android.library with AGP 9 built-in Kotlin, Metro, Robolectric on JDK 21. */
class OcAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("dev.zacsweers.metro")
            extensions.configure<LibraryExtension> {
                namespace = Namespaces.of(path)
                compileSdk { ocCompileSdk() }
                defaultConfig { minSdk = Sdk.MIN }
                lint { ocLint(file("lint.xml")) } // plan 16 §2.2 table (LintPolicy, S1-SECGATES)
            }
            val toolchains = extensions.getByType<JavaToolchainService>()
            tasks.withType<Test>().configureEach {
                maxParallelForks = providers.gradleProperty("oc.test.forks").get().toInt()
                javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(Jdk.TEST_LAUNCHER)) })
            }
            enforceModuleRules()
        }
}
