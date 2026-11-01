package oc.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import oc.buildlogic.Sdk.ocCompileSdk
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.util.Properties

/**
 * `oc.android.application` (plan 03 §21.1), slice-0 form. Only the `preview` flavour exists: the side-by-side
 * `io.github.eslamasabry.opencode_mobile.native` package (ADR §10.1). The `stable` flavour (the real package, S16)
 * is added by P5/the switch, not before.
 */
class OcAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("dev.zacsweers.metro")

            val versionProps =
                Properties().apply { rootDir.resolve("version.properties").inputStream().use { load(it) } }

            extensions.configure<ApplicationExtension> {
                namespace = Namespaces.APP
                compileSdk { ocCompileSdk() }
                defaultConfig {
                    applicationId = Namespaces.APP
                    minSdk = Sdk.MIN
                    targetSdk = Sdk.TARGET
                    versionCode = versionProps.getProperty("versionCode").toInt() // ≥ 10000, never goes back (D3)
                    versionName = versionProps.getProperty("versionName")
                }
                flavorDimensions += "channel"
                productFlavors {
                    create("preview") {
                        dimension = "channel"
                        applicationIdSuffix = ".native" // side-by-side preview, own data, no migration
                    }
                }
                buildFeatures {
                    buildConfig = true
                    compose = true
                }
                lint { abortOnError = true }
            }
            enforceModuleRules()
        }
}
