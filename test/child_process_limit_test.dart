import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/phone_process_watch.dart';
import 'package:opencode_mobile/builtin/phone_processes.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/child_process_limit.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/screens/running_on_phone_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show captureTheme;
import 'support/running_on_phone_fakes.dart';

ChildProcessLimitStatus status({
  int? sdk = 34,
  ChildProcessLimitState state = ChildProcessLimitState.enforced,
  bool? dev = true,
  bool channel = true,
}) => ChildProcessLimitStatus(
  sdkInt: sdk,
  state: state,
  developerOptionsEnabled: dev,
  canOpenSettings: channel,
);

class _FakePlatform extends ChildProcessLimitPlatform {
  _FakePlatform(this.status) : super(fallbackSdk: () async => null);
  ChildProcessLimitStatus status;
  int opened = 0;
  @override
  Future<ChildProcessLimitStatus> read() async => status;
  @override
  Future<String> openDeveloperOptions() async {
    opened++;
    return 'developer';
  }
}

void main() {
  group('childLimitCard rules', () {
    test('below Android 14 or unknown: never', () {
      expect(
        childLimitCard(
          status: status(sdk: 33),
          recentKill: true,
          queuedOften: true,
        ),
        isNull,
      );
      expect(
        childLimitCard(
          status: status(sdk: null),
          recentKill: true,
          queuedOften: true,
        ),
        isNull,
      );
    });
    test('lifted: never', () {
      expect(
        childLimitCard(
          status: status(state: ChildProcessLimitState.lifted),
          recentKill: true,
          queuedOften: true,
        ),
        isNull,
      );
    });
    test('no kill and not queuing: never a nag', () {
      expect(
        childLimitCard(status: status(), recentKill: false, queuedOften: false),
        isNull,
      );
    });
    test('a kill or frequent queuing shows the right kind', () {
      expect(
        childLimitCard(status: status(), recentKill: true, queuedOften: false),
        ChildLimitCardKind.openSettings,
      );
      expect(
        childLimitCard(status: status(), recentKill: false, queuedOften: true),
        ChildLimitCardKind.openSettings,
      );
      expect(
        childLimitCard(
          status: status(dev: false),
          recentKill: true,
          queuedOften: false,
        ),
        ChildLimitCardKind.enableDeveloperOptions,
      );
      expect(
        childLimitCard(
          status: status(dev: null),
          recentKill: true,
          queuedOften: false,
        ),
        ChildLimitCardKind.enableDeveloperOptions,
      );
    });
    test('channel missing: instructions only', () {
      expect(
        childLimitCard(
          status: status(channel: false),
          recentKill: true,
          queuedOften: false,
        ),
        ChildLimitCardKind.instructionsOnly,
      );
    });
    test('hidden stays hidden until a newer kill', () {
      final hidden = DateTime(2026, 11, 1, 10);
      expect(
        childLimitCard(
          status: status(),
          recentKill: true,
          queuedOften: false,
          hiddenAt: hidden,
          lastKillAt: DateTime(2026, 11, 1, 9),
        ),
        isNull,
      );
      expect(
        childLimitCard(
          status: status(),
          recentKill: true,
          queuedOften: false,
          hiddenAt: hidden,
        ),
        isNull,
      );
      expect(
        childLimitCard(
          status: status(),
          recentKill: true,
          queuedOften: false,
          hiddenAt: hidden,
          lastKillAt: DateTime(2026, 11, 1, 11),
        ),
        ChildLimitCardKind.openSettings,
      );
    });
  });

  group('ChildProcessLimitPlatform', () {
    setUp(() {
      debugPlatformCapabilities = PlatformCapabilities(
        platform: TargetPlatform.android,
        isWeb: false,
      );
      addTearDown(() => debugPlatformCapabilities = null);
    });

    test(
      'an APK without the native method falls back to the SDK reader',
      () async {
        TestWidgetsFlutterBinding.ensureInitialized();
        final platform = ChildProcessLimitPlatform(
          channel: const MethodChannel('oc/test-missing'),
          fallbackSdk: () async => 35,
        );
        final read = await platform.read();
        expect(read.sdkInt, 35);
        expect(read.canOpenSettings, isFalse);
        expect(read.state, ChildProcessLimitState.unknown);
        expect(await platform.openDeveloperOptions(), 'none');
      },
    );

    test('reads the native answer', () async {
      TestWidgetsFlutterBinding.ensureInitialized();
      const channel = MethodChannel('oc/test-present');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'openDeveloperOptions') {
              return {'opened': 'about'};
            }
            return {
              'sdkInt': 35,
              'developerOptionsEnabled': false,
              'restrictionsOff': true,
            };
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      final platform = ChildProcessLimitPlatform(channel: channel);
      final read = await platform.read();
      expect(read.state, ChildProcessLimitState.lifted);
      expect(read.developerOptionsEnabled, isFalse);
      expect(read.canOpenSettings, isTrue);
      expect(await platform.openDeveloperOptions(), 'about');
    });
  });

  group('card on Running on this phone', () {
    Future<_FakePlatform> pump(
      WidgetTester tester,
      ChildProcessLimitStatus s, {
      bool kill = true,
      DateTime? lastKillAt,
    }) async {
      final platform = _FakePlatform(s);
      final watch = PhoneProcessWatch(
        processes: FakePhoneProcesses(
          PhoneProcessSnapshot(processes: [proc(100, 1, 'opencode')]),
        ),
      );
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            phoneProcessWatchProvider.overrideWithValue(watch),
            childProcessLimitPlatformProvider.overrideWithValue(platform),
          ],
          child: MaterialApp(
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: RunningOnPhoneScreen(
              recentKill: kill,
              lastKillAt: lastKillAt,
            ),
          ),
        ),
      );
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      return platform;
    }

    setUp(() => SharedPreferences.setMockInitialValues({}));

    testWidgets('shows after a kill, opens Settings, hides and stays hidden', (
      tester,
    ) async {
      final platform = await pump(tester, status());
      expect(find.text('Let agents run more programs at once'), findsOneWidget);
      expect(
        find.textContaining('Disable child process restrictions'),
        findsOneWidget,
      );
      await tester.tap(find.text('Open Developer options'));
      await tester.pump();
      expect(platform.opened, 1);
      await tester.tap(find.text('Hide'));
      for (var i = 0; i < 4; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.text('Let agents run more programs at once'), findsNothing);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getInt(ChildLimitCardHide.key), isNotNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('absent without a kill, on Android 13, and when lifted', (
      tester,
    ) async {
      await pump(tester, status(), kill: false);
      expect(find.text('Let agents run more programs at once'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      await pump(tester, status(sdk: 33));
      expect(find.text('Let agents run more programs at once'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      await pump(tester, status(state: ChildProcessLimitState.lifted));
      expect(find.text('Let agents run more programs at once'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('an older APK shows the steps with no button', (tester) async {
      await pump(tester, status(channel: false));
      expect(find.textContaining('Open Settings › System'), findsOneWidget);
      expect(find.text('Open Developer options'), findsNothing);
      expect(find.text('Open About phone'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('Developer options off leads to About phone', (tester) async {
      await pump(tester, status(dev: false));
      expect(find.text('Open About phone'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });
}
