package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Paseo's supervisor lock (`paseo.pid` in the profile's Paseo home) outlives a
 * supervisor that Android killed: an app update or force stop, or proot ending
 * its tracees during a stop. Paseo reads the lock as a running daemon whenever
 * its PID names any live process again, even another app's (EPERM counts as
 * alive), and `paseo daemon run` then exits at once without starting. Every
 * Resume after that failed the same way.
 *
 * The app is the only launcher of a profile's Paseo home, so with no helper of
 * ours running and nothing answering on the helper's port the lock is stale.
 */
internal object PaseoStaleLock {
    const val FILE = "paseo.pid"
    private const val CONNECT_TIMEOUT_MS = 300

    /** Removes [paseoHome]'s lock when [helperRunning] is false and [portAnswers] says no. */
    fun clear(paseoHome: File, helperRunning: Boolean, portAnswers: () -> Boolean): Boolean {
        val lock = File(paseoHome, FILE).toPath()
        // A link is never followed: only a plain lock file is Paseo's.
        val stale = !helperRunning &&
            Files.isRegularFile(lock, LinkOption.NOFOLLOW_LINKS) &&
            !portAnswers()
        return stale && try { Files.deleteIfExists(lock) } catch (_: Exception) { false }
    }

    /** Whether anything accepts a loopback connection on [port] now. */
    fun loopbackAnswers(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS); true }
    } catch (_: Exception) { false }
}
