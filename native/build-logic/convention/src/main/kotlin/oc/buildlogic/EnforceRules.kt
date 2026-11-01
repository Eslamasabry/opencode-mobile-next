package oc.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

/** Wires ModuleRules and ExternalRules into dependency declaration: a wrong edge fails configuration. */
internal fun Project.enforceModuleRules() {
    val from = path
    configurations.configureEach {
        val inTest = name.contains("test", ignoreCase = true)
        val configName = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            ModuleRules.violation(from, path, inTest)?.let { throw GradleException("$it [configuration $configName]") }
        }
        dependencies.withType(ExternalModuleDependency::class.java).configureEach {
            ExternalRules.violation(from, group, name, inTest)?.let {
                throw GradleException("$it [configuration $configName]")
            }
        }
    }
}
