package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Outcome

/** Opaque receipt for one private token file. The path is never a token or a URL. */
public class GitSecretFile(public val shellPath: String) {
    override fun toString(): String = "GitSecretFile"
}

/** Only the built-in Linux binding implements this; a remote workspace never receives it. */
public interface GitCredentialDrop {
    public suspend fun place(secret: String): Outcome<GitSecretFile>

    public suspend fun remove(file: GitSecretFile): Outcome<Unit>
}

internal const val GIT_TOKEN_FILE: String = "OC_TOKEN_FILE"

/**
 * Answers GitHub only. The helper names the variable; neither the script nor argv contains the
 * token.
 */
internal const val GITHUB_HELPER: String =
    "!f() { test \"\$1\" = get || return 0; h=; p=; " +
        "while IFS== read -r k v; do test \"\$k\" = host && h=\$v; test \"\$k\" = protocol && p=\$v; done; " +
        "test \"\$h\" = github.com && test \"\$p\" = https || return 0; " +
        "test -f \"\$OC_TOKEN_FILE\" && test ! -L \"\$OC_TOKEN_FILE\" || return 1; " +
        "printf \"username=x-access-token\\npassword=%s\\n\" \"\$(cat \"\$OC_TOKEN_FILE\")\"; }; f"

internal val gitHubCredentialOptions: List<String> =
    listOf(
        "-c",
        "credential.helper=",
        "-c",
        "credential.helper=$GITHUB_HELPER",
        "-c",
        "url.https://github.com/.insteadOf=git@github.com:",
        "-c",
        "url.https://github.com/.insteadOf=ssh://git@github.com/",
    )
