// P6.7 Consent once, in flow: each consent is asked at the moment it
// matters, at most once per server, remembered, and a declined one explains
// itself on its row on What runs by itself (automation-settings).
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/consent_owners.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/first_reply_notify_offer.dart';
import 'package:opencode_mobile/state/first_run.dart';
import 'package:opencode_mobile/state/in_flow_consent.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/repeated_permission_consent.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/widgets/always_allow_invitation.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_consents.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';

final _en = lookupAppLocalizations(const Locale('en'));

Finder _key(String key) => find.byKey(ValueKey(key));

/// The Android side: the background service (notification permission) and
/// the battery exemption prompt.
class _Native {
  final calls = <String>[];

  Future<Map<String, dynamic>> call(
    String method, [
    Map<String, dynamic>? arguments,
  ]) async {
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
    calls.add(method);
    return {
      'enabled': method == 'enable',
      'active': method == 'enable',
      'notificationGranted': method == 'enable',
    };
  }
}

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  final replies = <({String requestID, String reply})>[];
  bool refuse = false;

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    replies.add((requestID: requestID, reply: reply));
    if (refuse) throw ApiException('server refused the reply');
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

late _Native _native;
late List<String> _lifecycle;
late Map<String, Object> _keepAlive;

Future<ConnectionController> _controller({
  Map<String, Object> values = const {},
  OpenCodeApi? api,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'phone',
        'name': 'Pixel',
        'baseUrl': 'http://192.168.1.20:4096',
        'username': '',
      },
      {
        'id': 'studio',
        'name': 'Studio',
        'baseUrl': 'http://192.168.1.30:4096',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'phone',
    ...values,
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  _native = _Native();
  return ConnectionController(
      store,
      backgroundLive: BackgroundLiveController(
        preferences: prefs,
        invoke: _native.call,
      ),
    )
    ..api = api ?? _Api()
    ..status = StreamStatus.connected;
}

Widget _app(Widget home) => ProviderScope(
  child: MaterialApp(
    theme: AppTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: home,
  ),
);

/// A screen whose context runs the phone server's first-start questions.
Future<BuildContext> _host(WidgetTester tester) async {
  late BuildContext captured;
  await tester.pumpWidget(
    _app(
      Scaffold(
        body: Builder(
          builder: (context) {
            captured = context;
            return const SizedBox.expand();
          },
        ),
      ),
    ),
  );
  return captured;
}

Future<void> _askPhone(WidgetTester tester, ConnectionController c) async {
  final context = await _host(tester);
  unawaited(
    askPhoneServerConsents(
      context,
      connection: c,
      bridge: AppLifecycleBridge(),
      profileId: 'phone',
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _openSettings(WidgetTester tester, ConnectionController c) async {
  // Tall enough that the merged page's last section is built without scrolling.
  tester.view.physicalSize = const Size(800, 6000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_app(NotificationsSettingsScreen(controller: c)));
  await tester.pumpAndSettle();
}

PermissionRequest _ask(String id, {List<String> always = const ['git *']}) =>
    PermissionRequest(
      id: id,
      sessionID: 'session-1',
      permission: 'bash',
      patterns: const ['git status'],
      always: always,
    );

/// The scope every [_ask] shares: bash `git status`, standing grant `git *`.
final _scope = PermissionConsentScope(
  sessionID: 'session-1',
  permission: 'bash',
  patterns: const ['git status'],
  alwaysPatterns: const ['git *'],
);

/// The background service and home-screen widget channel, and the
/// launcher shortcuts' one.
const _background = MethodChannel('oc/background');
const _shortcut = MethodChannel('oc/shortcut');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    AutomationPolicyController.resetShared();
    debugPlatformCapabilities = const PlatformCapabilities.android();
    _lifecycle = [];
    _keepAlive = {
      'manufacturer': 'Xiaomi',
      'brand': 'Redmi',
      'batteryOptimizationIgnored': false,
    };
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (_) async => null,
    );
    // Android capabilities turn on the home-screen widget, the launcher
    // shortcuts and the tile, whose writes profile deletion waits for;
    // unanswered, their channels would hang inside testWidgets.
    messenger.setMockMethodCallHandler(_background, (_) async => null);
    messenger.setMockMethodCallHandler(_shortcut, (_) async => null);
    messenger.setMockMethodCallHandler(
      const MethodChannel(AppLifecycleBridge.channelName),
      (call) async {
        _lifecycle.add(call.method);
        return switch (call.method) {
          'keepAliveInfo' => _keepAlive,
          'openKeepAliveSetting' => true,
          _ => null,
        };
      },
    );
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    AutomationPolicyController.resetShared();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      null,
    );
    messenger.setMockMethodCallHandler(_background, null);
    messenger.setMockMethodCallHandler(_shortcut, null);
    messenger.setMockMethodCallHandler(
      const MethodChannel(AppLifecycleBridge.channelName),
      null,
    );
  });

  group('phone server first start', () {
    testWidgets('asks battery then auto-start once; a No explains itself', (
      tester,
    ) async {
      final c = await _controller();
      addTearDown(c.dispose);
      await _askPhone(tester, c);

      // Battery first; nothing runs before the answer.
      expect(_key('phone-consent-batteryExemption'), findsOneWidget);
      expect(find.text(_en.consentBatteryTitle), findsOneWidget);
      expect(_native.calls, isEmpty);
      await tester.tap(_key('phone-consent-batteryExemption-allow'));
      await tester.pumpAndSettle();
      expect(_native.calls, contains('requestBatteryOptimizationExemption'));

      // Then the maker's auto-start, naming the maker.
      expect(_key('phone-consent-makerAutoStart'), findsOneWidget);
      expect(
        find.text(_en.consentMakerBody(KitBidi.auto('Xiaomi'))),
        findsOneWidget,
      );
      await tester.tap(find.text(_en.consentNotNow));
      await tester.pumpAndSettle();
      expect(_lifecycle, isNot(contains('openKeepAliveSetting')));

      // A later start asks nothing.
      await _askPhone(tester, c);
      expect(_key('phone-consent-batteryExemption'), findsNothing);
      expect(_key('phone-consent-makerAutoStart'), findsNothing);

      // Remembered for this server, after a restart too.
      final restored = await InFlowConsent.load(
        c.store.prefs,
        profileId: 'phone',
      );
      expect(
        restored.row(InFlowConsentKind.batteryExemption).choice,
        InFlowConsentChoice.accepted,
      );
      expect(
        restored.row(InFlowConsentKind.makerAutoStart).choice,
        InFlowConsentChoice.denied,
      );

      // What runs by itself: the declined one first, saying what it means.
      await _openSettings(tester, c);
      expect(_key('automation-answers'), findsOneWidget);
      expect(find.text(_en.consentWhyMaker), findsOneWidget);
      expect(find.text(_en.consentValueDeclined), findsOneWidget);
      expect(find.text(_en.consentAllowedSystem), findsOneWidget);
      expect(
        tester.getTopLeft(_key('automation-consent-makerAutoStart')).dy,
        lessThan(
          tester.getTopLeft(_key('automation-consent-batteryExemption')).dy,
        ),
      );
      // Another server was never asked: it has no answers.
      expect(
        (await InFlowConsent.load(
          c.store.prefs,
          profileId: 'studio',
        )).row(InFlowConsentKind.makerAutoStart).choice,
        InFlowConsentChoice.unseen,
      );
    });

    testWidgets('an exempt battery on stock Android asks nothing', (
      tester,
    ) async {
      _keepAlive = {
        'manufacturer': 'Google',
        'brand': 'google',
        'batteryOptimizationIgnored': true,
      };
      final c = await _controller();
      addTearDown(c.dispose);
      await _askPhone(tester, c);
      expect(_key('phone-consent-batteryExemption'), findsNothing);
      expect(_key('phone-consent-makerAutoStart'), findsNothing);
      await _openSettings(tester, c);
      expect(_key('automation-answers'), findsNothing);
    });

    testWidgets('a declined consent can be allowed from its row', (
      tester,
    ) async {
      final c = await _controller();
      addTearDown(c.dispose);
      await _askPhone(tester, c);
      await tester.tap(find.text(_en.consentNotNow));
      await tester.pumpAndSettle();
      await tester.tap(find.text(_en.consentNotNow));
      await tester.pumpAndSettle();
      expect(_native.calls, isEmpty);

      await _openSettings(tester, c);
      expect(find.text(_en.consentWhyBattery), findsOneWidget);
      await tester.tap(_key('automation-consent-batteryExemption'));
      await tester.pumpAndSettle();
      await tester.tap(_key('automation-consent-ask-batteryExemption-allow'));
      await tester.pumpAndSettle();
      expect(_native.calls, contains('requestBatteryOptimizationExemption'));
      expect(find.text(_en.consentWhyBattery), findsNothing);
      expect(
        ConsentOwners.inFlowLoaded(
          c.store.prefs,
          'phone',
        )!.row(InFlowConsentKind.batteryExemption).choice,
        InFlowConsentChoice.accepted,
      );
    });
  });

  group('Tell me when the agent needs me', () {
    // The question itself is a line in the chat's status slot
    // (test/chat_notify_offer_test.dart); here only its answers.
    Future<FirstReplyNotifyOffer> ask(
      WidgetTester tester,
      ConnectionController c,
    ) async {
      final offer = FirstReplyNotifyOffer(c);
      addTearDown(offer.dispose);
      offer.showFor(replyCompleted: true);
      await tester.pumpAndSettle();
      return offer;
    }

    const pending = <String, Object>{
      FirstRun.stateKey: 'done',
      FirstRun.notifyAskKey: 'pending',
    };

    testWidgets('is kept for the server it was asked on and explained', (
      tester,
    ) async {
      final c = await _controller(values: pending);
      addTearDown(c.dispose);
      final offer = await ask(tester, c);
      expect(offer.showFor(replyCompleted: true), isTrue);
      // Claimed before it showed: a closed app leaves "Not answered".
      expect(
        ConsentOwners.inFlowLoaded(
          c.store.prefs,
          'phone',
        )!.row(InFlowConsentKind.needsYouNotifications).choice,
        InFlowConsentChoice.offered,
      );

      await offer.decline();
      await tester.pumpAndSettle();
      expect(_native.calls, isEmpty);

      await _openSettings(tester, c);
      expect(find.text(_en.consentRowNeedsYou), findsOneWidget);
      expect(find.text(_en.consentWhyNeedsYou), findsOneWidget);

      // Allowing it later turns on what the preset stands for.
      await tester.tap(_key('automation-consent-needsYouNotifications'));
      await tester.pumpAndSettle();
      await tester.tap(
        _key('automation-consent-ask-needsYouNotifications-allow'),
      );
      await tester.pumpAndSettle();
      expect(_native.calls, contains('enable'));
      expect(c.notificationPreferences.requests, isTrue);
      expect(find.text(_en.consentAllowedNeedsYou), findsOneWidget);
    });

    testWidgets('accepting saves the yes, then turns on requests only', (
      tester,
    ) async {
      final c = await _controller(values: pending);
      addTearDown(c.dispose);
      await c.setNotifyFinishedRuns(false);
      await c.setNotifyRequests(false);
      final offer = await ask(tester, c);
      await offer.accept();
      await tester.pumpAndSettle();
      expect(_native.calls, contains('enable'));
      expect(c.notificationPreferences.requests, isTrue);
      // The preset is about being needed, not about finished runs.
      expect(c.notificationPreferences.finishedRuns, isFalse);
      expect(
        ConsentOwners.inFlowLoaded(
          c.store.prefs,
          'phone',
        )!.row(InFlowConsentKind.needsYouNotifications).choice,
        InFlowConsentChoice.accepted,
      );
    });

    testWidgets('a server asked before is not asked again', (tester) async {
      final c = await _controller(values: pending);
      addTearDown(c.dispose);
      final consent = await ConsentOwners.inFlow(c.store.prefs, 'phone');
      expect(await consent.requestNeedsYouPreset(), isTrue);

      final offer = await ask(tester, c);
      expect(offer.showFor(replyCompleted: true), isFalse);
      // The one-time question is settled, so tips are no longer held back.
      expect(FirstReplyNotifyOffer.pendingFor(c), isFalse);
      await _openSettings(tester, c);
      expect(find.text(_en.consentWhyUnfinished), findsOneWidget);
      expect(find.text(_en.consentValueUnanswered), findsOneWidget);
    });
  });

  group('Always allow on the third identical ask', () {
    Widget invitations(ConnectionController c, List<PermissionRequest> asks) =>
        _app(
          Scaffold(
            body: Column(
              children: [
                for (final ask in asks)
                  AlwaysAllowInvitation(
                    key: ValueKey('invite-${ask.id}'),
                    controller: c,
                    sessionID: ask.sessionID,
                    requestID: ask.id,
                  ),
              ],
            ),
          ),
        );

    Future<void> see(
      WidgetTester tester,
      ConnectionController c,
      PermissionRequest ask,
    ) async {
      c.handleEventForTesting(
        EventEnvelope(
          type: 'permission.asked',
          properties: {
            'id': ask.id,
            'sessionID': ask.sessionID,
            'permission': ask.permission,
            'patterns': ask.patterns,
            'metadata': <String, Object?>{},
            'always': ask.always,
          },
        ),
      );
      await tester.pumpWidget(invitations(c, [ask]));
      await tester.pumpAndSettle();
    }

    testWidgets('offered once, states its scope, recorded after the server '
        'took it', (tester) async {
      final api = _Api();
      final c = await _controller(api: api);
      addTearDown(c.dispose);

      await see(tester, c, _ask('r1'));
      expect(_key('always-allow-invite'), findsNothing);
      await see(tester, c, _ask('r2'));
      expect(_key('always-allow-invite'), findsNothing);
      // A replayed request is not a new ask.
      await tester.pumpWidget(const SizedBox());
      await see(tester, c, _ask('r2'));
      expect(_key('always-allow-invite'), findsNothing);

      await see(tester, c, _ask('r3'));
      expect(_key('always-allow-invite'), findsOneWidget);
      expect(
        find.text(_en.consentAlwaysAllowQuestion(KitBidi.ltr('git *'))),
        findsOneWidget,
      );
      // Rebuilt for the same request, the invitation stays.
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(invitations(c, [_ask('r3')]));
      await tester.pumpAndSettle();
      expect(_key('always-allow-invite'), findsOneWidget);

      // A refused reply is said and not recorded.
      api.refuse = true;
      await tester.tap(_key('always-allow-invite-accept'));
      await tester.pumpAndSettle();
      expect(find.textContaining('git *'), findsWidgets);
      await tester.tap(_key('always-allow-invite-confirm-allow'));
      await tester.pumpAndSettle();
      expect(_key('always-allow-invite-failed'), findsOneWidget);
      expect(api.replies.last, (requestID: 'r3', reply: 'always'));

      api.refuse = false;
      await tester.tap(_key('always-allow-invite-accept'));
      await tester.pumpAndSettle();
      await tester.tap(_key('always-allow-invite-confirm-allow'));
      await tester.pumpAndSettle();
      expect(_key('always-allow-invite'), findsNothing);
      final status = await ConsentOwners.repeated(
        c.store.prefs,
        'phone',
      ).status(_scope);
      expect(status.decision.name, 'accepted');

      // Never again for this scope.
      await see(tester, c, _ask('r4'));
      expect(_key('always-allow-invite'), findsNothing);
    });

    testWidgets('Keep asking is remembered, explained and can be undone', (
      tester,
    ) async {
      final api = _Api();
      final c = await _controller(api: api);
      addTearDown(c.dispose);
      for (final id in ['r1', 'r2', 'r3']) {
        await see(tester, c, _ask(id));
      }
      await tester.tap(_key('always-allow-invite-decline'));
      await tester.pumpAndSettle();
      expect(_key('always-allow-invite'), findsNothing);
      expect(api.replies, isEmpty);

      await _openSettings(tester, c);
      expect(_key('automation-consent-always-allow'), findsOneWidget);
      expect(find.text(_en.consentValueDeclinedCount(1)), findsOneWidget);
      await tester.tap(_key('automation-consent-always-allow'));
      await tester.pumpAndSettle();
      await tester.tap(_key('automation-consent-offer-again-confirm'));
      await tester.pumpAndSettle();
      expect(_key('automation-consent-always-allow'), findsNothing);

      // Offered again after three more identical asks.
      for (final id in ['r4', 'r5', 'r6']) {
        await see(tester, c, _ask(id));
      }
      expect(_key('always-allow-invite'), findsOneWidget);
    });
  });

  testWidgets('deleting a server removes its answers for good', (tester) async {
    final c = await _controller();
    addTearDown(c.dispose);
    final consent = await ConsentOwners.inFlow(c.store.prefs, 'studio');
    await consent.requestNeedsYouPreset();
    await consent.answer(InFlowConsentKind.needsYouNotifications, allow: false);
    await ConsentOwners.repeated(
      c.store.prefs,
      'studio',
    ).observe(scope: _scope, requestID: 'r1', supportsPersistentGrants: true);
    expect(c.store.prefs.getString('oc.inFlowConsent.studio'), isNotNull);
    expect(c.store.prefs.getString('oc.permissionConsent.studio'), isNotNull);

    await c.deleteProfileAndLocalData('studio');
    expect(c.store.prefs.getString('oc.inFlowConsent.studio'), isNull);
    expect(c.store.prefs.getString('oc.permissionConsent.studio'), isNull);
    // The closed owner is gone; a stale one cannot write the record back.
    expect(ConsentOwners.inFlowLoaded(c.store.prefs, 'studio'), isNull);
    await expectLater(
      consent.answer(InFlowConsentKind.needsYouNotifications, allow: true),
      throwsStateError,
    );
    expect(c.store.prefs.getString('oc.inFlowConsent.studio'), isNull);
  });
}
