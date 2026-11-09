package io.github.eslamasabry.oc.ui.kit.image

import java.io.File
import java.security.MessageDigest

/**
 * Thumbnails on disk under one profile's own folder, `<cache>/oc-images/oc.imageCache.<profileId>/` (the
 * `oc.<what>.<profileId>` naming the deletion sweep finds). Bounded: the oldest files go when the folder passes
 * [maxBytes]. Files live in the cache directory, so Android may also clear them; that only costs a re-read.
 */
public class KitFileImageDisk(private val dir: File, private val maxBytes: Long = DEFAULT_MAX_BYTES) : KitImageDisk {
    override suspend fun get(key: String): ByteArray? =
        try {
            File(dir, name(key)).takeIf { it.isFile }?.also { it.setLastModified(System.currentTimeMillis()) }?.readBytes()
        } catch (_: java.io.IOException) {
            null
        }

    override suspend fun put(key: String, bytes: ByteArray) {
        try {
            dir.mkdirs()
            val tmp = File(dir, name(key) + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(File(dir, name(key)))) tmp.delete()
            prune()
        } catch (_: java.io.IOException) {
            // a full or refused disk only costs a re-read
        }
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
    }

    public companion object {
        public const val DEFAULT_MAX_BYTES: Long = 64L shl 20
        private const val ROOT: String = "oc-images"

        /** The folder name for one profile: `oc.imageCache.<profileId>`. */
        public fun scopeName(profileId: String): String = "oc.imageCache.$profileId"

        public fun folder(cacheDir: File, profileId: String): File = File(File(cacheDir, ROOT), scopeName(profileId))

        /** Removes every thumbnail of [profileId]; true when nothing is left. */
        public fun clear(cacheDir: File, profileId: String): Boolean = folder(cacheDir, profileId).let { !it.exists() || it.deleteRecursively() }

        /** A factory for [KitImages.diskFor] over the app cache directory. */
        public fun factory(cacheDir: File): (String) -> KitImageDisk = { KitFileImageDisk(folder(cacheDir, it)) }

        private fun name(key: String): String =
            MessageDigest.getInstance("SHA-256").digest(key.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    }
}
