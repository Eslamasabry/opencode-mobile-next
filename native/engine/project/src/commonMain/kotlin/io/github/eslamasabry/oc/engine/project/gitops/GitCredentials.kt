package io.github.eslamasabry.oc.engine.project.gitops

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlin.random.Random

/*
 * Handing a GitHub sign-in to one `git push` without the token ever appearing in a script, a command line or a log
 * (port of lib/domain/git/git_credentials.dart and BuiltinGitSecretDrop in lib/builtin/git_shell.dart):
 *  1. the app writes the token to a fresh 0600 file in a 0700 folder, named at random, where the project's shell sees it;
 *  2. the script names only the file's path (`OC_TOKEN_FILE`); Git forgets every stored helper and asks one inline
 *     helper that reads the file for github.com only;
 *  3. SSH addresses of GitHub are rewritten to HTTPS for that one command;
 *  4. the file is removed by the script's exit trap and again by the app, whatever happened.
 * Remote servers and Termux never get the token: they push with their own sign-in.
 */

/** A secret file placed for one command. Never prints its paths' secret: it holds none. */
public data class GitSecretFile(
    /** The path as the project's shell sees it (`/root/.oc-git/c-…`). */
    val shellPath: String,
    /** The same file from the app's side, for removal. */
    val hostPath: String,
)

/** Places and removes [GitSecretFile]s where one environment's shell can read them (Ubuntu inside the app only). */
public interface GitCredentialDrop {
    /** Writes [secret] to a new 0600 file; a failure when the file cannot be made private (never a readable file). */
    public suspend fun place(secret: String): Outcome<GitSecretFile>

    /** Deletes [file]; safe to call twice and when it is already gone. */
    public suspend fun remove(file: GitSecretFile)
}

/** The variable naming the token file for the helper. */
public const val GIT_TOKEN_FILE_VARIABLE: String = "OC_TOKEN_FILE"

/** The inline credential helper: answers `get` for `host=github.com` only, from the file the variable names. No secret. */
public const val GIT_HUB_CREDENTIAL_HELPER: String =
    "!f() { test \"\$1\" = get || return 0; h=; " +
        "while IFS== read -r k v; do test \"\$k\" = host && h=\$v; done; " +
        "test \"\$h\" = github.com || return 0; " +
        "printf \"username=x-access-token\\npassword=%s\\n\" " +
        "\"\$(cat \"\$$GIT_TOKEN_FILE_VARIABLE\")\"; }; f"

/** `git -c …` options for one command run with a GitHub sign-in. */
public fun gitHubCredentialOptions(): List<String> =
    listOf(
        "-c", "credential.helper=",
        "-c", "credential.helper=$GIT_HUB_CREDENTIAL_HELPER",
        "-c", "url.https://github.com/.insteadOf=git@github.com:",
        "-c", "url.https://github.com/.insteadOf=ssh://git@github.com/",
    )

/** The app's side of the in-app Ubuntu's files, for the secret drop. Each call reports success; nothing throws. */
public interface SecretDropFiles {
    /** The Ubuntu root folder on the app's side, or null when no Ubuntu is installed. */
    public suspend fun rootfs(): String?

    public suspend fun createFolders(path: String): Boolean

    public suspend fun chmod(path: String, mode: String): Boolean

    /** The permission bits of [path] (`0x1ff` mask), or null when it cannot be read. */
    public suspend fun mode(path: String): Int?

    public suspend fun write(path: String, bytes: ByteArray): Boolean

    public suspend fun size(path: String): Long?

    public suspend fun delete(path: String): Boolean
}

/**
 * Places GitHub tokens for one command in Ubuntu inside the app: a file named at random under `/root/.oc-git`, made 0600
 * before the token is written, overwritten with zeros and deleted afterwards.
 */
public class PrivateSecretDrop(private val files: SecretDropFiles, private val random: Random) : GitCredentialDrop {
    override suspend fun place(secret: String): Outcome<GitSecretFile> {
        val root = files.rootfs() ?: return Failure.Folder(FolderProblem.NotInstalled).failed()
        val folder = root + SHELL_FOLDER
        if (!files.createFolders(folder) || !files.chmod(folder, "700") || files.mode(folder) != 0x1c0) {
            return Failure.StorageRefused().failed()
        }
        val name = "c-" + random.nextBytes(16).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val host = "$folder/$name"
        // Empty first, private second, secret last: the token is never in a file anyone else could read.
        if (!files.write(host, ByteArray(0))) return Failure.StorageRefused().failed()
        if (!files.chmod(host, "600") || files.mode(host) != 0x180 || !files.write(host, secret.encodeToByteArray())) {
            wipe(host)
            return Failure.StorageRefused().failed()
        }
        return GitSecretFile(shellPath = "$SHELL_FOLDER/$name", hostPath = host).ok()
    }

    override suspend fun remove(file: GitSecretFile): Unit = wipe(file.hostPath)

    private suspend fun wipe(path: String) {
        val length = files.size(path) ?: return // Already gone (the script's exit trap removed it first).
        if (length > 0) files.write(path, ByteArray(length.toInt()))
        files.delete(path)
    }

    public companion object {
        /** The folder inside Ubuntu. */
        public const val SHELL_FOLDER: String = "/root/.oc-git"
    }
}
