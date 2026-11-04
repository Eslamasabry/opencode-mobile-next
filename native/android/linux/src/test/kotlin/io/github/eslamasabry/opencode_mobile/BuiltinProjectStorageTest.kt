package io.github.eslamasabry.opencode_mobile

import org.junit.Test

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes

// Port of test/native/builtin_project_storage_harness.kt (test/builtin_project_storage_native_test.dart:68):
// the 14 native project-storage scenarios as JVM unit tests over the :android:linux copy (B-LINUX-030).

/** Executes the production helper on a host filesystem, without Android APIs. */
private class Fixture : AutoCloseable {
    val directory = Files.createTempDirectory("builtin-project-storage-").toFile()
    val files = File(directory, "files").apply { mkdir() }
    val cache = File(directory, "cache").apply { mkdir() }
    val archive = File(cache, "ubuntu-base.tar.gz")
    val legacy = File(files, "linux/ubuntu/root/projects")
    val projects = File(files, "projects")
    val storage = storage()

    fun storage(inspect: (File) -> BuiltinProjectStorage.Entry? = ::inspect) =
        BuiltinProjectStorage(files, archive, inspect)

    fun write(path: File, value: String) {
        check(path.parentFile.mkdirs() || path.parentFile.isDirectory)
        path.writeText(value)
    }

    override fun close() {
        Files.walk(directory.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}

private fun inspect(file: File): BuiltinProjectStorage.Entry? {
    val attributes = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        return null
    }
    val kind = when {
        attributes.isSymbolicLink -> BuiltinProjectStorage.Kind.LINK
        attributes.isDirectory -> BuiltinProjectStorage.Kind.DIRECTORY
        attributes.isRegularFile -> BuiltinProjectStorage.Kind.FILE
        else -> BuiltinProjectStorage.Kind.OTHER
    }
    return BuiltinProjectStorage.Entry(kind, attributes.size())
}

private fun failsSafely(action: () -> Unit) {
    val failure = try {
        action()
        null
    } catch (error: IllegalStateException) {
        error
    }
    check(failure != null)
    check(failure.message == "Project storage could not be safely accessed.")
    check(failure.cause == null)
}

class BuiltinProjectStorageTest {
    private fun scenario(block: (Fixture) -> Unit) = Fixture().use(block)

    @Test fun `B-LINUX-030 a fresh install prepares the projects folder and the mountpoint (fresh)`() = scenario { f ->
        f.storage.prepare()
        check(f.projects.isDirectory)
        check(!f.storage.rootfs.exists())
        check(f.storage.measure() == BuiltinProjectStorage.Measurement(0, 0))
        check(f.storage.rootfs.mkdirs())
        f.storage.prepare()
        check(f.legacy.isDirectory)
    }

    @Test fun `B-LINUX-030 the legacy projects move out with hidden files and git data (migration)`() = scenario { f ->
        f.write(File(f.legacy, "nested/source.dart"), "original source\n")
        f.write(File(f.legacy, ".env"), "synthetic-project-value\n")
        f.write(File(f.legacy, ".git/HEAD"), "ref: refs/heads/main\n")
        f.storage.prepare()
        check(File(f.projects, "nested/source.dart").readText() == "original source\n")
        check(File(f.projects, ".env").readText() == "synthetic-project-value\n")
        check(File(f.projects, ".git/HEAD").readText() == "ref: refs/heads/main\n")
        check(f.legacy.listFiles()!!.isEmpty())
        f.storage.prepare()
        check(File(f.projects, ".git/HEAD").isFile)
    }

    @Test fun `B-LINUX-030 an empty destination still receives the legacy projects (empty-destination)`() = scenario { f ->
        f.write(File(f.legacy, "source"), "old project")
        check(f.projects.mkdir())
        f.storage.prepare()
        check(File(f.projects, "source").readText() == "old project")
        check(f.legacy.listFiles()!!.isEmpty())
    }

    @Test fun `B-LINUX-030 a retry after the rename finishes the mountpoint (retry-after-rename)`() = scenario { f ->
        f.write(File(f.legacy, "source"), "old project")
        check(f.legacy.renameTo(f.projects))
        // Models a process death after atomic rename, before mountpoint mkdir.
        f.storage.prepare()
        check(f.legacy.isDirectory)
        check(File(f.projects, "source").readText() == "old project")
    }

    @Test fun `B-LINUX-030 removal keeps projects through a reinstall until delete everything (retain-reinstall-delete)`() = scenario { f ->
        f.write(File(f.legacy, "source"), "keep me")
        f.write(File(f.files, "linux/tool"), "runtime")
        f.write(f.archive, "archive")
        f.storage.removeRuntime()
        check(!File(f.files, "linux").exists())
        check(!f.archive.exists())
        check(File(f.projects, "source").readText() == "keep me")
        f.storage.prepare()
        check(f.storage.rootfs.mkdirs())
        f.storage.prepare()
        check(f.legacy.isDirectory)
        check(File(f.projects, "source").readText() == "keep me")
        f.storage.removeRuntime(alsoDeleteProjects = true)
        check(!f.projects.exists())
        check(!File(f.files, "linux").exists())
    }

    @Test fun `B-LINUX-030 a reset replaces only the system, keeping setup state, download and projects (reset-rootfs)`() = scenario { f ->
        f.write(File(f.legacy, "source"), "keep me")
        f.write(File(f.files, "linux/setup.json"), "setup state")
        f.write(f.archive, "download")
        f.storage.resetRootfs()
        check(!f.storage.rootfs.exists())
        check(File(f.files, "linux/setup.json").readText() == "setup state")
        check(f.archive.readText() == "download")
        check(File(f.projects, "source").readText() == "keep me")
    }

    @Test fun `B-LINUX-030 two project folders fail safely and nothing is touched (collision)`() = scenario { f ->
        f.write(File(f.legacy, "old"), "old")
        f.write(File(f.projects, "new"), "new")
        f.write(f.archive, "download")
        failsSafely { f.storage.measure() }
        failsSafely { f.storage.prepare() }
        failsSafely { f.storage.resetRootfs() }
        failsSafely { f.storage.removeRuntime(alsoDeleteProjects = true) }
        check(File(f.legacy, "old").readText() == "old")
        check(File(f.projects, "new").readText() == "new")
        check(f.archive.exists())
    }

    @Test fun `B-LINUX-030 linked ancestors fail safely and nothing outside is touched (symlink-ancestors)`() = scenario { f ->
        val outside = File(f.directory, "outside").apply { mkdir() }
        f.write(File(outside, "untouched"), "sentinel")
        for (relative in listOf("projects", "linux", "linux/ubuntu", "linux/ubuntu/root", "linux/ubuntu/root/projects")) {
            val link = File(f.files, relative)
            check(link.parentFile.mkdirs() || link.parentFile.isDirectory)
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            failsSafely { f.storage.prepare() }
            failsSafely { f.storage.measure() }
            failsSafely { f.storage.removeRuntime(true) }
            check(File(outside, "untouched").readText() == "sentinel")
            Files.delete(link.toPath())
        }
    }

    @Test fun `B-LINUX-030 links inside are moved as links, never followed or counted (symlink-contents)`() = scenario { f ->
        val outside = File(f.directory, "outside")
        f.write(outside, "outside")
        check(f.legacy.mkdirs())
        Files.createSymbolicLink(File(f.legacy, "linked").toPath(), outside.toPath())
        Files.createSymbolicLink(File(f.legacy, "dangling").toPath(), File(f.directory, "missing").toPath())
        Files.createSymbolicLink(File(f.files, "linux/linked").toPath(), outside.toPath())
        Files.createSymbolicLink(f.archive.toPath(), outside.toPath())
        f.storage.prepare()
        check(Files.isSymbolicLink(File(f.projects, "linked").toPath()))
        check(f.storage.measure() == BuiltinProjectStorage.Measurement(0, 0))
        f.storage.removeRuntime(true)
        check(outside.readText() == "outside")
        check(!Files.exists(f.archive.toPath(), NOFOLLOW_LINKS))
        check(!f.projects.exists())
    }

    @Test fun `B-LINUX-030 each removal choice is measured on its own (measurement)`() = scenario { f ->
        f.write(File(f.legacy, "source"), "12345")
        f.write(File(f.files, "linux/tool"), "1234567")
        f.write(File(f.files, "linux/ubuntu/etc/config"), "123")
        f.write(f.archive, "1234")
        val expected = BuiltinProjectStorage.Measurement(runtimeBytes = 14, projectsBytes = 5)
        check(f.storage.measure() == expected)
        check(!f.projects.exists())
        f.storage.prepare()
        check(f.storage.measure() == expected)
        f.write(File(f.projects, ".hidden"), "12")
        check(f.storage.measure() == expected.copy(projectsBytes = 7))
        f.storage.removeRuntime()
        check(f.storage.measure() == BuiltinProjectStorage.Measurement(0, 7))
    }

    @Test fun `B-LINUX-030 an inspection failure fails safely without deleting (inspection-failure)`() = scenario { f ->
        f.write(File(f.legacy, "source"), "keep")
        f.write(f.archive, "download")
        val failing = f.storage { file ->
            if (file == f.legacy) throw SecurityException("private synthetic path")
            inspect(file)
        }
        failsSafely { failing.prepare() }
        failsSafely { failing.measure() }
        failsSafely { failing.removeRuntime(true) }
        check(File(f.legacy, "source").readText() == "keep")
        check(f.archive.readText() == "download")
        // A traversal error after safe location validation remains unknown.
        val deepFailure = f.storage { file ->
            if (file.name == "source") throw IllegalStateException("private synthetic value")
            inspect(file)
        }
        failsSafely { deepFailure.measure() }
    }

    @Test fun `B-LINUX-030 a failed delete is never reported as removed (deletion-failure)`() = scenario { f ->
        f.write(File(f.legacy, "source"), "keep")
        f.write(f.archive, "download")
        val ready = File(f.files, "linux/ubuntu.ready")
        f.write(ready, "ready")
        val linux = File(f.files, "linux")
        var linuxInspections = 0
        val failing = f.storage { file ->
            val entry = inspect(file)
            if (file == linux && ++linuxInspections == 2) {
                // Force deleteTree to attempt File.delete on a nonempty dir.
                // A failed delete must not be reported as successful removal.
                BuiltinProjectStorage.Entry(BuiltinProjectStorage.Kind.FILE, 0)
            } else {
                entry
            }
        }
        failsSafely { failing.removeRuntime(true) }
        check(!ready.exists())
        check(linux.exists())
        check(File(f.projects, "source").readText() == "keep")
        check(f.archive.readText() == "download")
    }

    @Test fun `B-LINUX-030 read-only folders are still removed (read-only-directories)`() = scenario { f ->
        f.write(File(f.legacy, "app/main.go"), "package main\n")
        val module = File(f.files, "linux/ubuntu/root/go/pkg/mod/example.com/m@v1")
        f.write(File(module, "go.mod"), "module example.com/m\n")
        f.write(File(f.projects.parentFile, "linux/ubuntu.ready"), "ready")
        // Go leaves each cached module directory read-only; removal must finish.
        check(module.setWritable(false, false))
        f.storage.removeRuntime()
        check(!File(f.files, "linux").exists())
        check(File(f.projects, "app/main.go").readText() == "package main\n")
        val readOnlyProject = File(f.projects, "vendor/cache")
        f.write(File(readOnlyProject, "blob"), "x")
        check(readOnlyProject.setWritable(false, false))
        f.storage.removeRuntime(alsoDeleteProjects = true)
        check(!f.projects.exists())
    }

    @Test fun `B-LINUX-030 negative or overflowing sizes fail the measurement (invalid-size)`() = scenario { f ->
        f.write(File(f.legacy, "a"), "a")
        f.write(File(f.legacy, "b"), "b")
        val negative = f.storage { file ->
            inspect(file)?.let { if (it.kind == BuiltinProjectStorage.Kind.FILE) it.copy(bytes = -1) else it }
        }
        failsSafely { negative.measure() }
        val overflowing = f.storage { file ->
            inspect(file)?.let { if (it.kind == BuiltinProjectStorage.Kind.FILE) it.copy(bytes = Long.MAX_VALUE) else it }
        }
        failsSafely { overflowing.measure() }
    }
}
