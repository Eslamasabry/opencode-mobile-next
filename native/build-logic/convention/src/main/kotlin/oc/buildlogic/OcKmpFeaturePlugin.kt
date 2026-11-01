package oc.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** `oc.kmp.feature`: oc.kmp.compose + the standard feature dependencies. Plan 03 §6.5. */
class OcKmpFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("oc.kmp.compose")
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    listOf(":ui:kit", ":ui:design", ":ui:words", ":ui:routes", ":core:domain", ":core:model", ":core:common")
                        .forEach { implementation(project(it)) }
                    implementation(libs.bundle("feature-runtime"))
                }
                // :ui:testing joins commonTest when it exists (slice 1, P7).
            }
        }
}
