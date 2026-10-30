import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/server_gateway.dart';
import 'git_page_controller.dart';
import 'git_workspace.dart';
import 'github_account.dart';
import 'profiles.dart' show ServerProfile;

/// The Git state of one open chat: the project's [GitPageController] (read
/// once when the chat opens, so the header chip can name the branch) and the
/// GitHub sign-in of the connection. The chat owns one holder, calls [bind]
/// from its build with what it is showing, and disposes it with the chat.
///
/// A new controller is made when the connection, the gateway or the project
/// changes (a controller is bound to one folder). Listeners hear every change
/// of the controller, the account and the binding itself.
class ChatGitHolder extends ChangeNotifier {
  ChatGitHolder({
    GitWorkspaceResolution Function(
      ServerProfile? profile,
      ServerGateway? gateway,
      String? directory,
    )?
    resolve,
    GitHubAccountController Function(String profileId)? account,
    GitPageController Function(
      GitWorkspaceResolution resolution,
      GitHubAccountController? account,
    )?
    page,
  }) : _resolve = resolve ?? _defaultResolve,
       _makeAccount = account ?? _defaultAccount,
       _makePage = page ?? _defaultPage;

  final GitWorkspaceResolution Function(
    ServerProfile? profile,
    ServerGateway? gateway,
    String? directory,
  )
  _resolve;
  final GitHubAccountController Function(String profileId) _makeAccount;
  final GitPageController Function(
    GitWorkspaceResolution resolution,
    GitHubAccountController? account,
  )
  _makePage;

  static GitWorkspaceResolution _defaultResolve(
    ServerProfile? profile,
    ServerGateway? gateway,
    String? directory,
  ) => resolveGitWorkspace(
    profile: profile,
    gateway: gateway,
    directory: directory,
  );

  static GitHubAccountController _defaultAccount(String profileId) =>
      GitHubAccountController(profileId: profileId);

  static GitPageController _defaultPage(
    GitWorkspaceResolution resolution,
    GitHubAccountController? account,
  ) => GitPageController(resolution: resolution, account: account);

  GitPageController? _page;
  GitHubAccountController? _account;
  String? _key;
  bool _disposed = false;

  /// The project's Git controller; null before the first [bind].
  GitPageController? get page => _page;

  /// The GitHub sign-in; non-null only where this connection offers it.
  GitHubAccountController? get account => _account;

  /// The branch to name on the chip: null when detached or unknown. Until
  /// the project's first read of this chat lands it is the branch this run
  /// last read there, so the chip says "master" from the first frame
  /// instead of "Git" turning into "master" (jitter J8, 2026-10-28).
  String? get branchName {
    final snapshot = _page?.snapshot;
    if (snapshot != null) return snapshot.status.branch.head;
    final project = _project;
    return project == null ? null : _lastBranch[project];
  }

  /// The branch last read per project (`profile|directory`), this run.
  static final _lastBranch = <String, String?>{};
  String? _project;

  /// Points the holder at this project. Does nothing when nothing changed.
  /// Safe to call during a build: the first read starts after the frame.
  void bind({
    required ServerProfile? profile,
    required ServerGateway? gateway,
    required String? directory,
  }) {
    final key =
        '${profile?.id}|${directory?.trim()}|'
        '${gateway == null ? 0 : identityHashCode(gateway)}';
    if (key == _key || _disposed) return;
    _key = key;
    _project = '${profile?.id}|${directory?.trim()}';
    _release();
    final resolution = _resolve(profile, gateway, directory);
    final workspace = resolution.workspace;
    final account = workspace != null && workspace.offersGitHubSignIn
        ? _makeAccount(profile!.id)
        : null;
    final page = _makePage(resolution, account);
    _account = account;
    _page = page;
    account?.addListener(_forward);
    page.addListener(_forward);
    // Reading starts after this frame: the controller notifies at once.
    Future<void>.microtask(() {
      if (_disposed || _page != page) return;
      unawaited(account?.load());
      page.refresh();
    });
  }

  void _forward() {
    if (_disposed) return;
    final snapshot = _page?.snapshot;
    final project = _project;
    if (snapshot != null && project != null) {
      _lastBranch[project] = snapshot.status.branch.head;
    }
    notifyListeners();
  }

  void _release() {
    _page?.removeListener(_forward);
    _account?.removeListener(_forward);
    _page?.dispose();
    _account?.dispose();
    _page = null;
    _account = null;
  }

  @override
  void dispose() {
    _disposed = true;
    _release();
    super.dispose();
  }
}
