package io.github.eslamasabry.oc.engine.files

/**
 * The never-share-secrets guard (port of `WifiFileShare.looksSecret`, lib/platform/wifi_file_share.dart). A path
 * that may hold keys, passwords or sign-ins is never handed out over Wi-Fi, whatever folder it sits in: `.env*`,
 * private keys, `.ssh`/`.gnupg`/cloud CLI folders, every app-owned `.oc-*` folder, OpenCode's own config, and any
 * name with "password", "secret" or "token" in it. Matched on every path segment, case-insensitively, so a file
 * cannot dodge it by its folder.
 */
public object ShareGuard {
    private val secretFolders =
        setOf(
            "shared_prefs", "databases", "no_backup", "app_flutter", "code_cache",
            ".ssh", ".gnupg", ".codex", ".claude", ".aws", ".kube", ".docker", ".gcloud",
        )

    private val secretNames = setOf("auth.json", "shadow", ".netrc", ".npmrc", ".git-credentials")

    private val secretPrefixes = listOf(".env", "id_rsa", "id_ed25519", "id_ecdsa")

    private val secretSuffixes = listOf(".pem", ".key", ".p12", ".pfx", ".keystore", ".jks")

    private val secretWords = listOf("password", "secret", "token")

    public fun looksSecret(path: String): Boolean {
        val names = path.split('/', '\\').filter { it.isNotEmpty() }.map { it.lowercase() }
        if (names.isEmpty()) return true
        for (folder in names.dropLast(1)) {
            // .oc-builtin holds the phone server's password, .oc-profiles each agent's sign-ins.
            if (folder in secretFolders || folder.startsWith(".oc-")) return true
        }
        for (i in 0 until names.size - 1) {
            // ~/.config/opencode, ~/.local/share/opencode, ~/.config/gh, /etc/ssh
            if (names[i] == ".config" && (names[i + 1] == "opencode" || names[i + 1] == "gh")) return true
            if (names[i] == ".local" && i + 2 < names.size && names[i + 1] == "share" && names[i + 2] == "opencode") {
                return true
            }
            if (names[i] == "etc" && names[i + 1] == "ssh") return true
        }
        val name = names.last()
        if (name.startsWith(".oc-")) return true
        if (secretPrefixes.any { name.startsWith(it) }) return true
        if (name in secretNames) return true
        if (secretSuffixes.any { name.endsWith(it) }) return true
        if (secretWords.any { it in name }) return true
        return name.contains("credentials") && name.endsWith(".json")
    }
}
