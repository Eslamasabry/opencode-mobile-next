import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/phone_budget.dart';
import 'package:opencode_mobile/builtin/app_exit_recovery.dart'
    show appLifecycleBridgeProvider;
import 'package:opencode_mobile/builtin/heat_tier.dart';
import 'package:opencode_mobile/builtin/thermal_guard_teams.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/platform/thermal.dart';
import 'package:opencode_mobile/ui/screens/keep_running_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Lifecycle extends AppLifecycleBridge {
  @override
  Future<KeepAliveInfo> keepAliveInfo() async =>
      const KeepAliveInfo(manufacturer: 'Google', brand: 'google');
}

void main() {
  tearDown(() => heatThrottle = null);

  testWidgets(
    'Always full speed: off by default, saved, and acted on at once',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final en = lookupAppLocalizations(const Locale('en'));
      final prefs = await SharedPreferences.getInstance();
      final nice = <int>[];
      final throttle = AgentThrottle(
        fullSpeed: () => prefs.getBool(FullSpeedPreference.key) ?? false,
        run: (n) async {
          nice.add(n);
          return 1;
        },
        battery: () async => BatterySnapshot.unknown,
        powerSave: () async => false,
      );
      addTearDown(throttle.dispose);
      heatThrottle = throttle;
      await throttle.observe(
        const ThermalReading(status: ThermalStatus.none, headroom: 0.8),
      );
      expect(nice, [AgentThrottle.slowNice]);
      tester.view
        ..physicalSize = const Size(412, 915)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            appLifecycleBridgeProvider.overrideWithValue(_Lifecycle()),
          ],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const Scaffold(
              body: SingleChildScrollView(child: KeepRunningSection()),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text(en.fullSpeedSetting),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      // The phone is warm: the line says builds are running slower.
      expect(find.text(en.fullSpeedSettingEasing), findsOneWidget);
      final toggle = find.byKey(
        const ValueKey('keep-running-full-speed-switch'),
      );
      expect(tester.widget<Switch>(toggle).value, isFalse);
      await tester.tap(find.text(en.fullSpeedSetting));
      await tester.pumpAndSettle();
      expect(tester.widget<Switch>(toggle).value, isTrue);
      expect(prefs.getBool(FullSpeedPreference.key), isTrue);
      expect(nice, [AgentThrottle.slowNice, 0], reason: 'restored at once');
    },
  );
}
