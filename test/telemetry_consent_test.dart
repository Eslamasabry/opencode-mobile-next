// The one-time "Help improve OpenCode Mobile" question
// (lib/ui/widgets/telemetry_consent.dart): shown once, its answer sets the
// crash-report and analytics consent, and "Not now" never asks again.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/telemetry/telemetry.dart';
import 'package:opencode_mobile/telemetry/telemetry_provider.dart';
import 'package:opencode_mobile/ui/widgets/telemetry_consent.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Vendor implements TelemetryAdapter {
  _Vendor({this.present = true});
  final bool present;
  bool? collecting;
  final events = <String>[];
  @override
  Future<bool> probe() async => present;
  @override
  Future<void> setCollection({
    required bool usage,
    required bool crashes,
  }) async {
    usageOn = usage;
    crashesOn = crashes;
    collecting = usage || crashes;
  }

  bool? usageOn;
  bool? crashesOn;
  @override
  Future<void> crash(String c, String s, {required bool fatal}) async {}
  @override
  Future<void> failure(String n, Map<String, String> k) async {}
  @override
  Future<void> breadcrumb(String n) async {}
  @override
  Future<void> event(String n, Map<String, String> p) async =>
      events.add('$n ${p.entries.map((e) => '${e.key}=${e.value}').join(',')}');
  @override
  Future<void> setKeys(Map<String, String> keys) async => events.add(
    'keys|${keys.entries.map((e) => '${e.key}=${e.value}').join(',')}',
  );
  @override
  Future<void> setUserProperties(
    Map<String, String> properties,
  ) async => events.add(
    'props|${properties.entries.map((e) => '${e.key}=${e.value}').join(',')}',
  );
  @override
  Future<void> problemReport(String text, {required bool collecting}) async =>
      events.add('report|$collecting|${text.length}');
  @override
  Future<String?> appInstanceId() async => 'abcdef0123456789';
}

Future<TelemetryService> _service({
  bool present = true,
  _Vendor? vendor,
}) async {
  final service = TelemetryService(
    adapter: vendor ?? _Vendor(present: present),
  );
  await service.start();
  return service;
}

void main() {
  late SharedPreferences prefs;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
  });

  /// Pumps a page whose button asks, and returns the button finder.
  Future<Finder> pump(WidgetTester tester, Telemetry telemetry) async {
    tester.view.physicalSize = const Size(412, 915) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => Center(
            child: TextButton(
              onPressed: () => askTelemetryConsentOnce(
                context,
                prefs: prefs,
                telemetry: telemetry,
              ),
              child: const Text('ask'),
            ),
          ),
        ),
      ),
    );
    return find.text('ask');
  }

  testWidgets('Share both turns both consents on, once', (tester) async {
    final telemetry = await _service();
    final ask = await pump(tester, telemetry);
    await tester.tap(ask);
    await tester.pumpAndSettle();
    expect(find.text('Help improve OpenCode Mobile?'), findsOneWidget);
    expect(find.textContaining('Never sent: your code'), findsOneWidget);
    expect(telemetry.consented, isFalse);

    expect(find.text('Share both'), findsOneWidget);
    expect(find.text('Not now'), findsOneWidget);
    expect(find.text('Choose'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('telemetry-consent-share')));
    await tester.pumpAndSettle();
    expect(telemetry.usageConsented, isTrue);
    expect(telemetry.crashesConsented, isTrue);
    expect(prefs.getBool(telemetryConsentAskedKey), isTrue);

    // Asked once: a second start does not ask again.
    await tester.tap(ask);
    await tester.pumpAndSettle();
    expect(find.text('Help improve OpenCode Mobile?'), findsNothing);
  });

  testWidgets('saying yes counts this start as a cold open; not now sends '
      'nothing', (tester) async {
    // Found on the emulator: the cold open is counted before the answer and
    // dropped, so a first run that says yes had no app_open.
    final vendor = _Vendor();
    final telemetry = await _service(vendor: vendor);
    final ask = await pump(tester, telemetry);
    await tester.tap(ask);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('telemetry-consent-share')));
    await tester.pumpAndSettle();
    await tester.pump();
    expect(vendor.events, ['app_open kind=cold']);

    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    final quiet = _Vendor();
    final declined = await _service(vendor: quiet);
    final again = await pump(tester, declined);
    await tester.tap(again);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Not now'));
    await tester.pumpAndSettle();
    expect(quiet.events, isEmpty);
  });

  testWidgets('Choose opens the two switches, both off, and saves exactly '
      'what was set', (tester) async {
    final vendor = _Vendor();
    final telemetry = await _service(vendor: vendor);
    final ask = await pump(tester, telemetry);
    await tester.tap(ask);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('telemetry-consent-choose')));
    await tester.pumpAndSettle();
    expect(find.text('Usage and app health'), findsOneWidget);
    expect(find.text('Crash reports'), findsOneWidget);
    expect(telemetry.consented, isFalse);

    await tester.tap(find.byKey(const ValueKey('telemetry-consent-crashes')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('telemetry-consent-save')));
    await tester.pumpAndSettle();
    expect(telemetry.usageConsented, isFalse);
    expect(telemetry.crashesConsented, isTrue);
    expect(vendor.usageOn, isFalse);
    expect(vendor.crashesOn, isTrue);
    expect(prefs.getBool(telemetryConsentAskedKey), isTrue);
    // No usage was agreed, so this start is not counted as an open.
    await tester.pump();
    expect(vendor.events, isEmpty);
  });

  testWidgets('Choose and closing without saving leaves both off', (
    tester,
  ) async {
    final telemetry = await _service();
    final ask = await pump(tester, telemetry);
    await tester.tap(ask);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('telemetry-consent-choose')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('telemetry-consent-usage')));
    await tester.pumpAndSettle();
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(telemetry.consented, isFalse);
  });

  testWidgets('Not now leaves consent off and never asks again', (
    tester,
  ) async {
    final telemetry = await _service();
    final ask = await pump(tester, telemetry);
    await tester.tap(ask);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Not now'));
    await tester.pumpAndSettle();
    expect(find.text('Help improve OpenCode Mobile?'), findsNothing);
    expect(telemetry.consented, isFalse);

    for (var i = 0; i < 3; i++) {
      await tester.tap(ask);
      await tester.pumpAndSettle();
      expect(find.text('Help improve OpenCode Mobile?'), findsNothing);
    }
    expect(telemetry.consented, isFalse);
  });

  testWidgets('a build without a vendor never asks, and asks later once one '
      'is built in', (tester) async {
    final none = await _service(present: false);
    var ask = await pump(tester, none);
    await tester.tap(ask);
    await tester.pumpAndSettle();
    expect(find.text('Help improve OpenCode Mobile?'), findsNothing);
    expect(prefs.getBool(telemetryConsentAskedKey), isNull);

    ask = await pump(tester, await _service());
    await tester.tap(ask);
    await tester.pumpAndSettle();
    expect(find.text('Help improve OpenCode Mobile?'), findsOneWidget);
  });

  testWidgets('someone who already turned it on is not asked', (tester) async {
    final telemetry = await _service();
    await telemetry.setConsent(true);
    final ask = await pump(tester, telemetry);
    await tester.tap(ask);
    await tester.pumpAndSettle();
    expect(find.text('Help improve OpenCode Mobile?'), findsNothing);
    expect(prefs.getBool(telemetryConsentAskedKey), isTrue);
  });
}
