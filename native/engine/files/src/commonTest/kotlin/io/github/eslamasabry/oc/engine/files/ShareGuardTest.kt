package io.github.eslamasabry.oc.engine.files

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The never-share-secrets guard of Wi-Fi share (wifi_file_share_test.dart "app storage and key files are refused"). */
class ShareGuardTest {
    @Test
    fun `B-MEDIA-034 keys, env files and credential names are never shared`() {
        val refused =
            listOf(
                ".env", "app/.env", "app/.env.production", "deploy/id_rsa", "home/.ssh/config", "home/.ssh/known_hosts",
                "certs/server.pem", "certs/server.key", "release.keystore", "upload.jks", "cert.p12", "cert.pfx",
                "home/.netrc", ".npmrc", ".git-credentials", "home/.aws/config", "home/.kube/config",
                "home/.docker/config.json", "home/.gnupg/pubring.kbx", "home/.codex/auth.json", "home/.claude/notes.md",
                "home/.config/opencode/opencode.json", "home/.local/share/opencode/auth.json", "home/.config/gh/hosts.yml",
                "etc/ssh/sshd_config", "etc/shadow", "auth.json", "db_password.txt", "MY_SECRET.md", "github-token",
                "gcp-credentials.json", "data/.oc-builtin/server.json", "data/.oc-profiles/agent/state",
                ".oc-anything", "APP/.ENV", "Keys\\id_ed25519", "",
            )
        for (path in refused) assertTrue(ShareGuard.looksSecret(path), "should refuse: '$path'")
    }

    @Test
    fun `B-MEDIA-034 ordinary project files may be shared`() {
        val allowed =
            listOf(
                "README.md", "lib/main.dart", "docs/environment.md", "src/keyboard.kt", "assets/logo.png",
                "build/app.apk", "notes/credentials-howto.md", "src/env.ts", "report.pdf", "config/settings.json",
            )
        for (path in allowed) assertFalse(ShareGuard.looksSecret(path), "should allow: '$path'")
    }
}
