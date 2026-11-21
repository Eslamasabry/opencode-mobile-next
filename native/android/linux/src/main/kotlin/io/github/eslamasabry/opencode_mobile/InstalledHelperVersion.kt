package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import org.json.JSONObject

/** Read only the app-installed helper package; other layouts keep the bounded executable probe. */
internal object InstalledHelperVersion {
    private const val MAX_PACKAGE_BYTES = 65_536
    private val installedLink = Regex(
        "^(/home/oc/\\.local/share/oc-paseo/[0-9A-Za-z][0-9A-Za-z._+-]{0,79})/node_modules/\\.bin/paseo$"
    )
    private val version = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")

    fun read(rootfs: File, fallback: () -> String?): String? = fromPackage(rootfs) ?: fallback()

    /**
     * The folder name of the app-installed helper (`<version>-<lock hash>`), or null for any other layout. It names
     * the pin, so what is kept per pin (the esbuild cache) starts over when the pin moves.
     */
    fun installName(rootfs: File): String? = try {
        val bin = PhoneAgentPaths.resolve(rootfs, "home/oc/.local/bin")
        val target = Files.readSymbolicLink(File(bin, "paseo").toPath()).toString()
        installedLink.matchEntire(target)?.groupValues?.get(1)?.substringAfterLast('/')
    } catch (_: Exception) {
        null
    }

    private fun fromPackage(rootfs: File): String? = try {
        val bin = PhoneAgentPaths.resolve(rootfs, "home/oc/.local/bin")
        val target = Files.readSymbolicLink(File(bin, "paseo").toPath()).toString()
        val install = installedLink.matchEntire(target)?.groupValues?.get(1)
        if (install == null) null
        else {
            // Reject links in managed descendants. The package is part of the install's pinned code tree.
            val modules = PhoneAgentPaths.resolve(rootfs, install.removePrefix("/") + "/node_modules")
            val pkg = File(modules, "@getpaseo/cli/package.json")
            check(pkg.canonicalFile == pkg.absoluteFile)
            if (!pkg.isFile || pkg.length() !in 1..MAX_PACKAGE_BYTES.toLong()) null
            else {
                val bytes = pkg.inputStream().use { input ->
                    val buffer = ByteArray(MAX_PACKAGE_BYTES + 1)
                    var size = 0
                    while (size < buffer.size) {
                        val count = input.read(buffer, size, buffer.size - size)
                        if (count < 0) break
                        size += count
                    }
                    buffer.copyOf(size)
                }
                if (bytes.size > MAX_PACKAGE_BYTES) null
                else {
                    val json = JSONObject(bytes.toString(Charsets.UTF_8))
                    val found = json.opt("version") as? String
                    found?.takeIf { json.opt("name") == "@getpaseo/cli" && version.matches(it) }
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}
