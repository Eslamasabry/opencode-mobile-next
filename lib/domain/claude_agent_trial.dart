/// The Claude Agent trial (2026-11-01): OpenCode 2's own "Claude Agent"
/// provider on this phone's server, beside Claude Code through Paseo.
///
/// OpenCode (2.2.3 and later) runs the phone's installed `claude` through
/// Anthropic's Agent SDK with Claude Code's own sign-in; Claude then works
/// with OpenCode's tools and permissions. The app never reads, copies or
/// passes on that sign-in: it only points OpenCode at the program and the
/// sign-in folder Claude Code already uses on this phone, and tells OpenCode
/// that the person chose this login ("connect external").
///
/// Plan and evidence: /home/eslam/Storage2/wt/paseo-exit-plan-2026-11-01/.
library;

/// OpenCode's provider and integration id.
const claudeAgentProviderId = 'claude-agent';

/// OpenCode's login method for it: "Claude Code login (claude auth login)".
const claudeAgentLoginMethodId = 'claude-login';

/// The first OpenCode release with the provider (changelog v2.2.3).
const claudeAgentMinimumServer = '2.2.3';

/// Whether [version] (an OpenCode release such as "2.2.5" or "v2.2.5-beta")
/// is [minimum] or later. Unknown or malformed versions are not.
bool serverVersionAtLeast(String? version, String minimum) {
  List<int>? parse(String? raw) {
    final match = RegExp(
      r'^v?(\d+)\.(\d+)\.(\d+)',
    ).firstMatch(raw?.trim() ?? '');
    if (match == null) return null;
    return [for (var i = 1; i <= 3; i++) int.parse(match.group(i)!)];
  }

  final current = parse(version), required = parse(minimum);
  if (current == null || required == null) return false;
  for (var i = 0; i < 3; i++) {
    if (current[i] != required[i]) return current[i] > required[i];
  }
  return true;
}

/// Records that the person chose an integration's external login (OpenCode
/// `POST /integration/{id}/connect/external`). The server then checks the
/// login on its own; nothing secret is sent.
abstract interface class ExternalIntegrationGateway {
  Future<void> connectIntegrationExternal(
    String integrationID,
    String methodID,
  );
}

/// Where the trial stands for the connected phone server.
enum ClaudeAgentTrialState {
  /// Not this phone's OpenCode, too old, or the provider is not offered.
  unavailable,

  /// Offered, and the person has not tried it yet.
  available,

  /// Pointing OpenCode at Claude Code and waiting for its check.
  preparing,

  /// OpenCode found Claude Code but no sign-in.
  needsSignIn,

  /// Claude Code is not installed in this phone's Ubuntu.
  notInstalled,

  /// The sign-in folder's place is taken by a folder the app did not make.
  blocked,

  /// Claude Agent's models are in the list.
  ready,

  /// Something else went wrong; Check again retries.
  failed,
}
