package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxFolder
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxLocalStorage
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxRemovalSize
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** App-private filesystem access, independent of server health and the runtime's lifetime. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidLinuxLocalStorage(
    private val app: Application,
    private val dispatchers: AppDispatchers,
) : LinuxLocalStorage {
    private val linux
        get() = BuiltinLinux.get(app)

    private suspend fun <T> reading(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) {
            try {
                Outcome.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
            }
        }

    /**
     * Check every segment, including the root, before accessing a directory; never follow guest
     * symlinks.
     */
    private fun directory(root: File, relative: String): File? {
        var current = root
        for (part in listOf("") + relative.split('/').filter { it.isNotEmpty() }) {
            if (part.isNotEmpty()) current = File(current, part)
            check(!Files.isSymbolicLink(current.toPath()))
            if (!Files.exists(current.toPath(), NOFOLLOW_LINKS)) return null
            check(Files.isDirectory(current.toPath(), NOFOLLOW_LINKS))
        }
        return current
    }

    override suspend fun folders(path: String): Outcome<List<LinuxFolder>> = reading {
        val guest = requireNotNull(LinuxLocalStorage.normalize(path))
        val retained = directory(File(app.filesDir, "projects"), "")
        val rootfs =
            BuiltinDiscovery.rootfs(app.filesDir).takeIf {
                BuiltinDiscovery.readyMarker(app.filesDir).isFile
            }
        val inProjects =
            guest == LinuxLocalStorage.PROJECTS ||
                guest.startsWith("${LinuxLocalStorage.PROJECTS}/")
        val virtual = retained != null && guest in setOf("/", "/root")
        check(rootfs != null || (retained != null && inProjects) || virtual)
        val host =
            if (retained != null && inProjects)
                directory(retained, guest.removePrefix(LinuxLocalStorage.PROJECTS))
            else rootfs?.let { directory(it, guest) }
        if (host == null && !virtual) {
            check(guest == LinuxLocalStorage.PROJECTS)
            emptyList()
        } else {
            val entries =
                host
                    ?.let { folder ->
                        requireNotNull(folder.listFiles()).mapNotNull { file ->
                            val name = file.name
                            if (
                                name.startsWith('.') ||
                                    name.any { it.code < 0x20 || it.code == 0x7f } ||
                                    !Files.isDirectory(file.toPath(), NOFOLLOW_LINKS) ||
                                    Files.isSymbolicLink(file.toPath())
                            )
                                return@mapNotNull null
                            if (guest == "/" && name in setOf("dev", "proc", "sys"))
                                return@mapNotNull null
                            if (retained != null && guest == "/root" && name == "projects")
                                return@mapNotNull null
                            LinuxFolder(
                                name,
                                if (guest == "/") "/$name" else "$guest/$name",
                                Files.exists(File(file, ".git").toPath(), NOFOLLOW_LINKS),
                            )
                        }
                    }
                    .orEmpty()
                    .toMutableList()
            if (retained != null && guest == "/root")
                entries +=
                    LinuxFolder(
                        "projects",
                        LinuxLocalStorage.PROJECTS,
                        Files.exists(File(retained, ".git").toPath(), NOFOLLOW_LINKS),
                    )
            if (virtual && guest == "/" && entries.none { it.name == "root" })
                entries += LinuxFolder("root", "/root", false)
            entries.sortedWith(compareBy<LinuxFolder> { it.name.lowercase() }.thenBy { it.name })
        }
    }

    override suspend fun createProject(path: String): Outcome<Unit> = reading {
        val guest = requireNotNull(LinuxLocalStorage.normalize(path))
        require(guest != "/" && !guest.contains('\n') && !guest.contains('\r'))
        val retained = directory(File(app.filesDir, "projects"), "")
        val inProjects =
            guest == LinuxLocalStorage.PROJECTS ||
                guest.startsWith("${LinuxLocalStorage.PROJECTS}/")
        val root =
            if (retained != null && inProjects) retained else BuiltinDiscovery.rootfs(app.filesDir)
        val relative =
            if (retained != null && inProjects) guest.removePrefix(LinuxLocalStorage.PROJECTS)
            else guest
        // Visit every existing parent before handing a path to the guest; a guest link must not
        // redirect creation.
        directory(root, relative)
        var ancestor = relative.substringBeforeLast('/', "")
        while (ancestor.isNotEmpty()) {
            directory(root, ancestor)
            ancestor = ancestor.substringBeforeLast('/', "")
        }
        val encoded = Base64.encode(guest.encodeToByteArray())
        val script =
            "set -eu\ndir=\$(printf '%s' '$encoded' | base64 -d)\n" +
                "if [ -d \"\$dir\" ]; then exit 0; fi\n" +
                "mkdir -p \"\$dir\"\ngit init -q \"\$dir\"\n" +
                "name=\$(git config user.name || echo OpenCode)\n" +
                "email=\$(git config user.email || echo opencode@localhost)\n" +
                "(cd \"\$dir\" && git -c user.name=\"\$name\" -c user.email=\"\$email\" commit -q --allow-empty -m 'First commit') || true\n"
        check(linux.run(script, 60).exitCode == 0)
    }

    override suspend fun measure(): Outcome<LinuxRemovalSize> = reading {
        val values = linux.projectStorage()
        LinuxRemovalSize(
            requireNotNull(values["runtimeBytes"]),
            requireNotNull(values["projectsBytes"]),
            requireNotNull(values["measuredAtMilliseconds"]),
        )
    }

    override suspend fun remove(deleteProjects: Boolean, confirmationName: String?): Outcome<Unit> =
        reading {
            require(!deleteProjects || confirmationName == LinuxLocalStorage.DELETION_NAME)
            linux.uninstall(
                alsoDeleteProjects = deleteProjects,
                confirmationName = confirmationName.takeIf { deleteProjects },
            )
        }
}
