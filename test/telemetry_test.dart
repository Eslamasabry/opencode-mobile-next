// The vendor-neutral telemetry layer (lib/telemetry): consent gate, cleaning
// of everything an adapter receives, adapter swap through the provider, and
// the Settings switch. The secret markers are built from adjacent literals,
// like test/coverage/diag_privacy_test.dart, so no whole key is in this file.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/telemetry/telemetry.dart';
import 'package:opencode_mobile/telemetry/telemetry_provider.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Recorder implements TelemetryAdapter {
  _Recorder({this.present = true});
  final bool present;
  final calls = <String>[];
  bool? collecting;

  @override
  Future<bool> probe() async => present;
  @override
  Future<void> setCollectionEnabled(bool enabled) async => collecting = enabled;
  @override
  Future<void> crash(
    String category,
    String stack, {
    required bool fatal,
  }) async => calls.add('crash|$category|$fatal|$stack');
  @override
  Future<void> failure(
    String name,
    Map<String, String> keys,
  ) async => calls.add(
    'failure|$name|${keys.entries.map((e) => '${e.key}=${e.value}').join(',')}',
  );
  @override
  Future<void> breadcrumb(String name) async => calls.add('crumb|$name');
  @override
  Future<void> event(String name, Map<String, String> props) async =>
      calls.add('event|$name');
}

const _anthropic = 'sk-ant-api03-TELEMKEYONEabcdefghijklmnop';
const _github = 'ghp_TELEMGITHUBtoken0123456789abcdefgh';
const _url =
    'https://admin:TELEMURLPASSword@vps.example.net:4096/x?token=TELEMQUERYtoken99';

Future<TelemetryService> _service(
  _Recorder adapter, {
  bool consent = false,
}) async {
  final service = TelemetryService(
    adapter: adapter,
    store: MemoryTelemetryConsentStore(consent),
  );
  await service.start();
  return service;
}

void main() {
  tearDown(() {
    AppTelemetry.reset();
    KitRedact.clearKnownSecrets();
  });

  test(
    'consent is off by default and the adapter receives no report',
    () async {
      final adapter = _Recorder();
      final service = await _service(adapter);
      expect(service.consented, isFalse);
      expect(service.available, isTrue);
      expect(adapter.collecting, isFalse);

      service.recordCrash('Invalid state', StackTrace.current, fatal: true);
      service.recordFailure('setup_step_failed', {'step': 'node', 'exit': 127});
      service.breadcrumb('open_settings');
      await Future<void>.delayed(Duration.zero);
      expect(adapter.calls, isEmpty);
    },
  );

  test('turning consent on starts collection and off stops it again', () async {
    final adapter = _Recorder();
    final service = await _service(adapter);
    await service.setConsent(true);
    expect(adapter.collecting, isTrue);
    service.recordFailure('setup_step_failed', {'step': 'node', 'exit': 127});
    await Future<void>.delayed(Duration.zero);
    expect(adapter.calls, ['failure|setup_step_failed|step=node,exit=127']);

    await service.setConsent(false);
    expect(adapter.collecting, isFalse);
    service.recordFailure('setup_step_failed', {'step': 'node'});
    await Future<void>.delayed(Duration.zero);
    expect(adapter.calls, hasLength(1));
  });

  test('a build without the vendor never reports, even with consent', () async {
    final adapter = _Recorder(present: false);
    final service = await _service(adapter, consent: true);
    expect(service.available, isFalse);
    service.recordFailure('x_failed');
    await Future<void>.delayed(Duration.zero);
    expect(adapter.calls, isEmpty);
    expect(adapter.collecting, isNull);
  });

  test('values that are not short tokens are dropped, never cleaned', () async {
    final adapter = _Recorder();
    final service = await _service(adapter, consent: true);
    service.recordFailure('prompt_not_accepted', {
      'kind': 'signIn',
      'message': 'Insufficient credits for account a@b.c',
      'url': _url,
      'token': _github,
      'key': _anthropic,
      'path': '/home/someone/project/secret.txt',
      'Bad Key': 'ok',
    });
    await Future<void>.delayed(Duration.zero);
    final all = adapter.calls.join('\n');
    expect(all, contains('kind=signIn'));
    for (final secret in [
      'TELEMKEYONE',
      'TELEMGITHUB',
      'TELEMURLPASS',
      'TELEMQUERY',
      'Insufficient',
      '/home/someone',
      'Bad Key',
    ]) {
      expect(all, isNot(contains(secret)));
    }
  });

  test('a crash keeps its category and code locations, nothing else', () async {
    final adapter = _Recorder();
    final service = await _service(adapter, consent: true);
    final stack = StackTrace.fromString('''
#0      Foo.bar (package:opencode_mobile/a/b.dart:12:3)
#1      _secretFrame (file:///home/someone/work/$_github/x.dart:9:9)
#2      main (package:opencode_mobile/main.dart:5:1)
Authorization: Bearer TELEMBEARERtoken.abc
''');
    service.recordCrash('Invalid state', stack, fatal: true);
    service.recordCrash('Bad thing: $_anthropic at $_url', stack);
    await Future<void>.delayed(Duration.zero);
    expect(adapter.calls.first, startsWith('crash|invalid_state|true|'));
    expect(
      adapter.calls.first,
      contains('package:opencode_mobile/a/b.dart:12:3'),
    );
    final all = adapter.calls.join('\n');
    for (final secret in [
      'TELEMKEYONE',
      'TELEMGITHUB',
      'TELEMURLPASS',
      'TELEMBEARER',
      '/home/someone',
      'file://',
    ]) {
      expect(all, isNot(contains(secret)));
    }
  });

  test('provider override swaps the vendor behind the interface', () async {
    final adapter = _Recorder();
    final service = await _service(adapter, consent: true);
    final container = ProviderContainer(
      overrides: [telemetryProvider.overrideWithValue(service)],
    );
    addTearDown(container.dispose);
    container.read(telemetryProvider).recordFailure('model_download_failed');
    await Future<void>.delayed(Duration.zero);
    expect(adapter.calls, ['failure|model_download_failed|']);
  });

  test(
    'consent persists in preferences and is read at the next start',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final first = TelemetryService(
        adapter: _Recorder(),
        store: PreferencesTelemetryConsentStore(prefs),
      );
      await first.start();
      await first.setConsent(true);
      final adapter = _Recorder();
      final second = TelemetryService(
        adapter: adapter,
        store: PreferencesTelemetryConsentStore(prefs),
      );
      await second.start();
      expect(second.consented, isTrue);
      expect(adapter.collecting, isTrue);
    },
  );

  group('Settings › Privacy and data switch', () {
    Future<ConnectionController> controller() async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      return ConnectionController(ProfileStore(prefs: prefs));
    }

    Future<void> pump(WidgetTester tester, Telemetry telemetry) async {
      final c = await controller();
      addTearDown(c.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [telemetryProvider.overrideWithValue(telemetry)],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: PrivacySettingsScreen(controller: c),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('hidden when the build has no vendor', (tester) async {
      await pump(tester, await _service(_Recorder(present: false)));
      expect(
        find.byKey(const ValueKey('privacy-send-telemetry')),
        findsNothing,
      );
    });

    testWidgets('off by default; the switch drives consent', (tester) async {
      final adapter = _Recorder();
      final service = await _service(adapter);
      await pump(tester, service);
      final row = find.byKey(const ValueKey('privacy-send-telemetry'));
      await tester.scrollUntilVisible(
        row,
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Send reports to developer'), findsOneWidget);
      expect(service.consented, isFalse);
      await tester.tap(row);
      await tester.pumpAndSettle();
      expect(service.consented, isTrue);
      expect(adapter.collecting, isTrue);
      await tester.tap(row);
      await tester.pumpAndSettle();
      expect(service.consented, isFalse);
      expect(adapter.collecting, isFalse);
    });
  });
}
