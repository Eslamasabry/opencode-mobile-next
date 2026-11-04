package io.github.eslamasabry.oc.engine.agents.scripts

/**
 * Port of lib/builtin/setup/claude_scripts.dart, byte-faithful: the pinned Claude Code installer for the built-in
 * Ubuntu (run with [SetupPrelude.with]). It downloads the official release payload for the phone's ABI, checks the
 * pinned SHA-256 before the first execution, and activates it with the component update helpers, so a failed update
 * restores the previous good generation.
 */
public object ClaudeScripts {
    public const val VERSION: String = "2.1.316"
    public const val ARM64_SHA256: String = "55cdb8e162d190432696ea54010e0fca2e18d06fa7a7127fd563b9ffd4f22624"
    public const val X64_SHA256: String = "fd13c70000fec3f2116dfbcbb6121210b4a29328bf0e46210a59dd594a67d824"

    // Official release manifest. Do not use the remote install.sh or npm's optional dependency selection.
    public val install: String =
        "set -eu\n" +
            "case \"\$(uname -m)\" in\n" +
            "  aarch64|arm64) claude_arch=arm64; claude_sha=$ARM64_SHA256 ;;\n" +
            "  x86_64|amd64) claude_arch=x64; claude_sha=$X64_SHA256 ;;\n" +
            "  *) echo '[oc] Claude requires ARM64 or x64 Ubuntu' >&2; exit 1 ;;\n" +
            "esac\n" +
            "case \"\$(getconf GNU_LIBC_VERSION)\" in\n" +
            "  \"glibc \"*) ;;\n" +
            "  *) echo '[oc] Claude requires glibc Ubuntu' >&2; exit 1 ;;\n" +
            "esac\n" +
            "claude_dir=/opt/oc-claude\n" +
            "claude_target=\"\$claude_dir/claude\"\n" +
            "claude_link=/usr/local/bin/claude\n" +
            "oc_update_recover \"\$claude_target\" || exit 1\n" +
            "oc_update_recover \"\$claude_link\" || exit 1\n" +
            "mkdir -p \"\$claude_dir\"\n" +
            "claude_part=\"\$claude_dir/claude.new\"\n" +
            "claude_link_new=\"\$claude_link.new\"\n" +
            "trap 'rm -f \"\$claude_part\" \"\$claude_link_new\"' EXIT\n" +
            "trap 'exit 129' HUP\n" +
            "trap 'exit 130' INT\n" +
            "trap 'exit 143' TERM\n" +
            "oc_stage 'Downloading Claude Code $VERSION'\n" +
            "oc_download \"https://downloads.claude.ai/claude-code-releases/$VERSION/linux-\$claude_arch/claude\" \\\n" +
            "  \"\$claude_part\" \"\$claude_sha\"\n" +
            "# oc_download verifies the pinned SHA before this first execution.\n" +
            "chmod 755 \"\$claude_part\"\n" +
            "export DISABLE_AUTOUPDATER=1\n" +
            "[ \"\$(\"\$claude_part\" --version)\" = '$VERSION (Claude Code)' ]\n" +
            "mkdir -p /usr/local/bin\n" +
            "rm -f \"\$claude_link_new\"\n" +
            "ln -s \"\$claude_target\" \"\$claude_link_new\"\n" +
            "claude_update_failed() {\n" +
            "  claude_restore_failed=0\n" +
            "  oc_update_recover \"\$claude_target\" || claude_restore_failed=1\n" +
            "  oc_update_recover \"\$claude_link\" || claude_restore_failed=1\n" +
            "  if [ \"\$claude_restore_failed\" != 0 ]; then\n" +
            "    echo '[oc] A component update could not be restored. Run setup again.' >&2\n" +
            "  else\n" +
            "    echo '[oc] Claude could not finish updating. Run setup again.' >&2\n" +
            "  fi\n" +
            "  exit 1\n" +
            "}\n" +
            "oc_update_activate \"\$claude_target\" \"\$claude_part\" || claude_update_failed\n" +
            "oc_update_activate \"\$claude_link\" \"\$claude_link_new\" || claude_update_failed\n" +
            "if ! claude_active_version=\$(\"\$claude_link\" --version 2>/dev/null) ||\n" +
            "  [ \"\$claude_active_version\" != '$VERSION (Claude Code)' ]; then\n" +
            "  claude_update_failed\n" +
            "fi\n" +
            "# Commit code before its command link; retain each previous good generation.\n" +
            "oc_update_commit \"\$claude_target\" || claude_update_failed\n" +
            "oc_update_commit \"\$claude_link\" || claude_update_failed\n" +
            "oc_version '$VERSION'\n"
}
