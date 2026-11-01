/// Original pinned manifest and installer retained for compatibility.
/// Phone agents register these same payload pins through AgentPhoneScripts,
/// installing as oc. Device qualification is the explicit four-step self-test.
abstract final class ClaudeScripts {
  static const version = '2.1.316';
  static const arm64Sha256 =
      '55cdb8e162d190432696ea54010e0fca2e18d06fa7a7127fd563b9ffd4f22624';
  static const x64Sha256 =
      'fd13c70000fec3f2116dfbcbb6121210b4a29328bf0e46210a59dd594a67d824';

  // Official release manifest, 2.1.316 (published 2026-10-31). Do not use the remote install.sh or
  // npm's optional dependency selection: neither is our pinned payload.
  static const install =
      '''
set -eu
case "\$(uname -m)" in
  aarch64|arm64) claude_arch=arm64; claude_sha=$arm64Sha256 ;;
  x86_64|amd64) claude_arch=x64; claude_sha=$x64Sha256 ;;
  *) echo '[oc] Claude requires ARM64 or x64 Ubuntu' >&2; exit 1 ;;
esac
case "\$(getconf GNU_LIBC_VERSION)" in
  "glibc "*) ;;
  *) echo '[oc] Claude requires glibc Ubuntu' >&2; exit 1 ;;
esac
claude_dir=/opt/oc-claude
claude_target="\$claude_dir/claude"
claude_link=/usr/local/bin/claude
oc_update_recover "\$claude_target" || exit 1
oc_update_recover "\$claude_link" || exit 1
mkdir -p "\$claude_dir"
claude_part="\$claude_dir/claude.new"
claude_link_new="\$claude_link.new"
trap 'rm -f "\$claude_part" "\$claude_link_new"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
oc_stage 'Downloading Claude Code $version'
oc_download "https://downloads.claude.ai/claude-code-releases/$version/linux-\$claude_arch/claude" \\
  "\$claude_part" "\$claude_sha"
# oc_download verifies the pinned SHA before this first execution.
chmod 755 "\$claude_part"
export DISABLE_AUTOUPDATER=1
[ "\$("\$claude_part" --version)" = '$version (Claude Code)' ]
mkdir -p /usr/local/bin
rm -f "\$claude_link_new"
ln -s "\$claude_target" "\$claude_link_new"
claude_update_failed() {
  claude_restore_failed=0
  oc_update_recover "\$claude_target" || claude_restore_failed=1
  oc_update_recover "\$claude_link" || claude_restore_failed=1
  if [ "\$claude_restore_failed" != 0 ]; then
    echo '[oc] A component update could not be restored. Run setup again.' >&2
  else
    echo '[oc] Claude could not finish updating. Run setup again.' >&2
  fi
  exit 1
}
oc_update_activate "\$claude_target" "\$claude_part" || claude_update_failed
oc_update_activate "\$claude_link" "\$claude_link_new" || claude_update_failed
if ! claude_active_version=\$("\$claude_link" --version 2>/dev/null) ||
  [ "\$claude_active_version" != '$version (Claude Code)' ]; then
  claude_update_failed
fi
# Commit code before its command link; retain each previous good generation.
oc_update_commit "\$claude_target" || claude_update_failed
oc_update_commit "\$claude_link" || claude_update_failed
oc_version '$version'
''';
}
