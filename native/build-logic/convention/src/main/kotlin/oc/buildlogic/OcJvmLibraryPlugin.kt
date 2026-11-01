package oc.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** `oc.jvm.library`: plain kotlin("jvm") (ADR §7.4). */
class OcJvmLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(Jdk.BUILD) }
            dependencies { add("testImplementation", kotlin("test")) }
            tasks.withType<Test>().configureEach { useJUnitPlatform() }
            enforceModuleRules()
        }
}

private fun org.gradle.kotlin.dsl.DependencyHandlerScope.kotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module"
