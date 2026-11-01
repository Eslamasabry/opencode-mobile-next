import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/whats_new.dart';
import 'package:opencode_mobile/ui/screens/settings/tips_screen.dart';
import 'package:opencode_mobile/ui/teaching/teaching.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/settings/updates_screen.dart';
import 'package:opencode_mobile/ui/screens/whats_new_places.dart';
import 'package:opencode_mobile/ui/screens/whats_new_sheet.dart';
import 'package:opencode_mobile/update/app_update_controller.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/app_update_fakes.dart';

Widget _app(Widget home, {Locale locale = const Locale('en')}) => MaterialApp(
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

/// Profiles in memory: `load` would otherwise wait on secure storage.
class _Store extends ProfileStore {
  _Store({required super.prefs});
  @override
  List<ServerProfile> get profiles => const [];
  @override
  String? get activeId => null;
}

void main() {
  group('when it opens', () {
    Future<SharedPreferences> prefs([Map<String, Object> v = const {}]) async {
      SharedPreferences.setMockInitialValues(v);
      return SharedPreferences.getInstance();
    }

    bool due(
      SharedPreferences p, {
      String build = '58',
      bool existing = true,
    }) => WhatsNew.takeDue(
      p,
      version: '2.2.0',
      build: build,
      existingInstall: existing,
    );

    test('once after an update, never twice', () async {
      final p = await prefs({WhatsNew.seenBuildKey: '57'});
      expect(due(p), isTrue);
      expect(due(p), isFalse);
    });

    test('never on a fresh install, and it is not owed later', () async {
      final p = await prefs();
      expect(due(p, existing: false), isFalse);
      expect(due(p, existing: false), isFalse);
      // The next update is a real update.
      expect(due(p, build: '59'), isTrue);
    });

    test('a person updating from before the feature sees it once', () async {
      final p = await prefs();
      expect(due(p, existing: true), isTrue);
      expect(due(p, existing: true), isFalse);
    });

    test('a version with no lines shows nothing', () async {
      final p = await prefs({WhatsNew.seenBuildKey: '57'});
      expect(
        WhatsNew.takeDue(
          p,
          version: '9.9.9',
          build: '58',
          existingInstall: true,
        ),
        isFalse,
      );
    });
  });

  group('the walkthrough', () {
    setUp(() {
      PackageInfo.setMockInitialValues(
        appName: 'OpenCode Mobile',
        packageName: 'com.example.opencode_mobile',
        version: '2.2.0',
        buildNumber: '58',
        buildSignature: '',
      );
    });

    void withAndroid(
      String name,
      Future<void> Function(WidgetTester tester) body,
    ) {
      testWidgets(name, (tester) async {
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        try {
          await body(tester);
        } finally {
          debugDefaultTargetPlatformOverride = null;
        }
      });
    }

    // The Updates page reads the app-wide update holder; a fake one never
    // reaches the network.
    AppUpdateController updates() => AppUpdateController(
      source: FakeUpdateSource()..latest = null,
      platform: FakeUpdatePlatform(),
      prefs: SharedPreferences.getInstance,
      installed: () async => const InstalledApp(
        packageName: 'io.github.eslamasabry.opencode_mobile',
        version: '2.2.0',
        build: 58,
      ),
      cacheDir: () async => Directory.systemTemp,
    );

    Widget host({Locale locale = const Locale('en')}) => ProviderScope(
      overrides: [appUpdateProvider.overrideWithValue(updates())],
      child: _app(
        Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => showWhatsNewSheet(context, version: '2.2.0'),
              child: const Text('open'),
            ),
          ),
        ),
        locale: locale,
      ),
    );

    Future<void> open(WidgetTester tester) async {
      await tester.pumpWidget(host());
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
    }

    Future<void> tapKey(WidgetTester tester, String key) async {
      await tester.tap(find.byKey(ValueKey(key)));
      await tester.pumpAndSettle();
    }

    withAndroid('Next and Back move one new thing at a time', (tester) async {
      await open(tester);
      expect(find.text("What's new in 2.2.0"), findsOneWidget);
      expect(find.text('Step 1 of 5'), findsOneWidget);
      expect(find.byKey(const ValueKey('whats-new-back')), findsNothing);
      await tapKey(tester, 'whats-new-next');
      expect(find.text('Step 2 of 5'), findsOneWidget);
      expect(
        find.text(
          'OpenCode 2.2.5 is built in, and your agents update themselves.',
        ),
        findsOneWidget,
      );
      await tapKey(tester, 'whats-new-back');
      expect(find.text('Step 1 of 5'), findsOneWidget);
    });

    withAndroid('Skip leaves at any step', (tester) async {
      await open(tester);
      await tapKey(tester, 'whats-new-next');
      await tapKey(tester, 'whats-new-skip');
      expect(find.byKey(const ValueKey('whats-new-sheet')), findsNothing);
    });

    withAndroid('Share is on the last step and opens the share sheet', (
      tester,
    ) async {
      final calls = <MethodCall>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        const MethodChannel('oc/share'),
        (call) async {
          calls.add(call);
          return true;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          const MethodChannel('oc/share'),
          null,
        ),
      );
      await open(tester);
      expect(find.byKey(const ValueKey('whats-new-share')), findsNothing);
      for (var i = 0; i < 4; i++) {
        await tapKey(tester, 'whats-new-next');
      }
      expect(find.text('Step 5 of 5'), findsOneWidget);
      expect(find.byKey(const ValueKey('whats-new-skip')), findsNothing);
      await tapKey(tester, 'whats-new-share');
      expect(calls, hasLength(1));
      expect(calls.single.method, 'shareText');
      expect(
        (calls.single.arguments as Map)['text'],
        contains('https://eslamasabry.github.io/opencode-mobile-next/'),
      );
    });

    withAndroid('Show me goes to the real control and highlights it', (
      tester,
    ) async {
      await open(tester);
      await tapKey(tester, 'whats-new-show-me');
      // The walkthrough is gone; Settings › This app › Updates is open with
      // the tour over its Check now row.
      expect(find.byKey(const ValueKey('whats-new-sheet')), findsNothing);
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.byType(UpdatesScreen), findsOneWidget);
      expect(find.byKey(WhatsNewPlaces.updatesAnchor), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-tour-step-0')), findsOneWidget);
      expect(find.text('Check for an update here, any time.'), findsOneWidget);
    });

    withAndroid(
      'Show me for the message-box steps waits for a chat to be wired',
      (tester) async {
        await open(tester);
        for (var i = 0; i < 4; i++) {
          await tapKey(tester, 'whats-new-next');
        }
        expect(find.byKey(const ValueKey('whats-new-show-me')), findsNothing);
      },
    );

    withAndroid('a step whose page needs no connection can be shown', (
      tester,
    ) async {
      expect(WhatsNewPlaces.available(WhatsNewTarget.updates, null), isTrue);
      expect(WhatsNewPlaces.available(WhatsNewTarget.agents, null), isFalse);
      expect(
        WhatsNewPlaces.anchor(WhatsNewTarget.agents),
        const ValueKey('agents-hub-list'),
      );
      expect(
        WhatsNewPlaces.anchor(WhatsNewTarget.updates),
        const ValueKey('updates-check-now'),
      );
    });

    withAndroid('a step with no controller has no Show me', (tester) async {
      await open(tester);
      await tapKey(tester, 'whats-new-next');
      expect(find.byKey(const ValueKey('whats-new-show-me')), findsNothing);
    });

    withAndroid('About › What\'s new replays it', (tester) async {
      await tester.pumpWidget(_app(const AboutScreen()));
      // The package info is a real platform future: give it real time.
      for (var i = 0; i < 10; i++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 50)),
        );
        await tester.pumpAndSettle();
      }
      await tester.tap(find.byKey(const ValueKey('about-whats-new')));
      await tester.pumpAndSettle();
      expect(find.text("What's new in 2.2.0"), findsOneWidget);
    });

    withAndroid('Help › Tips replays it', (tester) async {
      SharedPreferences.setMockInitialValues({});
      final controller = ConnectionController(
        _Store(prefs: await SharedPreferences.getInstance()),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(TipsScreen(controller: controller)));
      await tester.pumpAndSettle();
      final showMe = find.byKey(
        ValueKey('tips-show-me-${TeachingIds.whatsNew}'),
      );
      await tester.ensureVisible(showMe);
      await tester.tap(showMe);
      for (var i = 0; i < 10; i++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 50)),
        );
        await tester.pumpAndSettle();
      }
      expect(find.text("What's new in 2.2.0"), findsOneWidget);
    });

    withAndroid('reads in Arabic', (tester) async {
      await tester.pumpWidget(host(locale: const Locale('ar')));
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('الجديد في 2.2.0'), findsOneWidget);
    });
  });
}
