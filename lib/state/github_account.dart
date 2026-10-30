import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../domain/git/github_device_flow.dart';
import '../ui/kit/kit_redact.dart';
import 'profiles.dart' show ProfileStore;

/// The GitHub sign-in of one profile, in secure storage under
/// `oc.githubToken.<profileId>` (JSON of [GitHubCredential]). Swept by
/// `ProfileStore.remove` and `ProfileStore.resetSavedSignIns`. Every token
/// read or written is registered with [KitRedact] first.
class GitHubTokenStore {
  GitHubTokenStore({FlutterSecureStorage? secure})
    : _secure = secure ?? const FlutterSecureStorage();

  final FlutterSecureStorage _secure;

  static String keyFor(String profileId) =>
      '${ProfileStore.githubTokenKeyPrefix}$profileId';

  Future<GitHubCredential?> read(String profileId) async {
    final raw = await _secure.read(key: keyFor(profileId));
    if (raw == null || raw.isEmpty) return null;
    Object? json;
    try {
      json = jsonDecode(raw);
    } on FormatException {
      return null;
    }
    final credential = GitHubCredential.fromJson(json);
    if (credential != null) _register(credential);
    return credential;
  }

  Future<void> write(String profileId, GitHubCredential credential) async {
    _register(credential);
    await _secure.write(
      key: keyFor(profileId),
      value: jsonEncode(credential.toJson()),
    );
  }

  Future<void> delete(String profileId) =>
      _secure.delete(key: keyFor(profileId));

  static void _register(GitHubCredential credential) {
    KitRedact.registerKnownSecret(credential.accessToken);
    final refresh = credential.refreshToken;
    if (refresh != null) KitRedact.registerKnownSecret(refresh);
  }
}

/// Where the GitHub sign-in stands, for the page to show.
sealed class GitHubSignInState {
  const GitHubSignInState();
}

/// Not yet read from storage.
class GitHubSignInLoading extends GitHubSignInState {
  const GitHubSignInLoading();
}

/// No sign-in. [problem] says why the last attempt (or the stored token)
/// ended, when it did; [details] is GitHub's text for Details only.
class GitHubSignedOut extends GitHubSignInState {
  const GitHubSignedOut({this.problem, this.details = ''});
  final GitHubSignInProblem? problem;
  final String details;
}

/// Asking GitHub for a code.
class GitHubRequestingCode extends GitHubSignInState {
  const GitHubRequestingCode();
}

/// Waiting for the person to enter [userCode] at [verificationUri] (opened
/// with `openExternalLink`). Polling runs until [expiresAt].
class GitHubAwaitingUser extends GitHubSignInState {
  const GitHubAwaitingUser({
    required this.userCode,
    required this.verificationUri,
    required this.expiresAt,
  });
  final String userCode;
  final Uri verificationUri;
  final DateTime expiresAt;
}

/// GitHub accepted; reading the account name.
class GitHubFinishing extends GitHubSignInState {
  const GitHubFinishing();
}

class GitHubSignedIn extends GitHubSignInState {
  const GitHubSignedIn(this.login);
  final String login;
}

/// Sign-in to GitHub for one profile's environment.
class GitHubAccountController extends ChangeNotifier {
  GitHubAccountController({
    required this.profileId,
    GitHubTokenStore? store,
    GitHubDeviceFlow? flow,
    DateTime Function()? now,
  }) : _store = store ?? GitHubTokenStore(),
       _flow = flow ?? GitHubDeviceFlow(),
       _now = now ?? DateTime.now;

  final String profileId;
  final GitHubTokenStore _store;
  final GitHubDeviceFlow _flow;
  final DateTime Function() _now;

  GitHubSignInState _state = const GitHubSignInLoading();
  GitHubSignInState get state => _state;

  GitHubCredential? _credential;
  int _attempt = 0;
  bool _disposed = false;

  bool get signedIn => _state is GitHubSignedIn;
  bool get signingIn =>
      _state is GitHubRequestingCode ||
      _state is GitHubAwaitingUser ||
      _state is GitHubFinishing;

  void _set(GitHubSignInState next) {
    _state = next;
    if (!_disposed) notifyListeners();
  }

  /// Reads the stored sign-in.
  Future<void> load() async {
    GitHubCredential? stored;
    try {
      stored = await _store.read(profileId);
    } catch (_) {
      stored = null;
    }
    _credential = stored;
    if (signingIn) return;
    _set(
      stored == null ? const GitHubSignedOut() : GitHubSignedIn(stored.login),
    );
  }

  /// Starts device flow. Moves through [GitHubRequestingCode] and
  /// [GitHubAwaitingUser] to [GitHubSignedIn], or back to [GitHubSignedOut]
  /// with the problem. Completes when the attempt ends.
  Future<void> startSignIn() async {
    final attempt = ++_attempt;
    bool stale() => _disposed || attempt != _attempt;
    _set(const GitHubRequestingCode());
    try {
      final code = await _flow.requestCode();
      if (stale()) return;
      _set(
        GitHubAwaitingUser(
          userCode: code.userCode,
          verificationUri: code.verificationUri,
          expiresAt: code.expiresAt,
        ),
      );
      final token = await _flow.pollForToken(code, cancelled: stale);
      if (stale()) return;
      _set(const GitHubFinishing());
      final login = await _flow.fetchLogin(token.accessToken);
      final credential = token.withLogin(login);
      await _store.write(profileId, credential);
      if (stale()) return;
      _credential = credential;
      _set(GitHubSignedIn(login));
    } on GitHubSignInException catch (error) {
      if (stale() || error.problem == GitHubSignInProblem.cancelled) return;
      _set(GitHubSignedOut(problem: error.problem, details: error.details));
    } catch (_) {
      if (stale()) return;
      _set(const GitHubSignedOut(problem: GitHubSignInProblem.unexpected));
    }
  }

  /// Stops waiting for the person; nothing is stored.
  void cancelSignIn() {
    if (!signingIn) return;
    _attempt++;
    _set(
      _credential == null
          ? const GitHubSignedOut()
          : GitHubSignedIn(_credential!.login),
    );
  }

  /// Forgets the token on this device. Revoking the app on GitHub itself is
  /// at [gitHubAuthorizedAppsUrl].
  Future<void> signOut() async {
    _attempt++;
    _credential = null;
    try {
      await _store.delete(profileId);
    } finally {
      _set(const GitHubSignedOut());
    }
  }

  /// The token for one Git command, refreshed first when it has expired and
  /// can be refreshed; null when signed out.
  Future<String?> tokenForGit() async {
    var credential = _credential;
    if (credential == null) return null;
    final now = _now();
    if (credential.expiredAt(now)) {
      if (!credential.canRefreshAt(now)) {
        await _forget(GitHubSignInProblem.tokenRejected);
        return null;
      }
      try {
        credential = await _flow.refresh(credential);
        await _store.write(profileId, credential);
        _credential = credential;
      } on GitHubSignInException catch (error) {
        if (error.problem == GitHubSignInProblem.network) return null;
        await _forget(GitHubSignInProblem.tokenRejected);
        return null;
      }
    }
    return credential.accessToken;
  }

  /// The name and private email to put on saves, from the account (null when
  /// signed out or GitHub cannot be reached). The token stays in here.
  Future<GitHubProfile?> commitProfile() async {
    final token = await tokenForGit();
    if (token == null) return null;
    try {
      return await _flow.fetchProfile(token);
    } on GitHubSignInException {
      return null;
    }
  }

  /// GitHub refused the token during a push or pull: forget it and ask for
  /// a new sign-in (feasibility doc, exact flow step 4).
  Future<void> tokenRejected() => _forget(GitHubSignInProblem.tokenRejected);

  Future<void> _forget(GitHubSignInProblem problem) async {
    _credential = null;
    try {
      await _store.delete(profileId);
    } catch (_) {}
    _set(GitHubSignedOut(problem: problem));
  }

  @override
  void dispose() {
    _disposed = true;
    _attempt++;
    super.dispose();
  }
}
