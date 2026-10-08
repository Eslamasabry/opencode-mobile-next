import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/app_exit_history.dart';
import 'package:opencode_mobile/domain/diagnostics_error.dart';
import 'package:opencode_mobile/platform/app_exit.dart';

import 'native/kotlin_jar_cache.dart';

Map<String, Object?> _entry({
  int reason = AndroidExitReason.crash,
  int subReason = -1,
  int timestamp = 1000,
}) => {
  'reason': reason,
  'importance': 100,
  'subReason': subReason,
  'timestamp': timestamp,
  'description': 'private-token /private/path https://private.example',
  'category': 'normal',
  'trace': 'private stack with credentials',
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('exit history bridge', () {
    const channel = MethodChannel('test.exit-history');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    late AppLifecycleBridge bridge;
    late List<MethodCall> calls;
    Object? response;
    setUp(() {
      bridge = AppLifecycleBridge(channel: channel);
      calls = [];
      response = {'supported': true, 'entries': <Object?>[]};
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        return response;
      });
    });
    tearDown(() => messenger.setMockMethodCallHandler(channel, null));

    test('invalid limits make no native request', () async {
      for (final limit in [-1, 0, 51, 1000000]) {
        final result = await bridge.exitHistory(limit: limit);
        expect(result.error, DiagnosticsError.invalidLimit);
        expect(result.entries, isEmpty);
      }
      expect(calls, isEmpty);
    });

    test(
      'supported empty, unsupported and absent channel are distinct',
      () async {
        final empty = await bridge.exitHistory();
        expect(empty.supported, isTrue);
        expect(empty.error, isNull);
        expect(empty.entries, isEmpty);
        response = {'supported': false, 'entries': <Object?>[]};
        final unsupported = await bridge.exitHistory();
        expect(unsupported.supported, isFalse);
        expect(unsupported.error, isNull);
        messenger.setMockMethodCallHandler(channel, null);
        final absent = await bridge.exitHistory();
        expect(absent.supported, isFalse);
        expect(absent.error, isNull);
      },
    );

    test('read errors expose a typed failure without native text', () async {
      response = {'supported': true, 'entries': [], 'error': 'unavailable'};
      expect((await bridge.exitHistory()).error, DiagnosticsError.unavailable);
      messenger.setMockMethodCallHandler(channel, (call) async {
        throw PlatformException(
          code: 'private-error',
          message: 'private-token',
          details: '/private/path',
        );
      });
      final failed = await bridge.exitHistory();
      expect(failed.supported, isTrue);
      expect(failed.entries, isEmpty);
      expect(failed.error, DiagnosticsError.unavailable);
    });

    test('history is newest first, capped, immutable and UTC', () async {
      response = {
        'supported': true,
        'entries': [
          _entry(timestamp: 1000),
          _entry(timestamp: 3000),
          _entry(timestamp: 2000),
        ],
      };
      final history = await bridge.exitHistory(limit: 2);
      expect(calls.single.method, 'exitHistory');
      expect(calls.single.arguments, {'limit': 2});
      expect(history.entries.map((e) => e.at.millisecondsSinceEpoch), [
        3000,
        2000,
      ]);
      expect(history.entries.every((e) => e.at.isUtc), isTrue);
      expect(() => history.entries.clear(), throwsUnsupportedError);
      expect(history.entries.first.importance, 100);
    });

    test('default and boundary limits are forwarded unchanged', () async {
      await bridge.exitHistory();
      await bridge.exitHistory(limit: 1);
      await bridge.exitHistory(limit: 50);
      expect(calls.map((c) => c.arguments), [
        {'limit': 10},
        {'limit': 1},
        {'limit': 50},
      ]);
    });

    test(
      'classification reuses recovery including update subreasons',
      () async {
        final cases = <(int, int, AppExitCategory)>[
          (AndroidExitReason.unknown, -1, AppExitCategory.normal),
          (AndroidExitReason.exitSelf, -1, AppExitCategory.normal),
          (AndroidExitReason.packageUpdated, -1, AppExitCategory.update),
          (AndroidExitReason.packageStateChange, -1, AppExitCategory.update),
          (
            AndroidExitReason.userRequested,
            AndroidExitReason.subPackageUpdate,
            AppExitCategory.update,
          ),
          (
            AndroidExitReason.userRequested,
            AndroidExitReason.subForceStop,
            AppExitCategory.forceStop,
          ),
          (AndroidExitReason.userStopped, -1, AppExitCategory.forceStop),
          (AndroidExitReason.lowMemory, -1, AppExitCategory.lowMemory),
          (AndroidExitReason.crash, -1, AppExitCategory.crash),
          (AndroidExitReason.crashNative, -1, AppExitCategory.crash),
          (AndroidExitReason.anr, -1, AppExitCategory.crash),
          (AndroidExitReason.initializationFailure, -1, AppExitCategory.crash),
          (
            AndroidExitReason.signaled,
            AndroidExitReason.subMemoryPressure,
            AppExitCategory.lowMemory,
          ),
          (
            AndroidExitReason.other,
            AndroidExitReason.subRemoveTask,
            AppExitCategory.forceStop,
          ),
          (
            AndroidExitReason.other,
            AndroidExitReason.subPackageUpdate,
            AppExitCategory.update,
          ),
          (
            AndroidExitReason.excessiveResourceUsage,
            -1,
            AppExitCategory.killed,
          ),
        ];
        for (final (reason, subReason, expected) in cases) {
          response = {
            'supported': true,
            'entries': [_entry(reason: reason, subReason: subReason)],
          };
          final result = await bridge.exitHistory();
          final entry = result.entries.single;
          expect(entry.category, expected, reason: '$reason/$subReason');
          expect(entry.description, expected.safeDescription);
          expect(entry.description.length, lessThanOrEqualTo(64));
          expect(entry.description, isNot(contains('private')));
        }
      },
    );

    test(
      'malformed metadata cannot become a successful empty history',
      () async {
        for (final bad in [
          null,
          {'supported': true},
          {'supported': 'yes', 'entries': []},
          {
            'supported': true,
            'entries': [null],
          },
          {
            'supported': true,
            'entries': [
              {..._entry(), 'timestamp': 0},
            ],
          },
          {
            'supported': true,
            'entries': [
              {..._entry(), 'timestamp': 8640000000000001},
            ],
          },
          {
            'supported': true,
            'entries': [
              {..._entry(), 'reason': '4'},
            ],
          },
          {
            'supported': true,
            'entries': [
              {..._entry(), 'subReason': '25'},
            ],
          },
          {'supported': true, 'entries': List.generate(51, (_) => _entry())},
        ]) {
          response = bad;
          final history = await bridge.exitHistory();
          expect(history.entries, isEmpty);
          expect(history.error, DiagnosticsError.unavailable);
        }
      },
    );

    test(
      'history reads leave the startup recovery operation independent',
      () async {
        messenger.setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          if (call.method == 'exitHistory') {
            return {
              'supported': true,
              'entries': [_entry()],
            };
          }
          return {
            'exit': _entry(),
            'previousServices': ['aiteam'],
          };
        });
        expect((await bridge.exitHistory()).entries.length, 1);
        expect((await bridge.exitHistory()).entries.length, 1);
        final startup = await bridge.launchReport();
        expect(startup.exit?.reason, AndroidExitReason.crash);
        expect(startup.previousServices, ['aiteam']);
        expect(calls.map((c) => c.method), [
          'exitHistory',
          'exitHistory',
          'launchReport',
        ]);
      },
    );
  });

  test('domain history snapshots its input and normalizes entry time', () {
    final entry = AppExitEntry(
      reason: 1,
      importance: 100,
      at: DateTime(2026, 10, 8),
      category: AppExitCategory.normal,
    );
    final input = [entry];
    final history = AppExitHistory(supported: true, entries: input);
    input.clear();
    expect(history.entries.single, same(entry));
    expect(entry.at.isUtc, isTrue);
    expect(() => history.entries.add(entry), throwsUnsupportedError);
  });

  group('native exit history policy', () {
    final compiler =
        Platform.environment['KOTLINC'] ??
        '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
    final available = File(compiler).existsSync();
    late String jar;
    setUpAll(() async {
      if (!available) return;
      jar = await cachedKotlinJar(
        compiler: compiler,
        sources: [
          'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/AppExitHistoryPolicy.kt',
          'test/native/app_exit_history_harness.kt',
        ],
      );
    });
    for (final scenario in [
      'query-bounds',
      'main-process-history',
      'safe-summaries',
      'read-failure',
    ]) {
      test(
        scenario,
        skip: available ? null : 'Kotlin compiler unavailable',
        () async {
          final result = await Process.run(
            Platform.environment['JAVA'] ?? 'java',
            [
              '-cp',
              jar,
              'io.github.eslamasabry.opencode_mobile.App_exit_history_harnessKt',
              scenario,
            ],
          );
          expect(
            result.exitCode,
            0,
            reason: '${result.stdout}\n${result.stderr}',
          );
          expect(result.stdout, contains('PASS $scenario'));
        },
      );
    }
  });
}
