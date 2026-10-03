/// Original pinned manifest and installer retained for compatibility.
/// Phone agents register these same payload pins through AgentPhoneScripts,
/// installing as oc. Device qualification is the explicit four-step self-test.
abstract final class ClaudeScripts {
  static const version = '2.1.283';
  static const arm64Sha256 =
      '346d294f0103d6fc0de11ac953579b5c62dfa90698a4cfc486b6f927c615e697';
  static const x64Sha256 =
      '1859583ce32920595c61ef868bee52e1b1594f7486db209935e01f1e5e804ae2';

  // Official release manifest, 2.1.283. Do not use the remote install.sh or
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
mkdir -p "\$claude_dir"
claude_part="\$claude_dir/claude.new"
trap 'rm -f "\$claude_part"' EXIT
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
mv -f "\$claude_part" "\$claude_dir/claude"
mkdir -p /usr/local/bin
ln -sf "\$claude_dir/claude" /usr/local/bin/claude
oc_version '$version'
''';
}
