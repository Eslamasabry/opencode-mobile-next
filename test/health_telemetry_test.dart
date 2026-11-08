// Health and diagnostics telemetry (docs/telemetry/health-and-diagnostics.md):
// two consents, the fixed words and ranges, nothing sent with a consent off,
// the events at the places that fire them, the old answer carried over, the
// problem report's redaction, and the measures that cost nothing when
// `usage` is off.
import 'dart:async';
import 'dart:io';
import 'dart:ui' show FrameTiming;

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/feedback/crash_offer.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/domain/app_exit_history.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/telemetry/analytics_events.dart';
import 'package:opencode_mobile/telemetry/app_log_ring.dart';
import 'package:opencode_mobile/telemetry/health_probe.dart';
import 'package:opencode_mobile/telemetry/health_schema.dart';
import 'package:opencode_mobile/telemetry/problem_report_collector.dart';
import 'package:opencode_mobile/telemetry/report_redaction.dart';
import 'package:opencode_mobile/telemetry/telemetry.dart';
import 'package:opencode_mobile/telemetry/telemetry_provider.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/telemetry_recorder.dart';

const _newEvents = [
  AnalyticsEvent.connAttempt,
  AnalyticsEvent.connDrop,
  AnalyticsEvent.sendResult,
  AnalyticsEvent.turnStall,
  AnalyticsEvent.queueFlush,
  AnalyticsEvent.linuxSetupStep,
  AnalyticsEvent.agentStart,
  AnalyticsEvent.bgServiceEnd,
  AnalyticsEvent.updateResult,
  AnalyticsEvent.appStart,
  AnalyticsEvent.jank,
  AnalyticsEvent.memoryPressure,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(() {
    AppTelemetry.reset();
    AppTelemetry.health = null;
    AppLogRing.instance.clear();
    KitRedact.clearKnownSecrets();
  });

  group('only listed words and ranges are sent', () {
    test('every new event is in the schema, and its values are fixed', () {
      for (final event in _newEvents) {
        final schema = analyticsSchema[event.wire];
        expect(schema, isNotNull, reason: event.wire);
        for (final values in schema!.values) {
          expect(values, isNotEmpty);
        }
      }
    });

    test('unknown values, extra keys and free text are dropped for every '
        'new event', () {
      final hostile = <Object?>[
        'a free text sentence',
        '/data/user/0/app/files',
        'vps.example.net',
        '100.126.15.6',
        'https://host/x?token=1',
        'x' * 60,
        '',
        '<>',
        '500-2001',
        42,
        null,
      ];
      for (final event in _newEvents) {
        final keys = analyticsSchema[event.wire]!.keys;
        for (final key in [...keys, 'message', 'path', 'host']) {
          for (final value in hostile) {
            final clean = TelemetryService.cleanProps(event, {key: value});
            expect(clean, isEmpty, reason: '${event.wire}.$key=$value');
          }
        }
      }
    });

    test('listed values pass, and only they', () {
      final clean = TelemetryService.cleanProps(AnalyticsEvent.connAttempt, {
        'server_kind': 'phone',
        'result': 'fail',
        'fail_kind': 'server_5xx',
        'ms_range': '>10000',
        'error_message': 'connect failed to 10.0.0.2',
      });
      expect(clean, {
        'server_kind': 'phone',
        'result': 'fail',
        'fail_kind': 'server_5xx',
        'ms_range': '>10000',
      });
      // A word from another event's list is not accepted here.
      expect(
        TelemetryService.cleanProps(AnalyticsEvent.connAttempt, {
          'fail_kind': 'too_large',
        }),
        isEmpty,
      );
      expect(
        TelemetryService.cleanProps(AnalyticsEvent.sendResult, {
          'fail_kind': 'too_large',
        }),
        {'fail_kind': 'too_large'},
      );
    });

    test('every range function answers only a listed word', () {
      for (var ms = 0; ms < 20000; ms += 37) {
        final d = Duration(milliseconds: ms);
        expect(HealthRanges.connMs, contains(HealthRanges.connMsOf(d)));
        expect(HealthRanges.appStartMs, contains(HealthRanges.appStartMsOf(d)));
        expect(HealthRanges.firstReply, contains(HealthRanges.firstReplyOf(d)));
      }
      for (var n = 0; n < 60; n++) {
        expect(HealthRanges.counts, contains(HealthRanges.countOf(n)));
        expect(
          HealthRanges.connections,
          contains(HealthRanges.connectionsOf(n)),
        );
      }
      for (var gb = 0.0; gb < 40; gb += 0.7) {
        expect(HealthRanges.ram, contains(HealthRanges.ramOf(gb)));
        expect(HealthRanges.storage, contains(HealthRanges.storageOf(gb)));
        expect(HealthRanges.jankPct, contains(HealthRanges.jankPctOf(gb * 3)));
      }
      for (final hz in <double>[30, 59.9, 60, 90, 120, 144, 165]) {
        expect(HealthRanges.refreshHz, contains(HealthRanges.refreshHzOf(hz)));
      }
      expect(HealthRanges.refreshHzOf(120), '120');
      expect(HealthRanges.refreshHzOf(144), 'other');
      expect(HealthRanges.stallMinutesOf(const Duration(minutes: 7)), '5-15');
    });

    test('custom keys and user properties drop unknown values', () {
      final keys = TelemetryService.cleanListed(
        {
          'build_kind': 'nightly',
          'server_kind': 'phone',
          'engine_state': 'ready',
          'connections': '2-3',
          'exit_reason': 'because it crashed at /data/x',
          'app_version': '2.1.5',
          'oc_server_version': '2.1.28',
          'report_code': 'abcdef01-3',
          'free_text': 'hello',
        },
        healthKeyWords,
        healthKeyPatterns,
      );
      expect(keys, {
        'server_kind': 'phone',
        'engine_state': 'ready',
        'connections': '2-3',
        'app_version': '2.1.5',
        'report_code': 'abcdef01-3',
      });
      final props = TelemetryService.cleanListed(
        {
          'ram_range': '4-6',
          'free_storage_range': '12',
          'abi': 'riscv',
          'webview_major': 133,
          'notif_allowed': true,
          'server_kind': 'both',
          'oc_server_version': '2.0',
          'model': 'Pixel',
        },
        healthPropertyWords,
        healthPropertyPatterns,
      );
      expect(props, {
        'ram_range': '4-6',
        'webview_major': '133',
        'notif_allowed': 'yes',
        'server_kind': 'both',
        'oc_server_version': '2.0',
      });
    });

    test('breadcrumbs are only the listed area.action words', () {
      for (final ok in [
        'app.start',
        'nav.open:chat',
        'nav.open:new_chat',
        'chat.send',
        'linux.step:node',
        'agent.start:claude_code',
        'update.fail',
        'settings.change:privacy',
      ]) {
        expect(BreadcrumbWords.isListed(ok), isTrue, reason: ok);
      }
      for (final bad in [
        'open_settings',
        'nav.open:/home/someone/secret',
        'nav.open',
        'chat.send:hello',
        'linux.step:rm -rf',
        'agent.start:Claude',
        'a free sentence',
        '',
      ]) {
        expect(BreadcrumbWords.isListed(bad), isFalse, reason: bad);
      }
      // The navigation words are the screen_view words.
      expect(BreadcrumbWords.named['nav.open'], {
        for (final s in AnalyticsScreen.values) s.wire,
      });
    });

    test('an error is sent as a kind, never its message', () async {
      final adapter = RecordingAdapter();
      AppTelemetry.install(await startedService(adapter, usage: true));
      HealthFlow.connAttempt(
        serverKind: 'computer',
        ok: false,
        elapsed: const Duration(milliseconds: 700),
        failure: const SocketException(
          'Failed host lookup: secret-host.example.net',
        ),
      );
      HealthFlow.sendResult(
        result: 'fail',
        failure: ApiException(
          'Key sk-ant-SECRETSECRETSECRET rejected',
          statusCode: 400,
        ),
      );
      await settleTelemetry();
      expect(adapter.events.map((e) => e.$2), [
        {
          'server_kind': 'computer',
          'result': 'fail',
          'fail_kind': 'dns',
          'ms_range': '500-2000',
        },
        {
          'result': 'fail',
          'fail_kind': 'rejected',
          'first_reply_range': 'none',
        },
      ]);
      final all = adapter.events.map((e) => '${e.$1}${e.$2}').join();
      expect(all, isNot(contains('secret')));
      expect(all, isNot(contains('SECRET')));
    });

    test('failKindOf maps errors to the fixed words', () {
      expect(failKindOf(TimeoutException('x')), 'timeout');
      expect(failKindOf(const HandshakeException('bad cert')), 'tls');
      expect(failKindOf(ApiException('x', statusCode: 401)), 'auth');
      expect(failKindOf(ApiException('x', statusCode: 503)), 'server_5xx');
      expect(failKindOf(ApiException('x', statusCode: 404)), 'protocol');
      expect(
        failKindOf(ApiException('x', statusCode: 404), forSend: true),
        'rejected',
      );
      expect(
        failKindOf(ApiException('x', statusCode: 413), forSend: true),
        'too_large',
      );
      expect(
        failKindOf(const SocketException('Connection refused')),
        'refused',
      );
      expect(
        failKindOf(const SocketException('Network is unreachable')),
        'offline',
      );
      expect(failKindOf(StateError('anything else')), 'other');
      expect(failKindOf(null), 'other');
      final all = {...HealthWords.failKinds, ...HealthWords.sendOnlyFailKinds};
      for (final error in <Object?>[
        TimeoutException('x'),
        const FormatException('x'),
        StateError('x'),
        ApiException('x', statusCode: 422),
      ]) {
        expect(all, contains(failKindOf(error, forSend: true)));
      }
    });
  });

  group('two consents', () {
    Future<void> everything() async {
      HealthFlow.connAttempt(
        serverKind: 'phone',
        ok: true,
        elapsed: const Duration(milliseconds: 90),
      );
      AppTelemetry.crumb('conn.connect');
      AppTelemetry.current.recordFailure('process_exit', {
        'exit_reason': 'anr',
      });
      AppTelemetry.current.recordCrash('Invalid state', StackTrace.current);
      AppTelemetry.current.setHealthKeys({'build_kind': 'sideload'});
      AppTelemetry.current.setUserProperties({'abi': 'arm64'});
      await settleTelemetry();
    }

    test('both off (the default): nothing reaches the vendor', () async {
      final adapter = RecordingAdapter();
      final service = await startedService(adapter);
      AppTelemetry.install(service);
      expect(service.usageConsented, isFalse);
      expect(service.crashesConsented, isFalse);
      expect(adapter.usageOn, isFalse);
      expect(adapter.crashesOn, isFalse);
      await everything();
      expect(adapter.calls, isEmpty);
    });

    test('usage only: events and properties, no crash data', () async {
      final adapter = RecordingAdapter();
      AppTelemetry.install(await startedService(adapter, usage: true));
      await everything();
      expect(adapter.calls, ['event', 'properties']);
      expect(adapter.crashes, isEmpty);
      expect(adapter.failures, isEmpty);
      expect(adapter.crumbs, isEmpty);
      expect(adapter.keys, isEmpty);
      expect(adapter.properties, {'abi': 'arm64'});
    });

    test('crashes only: crash data, no events or properties', () async {
      final adapter = RecordingAdapter();
      AppTelemetry.install(await startedService(adapter, crashes: true));
      await everything();
      expect(adapter.events, isEmpty);
      expect(adapter.properties, isEmpty);
      expect(adapter.crashes, hasLength(1));
      expect(adapter.failures.single.$1, 'process_exit');
      expect(adapter.failures.single.$2, {'exit_reason': 'anr'});
      expect(adapter.crumbs, ['conn.connect', 'conn.connect']);
      expect(adapter.keys, {'build_kind': 'sideload'});
    });

    test('each switch turns only its own vendor side on and off', () async {
      final adapter = RecordingAdapter();
      final service = await startedService(adapter);
      await service.setUsageConsent(true);
      expect((adapter.usageOn, adapter.crashesOn), (true, false));
      await service.setCrashesConsent(true);
      expect((adapter.usageOn, adapter.crashesOn), (true, true));
      await service.setUsageConsent(false);
      expect((adapter.usageOn, adapter.crashesOn), (false, true));
      await service.setConsent(false);
      expect((adapter.usageOn, adapter.crashesOn), (false, false));
    });

    test('a build without the vendor never sends, with both on', () async {
      final adapter = RecordingAdapter(present: false);
      AppTelemetry.install(
        await startedService(adapter, usage: true, crashes: true),
      );
      await everything();
      expect(adapter.calls, isEmpty);
    });

    test('breadcrumbs stay on the phone without crash consent', () async {
      final adapter = RecordingAdapter();
      AppTelemetry.install(await startedService(adapter));
      AppTelemetry.crumb('chat.send');
      AppTelemetry.crumb('not a listed crumb');
      await settleTelemetry();
      expect(adapter.crumbs, isEmpty);
      expect(
        AppLogRing.instance.lines.where((l) => l.contains('crumb chat.send')),
        hasLength(1),
      );
      expect(AppLogRing.instance.lines.join(), isNot(contains('not a listed')));
    });
  });

  group('2.1.5 answers carry over, so nobody is asked twice', () {
    Future<SharedPreferences> prefs(Map<String, Object> values) async {
      SharedPreferences.setMockInitialValues(values);
      return SharedPreferences.getInstance();
    }

    test('no old answer: both off and still to be asked', () async {
      final p = await prefs({});
      expect(PreferencesTelemetryConsentStore.carriedOver(p), (
        usage: false,
        crashes: false,
      ));
      await PreferencesTelemetryConsentStore.migrate(p);
      expect(p.containsKey(telemetryUsageKey), isFalse);
      expect(p.getBool(telemetryConsentAskedKey), isNull);
    });

    test('the old yes turns usage on, and crashes on with it', () async {
      final p = await prefs({telemetryConsentKey: true});
      expect(PreferencesTelemetryConsentStore.carriedOver(p), (
        usage: true,
        crashes: true,
      ));
      await PreferencesTelemetryConsentStore.migrate(p);
      expect(p.getBool(telemetryUsageKey), isTrue);
      expect(p.getBool(telemetryCrashesKey), isTrue);
      expect(p.getBool(telemetryConsentAskedKey), isTrue);
    });

    test(
      'the old yes with the crash offer turned off keeps crashes off',
      () async {
        final p = await prefs({
          telemetryConsentKey: true,
          CrashReportOffer.enabledKey: false,
        });
        expect(PreferencesTelemetryConsentStore.carriedOver(p), (
          usage: true,
          crashes: false,
        ));
      },
    );

    test('an old no, or only the crash offer, turns nothing on', () async {
      for (final values in <Map<String, Object>>[
        {telemetryConsentKey: false},
        {CrashReportOffer.enabledKey: true},
        {CrashReportOffer.enabledKey: false},
      ]) {
        final p = await prefs(values);
        expect(PreferencesTelemetryConsentStore.carriedOver(p), (
          usage: false,
          crashes: false,
        ), reason: '$values');
        await PreferencesTelemetryConsentStore.migrate(p);
        expect(p.getBool(telemetryConsentAskedKey), isTrue, reason: '$values');
      }
    });

    test('a saved new answer wins over the old one', () async {
      final p = await prefs({
        telemetryConsentKey: true,
        telemetryUsageKey: false,
        telemetryCrashesKey: true,
      });
      expect(PreferencesTelemetryConsentStore.carriedOver(p), (
        usage: false,
        crashes: true,
      ));
      await PreferencesTelemetryConsentStore.migrate(p);
      expect(p.getBool(telemetryUsageKey), isFalse);
    });

    test('the service starts from the carried-over answer', () async {
      final p = await prefs({telemetryConsentKey: true});
      final adapter = RecordingAdapter();
      final service = TelemetryService(
        adapter: adapter,
        store: PreferencesTelemetryConsentStore(p),
      );
      await service.start();
      expect(service.usageConsented, isTrue);
      expect(service.crashesConsented, isTrue);
      expect((adapter.usageOn, adapter.crashesOn), (true, true));
      await service.setUsageConsent(false);
      final again = PreferencesTelemetryConsentStore(p).read();
      expect(again, (usage: false, crashes: true));
    });
  });

  group('the events fire where the app does the thing', () {
    Future<RecordingAdapter> installed() async {
      final adapter = RecordingAdapter();
      AppTelemetry.install(
        await startedService(adapter, usage: true, crashes: true),
      );
      return adapter;
    }

    test('conn_drop says whether the connection came back', () async {
      final adapter = await installed();
      HealthFlow.connDrop(
        serverKind: 'computer',
        reason: 'background',
        recovered: true,
      );
      HealthFlow.connDrop(
        serverKind: 'phone',
        reason: 'other',
        recovered: false,
      );
      await settleTelemetry();
      expect(adapter.events.map((e) => e.$2), [
        {'server_kind': 'computer', 'reason': 'background', 'recovered': 'yes'},
        {'server_kind': 'phone', 'reason': 'other', 'recovered': 'no'},
      ]);
      expect(adapter.crumbs, ['conn.reconnect', 'conn.drop']);
    });

    test('send_result: ok with the first reply time, queued, failed', () async {
      final adapter = await installed();
      HealthFlow.sendResult(
        result: 'ok',
        firstReply: const Duration(seconds: 12),
      );
      HealthFlow.sendResult(result: 'queued');
      HealthFlow.sendResult(
        result: 'fail',
        failure: ApiException('too big', statusCode: 413),
      );
      await settleTelemetry();
      expect(adapter.events.map((e) => e.$2), [
        {'result': 'ok', 'first_reply_range': '10-30s'},
        {'result': 'queued', 'first_reply_range': 'none'},
        {
          'result': 'fail',
          'fail_kind': 'too_large',
          'first_reply_range': 'none',
        },
      ]);
    });

    test('turn_stall names the stage and the wait', () async {
      final adapter = await installed();
      HealthFlow.turnStall(
        stage: 'waiting_approval',
        waited: const Duration(minutes: 20),
      );
      await settleTelemetry();
      expect(adapter.events.single.$2, {
        'stage': 'waiting_approval',
        'minutes_range': '15-60',
      });
    });

    test(
      'linux_setup_step: a Node.js crash gives the signal and safe mode',
      () async {
        final adapter = await installed();
        HealthFlow.linuxSetupStep(
          step: 'node',
          ok: false,
          elapsed: const Duration(seconds: 42),
          exit: 132,
          compatMode: 'armcap',
          freeGigabytes: 3.2,
        );
        HealthFlow.linuxSetupStep(
          step: 'essentials',
          ok: false,
          elapsed: const Duration(seconds: 3),
          exit: 127,
        );
        HealthFlow.linuxSetupStep(
          step: 'done',
          ok: true,
          elapsed: const Duration(minutes: 9),
        );
        await settleTelemetry();
        expect(adapter.events.map((e) => e.$2), [
          {
            'step': 'node',
            'result': 'fail',
            'ms_range': '10000-60000',
            'exit_kind': 'signal',
            'signal': 'SIGILL',
            'compat_mode': 'armcap',
            'free_space_range': '2-8',
          },
          {
            'step': 'essentials',
            'result': 'fail',
            'ms_range': '<10000',
            'exit_kind': '127',
            'signal': 'none',
            'compat_mode': 'none',
          },
          {
            'step': 'done',
            'result': 'ok',
            'ms_range': '>300000',
            'exit_kind': '0',
            'signal': 'none',
            'compat_mode': 'none',
          },
        ]);
        expect(adapter.crumbs, contains('linux.step:node'));
        expect(adapter.crumbs, contains('linux.fail'));
      },
    );

    test('exit codes become exit kinds and signals', () {
      expect(HealthFlow.signalOfExit(139), 'SIGSEGV');
      expect(HealthFlow.signalOfExit(135), 'SIGBUS');
      expect(HealthFlow.signalOfExit(137), 'SIGKILL');
      expect(HealthFlow.signalOfExit(143), 'other');
      expect(HealthFlow.signalOfExit(127), 'none');
      expect(HealthFlow.exitKindOf(126, ok: false), '126');
      expect(HealthFlow.exitKindOf(2, ok: false), 'other');
      expect(HealthFlow.exitKindOf(null, ok: false), 'other');
    });

    test('agent_start words, and an unknown agent is "other"', () async {
      final adapter = await installed();
      HealthFlow.agentStart(agent: 'claude_code', ok: true);
      HealthFlow.agentStart(
        agent: 'codex',
        ok: false,
        failKind: 'needs_sign_in',
      );
      HealthFlow.agentStart(agent: 'some-new-agent', ok: true);
      await settleTelemetry();
      expect(adapter.events.map((e) => e.$2), [
        {'agent': 'claude_code', 'result': 'ok'},
        {'agent': 'codex', 'result': 'fail', 'fail_kind': 'needs_sign_in'},
        {'agent': 'other', 'result': 'ok'},
      ]);
      expect(adapter.crumbs, contains('agent.start:claude_code'));
      expect(adapter.crumbs, contains('agent.fail'));
    });

    test('update_result for an app update and a patch', () async {
      final adapter = await installed();
      HealthFlow.updateResult(
        kind: 'in_app',
        result: 'failed',
        failKind: 'checksum',
      );
      HealthFlow.updateResult(kind: 'patch', result: 'installed');
      HealthFlow.updateResult(kind: 'in_app', result: 'declined');
      await settleTelemetry();
      expect(adapter.events.map((e) => e.$2), [
        {'kind': 'in_app', 'result': 'failed', 'fail_kind': 'checksum'},
        {'kind': 'patch', 'result': 'installed'},
        {'kind': 'in_app', 'result': 'declined'},
      ]);
      expect(adapter.crumbs, ['update.fail', 'update.install', 'update.offer']);
    });

    test('bg_service_end fires when Android stops the service, and when '
        'the person pauses it', () async {
      final previous = debugPlatformCapabilities;
      debugPlatformCapabilities = const PlatformCapabilities.android();
      addTearDown(() => debugPlatformCapabilities = previous);
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      final adapter = await installed();
      final controller = BackgroundLiveController(
        preferences: await SharedPreferences.getInstance(),
        invoke: (method, [arguments]) async => <String, dynamic>{},
      );
      addTearDown(controller.dispose);

      controller.handleNativeTimeout(const {'reason': 'timeLimit'});
      controller.handleNativeTimeout(const {'reason': 'userPause'});
      for (var i = 0; i < 5; i++) {
        await settleTelemetry();
      }
      expect(adapter.events.map((e) => e.$2), [
        {'reason': 'os_limit', 'battery_exempt': 'no'},
        {'reason': 'user_stopped', 'battery_exempt': 'no'},
      ]);
    });

    test('bg_service_end reads nothing when usage is off', () async {
      final previous = debugPlatformCapabilities;
      debugPlatformCapabilities = const PlatformCapabilities.android();
      addTearDown(() => debugPlatformCapabilities = previous);
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      final adapter = RecordingAdapter();
      AppTelemetry.install(await startedService(adapter, crashes: true));
      var asked = false;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('oc/lifecycle'), (
            call,
          ) async {
            asked = true;
            return null;
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(
              const MethodChannel('oc/lifecycle'),
              null,
            ),
      );
      final controller = BackgroundLiveController(
        preferences: await SharedPreferences.getInstance(),
        invoke: (method, [arguments]) async => <String, dynamic>{},
      );
      addTearDown(controller.dispose);
      controller.handleNativeTimeout(const {'reason': 'timeLimit'});
      for (var i = 0; i < 5; i++) {
        await settleTelemetry();
      }
      expect(asked, isFalse);
      expect(adapter.events, isEmpty);
    });
  });

  group('app start, jank and memory pressure', () {
    test('Android exit reasons become the contract words', () {
      expect(exitReasonWord(AndroidExitReason.crash), 'crash');
      expect(exitReasonWord(AndroidExitReason.crashNative), 'crash_native');
      expect(exitReasonWord(AndroidExitReason.anr), 'anr');
      expect(exitReasonWord(AndroidExitReason.lowMemory), 'low_memory');
      expect(
        exitReasonWord(AndroidExitReason.excessiveResourceUsage),
        'excessive_resource',
      );
      expect(exitReasonWord(AndroidExitReason.signaled), 'signaled');
      expect(exitReasonWord(AndroidExitReason.userRequested), 'user_requested');
      expect(
        exitReasonWord(AndroidExitReason.dependencyDied),
        'dependency_died',
      );
      expect(exitReasonWord(AndroidExitReason.permissionChange), 'other');
      expect(exitReasonWord(AndroidExitReason.exitSelf), isNull);
      expect(exitReasonWord(AndroidExitReason.packageUpdated), isNull);
      for (var reason = 0; reason < 20; reason++) {
        final word = exitReasonWord(reason);
        if (word != null) expect(HealthWords.exitReasons, contains(word));
      }
    });

    test('a server version keeps only major.minor', () {
      expect(majorMinorOf('2.0.28'), '2.0');
      expect(majorMinorOf('v2.1.1-beta'), '2.1');
      expect(majorMinorOf('1.14.19'), '1.14');
      expect(majorMinorOf('nightly'), isNull);
      expect(majorMinorOf(null), isNull);
    });

    test(
      'memory pressure is sent once per level, only with usage on',
      () async {
        SharedPreferences.setMockInitialValues({});
        final prefs = await SharedPreferences.getInstance();
        final adapter = RecordingAdapter();
        final service = await startedService(adapter, usage: true);
        var now = DateTime(2026, 11, 8, 12);
        final health = HealthTelemetry(
          telemetry: service,
          prefs: prefs,
          bridge: _FakeBridge(),
          now: () => now,
        );
        health.memoryPressureForTest('low');
        health.memoryPressureForTest('low');
        health.memoryPressureForTest('critical');
        health.memoryPressureForTest('catastrophic');
        now = now.add(const Duration(minutes: 6));
        health.memoryPressureForTest('low');
        await settleTelemetry();
        expect(adapter.events.map((e) => e.$2['level']), [
          'low',
          'critical',
          'low',
        ]);

        final quiet = RecordingAdapter();
        final off = HealthTelemetry(
          telemetry: await startedService(quiet, crashes: true),
          prefs: prefs,
          bridge: _FakeBridge(),
        );
        off.memoryPressureForTest('critical');
        await settleTelemetry();
        expect(quiet.events, isEmpty);
      },
    );

    test('jank is sent once per page kind, from the frame timings', () async {
      final sent = <(AnalyticsScreen, String, String)>[];
      final meter = JankMeter(
        report: (s, pct, hz) => sent.add((s, pct, hz)),
        refreshRate: () => 120,
      );
      meter.start();
      addTearDown(meter.stop);
      meter.screen(AnalyticsScreen.chat);
      // 120 Hz: the budget is 8.3 ms. 40 frames, 10 of them 20 ms.
      meter.onTimings([
        for (var i = 0; i < 40; i++) _frame(i < 10 ? 20000 : 4000),
      ]);
      meter.screen(AnalyticsScreen.settings);
      expect(sent, [(AnalyticsScreen.chat, '>15', '120')]);

      // Frames on the settings page are reported when it is left; coming
      // back to the chat page says nothing more in this session.
      meter.onTimings([for (var i = 0; i < 40; i++) _frame(30000)]);
      meter.screen(AnalyticsScreen.chat);
      expect(sent.last, (AnalyticsScreen.settings, '>15', '120'));
      meter.onTimings([for (var i = 0; i < 40; i++) _frame(30000)]);
      meter.screen(AnalyticsScreen.files);
      expect(sent, hasLength(2));

      // Too few frames are not a number.
      meter.onTimings([for (var i = 0; i < 5; i++) _frame(30000)]);
      meter.endSession();
      expect(sent, hasLength(2));
    });

    test('a smooth page reports a low janky share', () {
      final sent = <(AnalyticsScreen, String, String)>[];
      final meter = JankMeter(
        report: (s, pct, hz) => sent.add((s, pct, hz)),
        refreshRate: () => 60,
      )..start();
      addTearDown(meter.stop);
      meter.screen(AnalyticsScreen.conversations);
      meter.onTimings([
        for (var i = 0; i < 200; i++) _frame(i == 0 ? 40000 : 5000),
      ]);
      meter.endSession();
      expect(sent.single, (AnalyticsScreen.conversations, '<1', '60'));
    });

    test('with usage off nothing is measured and nothing is asked of the '
        'phone', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final bridge = _FakeBridge();
      final adapter = RecordingAdapter();
      final service = await startedService(adapter, crashes: true);
      final health = HealthTelemetry(
        telemetry: service,
        prefs: prefs,
        bridge: bridge,
        lifecycle: AppLifecycleBridge(
          channel: const MethodChannel('oc/test-none'),
        ),
        appVersion: () async => '2.1.5',
      );
      addTearDown(health.dispose);
      AppStartClock.begin();
      AppStartClock.firstFrameDone();
      health.start();
      health.warmStart();
      await settleTelemetry();
      await settleTelemetry();
      // The jank meter never registered its frame callback.
      expect(health.jank.running, isFalse);
      // The device context was asked for only for the crash keys' build
      // kind, and no usage event or property was produced.
      expect(adapter.events, isEmpty);
      expect(adapter.properties, isEmpty);

      // Turning usage on starts the meter, the context read and app_start.
      await service.setUsageConsent(true);
      await settleTelemetry();
      await settleTelemetry();
      expect(health.jank.running, isTrue);
      expect(adapter.properties['ram_range'], '4-6');
      expect(adapter.properties['abi'], 'arm64');
      expect(adapter.properties['webview_major'], '133');
      expect(adapter.properties['notif_allowed'], 'yes');
      expect(adapter.properties['free_storage_range'], '8-32');
      final start = adapter.events.singleWhere((e) => e.$1 == 'app_start');
      expect(start.$2['kind'], 'cold');
      expect(HealthRanges.appStartMs, contains(start.$2['ms_range']));

      // And off again stops it.
      await service.setUsageConsent(false);
      expect(health.jank.running, isFalse);
    });

    test('the context a crash carries is words: build kind, app version, '
        'servers', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final adapter = RecordingAdapter();
      final service = await startedService(adapter, crashes: true);
      final health = HealthTelemetry(
        telemetry: service,
        prefs: prefs,
        bridge: _FakeBridge(),
        lifecycle: AppLifecycleBridge(
          channel: const MethodChannel('oc/test-none'),
        ),
        appVersion: () async => '2.1.5',
      );
      addTearDown(health.dispose);
      health.start();
      health.updateConnection(
        serverKind: 'phone',
        engineState: 'ready',
        connections: 3,
        userServerKind: 'both',
        serverVersion: '2.0.28',
      );
      await settleTelemetry();
      await settleTelemetry();
      expect(adapter.keys, {
        'app_version': '2.1.5',
        'build_kind': 'sideload',
        'server_kind': 'phone',
        'engine_state': 'ready',
        'connections': '2-3',
        'oc_server_version': '2.0',
      });
      // No usage consent: no property left the phone.
      expect(adapter.properties, isEmpty);
    });

    test('the last process end is reported once, as a kind', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final adapter = RecordingAdapter();
      final service = await startedService(adapter, crashes: true);
      final lifecycle = _FakeLifecycle([
        (reason: AndroidExitReason.exitSelf, at: 1000),
        (reason: AndroidExitReason.anr, at: 3000),
        (reason: AndroidExitReason.lowMemory, at: 2000),
      ]);
      HealthTelemetry make() => HealthTelemetry(
        telemetry: service,
        prefs: prefs,
        bridge: _FakeBridge(),
        lifecycle: lifecycle,
        appVersion: () async => '2.1.5',
      );
      final first = make()..start();
      addTearDown(first.dispose);
      for (var i = 0; i < 5; i++) {
        await settleTelemetry();
      }
      expect(adapter.failures.map((f) => '${f.$1} ${f.$2}'), [
        'process_exit {exit_reason: anr}',
      ]);

      // The next start sees the same record and says nothing.
      final second = make()..start();
      addTearDown(second.dispose);
      for (var i = 0; i < 5; i++) {
        await settleTelemetry();
      }
      expect(adapter.failures, hasLength(1));
    });

    test('with crashes off the exit record is not even read', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final lifecycle = _FakeLifecycle([
        (reason: AndroidExitReason.anr, at: 5),
      ]);
      final service = await startedService(RecordingAdapter(), usage: true);
      final health = HealthTelemetry(
        telemetry: service,
        prefs: prefs,
        bridge: _FakeBridge(),
        lifecycle: lifecycle,
        appVersion: () async => '2.1.5',
      )..start();
      addTearDown(health.dispose);
      for (var i = 0; i < 5; i++) {
        await settleTelemetry();
      }
      expect(lifecycle.reads, 0);
    });
  });

  group('send a problem report', () {
    test('redaction removes a key, a URL, a host and a path', () {
      const secret = 'sk-ant-api03-REDACTMEKEYabcdefghijklmnop';
      final out = ReportRedaction.redact('''
12:00:01 event conn_attempt result=fail
12:00:02 error with key $secret in config
12:00:03 GET https://admin:hunter22@my-box.tail1234.ts.net:4096/session/ses_1?token=abc123 failed
12:00:04 server my-box.tail1234.ts.net refused 192.168.1.20:4096
12:00:05 wrote /data/user/0/io.github.eslamasabry.opencode_mobile/files/home/secret-project/x.dart
12:00:06 at #0 Foo.bar (package:opencode_mobile/state/connection.dart:120:7)
12:00:07 contact me@example.com
12:00:08 loaded C:\\Users\\someone\\proj\\file.txt and ~/work/private
''');
      expect(out, isNot(contains('REDACTME')));
      expect(out, isNot(contains('hunter22')));
      expect(out, isNot(contains('my-box')));
      expect(out, isNot(contains('tail1234')));
      expect(out, isNot(contains('192.168.1.20')));
      expect(out, isNot(contains('/data/user/0')));
      expect(out, isNot(contains('secret-project')));
      expect(out, isNot(contains('me@example.com')));
      expect(out, isNot(contains('someone')));
      expect(out, isNot(contains('private')));
      expect(out, isNot(contains('abc123')));
      // What a developer reads a crash by stays.
      expect(
        out,
        contains('package:opencode_mobile/state/connection.dart:120:7'),
      );
      expect(out, contains('event conn_attempt result=fail'));
      expect(ReportRedaction.redact(out), out, reason: 'idempotent');
    });

    test('redaction also masks registered secrets', () {
      KitRedact.registerKnownSecret('correct-horse-battery');
      expect(
        ReportRedaction.redact('password was correct-horse-battery today'),
        isNot(contains('correct-horse')),
      );
    });

    test('the report holds the last 500 log lines, keys and exits, already '
        'redacted', () async {
      final adapter = RecordingAdapter();
      final service = await startedService(adapter, crashes: true);
      AppTelemetry.install(service);
      service.setHealthKeys({'build_kind': 'store'});
      final ring = AppLogRing(capacity: 500);
      for (var i = 0; i < 700; i++) {
        ring.add('line $i');
      }
      ring.add('opened http://10.0.0.5:4096/x and /home/me/secret');
      final collector = ProblemReportCollector(
        telemetry: service,
        ring: ring,
        lifecycle: _FakeLifecycle([
          (reason: AndroidExitReason.crash, at: 90000),
        ]),
        now: () => DateTime.utc(2026, 11, 8, 12),
      );
      final text = await collector.build();
      expect(text, contains('build_kind=store'));
      expect(text, contains('crash'));
      expect(text, contains('Log (last 500 lines)'));
      expect(text, contains('line 699'));
      expect(text, isNot(contains('line 100\n')));
      expect(text, isNot(contains('10.0.0.5')));
      expect(text, isNot(contains('/home/me')));
      expect(
        text.split('\n').where((l) => l.startsWith('  ')).length,
        lessThan(520),
      );
    });

    test(
      'sending uploads the previewed text once and returns the code',
      () async {
        final adapter = RecordingAdapter();
        // Both switches off: a problem report is asked every time.
        final reports = MemoryTelemetryReportStore();
        final service = await startedService(adapter, reports: reports);
        final first = await service.sendProblemReport('the previewed text');
        final second = await service.sendProblemReport('another');
        expect(first, 'abcdef01-1');
        expect(second, 'abcdef01-2');
        expect(adapter.reports.map((r) => (r.text, r.collecting)), [
          ('the previewed text', false),
          ('another', false),
        ]);
        expect(await service.reportCode(), 'abcdef01-2');
        // Nothing else was sent with the switches off.
        expect(adapter.events, isEmpty);
        expect(adapter.crashes, isEmpty);
      },
    );

    test(
      'without an install id from the vendor a local code is kept',
      () async {
        final adapter = RecordingAdapter(instanceId: null);
        final reports = MemoryTelemetryReportStore();
        final service = await startedService(adapter, reports: reports);
        final code = await service.sendProblemReport('x');
        expect(code, matches(RegExp(r'^[0-9a-f]{8}-1$')));
        final again = await service.reportCode();
        expect(again, startsWith(code!.substring(0, 8)));
      },
    );

    test(
      'a build without the vendor sends no report and has no code',
      () async {
        final adapter = RecordingAdapter(present: false);
        final service = await startedService(adapter);
        expect(await service.sendProblemReport('x'), isNull);
        expect(await service.reportCode(), isNull);
        expect(adapter.reports, isEmpty);
      },
    );
  });
}

FrameTiming _frame(int totalMicros) => FrameTiming(
  vsyncStart: 0,
  buildStart: 0,
  buildFinish: totalMicros ~/ 2,
  rasterStart: totalMicros ~/ 2,
  rasterFinish: totalMicros,
  rasterFinishWallTime: totalMicros,
);

class _FakeBridge extends HealthBridge {
  _FakeBridge() : super(channel: const MethodChannel('oc/test-health'));
  int contextReads = 0;

  @override
  Future<HealthDeviceContext?> deviceContext() async {
    contextReads++;
    return const HealthDeviceContext(
      ramBytes: 5 * 1024 * 1024 * 1024,
      freeBytes: 12 * 1024 * 1024 * 1024,
      abi: 'arm64',
      webviewMajor: 133,
      notificationsAllowed: true,
      processUptimeMs: 2500,
    );
  }

  @override
  void listenMemoryPressure(void Function(String level) onLevel) {}
}

class _FakeLifecycle extends AppLifecycleBridge {
  _FakeLifecycle(this.exits)
    : super(channel: const MethodChannel('oc/test-lifecycle'));
  final List<({int reason, int at})> exits;
  int reads = 0;

  @override
  Future<AppExitHistory> exitHistory({int limit = 10}) async {
    reads++;
    return AppExitHistory(
      supported: true,
      entries: [
        for (final e in exits)
          AppExitEntry(
            reason: e.reason,
            importance: 100,
            at: DateTime.fromMillisecondsSinceEpoch(e.at, isUtc: true),
            category: AppExitCategory.crash,
          ),
      ],
    );
  }
}
