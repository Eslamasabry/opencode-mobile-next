// :tools:rules: the source-pattern guard tests of :quality (and of a few modules) as detekt rules, checked on the
// check path (task ocRules; CI's quality shard and the release script call it once; -Poc.rulesOnCompile=true hooks it into every compile), plus one
// plain check task for the repository-file and data checks (ocDataChecks, same hook). A JVM tool: it ships nothing
// and no module may depend on it (ModuleRules: tools depend on nothing). The old guard tests stay.
// Skip both for one run with -Poc.skipRules=true.
plugins {
    id("oc.jvm.library")
}

val detektVersion = "1.23.8"

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:$detektVersion")
}

// The detekt CLI (the same pinned version tool/qa/detekt.sh runs by hand) and the Kotlin stdlib the rules were built
// with, which goes first on the run's classpath: the CLI jar carries an older stdlib.
val detektCli: Configuration by configurations.creating { isCanBeConsumed = false }
val ruleStdlib: Configuration by configurations.creating { isCanBeConsumed = false }

dependencies {
    detektCli("io.gitlab.arturbosch.detekt:detekt-cli:$detektVersion:all")
    ruleStdlib("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
}

val sourceInputs = listOf("core", "protocol", "engine", "ui", "feature", "android", "androidApp", "desktopApp")
val skipRules = providers.gradleProperty("oc.skipRules").orNull == "true"

tasks.register<JavaExec>("ocSourceRules") {
    group = "verification"
    description = "Runs the OC source rules (the guard tests that scan source, as detekt rules) over the native tree."
    enabled = !skipRules
    dependsOn(tasks.jar)
    classpath = ruleStdlib + detektCli
    mainClass = "io.gitlab.arturbosch.detekt.cli.Main"
    maxHeapSize = "2g"
    val config = layout.projectDirectory.file("config/oc-rules.yml")
    val rulesJar = tasks.jar.flatMap { it.archiveFile }
    val root = rootProject.layout.projectDirectory
    // The rules read these files themselves (baselines and allow lists), so a change to one reruns the scan.
    inputs.files(sourceInputs.map { dir -> fileTree(root.dir(dir)) { include("**/*.kt"); exclude("**/build/**") } })
        .withPropertyName("sources").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(root.dir("quality/config")).withPropertyName("ruleConfig").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(config).withPropertyName("rulesConfig")
    inputs.file(rulesJar).withPropertyName("rulesJar").withPathSensitivity(PathSensitivity.NONE)
    val marker = layout.buildDirectory.file("ocSourceRules.ok")
    outputs.file(marker)
    val inputArg = sourceInputs.map { root.dir(it).asFile }.filter { it.isDirectory }.joinToString(",") { it.absolutePath }
    args(
        "--input", inputArg,
        "--config", config.asFile.absolutePath,
        "--plugins", rulesJar.get().asFile.absolutePath,
        "--disable-default-rulesets",
        "--excludes", "**/build/**",
        "--parallel",
    )
    val startFile = layout.buildDirectory.file("ocSourceRules.start")
    doFirst { startFile.get().asFile.apply { parentFile.mkdirs() }.writeText(System.currentTimeMillis().toString()) }
    doLast {
        val took = (System.currentTimeMillis() - startFile.get().asFile.readText().trim().toLong()) / 1000.0
        logger.lifecycle("ocSourceRules: scan took $took s")
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}

tasks.register("ocRules") {
    group = "verification"
    description = "The OC source rules and the repository-file checks, once. The task CI's quality shard and the release script call."
    dependsOn("ocSourceRules", "ocDataChecks")
}

tasks.register<JavaExec>("ocDataChecks") {
    group = "verification"
    description = "Plain checks of repository files and data (module owners, test sources, fixtures, agent pins)."
    enabled = !skipRules
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.eslamasabry.oc.tools.rules.data.DataChecksKt"
    val root = rootProject.layout.projectDirectory
    args(root.asFile.absolutePath)
    inputs.files(
        fileTree(root) {
            include("MODULE_OWNERS", "settings.gradle.kts", "fixtures/oc2/**", "engine/agents/pins/**")
            include("feature/*/build.gradle.kts", "ui/*/build.gradle.kts", "*/*/build.gradle.kts", "*/build.gradle.kts")
            include("engine/agents/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/agents/catalog/AgentPinsJson.kt")
            include("feature/**/*.kt", "ui/**/*.kt", "androidApp/**/*.kt") // the gutter and collectAsState counts
            exclude("**/build/**")
        },
    ).withPropertyName("checkedFiles").withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("ocDataChecks.ok")
    outputs.file(marker)
    doLast { marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n") }
}
