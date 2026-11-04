package io.github.eslamasabry.opencode_mobile.graph

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.git.GitCredentialDrop
import io.github.eslamasabry.oc.engine.git.GitSecretFile
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.SecureRandom
import kotlinx.coroutines.withContext

/**
 * A new 0700 directory and 0600 file for each command. Empty file first, verified descriptor
 * permissions second, token last. Every ancestor is an app-owned directory, never a link; deletion
 * never follows a substituted link.
 */
internal class BuiltinGitCredentialDrop(
    private val linux: BuiltinLinux,
    private val dispatchers: AppDispatchers,
    private val stillOwned: () -> Boolean,
) : GitCredentialDrop {
    private val random = SecureRandom()
    private val receipts = HashMap<GitSecretFile, File>()

    override suspend fun place(secret: String): Outcome<GitSecretFile> =
        withContext(dispatchers.io) {
            var command: File? = null
            try {
                check(
                    secret.isNotEmpty() &&
                        secret.length <= 65536 &&
                        '\n' !in secret &&
                        '\r' !in secret &&
                        '\u0000' !in secret
                )
                val tokenBytes = secret.toByteArray(Charsets.UTF_8)
                check(tokenBytes.size <= 65536)
                check(stillOwned() && linux.installed)
                val root = privateRoot()
                val nonce =
                    ByteArray(16).also(random::nextBytes).joinToString("") {
                        "%02x".format(it.toInt() and 255)
                    }
                command = File(root, "c-$nonce")
                Os.mkdir(command.absolutePath, 448)
                requireDirectory(command, 448)
                val target = File(command, "token")
                val fd =
                    Os.open(
                        target.absolutePath,
                        OsConstants.O_WRONLY or
                            OsConstants.O_CREAT or
                            OsConstants.O_EXCL or
                            OsConstants.O_NOFOLLOW,
                        384,
                    )
                FileOutputStream(fd).use { output ->
                    Os.fchmod(fd, 384)
                    val stat = Os.fstat(fd)
                    check(
                        OsConstants.S_ISREG(stat.st_mode) &&
                            stat.st_uid == Os.getuid() &&
                            stat.st_mode and 511 == 384 &&
                            stat.st_nlink == 1L
                    )
                    check(stillOwned())
                    output.write(tokenBytes)
                    output.fd.sync()
                }
                val receipt = GitSecretFile("/root/.oc-git/${command.name}/token")
                synchronized(receipts) { receipts[receipt] = target }
                Outcome.Ok(receipt)
            } catch (_: Exception) {
                command?.let { erase(it) }
                unavailable()
            }
        }

    override suspend fun remove(file: GitSecretFile): Outcome<Unit> =
        withContext(dispatchers.io) {
            // Object identity binds cleanup to a file this instance actually issued. A forged path
            // never reaches IO.
            val target =
                synchronized(receipts) { receipts[file] } ?: return@withContext Outcome.Ok(Unit)
            if (!erase(target.parentFile!!)) return@withContext unavailable()
            synchronized(receipts) { receipts.remove(file) }
            Outcome.Ok(Unit)
        }

    private fun privateRoot(): File {
        val rootfs = linux.rootfs.absoluteFile
        // The app-private files directory is the trusted boundary; Android may alias /data/user/0
        // itself.
        val runtimeHome = rootfs.parentFile!!
        requireDirectory(runtimeHome.parentFile!!)
        requireDirectory(runtimeHome)
        // The runtime tree and the root home are never accepted through symbolic links.
        requireDirectory(rootfs)
        val home = File(rootfs, "root")
        requireDirectory(home)
        val folder = File(home, ".oc-git")
        try {
            Os.mkdir(folder.absolutePath, 448)
        } catch (e: ErrnoException) {
            if (e.errno != OsConstants.EEXIST) throw e
        }
        requireDirectory(folder, 448)
        return folder
    }

    private fun requireDirectory(directory: File, mode: Int? = null) {
        val stat = Os.lstat(directory.absolutePath)
        check(OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == Os.getuid())
        if (mode != null) check(stat.st_mode and 511 == mode)
    }

    private fun erase(command: File): Boolean =
        try {
            // Revalidate the entire path before touching it. Never traverse a changed root or
            // command directory.
            val root = privateRoot()
            check(command.parentFile == root && Regex("c-[a-f0-9]{32}").matches(command.name))
            requireDirectory(command, 448)
            val target = File(command, "token")
            try {
                val stat = Os.lstat(target.absolutePath)
                if (OsConstants.S_ISREG(stat.st_mode)) {
                    val fd =
                        Os.open(
                            target.absolutePath,
                            OsConstants.O_WRONLY or
                                OsConstants.O_NOFOLLOW or
                                OsConstants.O_NONBLOCK,
                            0,
                        )
                    FileOutputStream(fd).use { output ->
                        val opened = Os.fstat(fd)
                        check(
                            OsConstants.S_ISREG(opened.st_mode) &&
                                opened.st_uid == Os.getuid() &&
                                opened.st_nlink == 1L &&
                                opened.st_size <= 65536
                        )
                        output.write(ByteArray(opened.st_size.toInt()))
                        output.fd.sync()
                    }
                }
                Files.deleteIfExists(target.toPath())
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.ENOENT) throw e
            }
            Files.deleteIfExists(command.toPath())
            true
        } catch (e: ErrnoException) {
            e.errno == OsConstants.ENOENT
        } catch (_: Exception) {
            false
        }

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId("git")))
}
