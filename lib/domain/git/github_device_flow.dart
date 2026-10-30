/// GitHub sign-in by device flow (RFC 8628 as GitHub runs it).
///
/// The app asks GitHub for a short code, the person opens
/// github.com/login/device in a browser and types it, and the app polls until
/// GitHub hands over a token. Only the public client id is needed; there is
/// no client secret in the app
/// (docs/design/git-page-push-feasibility-2026-10-19.md §1).
library;

import 'dart:async';

import 'package:dio/dio.dart';

import '../../ui/kit/kit_redact.dart';

/// The app's GitHub OAuth App (device flow enabled). Public by design.
const gitHubClientId = 'Ov23liG1iMtZQSsgpXz4';

/// Push and pull to private repositories need `repo`.
const gitHubScope = 'repo';

const gitHubDeviceCodeUrl = 'https://github.com/login/device/code';
const gitHubAccessTokenUrl = 'https://github.com/login/oauth/access_token';
const gitHubUserUrl = 'https://api.github.com/user';

/// Where people review or revoke the app's access.
const gitHubAuthorizedAppsUrl =
    'https://github.com/settings/connections/applications/$gitHubClientId';

/// What GitHub answered to "give me a code".
class GitHubDeviceCode {
  const GitHubDeviceCode({
    required this.deviceCode,
    required this.userCode,
    required this.verificationUri,
    required this.expiresAt,
    required this.interval,
  });

  /// Secret to the app: only ever sent back to GitHub.
  final String deviceCode;

  /// What the person types (`WDJB-MJHT`).
  final String userCode;

  /// Where they type it (`https://github.com/login/device`).
  final Uri verificationUri;
  final DateTime expiresAt;

  /// How long to wait between polls.
  final Duration interval;

  @override
  String toString() => 'GitHubDeviceCode($userCode)';
}

/// A token GitHub issued, with the account it belongs to once known.
/// Who a GitHub sign-in belongs to.
class GitHubProfile {
  const GitHubProfile({required this.login, this.id, this.name});

  final String login;
  final int? id;
  final String? name;

  /// The name a commit made by this person carries.
  String get commitName => name ?? login;

  /// GitHub's private address for the account (id, a plus, the login, then
  /// users.noreply.github.com): a push keeps the real email out of history.
  String? get commitEmail =>
      id == null ? null : '$id+$login@users.noreply.github.com';
}

class GitHubCredential {
  const GitHubCredential({
    required this.accessToken,
    this.login = '',
    this.scope = '',
    this.refreshToken,
    this.expiresAt,
    this.refreshExpiresAt,
  });

  final String accessToken;

  /// The GitHub account name (`octocat`).
  final String login;
  final String scope;

  /// Present only when the OAuth App issues expiring tokens.
  final String? refreshToken;
  final DateTime? expiresAt;
  final DateTime? refreshExpiresAt;

  bool expiredAt(DateTime now) =>
      expiresAt != null && !now.isBefore(expiresAt!.subtract(_skew));

  bool canRefreshAt(DateTime now) =>
      refreshToken != null &&
      (refreshExpiresAt == null || now.isBefore(refreshExpiresAt!));

  static const _skew = Duration(minutes: 2);

  GitHubCredential withLogin(String value) => GitHubCredential(
    accessToken: accessToken,
    login: value,
    scope: scope,
    refreshToken: refreshToken,
    expiresAt: expiresAt,
    refreshExpiresAt: refreshExpiresAt,
  );

  Map<String, Object?> toJson() => {
    'accessToken': accessToken,
    'login': login,
    'scope': scope,
    if (refreshToken != null) 'refreshToken': refreshToken,
    if (expiresAt != null) 'expiresAt': expiresAt!.toUtc().toIso8601String(),
    if (refreshExpiresAt != null)
      'refreshExpiresAt': refreshExpiresAt!.toUtc().toIso8601String(),
  };

  static GitHubCredential? fromJson(Object? json) {
    if (json is! Map) return null;
    final token = json['accessToken'];
    if (token is! String || token.isEmpty) return null;
    DateTime? time(Object? v) => v is String ? DateTime.tryParse(v) : null;
    final refresh = json['refreshToken'];
    return GitHubCredential(
      accessToken: token,
      login: json['login'] is String ? json['login'] as String : '',
      scope: json['scope'] is String ? json['scope'] as String : '',
      refreshToken: refresh is String && refresh.isNotEmpty ? refresh : null,
      expiresAt: time(json['expiresAt']),
      refreshExpiresAt: time(json['refreshExpiresAt']),
    );
  }

  /// Never prints the tokens.
  @override
  String toString() => 'GitHubCredential(login: $login)';
}

enum GitHubSignInProblem {
  /// The code ran out before the person entered it.
  expired,

  /// The person pressed Cancel on GitHub's page.
  denied,

  /// The OAuth App does not have device flow switched on.
  deviceFlowDisabled,

  /// GitHub could not be reached.
  network,

  /// GitHub refused the stored token (signed out on GitHub, revoked).
  tokenRejected,

  /// GitHub answered something the app does not understand.
  unexpected,

  /// The app stopped waiting (the person cancelled in the app).
  cancelled,
}

class GitHubSignInException implements Exception {
  const GitHubSignInException(this.problem, {this.details = ''});

  final GitHubSignInProblem problem;

  /// GitHub's `error`/`error_description`, for Details only. Never a token.
  final String details;

  @override
  String toString() => 'GitHubSignInException(${problem.name})';
}

/// The HTTP side of device flow. Every network call goes through the
/// injected [Dio], and every wait through [sleep], so tests run instantly.
class GitHubDeviceFlow {
  GitHubDeviceFlow({
    Dio? dio,
    Future<void> Function(Duration)? sleep,
    DateTime Function()? now,
    this.clientId = gitHubClientId,
  }) : _dio = dio ?? Dio(),
       _sleep = sleep ?? Future<void>.delayed,
       _now = now ?? DateTime.now;

  final Dio _dio;
  final Future<void> Function(Duration) _sleep;
  final DateTime Function() _now;
  final String clientId;

  static const _timeout = Duration(seconds: 20);

  /// How many network failures in a row end the wait.
  static const maxPollFailures = 3;

  Options get _form => Options(
    contentType: Headers.formUrlEncodedContentType,
    headers: {'Accept': 'application/json'},
    responseType: ResponseType.json,
    validateStatus: (_) => true,
    sendTimeout: _timeout,
    receiveTimeout: _timeout,
  );

  Future<GitHubDeviceCode> requestCode({String scope = gitHubScope}) async {
    final body = await _post(gitHubDeviceCodeUrl, {
      'client_id': clientId,
      'scope': scope,
    });
    final error = body['error'];
    if (error is String) {
      throw GitHubSignInException(
        error == 'device_flow_disabled'
            ? GitHubSignInProblem.deviceFlowDisabled
            : GitHubSignInProblem.unexpected,
        details: _errorDetails(body),
      );
    }
    final deviceCode = body['device_code'];
    final userCode = body['user_code'];
    final uri = Uri.tryParse('${body['verification_uri'] ?? ''}');
    if (deviceCode is! String ||
        userCode is! String ||
        uri == null ||
        uri.scheme != 'https') {
      throw const GitHubSignInException(GitHubSignInProblem.unexpected);
    }
    KitRedact.registerKnownSecret(deviceCode);
    final expiresIn = _int(body['expires_in']) ?? 900;
    final interval = _int(body['interval']) ?? 5;
    return GitHubDeviceCode(
      deviceCode: deviceCode,
      userCode: userCode,
      verificationUri: uri,
      expiresAt: _now().add(Duration(seconds: expiresIn)),
      interval: Duration(seconds: interval.clamp(1, 60)),
    );
  }

  /// Polls until the person finishes on GitHub. Honors `slow_down` (+5 s, or
  /// the interval GitHub names), ends on `expired_token`/`access_denied`, and
  /// gives up at the code's expiry. [cancelled] is checked before each poll.
  Future<GitHubCredential> pollForToken(
    GitHubDeviceCode code, {
    bool Function()? cancelled,
  }) async {
    var interval = code.interval;
    var failures = 0;
    while (true) {
      await _sleep(interval);
      if (cancelled?.call() ?? false) {
        throw const GitHubSignInException(GitHubSignInProblem.cancelled);
      }
      if (!_now().isBefore(code.expiresAt)) {
        throw const GitHubSignInException(GitHubSignInProblem.expired);
      }
      final Map<String, Object?> body;
      try {
        body = await _post(gitHubAccessTokenUrl, {
          'client_id': clientId,
          'device_code': code.deviceCode,
          'grant_type': 'urn:ietf:params:oauth:grant-type:device_code',
        });
        failures = 0;
      } on GitHubSignInException catch (error) {
        if (error.problem != GitHubSignInProblem.network) rethrow;
        if (++failures >= maxPollFailures) rethrow;
        continue;
      }
      if (cancelled?.call() ?? false) {
        throw const GitHubSignInException(GitHubSignInProblem.cancelled);
      }
      final error = body['error'];
      switch (error) {
        case null:
          return _credential(body);
        case 'authorization_pending':
          continue;
        case 'slow_down':
          final named = _int(body['interval']);
          interval = named != null
              ? Duration(seconds: named)
              : interval + const Duration(seconds: 5);
          continue;
        case 'expired_token':
          throw GitHubSignInException(
            GitHubSignInProblem.expired,
            details: _errorDetails(body),
          );
        case 'access_denied':
          throw GitHubSignInException(
            GitHubSignInProblem.denied,
            details: _errorDetails(body),
          );
        case 'device_flow_disabled':
          throw GitHubSignInException(
            GitHubSignInProblem.deviceFlowDisabled,
            details: _errorDetails(body),
          );
        default:
          throw GitHubSignInException(
            GitHubSignInProblem.unexpected,
            details: _errorDetails(body),
          );
      }
    }
  }

  /// Exchanges a refresh token for a new pair (expiring-token apps only).
  Future<GitHubCredential> refresh(GitHubCredential current) async {
    final refreshToken = current.refreshToken;
    if (refreshToken == null) {
      throw const GitHubSignInException(GitHubSignInProblem.tokenRejected);
    }
    final body = await _post(gitHubAccessTokenUrl, {
      'client_id': clientId,
      'grant_type': 'refresh_token',
      'refresh_token': refreshToken,
    });
    if (body['error'] != null) {
      throw GitHubSignInException(
        GitHubSignInProblem.tokenRejected,
        details: _errorDetails(body),
      );
    }
    return _credential(body).withLogin(current.login);
  }

  /// The account name the token belongs to (`GET /user`).
  Future<String> fetchLogin(String token) async =>
      (await fetchProfile(token)).login;

  /// The account's login, numeric id and display name (`GET /user`).
  Future<GitHubProfile> fetchProfile(String token) async {
    final Response<Object?> response;
    try {
      response = await _dio.get<Object?>(
        gitHubUserUrl,
        options: Options(
          headers: {
            'Accept': 'application/vnd.github+json',
            'Authorization': 'Bearer $token',
            'X-GitHub-Api-Version': '2022-11-28',
          },
          responseType: ResponseType.json,
          validateStatus: (_) => true,
          receiveTimeout: _timeout,
        ),
      );
    } on DioException {
      throw const GitHubSignInException(GitHubSignInProblem.network);
    }
    if (response.statusCode == 401) {
      throw const GitHubSignInException(GitHubSignInProblem.tokenRejected);
    }
    final data = response.data;
    final login = data is Map ? data['login'] : null;
    if (response.statusCode != 200 || login is! String || login.isEmpty) {
      throw GitHubSignInException(
        GitHubSignInProblem.unexpected,
        details: 'GET /user answered ${response.statusCode}',
      );
    }
    final id = (data as Map)['id'];
    final name = data['name'];
    return GitHubProfile(
      login: login,
      id: id is int ? id : null,
      name: name is String && name.trim().isNotEmpty ? name.trim() : null,
    );
  }

  GitHubCredential _credential(Map<String, Object?> body) {
    final token = body['access_token'];
    if (token is! String || token.isEmpty) {
      throw const GitHubSignInException(GitHubSignInProblem.unexpected);
    }
    KitRedact.registerKnownSecret(token);
    final refresh = body['refresh_token'];
    if (refresh is String) KitRedact.registerKnownSecret(refresh);
    final now = _now();
    final expiresIn = _int(body['expires_in']);
    final refreshIn = _int(body['refresh_token_expires_in']);
    return GitHubCredential(
      accessToken: token,
      scope: body['scope'] is String ? body['scope'] as String : '',
      refreshToken: refresh is String && refresh.isNotEmpty ? refresh : null,
      expiresAt: expiresIn == null
          ? null
          : now.add(Duration(seconds: expiresIn)),
      refreshExpiresAt: refreshIn == null
          ? null
          : now.add(Duration(seconds: refreshIn)),
    );
  }

  Future<Map<String, Object?>> _post(
    String url,
    Map<String, String> fields,
  ) async {
    final Response<Object?> response;
    try {
      response = await _dio.post<Object?>(url, data: fields, options: _form);
    } on DioException {
      throw const GitHubSignInException(GitHubSignInProblem.network);
    }
    final data = response.data;
    if (data is Map) {
      KitRedact.registerCredentialValues(data);
      return data.cast<String, Object?>();
    }
    final status = response.statusCode ?? 0;
    throw GitHubSignInException(
      status >= 500 || status == 0
          ? GitHubSignInProblem.network
          : GitHubSignInProblem.unexpected,
      details: 'GitHub answered $status',
    );
  }

  static int? _int(Object? value) => switch (value) {
    final int v => v,
    final num v => v.toInt(),
    final String v => int.tryParse(v),
    _ => null,
  };

  static String _errorDetails(Map<String, Object?> body) {
    final error = body['error'];
    final description = body['error_description'];
    return [
      if (error is String) error,
      if (description is String) description,
    ].join(': ');
  }
}
