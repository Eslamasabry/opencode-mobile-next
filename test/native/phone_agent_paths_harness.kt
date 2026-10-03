package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files

fun main(arguments: Array<String>) {
    val scenario = arguments.single()
    val temporary = Files.createTempDirectory("oc-agent-paths-").toFile()
    try {
        val actual = File(temporary, "actual-files").apply { mkdirs() }
        val alias = File(temporary, "android-files-alias")
        Files.createSymbolicLink(alias.toPath(), actual.toPath())
        val home = File(alias, "linux").apply { mkdirs() }
        when (scenario) {
            "anchor-alias" -> {
                // The anchor itself is Android-owned/trusted, while managed
                // descendants below it must retain their own link checks.
                val agent = productionAgentRoot(home)
                check(agent.isDirectory)
                check(agent.canonicalFile == File(actual, "linux/agent-root-view"))
                check(File(agent, "projects").isDirectory)
            }
            "managed-symlink" -> {
                val target = File(temporary, "outside").apply { mkdirs() }
                val managed = File(home, "agent-root-view")
                Files.createSymbolicLink(managed.toPath(), target.toPath())
                check(runCatching { productionAgentRoot(home) }.isFailure)
                check(!File(target, "projects").exists())
                check(Files.isSymbolicLink(managed.toPath()))
            }
            "managed-ancestor-symlink" -> {
                check(home.delete())
                val target = File(temporary, "outside-linux").apply { mkdirs() }
                Files.createSymbolicLink(home.toPath(), target.toPath())
                check(runCatching { productionAgentRoot(home) }.isFailure)
                check(!File(target, "agent-root-view").exists())
            }
            "projects-symlink" -> {
                val agent = File(home, "agent-root-view").apply { mkdirs() }
                val target = File(temporary, "outside-projects").apply { mkdirs() }
                Files.createSymbolicLink(File(agent, "projects").toPath(), target.toPath())
                check(runCatching { productionAgentRoot(home) }.isFailure)
                check(target.listFiles()?.isEmpty() == true)
            }
            "repeat" -> {
                val agent = productionAgentRoot(home)
                val marker = File(agent, "keep").apply { writeText("fixture") }
                check(productionAgentRoot(home).canonicalFile == agent.canonicalFile)
                check(marker.readText() == "fixture")
            }
            else -> error("Unknown scenario")
        }
        println("PASS $scenario")
    } finally {
        // Never traverse the synthetic anchor/managed symlinks on cleanup.
        Files.walk(temporary.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
