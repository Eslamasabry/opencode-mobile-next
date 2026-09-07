import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/quota/provider_quota_client.dart';
import 'package:opencode_mobile/state/profiles.dart';

import 'provider_quota_test.dart' show providerQuotaFixture;

const _password = 'fixture-only-basic-password';

ServerProfile _profile({String url = 'https://collector.example:8443/'}) =>
    ServerProfile(
      id: 'profile-a',
      name: 'Fixture',
      baseUrl: url,
      username: 'fixture-user',
      password: _password,
    );

class _Adapter implements HttpClientAdapter {
  _Adapter(this.handler);

  final FutureOr<ResponseBody> Function(RequestOptions) handler;
  final requests = <RequestOptions>[];
  int closes = 0;
  int cancellations = 0;
  bool forceClosed = false;
  bool requestHadBody = false;
  final called = Completer<void>();
  final cancellationObserved = Completer<void>();

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    requestHadBody = requestStream != null;
    cancelFuture?.then((_) {
      cancellations++;
      if (!cancellationObserved.isCompleted) cancellationObserved.complete();
    });
    if (!called.isCompleted) called.complete();
    return await handler(options);
  }

  @override
  void close({bool force = false}) {
    closes++;
    forceClosed = force;
  }
}

ResponseBody _json({int status = 200, Object? value}) =>
    ResponseBody.fromString(
      jsonEncode(value ?? providerQuotaFixture()),
      status,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );

Matcher _failure(QuotaFailureKind kind) => throwsA(
  isA<ProviderQuotaFailure>().having((failure) => failure.kind, 'kind', kind),
);

void main() {
  test(
    'Claude collection is unavailable before any authenticated request',
    () async {
      final adapter = _Adapter(
        (_) =>
            _json(value: providerQuotaFixture(provider: QuotaProvider.claude)),
      );
      final gateway = HttpProviderQuotaGateway(
        _profile(),
        provider: QuotaProvider.claude,
        adapter: adapter,
      );
      addTearDown(gateway.close);
      await expectLater(
        gateway.readSnapshot(),
        _failure(QuotaFailureKind.unsupported),
      );
      expect(adapter.requests, isEmpty);
    },
  );

  test(
    'one fixed same-origin GET, header-only Basic, dedicated options',
    () async {
      final adapter = _Adapter((_) => _json());
      final profile = _profile();
      final gateway = HttpProviderQuotaGateway(profile, adapter: adapter);
      addTearDown(gateway.close);
      final snapshot = await gateway.readSnapshot();
      expect(snapshot.status, ProviderQuotaStatus.ok);
      expect(adapter.requests.length, 1);
      final request = adapter.requests.single;
      expect(
        request.uri,
        Uri.parse('https://collector.example:8443/ocmn/quota/v1'),
      );
      expect(request.method, 'GET');
      expect(request.uri.hasQuery, isFalse);
      expect(request.uri.hasFragment, isFalse);
      expect(request.uri.userInfo.isEmpty, isTrue);
      expect(
        request.headers['Authorization'] ==
            'Basic ${base64Encode(utf8.encode('fixture-user:$_password'))}',
        isTrue,
      );
      expect(request.headers.containsKey('Cookie'), isFalse);
      expect(request.headers['Accept'], 'application/json');
      expect(adapter.requestHadBody, isFalse);
      expect(request.data, isNull);
      expect(request.followRedirects, isFalse);
      expect(request.maxRedirects, 0);
      expect(request.responseType, ResponseType.stream);
      expect(request.connectTimeout, const Duration(seconds: 10));
      expect(request.sendTimeout, const Duration(seconds: 10));
      expect(request.receiveTimeout, const Duration(seconds: 10));
      expect(gateway.toString(), 'HttpProviderQuotaGateway');
    },
  );

  test(
    'snapshots profile values rather than following mutable credentials/URL',
    () async {
      final adapter = _Adapter((_) => _json());
      final profile = _profile();
      final gateway = HttpProviderQuotaGateway(profile, adapter: adapter);
      addTearDown(gateway.close);
      profile
        ..baseUrl = 'https://other.example'
        ..username = 'changed-user'
        ..password = 'fixture-changed-password';
      await gateway.readSnapshot();
      expect(adapter.requests.single.uri.host, 'collector.example');
      expect(
        adapter.requests.single.headers['Authorization'] ==
            'Basic ${base64Encode(utf8.encode('fixture-user:$_password'))}',
        isTrue,
      );
    },
  );

  test('rejects unsafe origins before the adapter is called', () async {
    for (final (index, url) in [
      '',
      'collector.example',
      'http://remote.example',
      'http://192.0.2.10:4097',
      'http://localhost.attacker.example',
      'http://[::ffff:127.0.0.1]',
      'ftp://collector.example',
      'https://fixture-user:fixture-password@collector.example',
      'https://@collector.example',
      'https://collector.example?auth_token=fixture-only',
      'https://collector.example?',
      'https://collector.example#fixture-only',
      'https://collector.example#',
      'https://collector.example/api',
      'https://collector.example/ocmn/quota/v1',
      'https://collector.example:0',
      'https://collector.example:65536',
      'https://${'a' * 2048}.example',
    ].indexed) {
      final adapter = _Adapter((_) => _json());
      final gateway = HttpProviderQuotaGateway(
        _profile(url: url),
        adapter: adapter,
      );
      addTearDown(gateway.close);
      await expectLater(
        gateway.readSnapshot(),
        _failure(QuotaFailureKind.unavailable),
        reason: 'unsafe origin case $index',
      );
      expect(adapter.requests.length, 0);
      gateway.close();
    }
  });

  test('rejects raw empty userinfo even when Uri removes it', () async {
    const raw = 'https://@collector.example';
    expect(Uri.parse(raw).userInfo, isEmpty);
    expect(Uri.parse(raw).authority, 'collector.example');
    for (final url in [
      raw,
      '  HTTPS://@collector.example:8443/  ',
      'http://@localhost:4097',
      'http://@[::1]:4097/',
    ]) {
      final profile = _profile(url: url);
      expect(HttpProviderQuotaGateway.canReadProfile(profile), isFalse);
      final adapter = _Adapter((_) => _json());
      final gateway = HttpProviderQuotaGateway(profile, adapter: adapter);
      addTearDown(gateway.close);
      await expectLater(
        gateway.readSnapshot(),
        _failure(QuotaFailureKind.unavailable),
      );
      expect(adapter.requests.length, 0);
    }
  });

  test(
    'no empty-password, unreadable-keyring, or ambiguous Basic fallback',
    () async {
      for (final profile in [
        _profile()..password = '',
        _profile(url: 'http://127.0.0.1')..password = '',
        _profile()..requiresPasswordReentry = true,
        _profile()..username = 'ambiguous:user',
        _profile()..username = 'fixture\nuser',
        _profile()..password = 'fixture\npassword',
      ]) {
        final adapter = _Adapter((_) => _json());
        final gateway = HttpProviderQuotaGateway(profile, adapter: adapter);
        await expectLater(
          gateway.readSnapshot(),
          _failure(QuotaFailureKind.unavailable),
        );
        expect(adapter.requests.length, 0);
        gateway.close();
      }
    },
  );

  test(
    'approved loopback forms still send authentication and only the fixed path',
    () async {
      for (final url in [
        'http://localhost:4097',
        'http://127.0.0.1:4097/',
        'http://[::1]:4097',
      ]) {
        final adapter = _Adapter((_) => _json());
        final gateway = HttpProviderQuotaGateway(
          _profile(url: url),
          adapter: adapter,
        );
        await gateway.readSnapshot();
        expect(adapter.requests.single.uri.path, providerQuotaPath);
        expect(
          adapter.requests.single.headers.containsKey('Authorization'),
          isTrue,
        );
        expect(adapter.requests.single.followRedirects, isFalse);
        gateway.close();
      }
    },
  );

  test(
    'empty user is preserved, not silently substituted with another principal',
    () async {
      final adapter = _Adapter((_) => _json());
      final gateway = HttpProviderQuotaGateway(
        _profile()..username = '',
        adapter: adapter,
      );
      addTearDown(gateway.close);
      await gateway.readSnapshot();
      expect(
        adapter.requests.single.headers['Authorization'] ==
            'Basic ${base64Encode(utf8.encode(':$_password'))}',
        isTrue,
      );
    },
  );

  test(
    'HTTP failures are status-only, never follow redirects or probe fallbacks',
    () async {
      const statuses = {
        204: QuotaFailureKind.invalidResponse,
        301: QuotaFailureKind.invalidResponse,
        302: QuotaFailureKind.invalidResponse,
        307: QuotaFailureKind.invalidResponse,
        308: QuotaFailureKind.invalidResponse,
        400: QuotaFailureKind.invalidResponse,
        401: QuotaFailureKind.collectorAuth,
        403: QuotaFailureKind.collectorAuth,
        404: QuotaFailureKind.unsupported,
        405: QuotaFailureKind.unsupported,
        408: QuotaFailureKind.unavailable,
        429: QuotaFailureKind.unavailable,
        500: QuotaFailureKind.unavailable,
        502: QuotaFailureKind.unavailable,
        503: QuotaFailureKind.unavailable,
      };
      for (final entry in statuses.entries) {
        var bodyListened = false;
        final adapter = _Adapter(
          (_) => ResponseBody(
            Stream<Uint8List>.multi((stream) {
              bodyListened = true;
              stream.add(
                Uint8List.fromList(utf8.encode('fixture-private-response')),
              );
              stream.close();
            }),
            entry.key,
            headers: {
              'location': ['https://untrusted.example/collect'],
            },
          ),
        );
        final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
        await expectLater(gateway.readSnapshot(), _failure(entry.value));
        expect(adapter.requests.length, 1);
        expect(bodyListened, isFalse);
        gateway.close();
      }
    },
  );

  test('unexpected redirect metadata is also refused', () async {
    final adapter = _Adapter(
      (_) => ResponseBody.fromString(
        jsonEncode(providerQuotaFixture()),
        200,
        isRedirect: true,
      ),
    );
    final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
    addTearDown(gateway.close);
    await expectLater(
      gateway.readSnapshot(),
      _failure(QuotaFailureKind.invalidResponse),
    );
    expect(adapter.requests.length, 1);
  });

  test(
    'JSON, UTF-8, schema and account mismatch errors become safe failures',
    () async {
      final mismatch = providerQuotaFixture();
      (mismatch['account'] as Map)['status'] = 'mismatch';
      for (final body in <List<int>>[
        [],
        utf8.encode('{"private":"fixture-only",'),
        utf8.encode('<html>proxy login</html>'),
        [0xff, 0xfe],
        utf8.encode('null'),
        utf8.encode(jsonEncode(providerQuotaFixture()..['schemaVersion'] = 2)),
        utf8.encode(jsonEncode(mismatch)),
      ]) {
        final adapter = _Adapter((_) => ResponseBody.fromBytes(body, 200));
        final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
        await expectLater(
          gateway.readSnapshot(),
          _failure(QuotaFailureKind.invalidResponse),
        );
        gateway.close();
      }
    },
  );

  test(
    'accepts exactly 64 KiB while a single oversized chunk is refused',
    () async {
      final json = jsonEncode(providerQuotaFixture());
      for (final extra in [0, 1]) {
        final text =
            '$json${' ' * (HttpProviderQuotaGateway.maxResponseBytes - utf8.encode(json).length + extra)}';
        final adapter = _Adapter((_) => ResponseBody.fromString(text, 200));
        final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
        if (extra == 0) {
          expect((await gateway.readSnapshot()).windows.length, 2);
        } else {
          await expectLater(
            gateway.readSnapshot(),
            _failure(QuotaFailureKind.invalidResponse),
          );
        }
        gateway.close();
      }
    },
  );

  test(
    'caps streaming before Dio buffering and stops an oversized source early',
    () async {
      var produced = 0;
      var stopped = false;
      Stream<Uint8List> source() async* {
        try {
          for (var i = 0; i < 1024; i++) {
            produced++;
            yield Uint8List(1024);
          }
        } finally {
          stopped = true;
        }
      }

      final adapter = _Adapter(
        (_) => ResponseBody(
          source(),
          200,
          // An untrusted Content-Length does not bypass the actual byte ceiling.
          headers: {
            Headers.contentLengthHeader: ['1'],
          },
        ),
      );
      final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
      addTearDown(gateway.close);
      await expectLater(
        gateway.readSnapshot(),
        _failure(QuotaFailureKind.invalidResponse),
      );
      expect(produced, 65);
      expect(stopped, isTrue);
    },
  );

  test(
    'transport exceptions never expose raw errors, URL, or credentials',
    () async {
      for (final type in [
        DioExceptionType.connectionTimeout,
        DioExceptionType.receiveTimeout,
        DioExceptionType.sendTimeout,
        DioExceptionType.connectionError,
        DioExceptionType.badCertificate,
        DioExceptionType.unknown,
      ]) {
        final adapter = _Adapter(
          (request) => throw DioException(
            requestOptions: request,
            type: type,
            message: 'fixture-private-response $_password',
            error: StateError('fixture-private-response $_password'),
          ),
        );
        final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
        try {
          await gateway.readSnapshot();
          fail('Expected a typed quota failure');
        } on ProviderQuotaFailure catch (error) {
          expect(error.kind, QuotaFailureKind.unavailable);
          expect(error.toString(), 'ProviderQuotaFailure(unavailable)');
        }
        gateway.close();
      }
      final adapter = _Adapter(
        (_) => throw StateError('fixture-private-response'),
      );
      final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
      addTearDown(gateway.close);
      await expectLater(
        gateway.readSnapshot(),
        _failure(QuotaFailureKind.unavailable),
      );
    },
  );

  test('stream errors are sanitized too', () async {
    final adapter = _Adapter(
      (_) => ResponseBody(
        Stream<Uint8List>.error(StateError('fixture-private-response')),
        200,
      ),
    );
    final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
    addTearDown(gateway.close);
    await expectLater(
      gateway.readSnapshot(),
      _failure(QuotaFailureKind.unavailable),
    );
  });

  test(
    'close is idempotent and a closed gateway never calls an adapter',
    () async {
      final adapter = _Adapter((_) => _json());
      final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
      gateway.close();
      gateway.close();
      await expectLater(
        gateway.readSnapshot(),
        _failure(QuotaFailureKind.unavailable),
      );
      expect(adapter.requests.length, 0);
      expect(adapter.closes, 1);
      expect(adapter.forceClosed, isTrue);
    },
  );

  test('close settles a read whose headers have not arrived', () async {
    final headers = Completer<ResponseBody>();
    final adapter = _Adapter((_) => headers.future);
    final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
    final pending = expectLater(
      gateway.readSnapshot(),
      _failure(QuotaFailureKind.unavailable),
    );
    await adapter.called.future;
    gateway.close();
    // Cancellation itself is synchronous. The adapter's whenCancel observer
    // is a separate async branch from the public read's cancellation future;
    // neither branch is required to complete before the other one.
    expect(adapter.requests.single.cancelToken?.isCancelled, isTrue);
    await adapter.cancellationObserved.future;
    await pending;
    expect(adapter.cancellations, 1);
    expect(adapter.closes, 1);
    headers.completeError(StateError('fixture-late-transport-error'));
  });

  test(
    'close cancels a partially received body, not just the request future',
    () async {
      final listening = Completer<void>();
      final cancelled = Completer<void>();
      final body = StreamController<Uint8List>(
        onListen: () => listening.complete(),
        onCancel: () => cancelled.complete(),
      );
      final adapter = _Adapter((_) => ResponseBody(body.stream, 200));
      final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
      final pending = expectLater(
        gateway.readSnapshot(),
        _failure(QuotaFailureKind.unavailable),
      );
      await listening.future;
      body.add(Uint8List.fromList(utf8.encode('{')));
      gateway.close();
      await pending;
      await cancelled.future;
      await body.close();
      expect(adapter.closes, 1);
    },
  );

  testWidgets(
    'total deadline covers stalled headers without wall-clock waiting',
    (tester) async {
      final headers = Completer<ResponseBody>();
      final adapter = _Adapter((_) => headers.future);
      final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
      ProviderQuotaFailure? failure;
      final pending = gateway.readSnapshot().then<void>(
        (_) => fail('The stalled read must not succeed'),
        onError: (Object error) {
          failure = error as ProviderQuotaFailure;
        },
      );
      await tester.pump(Duration.zero);
      await tester.pump(const Duration(seconds: 9));
      expect(failure, isNull);
      await tester.pump(const Duration(seconds: 1));
      await pending;
      expect(failure?.kind, QuotaFailureKind.unavailable);
      expect(adapter.cancellations, 1);
      gateway.close();
      headers.complete(_json());
      await tester.pump(Duration.zero);
    },
  );

  testWidgets('total body deadline defeats slow-dripping responses', (
    tester,
  ) async {
    final body = StreamController<Uint8List>();
    final adapter = _Adapter((_) => ResponseBody(body.stream, 200));
    final gateway = HttpProviderQuotaGateway(_profile(), adapter: adapter);
    ProviderQuotaFailure? failure;
    final pending = gateway.readSnapshot().then<void>(
      (_) => fail('The incomplete read must not succeed'),
      onError: (Object error) {
        failure = error as ProviderQuotaFailure;
      },
    );
    await tester.pump(Duration.zero);
    for (var second = 0; second < 9; second++) {
      body.add(Uint8List.fromList([32]));
      await tester.pump(const Duration(seconds: 1));
    }
    expect(failure, isNull);
    await tester.pump(const Duration(seconds: 1));
    await pending;
    expect(failure?.kind, QuotaFailureKind.unavailable);
    gateway.close();
    await body.close();
    await tester.pump(Duration.zero);
  });
}
