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
  @override
  Future<bool> probe() async => present;
  @override
  Future<void> setCollectionEnabled(bool enabled) async => collecting = enabled;
  @override
  Future<void> crash(String c, String s, {required bool fatal}) async {}
  @override
  Future<void> failure(String n, Map<String, String> k) async {}
  @override
  Future<void> breadcrumb(String n) async {}
  @override
  Future<void> event(String n, Map<String, String> p) async {}
}

Future<TelemetryService> _service({bool present = true}) async {
  final service = TelemetryService(adapter: _Vendor(present: present));
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

  testWidgets('Share anonymous usage turns consent on, once', (tester) async {
    final telemetry = await _service();
    final ask = await pump(tester, telemetry);
    await tester.tap(ask);
    await tester.pumpAndSettle();
    expect(find.text('Help improve OpenCode Mobile?'), findsOneWidget);
    expect(find.textContaining('Never sent: your code'), findsOneWidget);
    expect(telemetry.consented, isFalse);

    await tester.tap(find.byKey(const ValueKey('telemetry-consent-share')));
    await tester.pumpAndSettle();
    expect(telemetry.consented, isTrue);
    expect(prefs.getBool(telemetryConsentAskedKey), isTrue);

    // Asked once: a second start does not ask again.
    await tester.tap(ask);
    await tester.pumpAndSettle();
    expect(find.text('Help improve OpenCode Mobile?'), findsNothing);
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
