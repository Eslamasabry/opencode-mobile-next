package oc.buildlogic

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * AGENT-2 external-I/O terminal: `:android:terminal` compiles a licensed source fork of upstream
 * `TerminalSession` (android/terminal/UPSTREAM.md). Every Android module resolves the hash-pinned
 * upstream `terminal-emulator:v0.118.3` AAR through [TermuxSessionForkTransform], which drops only
 * the upstream `TerminalSession` classes, so the app has one definition and no derived binary is
 * stored anywhere. Other AARs pass through untouched.
 */
internal object TermuxSessionFork {
    val FORKED: Attribute<Boolean> = Attribute.of("oc.termuxSessionFork", Boolean::class.javaObjectType)
    const val EMULATOR_AAR = "terminal-emulator-v0.118.3.aar"
    private const val PIN = "maven/com/github/termux/termux-app/terminal-emulator/v0.118.3/$EMULATOR_AAR"

    fun Project.applyTermuxSessionFork() {
        val pin = rootDir.resolve("third_party/SHA256SUMS").readLines()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1] == PIN }?.get(0)
            ?: error("third_party/SHA256SUMS has no pin for $PIN")
        dependencies.attributesSchema.attribute(FORKED)
        dependencies.artifactTypes.maybeCreate("aar").attributes.attribute(FORKED, false)
        dependencies.registerTransform(TermuxSessionForkTransform::class.java) {
            from.attribute(FORKED, false).attribute(ARTIFACT_TYPE, "aar")
            to.attribute(FORKED, true).attribute(ARTIFACT_TYPE, "aar")
            parameters.emulatorSha256.set(pin)
        }
        configurations.configureEach {
            if (isCanBeResolved && !isCanBeConsumed) attributes.attribute(FORKED, true)
        }
    }

    private val ARTIFACT_TYPE: Attribute<String> = Attribute.of("artifactType", String::class.java)
}

abstract class TermuxSessionForkTransform : TransformAction<TermuxSessionForkTransform.Parameters> {
    interface Parameters : TransformParameters {
        @get:Input val emulatorSha256: Property<String>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val aar = input.get().asFile
        if (aar.name != TermuxSessionFork.EMULATOR_AAR) {
            outputs.file(input)
            return
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(aar.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == parameters.emulatorSha256.get()) {
            "${aar.name} does not match its pin in third_party/SHA256SUMS (got $actual)"
        }
        val rows = ZipFile(aar).use { zip ->
            zip.entries().toList().map { entry ->
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                entry.name to if (entry.name == "classes.jar") stripSession(bytes) else bytes
            }
        }
        outputs.file("terminal-emulator-v0.118.3-oc1.aar").writeBytes(archive(rows))
    }

    private fun stripSession(jar: ByteArray): ByteArray {
        val rows = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(jar.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name == "com/termux/terminal/TerminalSession.class" || name.startsWith("com/termux/terminal/TerminalSession$")) continue
                rows += name to zip.readBytes()
            }
        }
        return archive(rows)
    }

    /** Sorted, stored, fixed-time entries: the derived AAR is byte-identical on every machine. */
    private fun archive(rows: List<Pair<String, ByteArray>>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            for ((name, data) in rows.sortedBy { it.first }) {
                val crc = CRC32().apply { update(data) }
                zip.putNextEntry(ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    this.crc = crc.value
                    timeLocal = FIXED_TIME
                })
                zip.write(data)
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private companion object {
        val FIXED_TIME: java.time.LocalDateTime = java.time.LocalDateTime.of(1980, 1, 1, 0, 0)
    }
}
