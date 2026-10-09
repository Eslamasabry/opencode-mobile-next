import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/crash_report.dart';
import 'package:opencode_mobile/diagnostics/device_diagnostics_gateway.dart';
import 'package:opencode_mobile/domain/app_diagnostics_gateway.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test/device_diagnostics_lifecycle');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late Directory temporary;
  late AppDiagnosticsController diagnostics;
  late CrashDiagnosticsController captures;
  late BackgroundLiveController background;
  late DeviceDiagnosticsGateway gateway;
  late List<String> shared;
  late List<String> lifecycleCalls;
  var failPerformance = false;

  setUp(() async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    SharedPreferences.setMockInitialValues({});
    temporary = Directory.systemTemp.createTempSync('device-diagnostics-');
    diagnostics = AppDiagnosticsController();
    captures = CrashDiagnosticsController.open(
      directory: temporary,
      diagnostics: diagnostics,
    );
    background = BackgroundLiveController(
      preferences: await SharedPreferences.getInstance(),
      invoke: (method, [arguments]) async {
        if (method == 'getBackgroundPause') {
          return const {
            'supported': true,
            'active': false,
            'paused': false,
            'reason': 'none',
            'at': null,
            'canResume': false,
          };
        }
        return const {'enabled': false, 'active': false};
      },
    );
    shared = [];
    lifecycleCalls = [];
    failPerformance = false;
    messenger.setMockMethodCallHandler(channel, (call) async {
      lifecycleCalls.add(call.method);
      return {
        'supported': true,
        'entries': [
          {
            'reason': AndroidExitReason.anr,
            'importance': 100,
            'timestamp': 1700000000000,
            'description': 'private-unregistered-provider-value',
          },
        ],
      };
    });
    gateway = DeviceDiagnosticsGateway(
      readPerformance: () async {
        if (failPerformance) throw StateError('private native details');
        return BuiltinPerformance.fromMap({
          'services': {
            'agent-host.private-profile': {
              'running': false,
              'lastExitCode': 137,
              'lastExitAtMs': 1700000001000,
              'lastStopRequested': false,
              'exitReason': 'memory_or_phantom_kill',
            },
          },
        });
      },
      lifecycle: AppLifecycleBridge(channel: channel),
      background: background,
      reports: CrashReportBuilder(
        loadController: () async => captures,
        shareText: (text) async {
          shared.add(text);
          return true;
        },
      ),
    );
  });

  test(
    'BD13 device diagnostics separates helper deaths from Android app exits',
    () async {
      final history = await gateway.exitHistory();
      expect(history.entries, hasLength(1));
      expect(history.entries.single.category, AppExitCategory.crash);
      expect(history.helperExits, hasLength(1));
      expect(history.helperExits.single.exitCode, 137);
      expect(history.helperExits.single.description, contains('Agent helper'));
      expect(
        history.helperExits.single.description,
        isNot(contains('private-profile')),
      );
      failPerformance = true;
      final fallback = await gateway.exitHistory();
      expect(fallback.entries, hasLength(1));
      expect(fallback.helperExits, isEmpty);
    },
  );

  tearDown(() {
    gateway.dispose();
    background.dispose();
    captures.dispose();
    diagnostics.dispose();
    messenger.setMockMethodCallHandler(channel, null);
    temporary.deleteSync(recursive: true);
    debugDefaultTargetPlatformOverride = null;
  });

  test(
    'gateway history is repeatable and never consumes launch recovery',
    () async {
      for (var i = 0; i < 2; i++) {
        final history = await gateway.exitHistory(limit: 1);
        expect(history.error, isNull);
        expect(history.entries.single.category, AppExitCategory.crash);
        expect(history.entries.single.at.isUtc, isTrue);
        expect(
          history.entries.single.description,
          isNot(contains('private-unregistered-provider-value')),
        );
      }
      expect(lifecycleCalls, ['exitHistory', 'exitHistory']);
    },
  );

  test(
    'gateway preview stays off until consent and shares exact safe text',
    () async {
      expect(
        (await gateway.previewCrashReport()).error,
        DiagnosticsError.captureDisabled,
      );
      expect(shared, isEmpty);
      expect(captures.enabled, isFalse);
      expect(captures.setEnabled(true), isTrue);
      captures.capture(
        StateError('private-unregistered-provider-value'),
        StackTrace.fromString('secret/path and conversation'),
        'flutter',
      );
      final report = await gateway.previewCrashReport();
      expect(report.error, isNull);
      final preview = report.preview!;
      expect(preview.recordCount, 1);
      expect(
        preview.text,
        isNot(contains('private-unregistered-provider-value')),
      );
      expect(preview.text, isNot(contains('secret/path')));
      expect(shared, isEmpty);
      expect(gateway.shareSupported, isTrue);
      expect((await gateway.shareCrashReport(preview)).opened, isTrue);
      expect(shared, [preview.text]);
    },
  );

  test('clearing captured evidence invalidates a gateway preview', () async {
    captures.setEnabled(true);
    captures.capture(StateError('secret'), null, 'flutter');
    final preview = (await gateway.previewCrashReport()).preview!;
    expect(captures.clear(), isTrue);
    final result = await gateway.shareCrashReport(preview);
    expect(result.opened, isFalse);
    expect(result.error, DiagnosticsError.stalePreview);
    expect(shared, isEmpty);
  });

  test('gateway notifies listeners of the existing background owner pause', () {
    var notices = 0;
    gateway.addListener(() => notices++);
    background.handleNativeTimeout({'reason': 'systemTimeout'});
    expect(gateway.backgroundPause.paused, isTrue);
    expect(gateway.backgroundPause.reason, BackgroundPauseReason.timeLimit);
    expect(notices, greaterThan(0));
    expect(background.active, isFalse);
  });
}
