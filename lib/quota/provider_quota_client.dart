import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../domain/provider_quota.dart';
import '../state/profiles.dart';

/// A separately installed, explicitly trusted deployment extension. This is
/// not an OpenCode API client and must only be acquired after screen consent.
///
/// Copies the profile's credential/origin values at construction. Neither later
/// profile edits nor a shared OpenCode Dio can change this request's target.
/// `adapter` is an ownership-transferring test seam, not a shared Dio seam:
/// cookies, interceptors, default credentials, and logging are never inherited.
class HttpProviderQuotaGateway implements ProviderQuotaGateway {
  static const maxResponseBytes = 64 * 1024;
  static const requestTimeout = Duration(seconds: 10);

  final String _baseUrl;
  final QuotaProvider provider;
  final String _username;
  final String _password;
  final bool _requiresPasswordReentry;
  final Dio _dio;
  final _requests = <CancelToken>{};
  bool _closed = false;

  HttpProviderQuotaGateway(
    ServerProfile profile, {
    HttpClientAdapter? adapter,
    this.provider = QuotaProvider.codex,
  }) : _baseUrl = profile.baseUrl,
       _username = profile.username,
       _password = profile.password,
       _requiresPasswordReentry = profile.requiresPasswordReentry,
       _dio = Dio(
         BaseOptions(
           connectTimeout: requestTimeout,
           sendTimeout: requestTimeout,
           receiveTimeout: requestTimeout,
           responseType: ResponseType.stream,
           followRedirects: false,
           maxRedirects: 0,
           validateStatus: (status) => status == 200,
           receiveDataWhenStatusError: false,
           headers: {'Accept': 'application/json'},
         ),
       ) {
    _dio.httpClientAdapter = _BoundedQuotaAdapter(
      adapter ?? _dio.httpClientAdapter,
    );
  }

  /// Setup eligibility only, NOT permission to send a request. Even loopback
  /// needs a nonempty password; unreadable secure storage never means anonymous.
  static bool canReadProfile(ServerProfile profile) => _validProfile(
    profile.baseUrl,
    profile.username,
    profile.password,
    profile.requiresPasswordReentry,
  );

  static bool _validProfile(
    String baseUrl,
    String username,
    String password,
    bool requiresPasswordReentry,
  ) {
    try {
      if (requiresPasswordReentry ||
          password.isEmpty ||
          baseUrl.length > 2048) {
        return false;
      }
      // RFC 7617 cannot represent a colon in the user-id. Do not silently
      // authenticate as a different principal by accepting one.
      if (username.contains(':') ||
          RegExp(r'[\x00-\x1f\x7f]').hasMatch(username) ||
          RegExp(r'[\x00-\x1f\x7f]').hasMatch(password)) {
        return false;
      }
      // Uri removes an empty userinfo delimiter: https://@host becomes
      // https://host, with both userInfo and authority losing the evidence.
      // Reject credentials in the raw authority before that normalization.
      final rawAuthority = RegExp(
        r'^https?://([^/?#]*)',
        caseSensitive: false,
      ).firstMatch(baseUrl.trim())?.group(1);
      if (rawAuthority == null || rawAuthority.contains('@')) return false;
      if (validateServerProfileUrl(
            baseUrl,
            username: username,
            password: password,
          ) !=
          null) {
        return false;
      }
      final uri = Uri.parse(baseUrl.trim());
      return !uri.hasQuery &&
          !uri.hasFragment &&
          !uri.authority.contains('@') &&
          uri.port > 0 &&
          uri.port <= 65535;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<ProviderQuotaSnapshot> readSnapshot() async {
    final cancel = CancelToken();
    try {
      if (_closed ||
          !_validProfile(
            _baseUrl,
            _username,
            _password,
            _requiresPasswordReentry,
          )) {
        throw const ProviderQuotaFailure(QuotaFailureKind.unavailable);
      }
      _requests.add(cancel);
      // A total deadline also bounds a peer that keeps dripping body bytes.
      // Cancellation must settle the caller even if an adapter stalls.
      return await Future.any<ProviderQuotaSnapshot>([
        _read(cancel),
        cancel.whenCancel.then<ProviderQuotaSnapshot>(
          (_) => throw const ProviderQuotaFailure(QuotaFailureKind.unavailable),
        ),
      ]).timeout(requestTimeout);
    } catch (error) {
      // Never retain a DioException: requestOptions contains Basic credentials,
      // and a network/JSON error may quote the URL or raw provider response.
      throw _safeFailure(error);
    } finally {
      _requests.remove(cancel);
      cancel.cancel();
    }
  }

  Future<ProviderQuotaSnapshot> _read(CancelToken cancel) async {
    final uri = Uri.parse(
      _baseUrl.trim(),
    ).replace(path: quotaPathFor(provider));
    final response = await _dio.getUri<ResponseBody>(
      uri,
      options: Options(
        headers: {
          'Authorization':
              'Basic ${base64Encode(utf8.encode('$_username:$_password'))}',
        },
      ),
      cancelToken: cancel,
    );
    final body = response.data;
    if (body == null || response.isRedirect || response.redirects.isNotEmpty) {
      throw const ProviderQuotaFailure(QuotaFailureKind.invalidResponse);
    }
    final bytes = BytesBuilder(copy: false);
    await for (final chunk in body.stream) {
      // The adapter bounds the stream BEFORE Dio can queue any response bytes.
      // Keep a defensive bound at the aggregation point as well.
      if (bytes.length + chunk.length > maxResponseBytes) {
        throw const _QuotaBodyTooLarge();
      }
      bytes.add(chunk);
    }
    try {
      final snapshot = ProviderQuotaSnapshot.fromJson(
        jsonDecode(utf8.decode(bytes.takeBytes())),
      );
      if (snapshot.provider != provider) {
        throw const ProviderQuotaFailure(QuotaFailureKind.invalidResponse);
      }
      return snapshot;
    } catch (_) {
      throw const ProviderQuotaFailure(QuotaFailureKind.invalidResponse);
    }
  }

  static ProviderQuotaFailure _safeFailure(Object error) {
    if (error is ProviderQuotaFailure) {
      return ProviderQuotaFailure(error.kind);
    }
    if (error is FormatException || error is _QuotaBodyTooLarge) {
      return const ProviderQuotaFailure(QuotaFailureKind.invalidResponse);
    }
    if (error is DioException) {
      if (error.error is _QuotaBodyTooLarge || error.error is FormatException) {
        return const ProviderQuotaFailure(QuotaFailureKind.invalidResponse);
      }
      final status = error.response?.statusCode;
      if (status == 401 || status == 403) {
        return const ProviderQuotaFailure(QuotaFailureKind.collectorAuth);
      }
      if (status == 404 || status == 405) {
        return const ProviderQuotaFailure(QuotaFailureKind.unsupported);
      }
      if (status != null && status < 500 && status != 408 && status != 429) {
        return const ProviderQuotaFailure(QuotaFailureKind.invalidResponse);
      }
    }
    return const ProviderQuotaFailure(QuotaFailureKind.unavailable);
  }

  @override
  void close() {
    if (_closed) return;
    _closed = true;
    for (final cancel in _requests.toList()) {
      cancel.cancel();
    }
    _requests.clear();
    try {
      _dio.close(force: true);
    } catch (_) {
      // A faulty adapter must not leak a raw exception during scope retirement.
    }
  }

  @override
  String toString() => 'HttpProviderQuotaGateway';
}

class _QuotaBodyTooLarge implements Exception {
  const _QuotaBodyTooLarge();
}

/// Dio's response stream handler eagerly subscribes to the adapter's stream.
/// Capping only the final BytesBuilder would leave an unbounded queue upstream.
class _BoundedQuotaAdapter implements HttpClientAdapter {
  final HttpClientAdapter _inner;
  _BoundedQuotaAdapter(this._inner);

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final response = await _inner.fetch(options, requestStream, cancelFuture);
    response.stream = _bounded(response.stream);
    return response;
  }

  Stream<Uint8List> _bounded(Stream<Uint8List> source) async* {
    var length = 0;
    await for (final chunk in source) {
      length += chunk.length;
      if (length > HttpProviderQuotaGateway.maxResponseBytes) {
        throw const _QuotaBodyTooLarge();
      }
      yield chunk;
    }
  }

  @override
  void close({bool force = false}) => _inner.close(force: force);
}
