import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/quota/antigravity_quota_client.dart';

class _FakeHttpClient implements HttpClient {
  _FakeHttpClient(this.handler);

  final FutureOr<({int statusCode, String body})> Function(Uri uri) handler;
  final uris = <Uri>[];
  final headers = <String, Map<String, String>>{};

  @override
  Duration? connectionTimeout;
  @override
  Duration idleTimeout = const Duration(seconds: 15);
  @override
  int? maxConnectionsPerHost;
  @override
  bool autoUncompress = true;
  @override
  String? userAgent;

  @override
  Future<HttpClientRequest> postUrl(Uri url) async {
    uris.add(url);
    final req = _FakeHttpClientRequest(url, handler);
    return req;
  }

  @override
  void close({bool force = false}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeHttpClientRequest implements HttpClientRequest {
  _FakeHttpClientRequest(this.uri, this.handler);

  final Uri uri;
  final FutureOr<({int statusCode, String body})> Function(Uri uri) handler;
  final _headers = _FakeHttpHeaders();
  final _body = <int>[];

  @override
  HttpHeaders get headers => _headers;

  @override
  void add(List<int> data) {
    _body.addAll(data);
  }

  @override
  Future<HttpClientResponse> close() async {
    final res = await handler(uri);
    return _FakeHttpClientResponse(res.statusCode, res.body);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeHttpHeaders implements HttpHeaders {
  final Map<String, String> values = {};

  @override
  void set(String name, Object value) {
    values[name.toLowerCase()] = value.toString();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeHttpClientResponse extends Stream<List<int>>
    implements HttpClientResponse {
  _FakeHttpClientResponse(this.statusCode, this.body);

  @override
  final int statusCode;
  final String body;

  @override
  StreamSubscription<List<int>> listen(
    void Function(List<int> event)? onData, {
    Function? onError,
    void Function()? onDone,
    bool? cancelOnError,
  }) {
    return Stream.value(utf8.encode(body)).listen(
      onData,
      onError: onError,
      onDone: onDone,
      cancelOnError: cancelOnError,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  final testNow = DateTime.utc(2026, 11, 7, 20, 0, 0);

  group('AntigravityQuotaClient', () {
    test('missing token returns needsSignIn: true', () async {
      final client = AntigravityQuotaClient(
        tokenReader: ({profileId, supportDir}) async => null,
        clock: () => testNow,
      );

      final readings = await client.fetchAllUsage();
      expect(readings.length, 1);
      final reading = readings.first;
      expect(reading.providerId, 'antigravity');
      expect(reading.displayName, 'Antigravity');
      expect(reading.needsSignIn, isTrue);
      expect(reading.windows, isEmpty);
    });

    test('401 response returns needsSignIn: true', () async {
      final fakeHttp = _FakeHttpClient((uri) => (statusCode: 401, body: '{}'));
      final client = AntigravityQuotaClient(
        clientFactory: () => fakeHttp,
        tokenReader: ({profileId, supportDir}) async => 'test-token',
        clock: () => testNow,
      );

      final readings = await client.fetchAllUsage();
      expect(readings.length, 1);
      final reading = readings.first;
      expect(reading.needsSignIn, isTrue);
      expect(reading.windows, isEmpty);
    });

    test('parseCliJson parses both Gemini and Claude & GPT groups accurately', () {
      final cliData = {
        'command': {
          'name': 'usage',
          'data': {
            'groups': [
              {
                'name': 'Gemini Models',
                'description': 'Models within this group: Gemini Flash, Gemini Pro',
                'buckets': [
                  {
                    'id': 'gemini-weekly',
                    'name': 'Weekly Limit Remaining',
                    'window': 'weekly',
                    'remaining_fraction': 0.6815,
                    'reset_time': '2026-11-14T07:25:34Z',
                  },
                  {
                    'id': 'gemini-5h',
                    'name': 'Five Hour Limit Remaining',
                    'window': '5h',
                    'remaining_fraction': 0.4579,
                    'reset_time': '2026-11-07T21:48:21Z',
                  },
                ],
              },
              {
                'name': 'Claude and GPT models',
                'description': 'Models within this group: Claude Opus, Claude Sonnet, GPT-OSS',
                'buckets': [
                  {
                    'id': '3p-weekly',
                    'name': 'Weekly Limit Remaining',
                    'window': 'weekly',
                    'remaining_fraction': 0.0,
                    'reset_time': '2026-11-11T10:05:57Z',
                  },
                  {
                    'id': '3p-5h',
                    'name': 'Five Hour Limit Remaining',
                    'window': '5h',
                    'disabled': true,
                    'remaining_fraction': 1.0,
                  },
                ],
              },
            ],
          },
        },
      };

      final readings = AntigravityQuotaClient.parseCliJson(
        cliData,
        currentNow: testNow,
      );

      expect(readings.length, 2);

      // 1. Gemini Models
      final gemini = readings[0];
      expect(gemini.providerId, 'antigravity');
      expect(gemini.displayName, 'Antigravity (Gemini Models)');
      expect(gemini.windows.length, 2);
      expect(gemini.windows[0].windowId, '5h');
      expect(gemini.windows[0].remainingPercent, 46);
      expect(gemini.windows[0].durationSeconds, 18000);
      expect(gemini.windows[0].resetsAt, DateTime.utc(2026, 11, 7, 21, 48, 21));
      expect(gemini.windows[1].windowId, 'weekly');
      expect(gemini.windows[1].remainingPercent, 68);
      expect(gemini.windows[1].durationSeconds, 604800);
      expect(gemini.windows[1].resetsAt, DateTime.utc(2026, 11, 14, 7, 25, 34));

      // 2. Claude and GPT models
      final claude = readings[1];
      expect(claude.providerId, 'antigravity_claude');
      expect(claude.displayName, 'Antigravity (Claude and GPT models)');
      expect(claude.windows.length, 2);
      expect(claude.windows[0].windowId, '5h');
      // Disabled 5h bucket must report 0%, NOT 100%!
      expect(claude.windows[0].remainingPercent, 0);
      expect(claude.windows[1].windowId, 'weekly');
      expect(claude.windows[1].remainingPercent, 0);
      expect(claude.windows[1].resetsAt, DateTime.utc(2026, 11, 11, 10, 5, 57));
    });

    test('fetchAllUsage reads via cliRunner when available', () async {
      final cliData = {
        'command': {
          'name': 'usage',
          'data': {
            'groups': [
              {
                'name': 'Gemini Models',
                'buckets': [
                  {
                    'id': 'gemini-5h',
                    'window': '5h',
                    'remaining_fraction': 0.45,
                    'reset_time': '2026-11-07T21:48:21Z',
                  },
                ],
              },
            ],
          },
        },
      };

      final client = AntigravityQuotaClient(
        cliRunner: (exe, args) async => ProcessResult(
          0,
          0,
          jsonEncode(cliData),
          '',
        ),
        clock: () => testNow,
      );

      final readings = await client.fetchAllUsage();
      expect(readings.length, 1);
      expect(readings[0].providerId, 'antigravity');
      expect(readings[0].windows[0].remainingPercent, 45);
    });

    test('parseHttpReadings parses Gemini and Claude quotas with plan label', () async {
      const summaryJson = '''
      {
        "groups": [
          {
            "displayName": "Claude and GPT models",
            "description": "Models within this group: Claude Opus, Claude Sonnet, GPT-OSS",
            "buckets": [
              {
                "bucketId": "3p-weekly",
                "displayName": "Weekly Limit Remaining",
                "window": "weekly",
                "resetTime": "2026-11-11T10:05:57Z",
                "remainingFraction": 0
              },
              {
                "bucketId": "3p-5h",
                "displayName": "Five Hour Limit Remaining",
                "window": "5h",
                "disabled": true,
                "remainingFraction": 1.0
              }
            ]
          }
        ]
      }
      ''';

      const quotaJson = '''
      {
        "buckets": [
          {
            "modelId": "gemini-3.8-flash-tiered",
            "remainingFraction": 0.85,
            "resetTime": "2026-11-08T01:16:30Z"
          }
        ]
      }
      ''';

      const tierJson = '''
      {
        "currentTier": {
          "id": "free-tier",
          "name": "Google AI Pro"
        }
      }
      ''';

      final fakeHttp = _FakeHttpClient((uri) {
        if (uri.path.contains('retrieveUserQuotaSummary')) {
          return (statusCode: 200, body: summaryJson);
        }
        if (uri.path.contains('retrieveUserQuota')) {
          return (statusCode: 200, body: quotaJson);
        }
        if (uri.path.contains('loadCodeAssist')) {
          return (statusCode: 200, body: tierJson);
        }
        return (statusCode: 404, body: '{}');
      });

      final client = AntigravityQuotaClient(
        clientFactory: () => fakeHttp,
        tokenReader: ({profileId, supportDir}) async => 'valid-bearer-token',
        clock: () => testNow,
      );

      final readings = await client.fetchAllUsage();
      expect(readings.length, 2);

      // Gemini first
      final gemini = readings[0];
      expect(gemini.providerId, 'antigravity');
      expect(gemini.displayName, 'Antigravity (Gemini Models)');
      expect(gemini.needsSignIn, isFalse);
      expect(gemini.planLabel, 'Google AI Pro');
      expect(gemini.windows[0].remainingPercent, 85);

      // Claude second
      final claude = readings[1];
      expect(claude.providerId, 'antigravity_claude');
      expect(claude.displayName, 'Antigravity (Claude and GPT models)');
      expect(claude.windows[0].remainingPercent, 0); // disabled = 0%
      expect(claude.windows[1].remainingPercent, 0);
    });

    test('parseHttpReadings parses distinct Gemini weekly and 5h windows from quota summary', () async {
      const summaryJson = '''
      {
        "groups": [
          {
            "displayName": "Gemini Models",
            "description": "Models within this group: Gemini Flash, Gemini Pro",
            "buckets": [
              {
                "bucketId": "gemini-weekly",
                "displayName": "Weekly Limit Remaining",
                "window": "weekly",
                "resetTime": "2026-11-14T07:25:34Z",
                "remainingFraction": 0.6815
              },
              {
                "bucketId": "gemini-5h",
                "displayName": "Five Hour Limit Remaining",
                "window": "5h",
                "resetTime": "2026-11-07T21:48:21Z",
                "remainingFraction": 0.4579
              }
            ]
          },
          {
            "displayName": "Claude and GPT models",
            "description": "Models within this group: Claude Opus, Claude Sonnet, GPT-OSS",
            "buckets": [
              {
                "bucketId": "3p-weekly",
                "displayName": "Weekly Limit Remaining",
                "window": "weekly",
                "resetTime": "2026-11-11T10:05:57Z",
                "remainingFraction": 0.0
              },
              {
                "bucketId": "3p-5h",
                "displayName": "Five Hour Limit Remaining",
                "window": "5h",
                "disabled": true,
                "remainingFraction": 1.0
              }
            ]
          }
        ]
      }
      ''';

      final fakeHttp = _FakeHttpClient((uri) {
        if (uri.path.contains('retrieveUserQuotaSummary')) {
          return (statusCode: 200, body: summaryJson);
        }
        if (uri.path.contains('loadCodeAssist')) {
          return (
            statusCode: 200,
            body: '{"paidTier":{"name":"Google AI Pro"},"cloudaicompanionProject":"aicode-consumers"}'
          );
        }
        return (statusCode: 200, body: '{}');
      });

      final client = AntigravityQuotaClient(
        clientFactory: () => fakeHttp,
        tokenReader: ({profileId, supportDir}) async => 'valid-bearer-token',
        clock: () => testNow,
      );

      final readings = await client.fetchAllUsage();
      expect(readings.length, 2);

      final gemini = readings[0];
      expect(gemini.providerId, 'antigravity');
      expect(gemini.displayName, 'Antigravity (Gemini Models)');
      expect(gemini.planLabel, 'Google AI Pro');
      expect(gemini.windows.length, 2);

      // 5-hour window
      expect(gemini.windows[0].windowId, '5h');
      expect(gemini.windows[0].remainingPercent, 46);
      expect(gemini.windows[0].resetsAt, DateTime.utc(2026, 11, 7, 21, 48, 21));

      // Weekly window - completely independent from 5-hour window!
      expect(gemini.windows[1].windowId, 'weekly');
      expect(gemini.windows[1].remainingPercent, 68);
      expect(gemini.windows[1].resetsAt, DateTime.utc(2026, 11, 14, 7, 25, 34));
    });

    test('network failure returns cached reading when available', () async {
      int calls = 0;
      final fakeHttp = _FakeHttpClient((uri) {
        calls++;
        if (calls <= 3) {
          if (uri.path.contains('retrieveUserQuotaSummary')) {
            return (
              statusCode: 200,
              body: '{"groups":[{"displayName":"Gemini Models","buckets":[{"window":"weekly","remainingFraction":0.4}]}]}'
            );
          }
          return (statusCode: 200, body: '{}');
        }
        throw const SocketException('No route to host');
      });

      final client = AntigravityQuotaClient(
        clientFactory: () => fakeHttp,
        tokenReader: ({profileId, supportDir}) async => 'token',
        clock: () => testNow,
      );

      final first = await client.fetchAllUsage();
      expect(first.length, 1);
      expect(first[0].windows.first.remainingPercent, 40);

      // Second fetch encounters network error and returns cached
      final second = await client.fetchAllUsage();
      expect(second.length, 1);
      expect(second[0].windows.first.remainingPercent, 40);
    });
  });
}
