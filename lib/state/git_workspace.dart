import '../builtin/builtin_linux.dart';
import '../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../builtin/git_shell.dart';
import '../domain/git/git_credentials.dart';
import '../domain/git/git_shell.dart';
import '../domain/remote_shell.dart';
import '../domain/server_gateway.dart';
import 'profiles.dart' show ServerProfile;

/// Where the project of the Git page lives, which decides how commands run
/// and whether a GitHub sign-in may be handed over.
enum GitHost {
  /// Ubuntu inside the app: commands via `BuiltinLinux.run`; GitHub sign-in
  /// handed over per command.
  phoneLinux,

  /// The connected server (`POST /api/shell`): pushes use the server's own
  /// Git sign-in; the app never sends its token.
  server,
}

/// Why the Git page cannot work for the current connection.
enum GitUnavailableReason {
  /// No connection.
  notConnected,

  /// No project folder is open.
  noProject,

  /// The server cannot run commands (OpenCode 1, Codex, Paseo, or an
  /// OpenCode 2 without `/api/shell`).
  serverCannotRunCommands,
}

/// One project, reachable through one shell.
class GitWorkspace {
  const GitWorkspace({
    required this.host,
    required this.directory,
    required this.shell,
    this.credentials,
  });

  final GitHost host;

  /// The project folder as [shell] sees it.
  final String directory;
  final GitShell shell;

  /// Non-null only where the app may place a GitHub token
  /// ([GitHost.phoneLinux]).
  final GitCredentialDrop? credentials;

  /// GitHub sign-in in the app helps here.
  bool get offersGitHubSignIn => credentials != null;

  /// Pushes use the sign-in that environment already has.
  bool get usesOwnGitSignIn => credentials == null;
}

/// What [resolveGitWorkspace] found.
class GitWorkspaceResolution {
  const GitWorkspaceResolution.available(GitWorkspace this.workspace)
    : reason = null;
  const GitWorkspaceResolution.unavailable(GitUnavailableReason this.reason)
    : workspace = null;

  final GitWorkspace? workspace;
  final GitUnavailableReason? reason;
}

/// Picks how to reach [directory] for [profile]/[gateway]:
/// Ubuntu inside the app first, then a server that reports [ServerCapabilities.remoteShell]. Gated on where the
/// server runs and on capabilities, never on the protocol flavor.
GitWorkspaceResolution resolveGitWorkspace({
  required ServerProfile? profile,
  required ServerGateway? gateway,
  required String? directory,
  BuiltinLinux? linux,
}) {
  if (profile == null || gateway == null) {
    return const GitWorkspaceResolution.unavailable(
      GitUnavailableReason.notConnected,
    );
  }
  final folder = directory?.trim();
  if (folder == null || folder.isEmpty) {
    return const GitWorkspaceResolution.unavailable(
      GitUnavailableReason.noProject,
    );
  }
  if (looksLikeInAppServer(profile)) {
    return GitWorkspaceResolution.available(
      GitWorkspace(
        host: GitHost.phoneLinux,
        directory: folder,
        shell: BuiltinGitShell(linux ?? BuiltinLinux()),
        credentials: BuiltinGitSecretDrop(),
      ),
    );
  }
  if (gateway case final RemoteShellGateway remote
      when gateway.capabilities.remoteShell) {
    return GitWorkspaceResolution.available(
      GitWorkspace(
        host: GitHost.server,
        directory: folder,
        shell: ServerGitShell(remote, directory: folder),
      ),
    );
  }
  return const GitWorkspaceResolution.unavailable(
    GitUnavailableReason.serverCannotRunCommands,
  );
}

/// The Git page's commands on the connected server. The script runs under
/// `sh -c` so the server's own shell (fish, zsh) does not matter.
class ServerGitShell implements GitShell {
  ServerGitShell(this._gateway, {this.directory});

  final RemoteShellGateway _gateway;
  final String? directory;

  @override
  Future<String> run(String script, {required Duration timeout}) async {
    final RemoteShellResult result;
    try {
      result = await _gateway.runShellCommand(
        'sh -c ${shQuote(script)}',
        directory: directory,
        timeout: timeout,
      );
    } on RemoteShellException catch (error) {
      throw GitShellException(
        error.failure == RemoteShellFailure.unsupported
            ? GitShellFailure.unsupported
            : GitShellFailure.unavailable,
      );
    }
    if (result.status == RemoteShellStatus.timedOut) {
      throw const GitShellException(GitShellFailure.timedOut);
    }
    return result.output;
  }
}
