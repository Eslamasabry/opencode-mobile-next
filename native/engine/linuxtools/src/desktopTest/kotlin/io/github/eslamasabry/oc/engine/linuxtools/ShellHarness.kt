package io.github.eslamasabry.oc.engine.linuxtools

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Runs a command and collects its output (the Dart tests' Process.runSync). */
internal data class Ran(val exitCode: Int, val stdout: String, val stderr: String)

internal fun runProcess(
    command: List<String>,
    env: Map<String, String> = emptyMap(),
    cwd: File? = null,
    stdin: String? = null,
): Ran {
    val builder = ProcessBuilder(command)
    builder.environment().putAll(env)
    cwd?.let { builder.directory(it) }
    val process = builder.start()
    if (stdin != null) process.outputStream.use { it.write(stdin.toByteArray()) } else process.outputStream.close()
    val out = process.inputStream.bufferedReader()
    val err = process.errorStream.bufferedReader()
    var stdout = ""
    var stderr = ""
    val t1 = Thread { stdout = out.readText() }.apply { start() }
    val t2 = Thread { stderr = err.readText() }.apply { start() }
    check(process.waitFor(120, TimeUnit.SECONDS)) { "command timed out: $command" }
    t1.join()
    t2.join()
    return Ran(process.exitValue(), stdout, stderr)
}

internal fun chmodX(file: File) {
    check(file.setExecutable(true, false))
}

internal fun writeExecutable(file: File, text: String) {
    file.parentFile.mkdirs()
    file.writeText(text)
    chmodX(file)
}

internal fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

internal fun symlink(link: File, target: String) {
    Files.createSymbolicLink(link.toPath(), Path.of(target))
}

internal fun exists(path: String): Boolean = Files.exists(Path.of(path), LinkOption.NOFOLLOW_LINKS)

/** A java.nio [RootfsFileSystem] that never follows links: what an Android binding does with java.io on the phone. */
internal class NioRootfsFileSystem : RootfsFileSystem {
    override suspend fun type(path: String): FsType {
        val p = Path.of(path)
        return when {
            Files.isSymbolicLink(p) -> FsType.Link
            !Files.exists(p, LinkOption.NOFOLLOW_LINKS) -> FsType.NotFound
            Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) -> FsType.Directory
            else -> FsType.File
        }
    }

    override suspend fun children(path: String): FolderResult<List<FsChild>> {
        val dir = File(path)
        val names = dir.list() ?: return FolderResult.Problem(FolderListProblem.Failed, path)
        return FolderResult.Ok(names.map { FsChild(it, type("$path/$it")) })
    }
}

/**
 * Deletes this tree WITHOUT following symbolic links (a link is removed, never its target). Kotlin's
 * File.deleteRecursively follows links to directories: with the fixtures' links to /etc it would empty the host's /etc.
 */
internal fun File.deleteTree() {
    val start = toPath()
    if (!Files.exists(start, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(
        start,
        object : java.nio.file.SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                Files.delete(file) // regular files and symbolic links alike: never the link's target
                return java.nio.file.FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): java.nio.file.FileVisitResult {
                Files.delete(dir)
                return java.nio.file.FileVisitResult.CONTINUE
            }
        },
    )
}

/**
 * Refuses to run a real script unless every path it is given points inside [root], a fresh temp folder: an empty or
 * "/" HOME/PREFIX would let tools.sh clean or scan the host instead of the fixture.
 */
internal fun requireHermetic(root: File, vararg paths: String) {
    val tmp = File(System.getProperty("java.io.tmpdir")).canonicalPath
    val base = root.canonicalPath
    check(base.startsWith("$tmp/") && base.length > tmp.length + 1) { "fixture root is not a fresh temp folder: $base" }
    for (path in paths) {
        check(path.isNotBlank() && path != "/") { "empty or root path handed to a real script" }
        check(File(path).canonicalPath.startsWith("$base/")) { "path outside the fixture: $path" }
    }
}
