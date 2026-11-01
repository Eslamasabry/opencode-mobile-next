package oc.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import oc.buildlogic.Sdk.ocCompileSdk
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** `oc.kmp.library`: android (AGP-KMP) + jvm("desktop") (+ iOS klibs with -Poc.ios=true). No Compose. Plan 03 §6.4. */
class OcKmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("dev.zacsweers.metro")

            val layer = ModuleRules.layerOf(path)
            val ios = providers.gradleProperty("oc.ios").orNull == "true"
            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(Jdk.BUILD)
                if (layer.explicitApi) explicitApi()

                // P-03-1 settled: the AGP-KMP target is an extension of the kotlin extension, type
                // KotlinMultiplatformAndroidLibraryTarget, reachable from a binary plugin with getByType.
                (this as ExtensionAware).extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>().apply {
                    namespace = Namespaces.of(path)
                    compileSdk { ocCompileSdk() }
                    minSdk = Sdk.MIN
                    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                    if (layer.hasPlatformSourceSets) withHostTest { isIncludeAndroidResources = true } // S15
                    if (file("consumer-rules.pro").isFile) {
                        optimization { consumerKeepRules.apply { publish = true; file("consumer-rules.pro") } }
                    }
                }
                jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
                if (ios) {
                    iosArm64()
                    iosSimulatorArm64()
                }

                compilerOptions {
                    optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.concurrent.atomics.ExperimentalAtomicApi")
                    allWarningsAsErrors.set(providers.gradleProperty("oc.werror").map(String::toBoolean).orElse(false))
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }

                sourceSets.commonMain.dependencies {
                    // `api`, not plan 03's `implementation`: StateFlow and ImmutableList/Map appear in every public
                    // engine and gateway signature, so consumers need them on their compile classpath.
                    api(libs.lib("kotlinx-coroutines-core"))
                    api(libs.lib("kotlinx-collections-immutable"))
                    implementation(libs.lib("kotlinx-serialization-json"))
                    implementation(libs.lib("metro-runtime"))
                }
                sourceSets.commonTest.dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.bundle("common-test"))
                    if (path != ":core:testing") implementation(project(":core:testing"))
                }
            }

            // Audit #10: Android host tests (and Robolectric) run on a JDK 21 launcher; everything else on the build JDK.
            val toolchains = extensions.getByType<JavaToolchainService>()
            tasks.withType<Test>().configureEach {
                maxParallelForks = providers.gradleProperty("oc.test.forks").get().toInt()
                if (name.contains("AndroidHostTest")) {
                    javaLauncher.set(
                        toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(Jdk.TEST_LAUNCHER)) },
                    )
                }
            }
            enforceModuleRules()
        }
}
