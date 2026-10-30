// Settings › Appearance › Text size: the app's own multiplier sits on top of
// the phone's font scale, is applied once at the app root, survives a restart,
// changes live, and leaves the main screens whole (no overflow) and every
// touch target at least 48dp at the smallest size.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'revamp/screen_phone_1_fixtures.dart';

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];

  @override
  String? get activeId => null;
}

class _NoUpdateService implements AppUpdateService {
  @override
  bool get isAvailable => false;
  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;
  @override
  Future<void> downloadUpdate() async {}
}

Future<(ConnectionController, _MemoryProfileStore)> _controller([
  Map<String, Object> saved = const {},
]) async {
  SharedPreferences.setMockInitialValues(saved);
  final store = _MemoryProfileStore(
    prefs: await SharedPreferences.getInstance(),
  );
  return (ConnectionController(store), store);
}

Widget _app(ConnectionController controller) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
    connProvider.overrideWithValue(controller),
  ],
  child: OcApp(updateService: _NoUpdateService()),
);

double _scale(WidgetTester tester) => MediaQuery.textScalerOf(
  tester.element(find.byType(Scaffold).first),
).scale(10);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  group('stored', () {
    test('Default until chosen; a choice survives a restart', () async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      expect(controller.textSize.value, KitTextSize.normal);
      await controller.setTextSize(KitTextSize.smallest);
      expect(controller.textSize.value, KitTextSize.smallest);
      expect(store.prefs.getString('oc.textSize'), 'smallest');

      final restarted = ConnectionController(ProfileStore(prefs: store.prefs));
      addTearDown(restarted.dispose);
      expect(restarted.textSize.value, KitTextSize.smallest);
      expect(KitTextSize.smallest.factor, 0.85);
      expect(KitTextSize.small.factor, 0.92);
      expect(KitTextSize.large.factor, 1.15);
    });
  });

  group('at the app root', () {
    testWidgets('multiplies the phone scale and follows a change live', (
      tester,
    ) async {
      final (controller, _) = await _controller({'oc.textSize': 'smallest'});
      addTearDown(controller.dispose);
      tester.platformDispatcher.textScaleFactorTestValue = 1.2;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      await tester.pumpWidget(_app(controller));
      await tester.pump(const Duration(milliseconds: 400));
      expect(_scale(tester), closeTo(12 * 0.85, 0.01));

      await controller.setTextSize(KitTextSize.large);
      await tester.pump();
      // Eases rather than jumps...
      await tester.pump(const Duration(milliseconds: 100));
      final mid = _scale(tester);
      expect(mid, greaterThan(12 * 0.85));
      expect(mid, lessThan(12 * 1.15));
      // ...and lands on the choice.
      await tester.pump(const Duration(milliseconds: 400));
      expect(_scale(tester), closeTo(12 * 1.15, 0.01));
    });

    testWidgets('the cap on runaway scales still wins', (tester) async {
      final (controller, _) = await _controller({'oc.textSize': 'large'});
      addTearDown(controller.dispose);
      tester.platformDispatcher.textScaleFactorTestValue = 2.4;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      await tester.pumpWidget(_app(controller));
      await tester.pump(const Duration(milliseconds: 400));
      expect(_scale(tester), closeTo(AppTheme.maxTextScale * 10, 0.01));
    });
  });

  group('Settings › Appearance › Text size', () {
    testWidgets('four choices; picking one saves it and keeps targets 48dp', (
      tester,
    ) async {
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          theme: AppTheme.dark(),
          builder: (context, child) => KitText.appScale(
            size: controller.textSize,
            max: AppTheme.maxTextScale,
            child: child!,
          ),
          home: AppearanceSettingsScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      // Text & motion opens Text size straight away.
      final tab = find.byKey(const ValueKey('appearance-group-comfort'));
      await Scrollable.ensureVisible(tester.element(tab));
      await tester.pumpAndSettle();
      await tester.tap(tab);
      await tester.pumpAndSettle();
      for (final name in ['smallest', 'small', 'normal', 'large']) {
        expect(find.byKey(ValueKey('text-size-$name')), findsOneWidget);
      }
      expect(find.text('Text size'), findsOneWidget);
      await tester.ensureVisible(
        find.byKey(const ValueKey('text-size-smallest')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('text-size-smallest')));
      await tester.pumpAndSettle();
      expect(controller.textSize.value, KitTextSize.smallest);
      expect(tester.takeException(), isNull);
      for (final name in ['smallest', 'small', 'normal', 'large']) {
        final size = tester.getSize(find.byKey(ValueKey('text-size-$name')));
        expect(size.height, greaterThanOrEqualTo(48), reason: name);
      }
    });
  });

  group('main screens at Smallest', () {
    Future<void> settle(WidgetTester tester) async {
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
    }

    Widget smallest(Widget child) => KitText.appScale(
      size: ValueNotifier(KitTextSize.smallest),
      max: AppTheme.maxTextScale,
      child: child,
    );

    testWidgets('Chats home', (tester) async {
      late ConnectionController controller;
      await pumpPhone(
        tester,
        home: smallest(const HomeScreen(initialTab: 0)),
        configure: (c) => controller = c,
      );
      await settle(tester);
      expect(tester.takeException(), isNull);
      expect(find.byType(HomeScreen), findsOneWidget);
      await unmountPhone(tester);
      // Home polls for sessions; stop it so no timer outlives the test.
      controller.dispose();
    });

    testWidgets('Settings', (tester) async {
      await pumpPhone(
        tester,
        home: smallest(
          Consumer(
            builder: (context, ref, _) =>
                SettingsScreen(controller: ref.watch(connProvider)),
          ),
        ),
      );
      await settle(tester);
      expect(tester.takeException(), isNull);
      expect(find.byType(SettingsScreen), findsOneWidget);
    });

    testWidgets('This phone', (tester) async {
      await pumpPhone(
        tester,
        home: smallest(const ThisPhoneScreen(kind: PhoneHostKind.termux)),
      );
      await settle(tester);
      expect(tester.takeException(), isNull);
      expect(find.byType(ThisPhoneScreen), findsOneWidget);
    });
  });
}
