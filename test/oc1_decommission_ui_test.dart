import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/domain/opencode_history_copy.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _BlockedHistoryLinux extends BuiltinLinux {
  _BlockedHistoryLinux({this.separateHistory = false});

  /// True: OpenCode 2 holds its own history (final). False: the phone is not
  /// answering yet (the move waits and retries).
  final bool separateHistory;
  int historyChecks = 0;
  @override
  Future<void> observePhoneAgentWork({
    required String profileId,
    required bool? busy,
  }) async {}

  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: true,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    final isCheck = script.contains(openCode1HistorySwitchCheckScript);
    if (isCheck) historyChecks++;
    // The readiness probe answers when the phone is up.
    if (!isCheck && separateHistory) {
      return const BuiltinLinuxRunResult(exitCode: 0, output: '');
    }
    return BuiltinLinuxRunResult(
      exitCode: 74,
      output: separateHistory
          ? openCode1HistorySwitchBlockedMessage
          : 'private-history-diagnostic',
    );
  }
}

void main() {
  testWidgets('remote OC1 shows the supported upgrade command', (tester) async {
    final copy = AppLocalizationsEn();
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: SavedServerConnectionCard(
            profileName: 'Work',
            baseUrl: 'https://work.example.test',
            error: unsupportedOpenCode1Message,
            attempts: 1,
            supportsTermux: false,
            onChangeServer: () {},
            onRetry: () {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text(copy.openCode1Unsupported), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-state-details')));
    await tester.pumpAndSettle();
    expect(
      tester.widget<KitCodeBlock>(find.byType(KitCodeBlock)).text,
      openCode2UpgradeCommand,
    );
  });

  testWidgets(
    'first launch attempts safe migration once and keeps legacy profile after refusal',
    (tester) async {
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        secure,
        (_) async => null,
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          secure,
          null,
        ),
      );
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.android,
        isWeb: false,
      );
      addTearDown(() => debugPlatformCapabilities = null);
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final source = ServerProfile(
        id: 'owner-phone',
        name: 'Owner phone',
        baseUrl: BuiltinLinux.serverUrl,
        password: 'synthetic-test-password',
        requiresOpenCode2Migration: true,
      );
      await store.upsert(source);
      await store.setActiveId(source.id);
      final controller = ConnectionController(store);
      final linux = _BlockedHistoryLinux(separateHistory: true);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(controller),
            builtinLinuxProvider.overrideWithValue(linux),
          ],
          child: const OcApp(),
        ),
      );
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(linux.historyChecks, 1);
      expect(source.requiresOpenCode2Migration, isTrue);
      expect(store.profiles.single.id, 'owner-phone');
      expect(find.textContaining('private-history-diagnostic'), findsNothing);
      // One failure, once: the page, not a dialog before it.
      expect(find.text('Close'), findsNothing);
      expect(
        find.text(AppLocalizationsEn().openCode2MoveFailedSeparateHistory),
        findsOneWidget,
      );
      expect(find.text(AppLocalizationsEn().commonRetry), findsNothing);
      expect(linux.historyChecks, 1);
      expect(controller.hasConnectedServer, isFalse);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
    },
  );

  testWidgets(
    'a phone that is not answering is waited for and retried, then fails once',
    (tester) async {
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        secure,
        (_) async => null,
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          secure,
          null,
        ),
      );
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.android,
        isWeb: false,
      );
      addTearDown(() => debugPlatformCapabilities = null);
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final source = ServerProfile(
        id: 'owner-phone',
        name: 'Owner phone',
        baseUrl: BuiltinLinux.serverUrl,
        password: 'synthetic-test-password',
        requiresOpenCode2Migration: true,
      );
      await store.upsert(source);
      await store.setActiveId(source.id);
      final controller = ConnectionController(store);
      final linux = _BlockedHistoryLinux();
      final copy = AppLocalizationsEn();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(controller),
            builtinLinuxProvider.overrideWithValue(linux),
          ],
          child: const OcApp(),
        ),
      );
      await tester.pump(const Duration(seconds: 1));
      // Calm progress while it waits; no failure, no dialog.
      expect(find.text(copy.openCode2MoveProgress), findsOneWidget);
      expect(find.text(copy.phoneServerCardFailedTitle), findsNothing);
      await tester.pump(const Duration(seconds: 90));
      expect(find.text(copy.openCode2MoveRetrying(1, 3)), findsOneWidget);
      expect(find.text(copy.phoneServerCardFailedTitle), findsNothing);
      // The whole schedule (4 tries, 3 pauses, bounded waits) ends in one
      // failure page with the way forward.
      for (var i = 0; i < 40; i++) {
        await tester.pump(const Duration(seconds: 10));
      }
      expect(find.text(copy.openCode2MoveFailedNotReady), findsOneWidget);
      expect(find.text(copy.commonRetry), findsOneWidget);
      expect(find.text('Close'), findsNothing);
      expect(linux.historyChecks, 4);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
    },
  );
}
