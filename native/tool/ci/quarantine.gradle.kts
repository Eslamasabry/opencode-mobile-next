// CI quarantine: excludes the tests listed in tool/ci/quarantine.txt from every Test task, so a red CI run
// means a regression in a test that is expected to pass. Applied from the root build.gradle.kts.
//
//   * active when CI=true (GitHub Actions sets it) or -Poc.quarantine=true
//   * off for local runs, and off whenever -PrunQuarantined is passed (the quarantined tests then run and may fail)
//   * no test file, module build file or workflow flag is involved; edit quarantine.txt only
//
// Line format of quarantine.txt:  <fully.qualified.Class>[#method] | <pixel|env|real> | <reason>
// See tool/ci/QUARANTINE.md.
val quarantineFile = rootProject.layout.projectDirectory.file("tool/ci/quarantine.txt")
val quarantineOn = providers.gradleProperty("runQuarantined").isPresent.not() &&
    (providers.environmentVariable("CI").orNull == "true" || providers.gradleProperty("oc.quarantine").orNull == "true")

if (quarantineOn && quarantineFile.asFile.exists()) {
    val patterns = providers.fileContents(quarantineFile).asText.get().lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split("|").map { it.trim() }
            require(parts.size >= 3 && parts[1] in setOf("pixel", "env", "real") && parts[2].isNotEmpty()) {
                "tool/ci/quarantine.txt: need '<Class[#method]> | <pixel|env|real> | <reason>', got: $line"
            }
            parts[0].replace('#', '.')
        }
        .toList()
    allprojects {
        tasks.withType<Test>().configureEach {
            patterns.forEach { filter.excludeTestsMatching(it) }
        }
    }
}
