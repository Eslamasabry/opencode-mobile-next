package oc.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import oc.buildlogic.LintPolicy.ocLint
import oc.buildlogic.Sdk.ocCompileSdk
import oc.buildlogic.TermuxSessionFork.applyTermuxSessionFork
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * `oc.android.application` (plan 03 §21.1), slice-0 form. Only the `preview` flavour exists: the
 * side-by-side `io.github.eslamasabry.opencode_mobile.native` package (ADR §10.1). The `stable`
 * flavour (the real package, S16) is added by P5/the switch, not before.
 */
class OcAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("dev.zacsweers.metro")

            val versionProps =
                Properties().apply {
                    rootDir.resolve("version.properties").inputStream().use { load(it) }
                }

            extensions.configure<ApplicationExtension> {
                namespace = Namespaces.APP
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                compileSdk { ocCompileSdk() }
                defaultConfig {
                    applicationId = Namespaces.APP
                    minSdk = Sdk.MIN
                    targetSdk = Sdk.TARGET
                    versionCode =
                        versionProps
                            .getProperty("versionCode")
                            .toInt() // ≥ 10000, never goes back (D3)
                    versionName = versionProps.getProperty("versionName")
                }
                flavorDimensions += "channel"
                productFlavors {
                    create("preview") {
                        dimension = "channel"
                        applicationIdSuffix =
                            ".native" // side-by-side preview, own data, no migration
                    }
                }
                buildFeatures {
                    buildConfig = true
                    compose = true
                }
                lint { ocLint(file("lint.xml")) } // plan 16 §2.2 table (LintPolicy, S1-SECGATES)
            }
            // AGP's built-in Kotlin defaults to 11; inline APIs from the KMP modules target 17.
            tasks.withType<KotlinJvmCompile>().configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
            applyTermuxSessionFork()
            enforceModuleRules()
        }
}
