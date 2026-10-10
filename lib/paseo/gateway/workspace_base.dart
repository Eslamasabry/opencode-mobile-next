part of '../gateway.dart';

// ---- Files, Changes, Worktrees and Terminal (OD1 lane "paseo") -------------
//
// These blocks sit in their own mixins so the daemon requests for the open
// folder's files, changes, worktrees and terminals stay apart from the chat
// code above. They run on the folder the gateway is scoped to.

/// What every workspace block below needs from [PaseoGateway].
mixin _PaseoWorkspaceBase {
  PaseoTransport get transport;
  String get _scope;
  int get _locationEpoch;
  void _checkLocation(String scope, int epoch);

  /// One daemon request for the open folder. A folder change while it ran
  /// throws, so nothing from the old folder lands in the new one.
  Future<Map<String, dynamic>> _workspaceRequest(
    String type,
    Map<String, dynamic> body, {
    bool mutation = false,
    Duration? timeout,
  }) async {
    final scope = _scope;
    final epoch = _locationEpoch;
    final result = await transport.request(
      type,
      body,
      mutation: mutation,
      timeout: timeout,
    );
    _checkLocation(scope, epoch);
    return result;
  }

  /// Checkout answers carry their failure as `{code, message}`; the words
  /// never reach the person (the daemon's text stays out of the app).
  void _requireCheckoutOk(Map<String, dynamic> payload) {
    if (payload['error'] != null) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
  }

  /// A path inside the open folder, the way the daemon wants it: relative,
  /// with `.` for the folder itself. An absolute path inside the folder is
  /// cut back to its relative form; anything else is passed on and the daemon
  /// refuses what lies outside.
  String _relativeToScope(String path) {
    var value = path.trim();
    final root = _scope;
    if (value == root) return '.';
    if (value.startsWith('$root/')) value = value.substring(root.length + 1);
    while (value.startsWith('./')) {
      value = value.substring(2);
    }
    return value.isEmpty ? '.' : value;
  }
}

/// Every workspace block on one base class, so [PaseoGateway] names them once.
abstract class _PaseoWorkspace = Object
    with
        _PaseoWorkspaceBase,
        _PaseoFilesApi,
        _PaseoChangesApi,
        _PaseoWorktreesApi;
