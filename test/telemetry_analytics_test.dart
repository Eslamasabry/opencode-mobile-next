// Product analytics on top of the opt-in telemetry layer (lib/telemetry):
// nothing leaves without consent, only listed events and values are sent,
// and no listed value can carry a path, an address or free text.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/conversation_nudges.dart';
import 'package:opencode_mobile/state/nudges.dart';
import 'package:opencode_mobile/telemetry/analytics_events.dart';
import 'package:opencode_mobile/telemetry/telemetry.dart';
import 'package:opencode_mobile/telemetry/telemetry_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Recorder implements TelemetryAdapter {
  _Recorder({this.present = true});
  final bool present;
  final events = <(String, Map<String, String>)>[];
  final other = <String>[];
  bool? collecting;

  @override
  Future<bool> probe() async => present;
  @override
  Future<void> setCollectionEnabled(bool enabled) async => collecting = enabled;
  @override
  Future<void> event(String name, Map<String, String> props) async =>
      events.add((name, props));
  @override
  Future<void> crash(
    String category,
    String stack, {
    required bool fatal,
  }) async => other.add('crash');
  @override
  Future<void> failure(String name, Map<String, String> keys) async =>
      other.add('failure');
  @override
  Future<void> breadcrumb(String name) async => other.add('crumb');
}

Future<TelemetryService> _service(
  TelemetryAdapter adapter, {
  bool consent = false,
}) async {
  final service = TelemetryService(
    adapter: adapter,
    store: MemoryTelemetryConsentStore(consent),
  );
  await service.start();
  return service;
}

/// One valid call per event.
final _everyEvent = <AnalyticsEvent, Map<String, Object?>>{
  AnalyticsEvent.appOpen: {'kind': 'cold'},
  AnalyticsEvent.setupStepReached: {'step': AnalyticsSetupStep.node},
  AnalyticsEvent.setupStepFailed: {
    'step': AnalyticsSetupStep.node,
    'kind': AnalyticsFailureKind.crash,
    'compat_mode': true,
    'node_variant': AnalyticsNodeVariant.compat,
  },
  AnalyticsEvent.setupDone: {
    'minutes': AnalyticsBuckets.minutesOf(const Duration(minutes: 7)),
  },
  AnalyticsEvent.firstReply: {
    'server_kind': AnalyticsServerKind.phone,
    'seconds': AnalyticsBuckets.secondsOf(const Duration(seconds: 12)),
  },
  AnalyticsEvent.connectComputerChosen: const {},
  AnalyticsEvent.updateOffered: const {},
  AnalyticsEvent.updateInstalled: const {},
};

Future<void> _settle() => Future<void>.delayed(Duration.zero);

void main() {
  tearDown(AppTelemetry.reset);

  group('consent', () {
    test('nothing is sent while consent is off (the default)', () async {
      final adapter = _Recorder();
      final service = await _service(adapter);
      expect(service.consented, isFalse);
      for (final entry in _everyEvent.entries) {
        service.track(entry.key, entry.value);
      }
      await _settle();
      expect(adapter.events, isEmpty);
    });

    test('nothing is sent when the build has no vendor', () async {
      final adapter = _Recorder(present: false);
      final service = await _service(adapter, consent: true);
      service.track(AnalyticsEvent.appOpen, {'kind': 'cold'});
      await _settle();
      expect(adapter.events, isEmpty);
    });

    test('every listed event is sent once consent is on, and stops when '
        'it is turned off', () async {
      final adapter = _Recorder();
      final service = await _service(adapter);
      await service.setConsent(true);
      for (final entry in _everyEvent.entries) {
        service.track(entry.key, entry.value);
      }
      await _settle();
      expect(adapter.events.map((e) => e.$1), [
        for (final event in _everyEvent.keys) event.wire,
      ]);
      expect(adapter.events[2].$2, {
        'step': 'node',
        'kind': 'crash',
        'compat_mode': 'true',
        'node_variant': 'compat',
      });

      await service.setConsent(false);
      adapter.events.clear();
      service.track(AnalyticsEvent.appOpen, {'kind': 'warm'});
      await _settle();
      expect(adapter.events, isEmpty);
      expect(adapter.collecting, isFalse);
    });

    test('the process-wide helper is inert until a consenting service is '
        'installed', () async {
      final adapter = _Recorder();
      AppTelemetry.track(AnalyticsEvent.updateOffered);
      AppTelemetry.install(await _service(adapter));
      AppTelemetry.track(AnalyticsEvent.updateOffered);
      await _settle();
      expect(adapter.events, isEmpty);
      AppTelemetry.install(await _service(adapter, consent: true));
      AppTelemetry.track(AnalyticsEvent.updateOffered);
      await _settle();
      expect(adapter.events.single.$1, 'update_offered');
    });
  });

  group('allow-list', () {
    test('unknown keys, unlisted values and free text are dropped', () {
      final clean =
          TelemetryService.cleanProps(AnalyticsEvent.setupStepFailed, {
            'step': 'node',
            'kind': 'Segmentation fault at /opt/node/bin/node',
            'compat_mode': 'maybe',
            'node_variant': 'v22.1.0',
            'error': 'boom',
            'server': 'vps.example.net',
            'user': 'someone@example.com',
          });
      expect(clean, {'step': 'node'});
    });

    test('a key listed for another event is dropped', () {
      expect(
        TelemetryService.cleanProps(AnalyticsEvent.appOpen, {
          'kind': 'cold',
          'step': 'node',
        }),
        {'kind': 'cold'},
      );
      expect(
        TelemetryService.cleanProps(AnalyticsEvent.updateInstalled, {
          'kind': 'cold',
        }),
        isEmpty,
      );
    });

    test('values of other types are dropped', () {
      expect(
        TelemetryService.cleanProps(AnalyticsEvent.firstReply, {
          'server_kind': 1,
          'seconds': const Duration(seconds: 3),
        }),
        isEmpty,
      );
    });

    test('every event and every listed value is safe', () {
      expect(analyticsSchema.keys, {
        for (final e in AnalyticsEvent.values) e.wire,
      });
      for (final MapEntry(key: event, value: props)
          in analyticsSchema.entries) {
        expect(analyticsValueIsSafe(event), isTrue, reason: event);
        for (final MapEntry(key: key, value: values) in props.entries) {
          expect(analyticsValueIsSafe(key), isTrue, reason: key);
          for (final value in values) {
            expect(analyticsValueIsSafe(value), isTrue, reason: value);
            expect(value, isNot(contains('/')));
            expect(value, isNot(contains('@')));
            expect(value, isNot(matches(RegExp(r'\w\.\w'))));
            expect(value.length, lessThanOrEqualTo(40));
          }
        }
      }
    });

    test('no value that reaches an adapter can hold a path, an address or '
        'more than 40 characters', () {
      final hostile = <Object?>[
        '/data/user/0/io.github/files',
        'C:/Users/someone',
        'someone@example.com',
        'vps.example.net',
        '100.126.15.6',
        'http://127.0.0.1:4096',
        'node.js',
        'x' * 41,
        'a valid looking sentence',
        '',
        null,
      ];
      for (final event in AnalyticsEvent.values) {
        for (final key in [...?analyticsSchema[event.wire]?.keys, 'other']) {
          for (final value in hostile) {
            final clean = TelemetryService.cleanProps(event, {key: value});
            expect(clean, isEmpty, reason: '${event.wire}.$key=$value');
          }
        }
      }
      for (final value in hostile.whereType<String>()) {
        expect(analyticsValueIsSafe(value), isFalse, reason: value);
      }
    });

    test('buckets only produce listed values', () {
      final minutes = analyticsSchema['setup_done']!['minutes']!;
      final seconds = analyticsSchema['first_reply']!['seconds']!;
      for (var s = 0; s < 4000; s += 7) {
        expect(
          minutes,
          contains(AnalyticsBuckets.minutesOf(Duration(seconds: s))),
        );
        expect(
          seconds,
          contains(AnalyticsBuckets.secondsOf(Duration(seconds: s))),
        );
      }
      expect(AnalyticsBuckets.secondsOf(null), 'unknown');
      expect(AnalyticsBuckets.minutesOf(const Duration(minutes: 1)), 'under_2');
      expect(AnalyticsBuckets.minutesOf(const Duration(hours: 2)), 'over_40');
    });

    test('unknown step ids and Node variants become other', () {
      expect(AnalyticsSetupStep.of('opencode'), AnalyticsSetupStep.opencode);
      expect(AnalyticsSetupStep.of('/opt/evil'), AnalyticsSetupStep.other);
      expect(AnalyticsNodeVariant.of('compat'), AnalyticsNodeVariant.compat);
      expect(AnalyticsNodeVariant.of('v22.1.0'), AnalyticsNodeVariant.other);
    });

    test('failure kinds come from the exit code and error text', () {
      expect(analyticsFailureKind(exit: 139), AnalyticsFailureKind.crash);
      expect(
        analyticsFailureKind(error: 'Segmentation fault (core dumped)'),
        AnalyticsFailureKind.crash,
      );
      expect(analyticsFailureKind(exit: 124), AnalyticsFailureKind.timeout);
      expect(
        analyticsFailureKind(error: 'write error: No space left on device'),
        AnalyticsFailureKind.space,
      );
      expect(
        analyticsFailureKind(error: 'Could not resolve host'),
        AnalyticsFailureKind.network,
      );
      expect(analyticsFailureKind(exit: 1), AnalyticsFailureKind.other);
    });
  });

  group('adapters', () {
    test(
      'the combined adapter reaches only vendors that are present',
      () async {
        final present = _Recorder();
        final absent = _Recorder(present: false);
        final service = await _service(
          CombinedTelemetryAdapter([present, absent]),
          consent: true,
        );
        expect(service.available, isTrue);
        service.track(AnalyticsEvent.connectComputerChosen);
        await _settle();
        expect(present.events.single.$1, 'connect_computer_chosen');
        expect(present.collecting, isTrue);
        expect(absent.events, isEmpty);
        expect(absent.collecting, isNull);
      },
    );

    test('analytics events are never sent as crash reports', () async {
      final adapter = _Recorder();
      final service = await _service(adapter, consent: true);
      service.track(AnalyticsEvent.appOpen, {'kind': 'warm'});
      await _settle();
      expect(adapter.other, isEmpty);
    });
  });

  group('call sites', () {
    TestWidgetsFlutterBinding.ensureInitialized();

    test(
      'app_open cold, and update_installed only after a version change',
      () async {
        final adapter = _Recorder();
        AppTelemetry.install(await _service(adapter, consent: true));
        SharedPreferences.setMockInitialValues({});
        final prefs = await SharedPreferences.getInstance();

        await AnalyticsLifecycle.start(prefs, version: () async => '2.1.4+62');
        await _settle();
        expect(adapter.events.map((e) => e.$1), ['app_open']);
        expect(adapter.events.single.$2, {'kind': 'cold'});

        adapter.events.clear();
        await AnalyticsLifecycle.start(prefs, version: () async => '2.1.4+62');
        await AnalyticsLifecycle.start(prefs, version: () async => '2.1.5+63');
        await _settle();
        expect(adapter.events.map((e) => e.$1), [
          'app_open',
          'app_open',
          'update_installed',
        ]);
        expect(prefs.getString(analyticsLastVersionKey), '2.1.5+63');
      },
    );

    test('first_reply is reported once, timed from the run that produced '
        'it', () async {
      SharedPreferences.setMockInitialValues({});
      final registry = NudgeRegistry(await SharedPreferences.getInstance());
      var now = DateTime(2026, 11, 7, 12);
      final reported = <Duration?>[];
      final watcher = ConversationNudgeWatcher(
        registry: registry,
        conversation: 'ses_1',
        now: () => now,
        onFirstReply: reported.add,
      );
      addTearDown(watcher.dispose);

      watcher.observe(
        const ConversationNudgeFacts(busy: true, hasAssistantReply: false),
      );
      now = now.add(const Duration(seconds: 20));
      watcher.observe(
        const ConversationNudgeFacts(busy: false, hasAssistantReply: true),
      );
      watcher.observe(
        const ConversationNudgeFacts(busy: false, hasAssistantReply: true),
      );
      expect(reported, [const Duration(seconds: 20)]);
      expect(AnalyticsBuckets.secondsOf(reported.single), '15_to_30');
    });
  });
}
