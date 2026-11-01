/// Points this phone's OpenCode server at Claude Code for the Claude Agent
/// trial (lib/domain/claude_agent_trial.dart).
///
/// The phone server runs as root; Claude Code is installed for the `oc` user
/// at /home/oc/.local/bin/claude and keeps its sign-in in the agent home's
/// folder `/home/oc/.oc-profiles/<owner>/claude` (the one home every phone
/// profile shares, `ProfileStore.phoneAgentOwnerId`). OpenCode finds
/// `claude` on its PATH (`OPENCODE_CLAUDE_PATH` would need the native
/// restore script too, so a later native release), so this writes, as root:
///
/// - `/usr/local/sbin/claude`, a small launcher ahead of the Terminal's
///   `/usr/local/bin/claude` on root's PATH. Piped (OpenCode, through the
///   Agent SDK) it runs Claude Code with `CLAUDE_CONFIG_DIR` set to that
///   folder and OpenCode's environment kept. Typed in the Terminal (a
///   terminal on stdin) it hands over to the Terminal's launcher unchanged.
/// - `/root/.claude`, a link to the same folder, so OpenCode's own reads of
///   Claude's saved chats (resuming one after Claude idles out) look there.
///   A real folder there that the app did not make is left alone: chats
///   still work, they only start over after five idle minutes.
///
/// Nothing is copied, read or moved: the sign-in stays where Claude Code
/// keeps it. Both files survive app and server restarts. Idempotent; it never
/// replaces a launcher it did not write.
library;

import 'builtin_linux.dart';

/// What [ClaudeAgentLink.run] found.
enum ClaudeAgentLinkResult {
  /// Launcher and link are in place.
  linked,

  /// Launcher in place; `/root/.claude` is someone else's folder.
  linkedWithoutResume,

  /// Claude Code is not installed for the agent home.
  notInstalled,

  /// `/usr/local/sbin/claude` exists and is not the app's.
  blocked,

  /// The script could not run.
  failed,
}

/// Runs [ClaudeAgentLink.script] in the in-app Ubuntu. Tests pass a fake.
abstract interface class ClaudeAgentLinkRunner {
  Future<ClaudeAgentLinkResult> link(String ownerId);
}

class ClaudeAgentLink implements ClaudeAgentLinkRunner {
  ClaudeAgentLink({BuiltinLinux? linux}) : _linux = linux ?? BuiltinLinux();

  final BuiltinLinux _linux;

  static final _owner = RegExp(r'^[A-Za-z0-9_-]{1,80}$');

  /// The marker line that makes `/usr/local/sbin/claude` the app's.
  static const shimMarker = '# oc-claude-agent-shim';

  /// The root script for agent home [ownerId]. Prints one result line.
  static String script(String ownerId) {
    if (!_owner.hasMatch(ownerId)) {
      throw ArgumentError.value(ownerId, 'ownerId', 'Not an agent home id.');
    }
    final home = '/home/oc/.oc-profiles/$ownerId/claude';
    return '''
set -u
home='$home'
claude=/home/oc/.local/bin/claude
shim=/usr/local/sbin/claude
if [ ! -x "\$claude" ] || [ ! -d "\$home" ]; then
  echo oc-claude-agent:not-installed
  exit 0
fi
if [ -e "\$shim" ] && ! grep -qx '$shimMarker' "\$shim" 2>/dev/null; then
  echo oc-claude-agent:blocked
  exit 0
fi
mkdir -p /usr/local/sbin || { echo oc-claude-agent:failed; exit 0; }
cat > "\$shim.new" <<'OC_SHIM' || { rm -f "\$shim.new"; echo oc-claude-agent:failed; exit 0; }
#!/bin/sh
$shimMarker
# OpenCode's Claude Agent on this phone finds this first on root's PATH.
# Typed in the Terminal, the launcher in /usr/local/bin runs Claude as oc.
if [ -t 0 ] && [ -x /usr/local/bin/claude ]; then exec /usr/local/bin/claude "\$@"; fi
export CLAUDE_CONFIG_DIR='$home'
export DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
exec /home/oc/.local/bin/claude "\$@"
OC_SHIM
chmod 755 "\$shim.new" && mv -f "\$shim.new" "\$shim" || { rm -f "\$shim.new"; echo oc-claude-agent:failed; exit 0; }
link=/root/.claude
if [ -L "\$link" ]; then
  ln -sfn "\$home" "\$link.new" && mv -Tf "\$link.new" "\$link" || { rm -f "\$link.new"; echo oc-claude-agent:failed; exit 0; }
elif [ -d "\$link" ] && [ -z "\$(ls -A "\$link" 2>/dev/null)" ]; then
  rmdir "\$link" && ln -s "\$home" "\$link" || { echo oc-claude-agent:failed; exit 0; }
elif [ -e "\$link" ]; then
  echo oc-claude-agent:linked-without-resume
  exit 0
else
  ln -s "\$home" "\$link" || { echo oc-claude-agent:failed; exit 0; }
fi
echo oc-claude-agent:linked
''';
  }

  /// The result line [script] printed, from the run's whole output.
  static ClaudeAgentLinkResult parse(int exitCode, String output) {
    if (exitCode != 0) return ClaudeAgentLinkResult.failed;
    final line = output
        .split('\n')
        .map((line) => line.trim())
        .lastWhere(
          (line) => line.startsWith('oc-claude-agent:'),
          orElse: () => '',
        );
    return switch (line) {
      'oc-claude-agent:linked' => ClaudeAgentLinkResult.linked,
      'oc-claude-agent:linked-without-resume' =>
        ClaudeAgentLinkResult.linkedWithoutResume,
      'oc-claude-agent:not-installed' => ClaudeAgentLinkResult.notInstalled,
      'oc-claude-agent:blocked' => ClaudeAgentLinkResult.blocked,
      _ => ClaudeAgentLinkResult.failed,
    };
  }

  @override
  Future<ClaudeAgentLinkResult> link(String ownerId) async {
    try {
      final run = await _linux.run(
        script(ownerId),
        timeout: const Duration(seconds: 60),
      );
      return parse(run.exitCode, run.output);
    } catch (_) {
      return ClaudeAgentLinkResult.failed;
    }
  }
}
