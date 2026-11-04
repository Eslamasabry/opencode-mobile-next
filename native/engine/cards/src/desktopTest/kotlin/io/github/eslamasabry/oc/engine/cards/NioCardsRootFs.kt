package io.github.eslamasabry.oc.engine.cards

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/** The java.nio [CardsRootFs] (the Android binding is the same code over `filesDir`). */
class NioCardsRootFs(private val support: File) : CardsRootFs {
    override suspend fun supportRealPath(): String = support.toPath().toRealPath().toString()

    override suspend fun entry(path: String): RootEntry {
        val p = Path.of(path)
        return when {
            Files.isSymbolicLink(p) -> RootEntry.Link
            !Files.exists(p, LinkOption.NOFOLLOW_LINKS) -> RootEntry.Missing
            Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) -> RootEntry.Directory
            Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) -> RootEntry.File
            else -> RootEntry.Other
        }
    }

    override suspend fun mode(path: String): Int? =
        try {
            Files.getPosixFilePermissions(Path.of(path), LinkOption.NOFOLLOW_LINKS).sumOf { bit(it) }
        } catch (e: Exception) {
            null
        }

    override suspend fun publishPrivate(directory: String, name: String, bytes: ByteArray): Boolean {
        var temporary: Path? = null
        return try {
            temporary = Files.createTempDirectory(Path.of(directory), ".search-")
            val file = temporary.resolve("descriptor")
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            Files.write(file, bytes)
            Files.move(file, Path.of(directory, name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            false
        } finally {
            temporary?.toFile()?.deleteRecursively()
        }
    }

    private fun bit(p: PosixFilePermission): Int =
        when (p) {
            PosixFilePermission.OWNER_READ -> 0x100
            PosixFilePermission.OWNER_WRITE -> 0x80
            PosixFilePermission.OWNER_EXECUTE -> 0x40
            PosixFilePermission.GROUP_READ -> 0x20
            PosixFilePermission.GROUP_WRITE -> 0x10
            PosixFilePermission.GROUP_EXECUTE -> 0x8
            PosixFilePermission.OTHERS_READ -> 0x4
            PosixFilePermission.OTHERS_WRITE -> 0x2
            PosixFilePermission.OTHERS_EXECUTE -> 0x1
        }
}
