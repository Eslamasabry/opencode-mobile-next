import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/background/widget_snapshot.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/camera.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/platform/share_intent.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/guide_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';

// Controller and isolated-screen regressions, not main()/AppBootstrap.load()
// coverage or evidence of a working iOS runner. Remote responses stay in memory.
class _RemoteApi extends OpenCodeApi with CompleteMessageHistory {
  _RemoteApi() : super(baseUrl: 'https://remote.example') {
    // No omitted fixture method may fall through to a real HTTP request.
    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          unexpectedRequests.add(options.path);
          handler.reject(DioException(requestOptions: options));
        },
      ),
    );
  }

  final unexpectedRequests = <String>[];
  final prompts = <(String, String)>[];
  final permissionReplies = <(String, String, String)>[];

  @override
  Future<Health> health() async => Health(healthy: true, version: 'test');

  @override
  Future<List<Session>> sessions() async => [
    Session(id: 'session-1', title: 'Remote session'),
  ];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Session> session(String id) async =>
      Session(id: id, title: 'Remote session');

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'message-1',
        sessionID: id,
        role: 'assistant',
        time: MsgTime(created: 1, completed: 2),
      ),
      parts: [
        Part(
          id: 'part-1',
          messageID: 'message-1',
          type: 'text',
          text: 'Remote result is still readable.',
        ),
      ],
    ),
  ];

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    prompts.add((sessionID, text));
  }

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  }) async {
    permissionReplies.add((sessionID, requestID, reply));
  }
}

Future<({ConnectionController controller, _RemoteApi api})>
_remoteState() async {
  SharedPreferences.setMockInitialValues({
    BackgroundLiveController.preferenceKey: true,
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Remote workstation',
        'baseUrl': 'https://remote.example',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final api = _RemoteApi();
  final controller = ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
  addTearDown(() {
    controller.dispose();
    expect(api.unexpectedRequests, isEmpty);
  });
  return (controller: controller, api: api);
}

Widget _screen(ConnectionController controller, Widget screen) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
    connProvider.overrideWithValue(controller),
  ],
  child: MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: screen,
  ),
);

Future<void> _pumpFrames(WidgetTester tester) async {
  // Chat may keep live indicators running; never wait for global settlement.
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _pumpNarrowCopy(
  WidgetTester tester,
  Widget screen,
  Brightness brightness,
) async {
  addTearDown(tester.view.reset);
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(360, 740);
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: brightness == Brightness.light
          ? AppTheme.light()
          : AppTheme.dark(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: const TextScaler.linear(2.5),
          disableAnimations: true,
        ),
        child: child!,
      ),
      home: screen,
    ),
  );
  await _pumpFrames(tester);
}

Future<void> _revealCopy(WidgetTester tester, Finder target) async {
  await tester.scrollUntilVisible(
    target,
    220,
    maxScrolls: 80,
    scrollable: find
        .descendant(
          of: find.byType(ListView),
          matching: find.byType(Scrollable),
        )
        .first,
  );
  await tester.pump();
  expect(target.hitTestable(), findsOneWidget);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const nativeChannels = [
    MethodChannel('oc/background'),
    MethodChannel('oc/termux'),
    MethodChannel('oc/voice'),
    MethodChannel('oc/camera'),
    MethodChannel('oc/share'),
  ];
  const secureStorage = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );
  late List<(String, String)> nativeCalls;

  setUp(() {
    final previousTarget = debugDefaultTargetPlatformOverride;
    final previousCapabilities = debugPlatformCapabilities;
    // Widget tests also use TargetPlatformVariant so the runtime target is
    // restored before Flutter verifies its debug globals. Plain tests set it
    // in their bodies; ShareIntent needs the runtime target, not just a gate.
    debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.iOS,
    );
    nativeCalls = [];
    for (final channel in nativeChannels) {
      messenger.setMockMethodCallHandler(channel, (call) async {
        nativeCalls.add((channel.name, call.method));
        throw MissingPluginException('No iOS implementation');
      });
    }
    // ProfileStore.load/upsert must never wait for the real secure store.
    messenger.setMockMethodCallHandler(secureStorage, (_) async => null);
    addTearDown(() {
      for (final channel in nativeChannels) {
        messenger.setMockMethodCallHandler(channel, null);
        channel.setMethodCallHandler(null);
      }
      messenger.setMockMethodCallHandler(secureStorage, null);
      debugDefaultTargetPlatformOverride = previousTarget;
      debugPlatformCapabilities = previousCapabilities;
      expect(
        nativeCalls,
        isEmpty,
        reason: 'no outgoing oc/* calls, including disposal',
      );
    });
  });

  for (final restore in [false, true]) {
    test(
      'iOS stale keep-live preference cannot prevent suspension ${restore ? 'after' : 'before'} restore',
      () async {
        debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
        final state = await _remoteState();
        final controller = state.controller;
        expect(controller.keepLiveInBackground, isFalse);
        if (restore) await controller.restoreBackgroundLiveMode();
        expect(await controller.setKeepLiveInBackground(true), isFalse);
        expect(controller.backgroundLive.enabled, isFalse);
        expect(controller.backgroundLive.active, isFalse);
        expect(controller.pendingCodingAlertOpen, isNull);

        // The remote domain path remains usable; no Android service is needed
        // to list sessions or answer a request while the app is in the foreground.
        await controller.refreshSessions();
        expect(controller.sortedSessions().single.title, 'Remote session');
        controller.handleEventForTesting(
          EventEnvelope(
            type: 'permission.v2.asked',
            properties: const {
              'id': 'permission-1',
              'sessionID': 'session-1',
              'action': 'edit',
              'resources': [],
            },
          ),
        );
        expect(controller.permissions, contains('permission-1'));
        await controller.answerPermission('permission-1', 'once');
        expect(state.api.permissionReplies, [
          ('session-1', 'permission-1', 'once'),
        ]);
        expect(controller.permissions, isEmpty);
        expect(controller.status, StreamStatus.connected);

        controller.suspendForLifecycle();
        expect(controller.lifecycleSuspended, isTrue);
        expect(controller.status, StreamStatus.disconnected);
        expect(controller.api, isNull);
        expect(state.api.isClosed, isTrue);
        expect(controller.profile?.id, 'profile-1');
        expect(controller.sortedSessions().single.title, 'Remote session');
        expect(controller.backgroundLive.lastError, isNull);
        expect(
          controller.store.prefs.getString(WidgetSessionSnapshot.prefsKey),
          isNull,
        );
        await controller.store.prefs.reload();
        expect(
          controller.store.prefs.getBool(
            BackgroundLiveController.preferenceKey,
          ),
          isTrue,
        );
        expect(nativeCalls, isEmpty);
      },
    );
  }

  test(
    'iOS native backstops stay unavailable without outgoing oc calls',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      final state = await _remoteState();
      final background = state.controller.backgroundLive;
      await state.controller.restoreBackgroundLiveMode();
      expect(await background.setEnabled(true), isFalse);
      await background.refreshStatus();
      expect(await background.requestBatteryOptimizationExemption(), isFalse);
      await background.publishLiveStatus(const LiveStatus(runningCount: 1));
      expect(
        await background.showCodingAlert(
          kind: CodingAlertKind.permission,
          sessionID: 'session-1',
          key: 'input:session-1',
          requestID: 'permission-1',
        ),
        isFalse,
      );
      expect(await background.dismissCodingAlert('input:session-1'), isFalse);
      expect(await background.consumeCodingAlertOpen(), isNull);

      final termux = await TermuxBridge.capabilities();
      expect(termux.platformSupported, isFalse);
      expect(termux.installed, isFalse);
      expect(await TermuxBridge.requestPermission(), isFalse);
      expect(await TermuxBridge.openTermux(), isFalse);
      expect(await TermuxBridge.openAppSettings(), isFalse);
      await expectLater(
        TermuxBridge.run('true'),
        throwsA(
          isA<TermuxBridgeException>().having(
            (error) => error.code,
            'code',
            TermuxBridge.unsupportedPlatformCode,
          ),
        ),
      );

      const voice = AndroidVoiceDevicePlatform();
      final device = await voice.getDeviceInfo();
      expect(device.captureSupported, isFalse);
      expect(device.hasMicrophone, isFalse);
      expect(
        await voice.requestMicrophonePermission(),
        VoiceMicrophonePermission.permanentlyDenied,
      );
      await voice.openAppSettings();
      const camera = AndroidCameraPlatform();
      expect(await camera.hasCamera(), isFalse);
      expect(
        await camera.requestCameraPermission(),
        CameraPermission.permanentlyDenied,
      );
      await camera.openAppSettings();

      final share = ShareIntent();
      addTearDown(share.dispose);
      expect(ShareIntent.supported, isFalse);
      await share.start();
      expect(share.take(), isNull);
      await WidgetSessionSnapshot(prefs: state.controller.store.prefs).update(
        sessions: [Session(id: 'session-1')],
        busySessions: const {},
        connected: true,
        profileID: 'profile-1',
      );
      expect(
        state.controller.store.prefs.getString(WidgetSessionSnapshot.prefsKey),
        isNull,
      );
      expect(nativeCalls, isEmpty);
    },
  );

  testWidgets(
    'iOS keeps remote server entry and paste pairing without Termux or scanning',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      await store.load();
      final controller = ConnectionController(store);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_screen(controller, const ServersScreen()));
      expect(find.text('Connect to your computer'), findsOneWidget);
      expect(find.text('Run OpenCode on this phone'), findsNothing);
      expect(find.textContaining('Termux'), findsNothing);

      await tester.tap(find.text('Connect to your computer'));
      await _pumpFrames(tester);
      expect(find.text('Paste pairing code'), findsOneWidget);
      expect(find.text('Scan'), findsNothing);
      expect(nativeCalls, isEmpty);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
    },
    variant: TargetPlatformVariant.only(TargetPlatform.iOS),
  );

  testWidgets(
    'iOS remote chat reads and sends with voice and photos gated off',
    (tester) async {
      final state = await _remoteState();
      await state.controller.restoreBackgroundLiveMode();
      await tester.binding.setSurfaceSize(const Size(400, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        _screen(state.controller, const ChatScreen(sessionID: 'session-1')),
      );
      await _pumpFrames(tester);
      expect(find.text('Remote result is still readable.'), findsOneWidget);

      await tester.tap(find.byKey(const Key('composer-tools-button')));
      await _pumpFrames(tester);
      expect(find.text('Commands'), findsOneWidget);
      expect(find.text('Attach file'), findsOneWidget);
      expect(find.text('Voice input'), findsNothing);
      expect(find.byKey(const Key('composer-tool-gallery')), findsNothing);
      expect(find.byKey(const Key('composer-tool-camera')), findsNothing);
      Navigator.of(tester.element(find.text('Commands'))).pop();
      await _pumpFrames(tester);

      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Continue remotely',
      );
      await tester.pump();
      await tester.tap(find.byTooltip('Send'));
      await _pumpFrames(tester);
      expect(state.api.prompts, [('session-1', 'Continue remotely')]);
      expect(state.controller.keepLiveInBackground, isFalse);
      expect(nativeCalls, isEmpty);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      await _pumpFrames(tester);
    },
    variant: TargetPlatformVariant.only(TargetPlatform.iOS),
  );

  testWidgets(
    'iOS settings omit background services without hiding privacy controls',
    (tester) async {
      final state = await _remoteState();
      await tester.binding.setSurfaceSize(const Size(500, 1400));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        _screen(state.controller, SettingsScreen(controller: state.controller)),
      );
      await _pumpFrames(tester);
      expect(find.text('Notifications & background'), findsNothing);
      expect(
        find.byKey(const ValueKey('settings-category-background')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('settings-category-privacy')),
        findsOneWidget,
      );
      expect(nativeCalls, isEmpty);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
    },
    variant: TargetPlatformVariant.only(TargetPlatform.iOS),
  );

  for (final brightness in Brightness.values) {
    testWidgets(
      '${brightness.name} iOS guide shows Keychain guidance at 360x740 and 2.5x',
      (tester) async {
        final l10n = lookupAppLocalizations(const Locale('en'));
        await _pumpNarrowCopy(tester, const GuideScreen(), brightness);
        final advanced = find.text('Advanced');
        await _revealCopy(tester, advanced);
        await tester.tap(advanced);
        await _pumpFrames(tester);
        final guidance = find.text(l10n.iosKeychainGuide);
        await _revealCopy(tester, guidance);
        expect(
          tester.getSemantics(guidance).label,
          contains(l10n.iosKeychainGuide),
        );
        expect(find.textContaining('libsecret'), findsNothing);
        expect(find.textContaining('Android Keystore'), findsNothing);
        expect(find.textContaining('Termux'), findsNothing);
        expect(
          find.byKey(const ValueKey('guide-termux-section')),
          findsNothing,
        );
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
      },
      variant: TargetPlatformVariant.only(TargetPlatform.iOS),
    );

    testWidgets(
      '${brightness.name} iOS About shows remote-only identity at 360x740 and 2.5x',
      (tester) async {
        final l10n = lookupAppLocalizations(const Locale('en'));
        const packageChannel = MethodChannel(
          'dev.fluttercommunity.plus/package_info',
        );
        messenger.setMockMethodCallHandler(
          packageChannel,
          (_) async => {
            'appName': 'OpenCode Mobile',
            'packageName': 'io.github.eslamasabry.opencodeMobile',
            'version': '1.0.0',
            'buildNumber': '1',
          },
        );
        addTearDown(
          () => messenger.setMockMethodCallHandler(packageChannel, null),
        );
        // Reset the process-wide asset fixture, as in the isolated About tests.
        // These are copy/layout checks, not warm-cache/bootstrap evidence.
        rootBundle.evict('PRIVACY.md');
        rootBundle.evict('THIRD_PARTY_NOTICES.md');
        await _pumpNarrowCopy(
          tester,
          const AboutScreen(initialTab: 1),
          brightness,
        );
        await tester.pumpAndSettle();
        final title = find.text(l10n.iosAppTitle);
        final summary = find.text(l10n.iosRemoteSummary);
        await _revealCopy(tester, title);
        expect(tester.getSemantics(title).label, contains(l10n.iosAppTitle));
        await _revealCopy(tester, summary);
        expect(
          tester.getSemantics(summary).label,
          contains(l10n.iosRemoteSummary),
        );
        expect(find.text('OpenCode for Android'), findsNothing);
        expect(find.text('OpenCode for desktop'), findsNothing);
        expect(
          find.textContaining('Voice recognition runs locally'),
          findsNothing,
        );
        expect(
          find.byKey(const ValueKey('about-signing-certificate')),
          findsNothing,
        );
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
      },
      variant: TargetPlatformVariant.only(TargetPlatform.iOS),
    );
  }
}
