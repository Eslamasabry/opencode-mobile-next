package oc.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** `oc.desktop.application`: kotlin("jvm") + Compose compiler + CMP desktop (plan 03 §21.2). */
class OcDesktopApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("dev.zacsweers.metro")
            extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(Jdk.BUILD) }
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    isolated.rootProject.projectDirectory.file("config/compose-stability.conf"),
                )
            }
            enforceModuleRules()
        }
}
