package oc.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** `oc.kmp.compose`: oc.kmp.library + Compose compiler + CMP + resources + stability config. Plan 03 §6.5. */
class OcKmpComposePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("oc.kmp.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")

            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    isolated.rootProject.projectDirectory.file("config/compose-stability.conf"),
                )
                if (providers.gradleProperty("oc.composeReports").orNull == "true") {
                    reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
                    metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
                }
            }

            // S18: only these modules own composeResources and a public Res class.
            val ownsResources = path in setOf(":ui:words", ":ui:kit", ":ui:kit-chat", ":ui:kit-content")
            extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
                publicResClass = ownsResources
                packageOfResClass = "${Namespaces.of(path)}.res"
                generateResClass =
                    if (ownsResources) ResourcesExtension.ResourceClassGeneration.Always
                    else ResourcesExtension.ResourceClassGeneration.Never
            }

            extensions.configure<KotlinMultiplatformExtension> {
                if (ownsResources) {
                    (this as ExtensionAware).extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>()
                        .androidResources { enable = true }
                }
                sourceSets.commonMain.dependencies {
                    implementation(libs.bundle("compose-core"))
                    implementation(libs.lib("compose-resources"))
                }
                sourceSets.getByName("desktopMain").dependencies {
                    implementation(libs.lib("kotlinx-coroutines-swing"))
                }
                sourceSets.commonTest.dependencies { implementation(libs.lib("compose-ui-test")) }
            }
        }
}
