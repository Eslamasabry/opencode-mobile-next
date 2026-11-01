package io.github.eslamasabry.oc.migration.readers.migration

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Named points between individual filesystem operations. Production passes [FaultPoints.None]; tests (and the
 * `qa` build's `StepInterrupter`, plan/05 §10.5 J2) throw [SimulatedCrash] at one point to prove that every
 * operation is resumable (audit 04+05 finding 5: "inject termination between individual filesystem operations").
 */
public fun interface FaultPoints {
    public fun reached(point: String)

    public companion object {
        public val None: FaultPoints = FaultPoints { }
    }
}

/** Thrown by a test fault point. Nothing catches it inside this library. */
public class SimulatedCrash(public val point: String) : RuntimeException("simulated crash at $point")

/** Records every point it sees; crashes at [crashAt] (the n-th time it is reached, 1-based). */
public class RecordingFaults(private val crashAt: String? = null, private val occurrence: Int = 1) : FaultPoints {
    public val seen: MutableList<String> = mutableListOf()
    override fun reached(point: String) {
        seen += point
        if (point == crashAt && seen.count { it == point } == occurrence) throw SimulatedCrash(point)
    }
}

public object Fs {
    public fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    public fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().toHex()
    }

    public fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** fsync a directory so a rename inside it is durable (Linux/Android; a no-op where unsupported). */
    public fun fsyncDir(dir: File) {
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (e: Exception) {
            // Some filesystems refuse to open directories; the rename is still atomic there.
        }
    }

    /** tmp file + fsync + atomic rename + parent fsync. */
    public fun writeAtomically(target: File, bytes: ByteArray, faults: FaultPoints = FaultPoints.None, label: String = target.name) {
        target.parentFile.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        faults.reached("$label:tmpWritten")
        move(tmp, target)
        faults.reached("$label:renamed")
        fsyncDir(target.parentFile)
    }

    /** Copy with hashing and fsync. Returns the SHA-256 of what was written. */
    public fun copyDurably(from: File, to: File): String {
        to.parentFile.mkdirs()
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(from).use { input ->
            FileOutputStream(to).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
                out.flush()
                out.fd.sync()
            }
        }
        return md.digest().toHex()
    }

    /** Atomic rename (replacing [to] if it is a file). */
    public fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    public fun deleteRecursively(dir: File) {
        if (dir.exists()) dir.walkBottomUp().forEach { it.delete() }
    }
}
