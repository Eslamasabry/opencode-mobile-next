// slice-termux-clarity (owner report on build 2061, 2026-09-28): a Termux
// user whose app storage was reset saw a truncated "Allow Termux access in
// phone setup…" row over the full first-run welcome, and This phone said
// "couldn't check… Try again" while offering acts that need Termux.
//
// These tests pin one typed cause per failure, the words and the one fix for
// each, the permission request itself, the gating of Termux-only acts, and
// the first screen that leads with what it found.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/termux_reach.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:opencode_mobile/ui/widgets/termux_problem_fix.dart';
import 'package:opencode_mobile/ui/widgets/termux_running_server_entry.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'revamp/screen_phone_1_fixtures.dart';

final _l10n = lookupAppLocalizations(const Locale('en'));

class _Connection extends ConnectionController {
  _Connection(super.store);
  final attempted = <ServerProfile>[];
  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    attempted.add(profile);
    api = FakeServerGateway(baseUrl: profile.baseUrl);
  }
}

/// A phone with Termux and the app's OpenCode in it, as Android and Termux
/// answer the `oc/termux` channel. No `expect` runs inside the handler: it
/// answers inside pumps.
class _Phone {
  bool installed = true;
  bool serviceAvailable = true;
  bool granted = false;

  /// Termux's `allow-external-apps` (kept across the app's storage reset).
  bool otherAppsAllowed = true;

  /// Termux answers commands (false: Android froze it).
  bool awake = true;

  /// What Android answers the permission question with.
  String accessAnswer = 'granted';
  String status =
      'phase=ready\nport=4096\nruntime=opencode1\nversion=1.18.32\npid=12\n';
  final methods = <String>[];

  Future<Object?> handle(MethodCall call) async {
    methods.add(call.method);
    switch (call.method) {
      case 'getCapabilities':
        return <String, Object>{
          'installed': installed,
          'version': '0.118',
          'serviceAvailable': serviceAvailable,
          'protocolSupported': true,
          'permissionGranted': granted,
        };
      case 'requestRunCommandAccess':
        if (accessAnswer == 'granted') granted = true;
        return accessAnswer;
      case 'openAppSettings' || 'openTermux':
        return true;
      case 'runInTermux':
        if (!granted) {
          throw PlatformException(code: 'permission_denied', message: 'x');
        }
        if (!awake) {
          throw PlatformException(code: 'command_timeout', message: 'x');
        }
        if (!otherAppsAllowed) {
          return {
            'stdout': '',
            'stderr': '',
            'exitCode': -1,
            'err': 2,
            'errorMessage':
                'OpenCode requires `allow-external-apps` property to be set '
                'to `true` in `~/.termux/termux.properties` file.',
          };
        }
        final script = (call.arguments as Map)['script'] as String;
        if (script.contains('server.password')) {
          return {'stdout': 'the-password-setup-wrote', 'exitCode': 0};
        }
        return {'stdout': status, 'stderr': '', 'exitCode': 0};
    }
    return null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/termux');
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final oldProbe = termuxRunningServerProbe;
  late _Phone phone;
  late ServerProbeResult health;

  setUp(() {
    phone = _Phone();
    health = const ServerProbeResult.failure(
      'password required',
      needsPassword: true,
    );
    TermuxAccess.blocked = false;
    debugPlatformCapabilities = const PlatformCapabilities.android();
    termuxRunningServerProbe =
        ({required baseUrl, username, password, cancellation}) async => health;
    messenger.setMockMethodCallHandler(channel, phone.handle);
    final secrets = <String, String>{};
    messenger.setMockMethodCallHandler(secure, (call) async {
      final args = (call.arguments as Map?) ?? const {};
      final key = args['key'] as String?;
      return switch (call.method) {
        'write' => secrets[key!] = args['value'] as String,
        'read' => secrets[key],
        'readAll' => secrets,
        'containsKey' => secrets.containsKey(key),
        'delete' => secrets.remove(key),
        _ => null,
      };
    });
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    termuxRunningServerProbe = oldProbe;
    TermuxAccess.blocked = false;
    messenger.setMockMethodCallHandler(channel, null);
    messenger.setMockMethodCallHandler(secure, null);
  });

  group('one typed cause', () {
    test('capabilities name the cause before any command', () {
      TermuxCapabilities caps({
        bool installed = true,
        bool service = true,
        bool granted = true,
      }) => TermuxCapabilities(
        installed: installed,
        version: '0.118',
        serviceAvailable: service,
        protocolSupported: true,
        permissionGranted: granted,
      );
      expect(
        termuxProblemOfCapabilities(caps(installed: false)),
        TermuxProblem.notInstalled,
      );
      expect(
        termuxProblemOfCapabilities(caps(service: false)),
        TermuxProblem.outdated,
      );
      expect(
        termuxProblemOfCapabilities(caps(granted: false)),
        TermuxProblem.accessNeeded,
      );
      TermuxAccess.blocked = true;
      expect(
        termuxProblemOfCapabilities(caps(granted: false)),
        TermuxProblem.accessBlocked,
      );
      expect(termuxProblemOfCapabilities(caps()), isNull);
      expect(
        termuxProblemOfCapabilities(const TermuxCapabilities.unavailable()),
        isNull,
      );
    });

    test('bridge failures map to their cause', () {
      TermuxProblem of(String code, [String message = '']) =>
          termuxProblemOfError(TermuxBridgeException(message, code: code));
      expect(of('permission_denied'), TermuxProblem.accessNeeded);
      expect(of('service_unavailable'), TermuxProblem.outdated);
      expect(of('command_timeout'), TermuxProblem.asleep);
      expect(of('missing_result'), TermuxProblem.asleep);
      expect(
        of('command_failed', 'needs `allow-external-apps` set to `true`'),
        TermuxProblem.otherAppsOff,
      );
      expect(of('command_failed', 'exit 1'), TermuxProblem.unknown);
    });

    test('the observation carries the cause', () async {
      // Storage reset: Android took the permission back; OpenCode still
      // answers on the phone.
      var seen = await detectTermuxRunningServer();
      expect(seen.state, TermuxRunningServerState.denied);
      expect(seen.problem, TermuxProblem.accessNeeded);
      expect(seen.heardOnPhone, isTrue);
      expect(phone.methods, ['getCapabilities']);

      phone.granted = true;
      phone.otherAppsAllowed = false;
      seen = await detectTermuxRunningServer();
      expect(seen.problem, TermuxProblem.otherAppsOff);

      phone.otherAppsAllowed = true;
      phone.awake = false;
      seen = await detectTermuxRunningServer();
      expect(seen.problem, TermuxProblem.asleep);

      phone.awake = true;
      health = const ServerProbeResult.failure('refused');
      seen = await detectTermuxRunningServer();
      expect(seen.problem, TermuxProblem.notAnswering);
      expect(seen.runtime, TermuxRuntime.openCode1);

      phone.installed = false;
      seen = await detectTermuxRunningServer();
      expect(seen.state, TermuxRunningServerState.absent);
      expect(seen.problem, TermuxProblem.notInstalled);
    });

    test('every cause has plain words and one named fix; retry only where '
        'it can help', () {
      for (final problem in TermuxProblem.values) {
        final words = termuxProblemWords(_l10n, problem, runtime: 'OpenCode 1');
        expect(words.line, isNotEmpty, reason: '$problem');
        expect(words.line, isNot(contains('Exception')), reason: '$problem');
        final retry = words.action == _l10n.commonRetry;
        expect(retry, problem.retryHelps, reason: '$problem');
      }
      expect(
        termuxProblemWords(_l10n, TermuxProblem.accessNeeded).action,
        'Allow access to Termux',
      );
      expect(
        termuxProblemWords(_l10n, TermuxProblem.otherAppsOff).action,
        'Allow other apps in Termux',
      );
      expect(
        termuxProblemWords(_l10n, TermuxProblem.asleep).action,
        'Open Termux',
      );
      expect(
        termuxProblemWords(
          _l10n,
          TermuxProblem.notAnswering,
          runtime: 'OpenCode 1',
        ).action,
        'Restart OpenCode 1 in Termux',
      );
      expect(
        termuxProblemWords(_l10n, TermuxProblem.notInstalled).action,
        'Get Termux',
      );
    });
  });

  group('the permission request', () {
    test('asks Android directly and remembers a permanent no', () async {
      expect(await TermuxAccess.request(), TermuxAccessAnswer.granted);
      expect(phone.methods, ['requestRunCommandAccess']);
      phone.accessAnswer = 'permanentlyDenied';
      phone.granted = false;
      expect(
        await TermuxAccess.request(),
        TermuxAccessAnswer.permanentlyDenied,
      );
      expect(TermuxAccess.blocked, isTrue);
    });

    Future<ServerProfile?> pumpEntry(
      WidgetTester tester, {
      List<ServerProfile> profiles = const [],
    }) async {
      ServerProfile? opened;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: SingleChildScrollView(
              child: TermuxRunningServerEntry(
                profiles: profiles,
                busy: false,
                revision: 0,
                onConnect: (profile) => opened = profile,
                onEnterCredentials: (_, _) {},
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return opened;
    }

    final saved = ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: TermuxBridge.managedServerUrl,
      password: 'synthetic',
      serverVersion: '1.18.32',
    );

    testWidgets('the row says the cause inline and fixes it in one tap, '
        'then connects', (tester) async {
      health = const ServerProbeResult.success('1.18.32');
      var connected = <ServerProfile>[];
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: SingleChildScrollView(
              child: TermuxRunningServerEntry(
                profiles: [saved],
                busy: false,
                revision: 0,
                onConnect: connected.add,
                onEnterCredentials: (_, _) {},
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      // One "Needs you", inline at the start of the line; the cause whole.
      expect(
        find.text(
          'Needs you · ${_l10n.termuxProblemAccessHeard}',
          findRichText: true,
        ),
        findsOneWidget,
      );
      expect(find.text(_l10n.commonRetry), findsNothing);
      await tester.tap(find.byKey(const ValueKey('termux-running-server-fix')));
      await tester.pumpAndSettle();
      expect(phone.methods, contains('requestRunCommandAccess'));
      // Granted: read again, found running, connected without another tap.
      expect(connected, [saved]);
    });

    testWidgets('a permanent no opens the app\'s permission settings', (
      tester,
    ) async {
      phone.accessAnswer = 'permanentlyDenied';
      await pumpEntry(tester, profiles: [saved]);
      await tester.tap(find.byKey(const ValueKey('termux-running-server-fix')));
      await tester.pumpAndSettle();
      expect(phone.methods, contains('openAppSettings'));
      // Coming back: the row names the settings page, not the dialog.
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pumpAndSettle();
      expect(find.text(_l10n.termuxFixOpenPermissions), findsOneWidget);
    });

    testWidgets('Termux refusing other apps offers the line to paste', (
      tester,
    ) async {
      phone.granted = true;
      phone.otherAppsAllowed = false;
      await pumpEntry(tester, profiles: [saved]);
      await tester.tap(find.byKey(const ValueKey('termux-running-server-fix')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('termux-other-apps-sheet')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('termux-other-apps-line')),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('termux-other-apps-open')));
      await tester.pumpAndSettle();
      expect(phone.methods, contains('openTermux'));
    });
  });

  group('the first screen after a storage reset', () {
    Future<(ProfileStore, _Connection)> pumpServers(WidgetTester tester) async {
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      final connection = _Connection(store);
      addTearDown(connection.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(connection),
          ],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            routes: {'/home': (_) => const Scaffold(body: Text('Work'))},
            home: const ServersScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return (store, connection);
    }

    testWidgets('leads with OpenCode found in Termux and one act; the '
        'welcome steps back', (tester) async {
      await pumpServers(tester);
      expect(find.byKey(const ValueKey('servers-welcome-hero')), findsNothing);
      expect(find.byKey(const ValueKey('welcome-question')), findsNothing);
      expect(find.text(_l10n.termuxLeadRunning), findsOneWidget);
      // The line does not repeat the heading.
      expect(find.text(_l10n.termuxLeadAccessLine), findsOneWidget);
      expect(find.text(_l10n.termuxProblemAccessHeard), findsNothing);
      expect(find.text('Allow access to Termux'), findsOneWidget);
      // Second: the fresh start; third: the other ways, compact.
      final lead = tester.getRect(find.byKey(const ValueKey('termux-lead')));
      final inApp = tester.getRect(
        find.byKey(const ValueKey('welcome-choice-in-app')),
      );
      final computer = tester.getRect(
        find.byKey(const ValueKey('welcome-choice-computer')),
      );
      expect(inApp.top, greaterThan(lead.bottom));
      expect(computer.top, greaterThan(inApp.bottom));
      expect(find.text(_l10n.phoneSetupStartOtherWays), findsOneWidget);
      expect(find.byKey(const ValueKey('welcome-choice-demo')), findsOneWidget);
    });

    testWidgets('Allow access connects to the found server and lands in '
        'Work', (tester) async {
      final (store, connection) = await pumpServers(tester);
      await tester.tap(find.byKey(const ValueKey('termux-lead-fix')));
      await tester.pumpAndSettle();
      expect(phone.methods, contains('requestRunCommandAccess'));
      // allow-external-apps survived the reset: the status runs, the
      // password setup wrote comes back from the phone, and it connects.
      final restored = store.profiles.single;
      expect(restored.baseUrl, TermuxBridge.managedServerUrl);
      expect(restored.password, 'the-password-setup-wrote');
      expect(connection.attempted.single.id, restored.id);
      expect(find.text('Work'), findsOneWidget);
    });

    testWidgets('with allow-external-apps gone the lead names that next', (
      tester,
    ) async {
      phone.otherAppsAllowed = false;
      await pumpServers(tester);
      await tester.tap(find.byKey(const ValueKey('termux-lead-fix')));
      await tester.pumpAndSettle();
      expect(find.text('Allow other apps in Termux'), findsOneWidget);
      expect(find.text(_l10n.termuxProblemOtherAppsOff), findsOneWidget);
    });

    testWidgets('without Termux the welcome stays as it was', (tester) async {
      phone.installed = false;
      await pumpServers(tester);
      expect(find.byKey(const ValueKey('welcome-question')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('welcome-choice-phone')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('termux-lead')), findsNothing);
    });
  });

  group('This phone while Termux cannot be reached', () {
    testWidgets('says the cause with its fix, holds back Termux acts and '
        'offers the in-app server', (tester) async {
      await pumpPhone(
        tester,
        home: const ThisPhoneScreen(kind: PhoneHostKind.termux),
      );
      await tester.pumpAndSettle();
      // Not "OpenCode 1": the page could not read which one.
      expect(find.byKey(const ValueKey('this-phone-title')), findsOneWidget);
      final title = tester.widget<RichText>(
        find
            .descendant(
              of: find.byKey(const ValueKey('this-phone-title')),
              matching: find.byType(RichText),
              matchRoot: true,
            )
            .first,
      );
      expect(title.text.toPlainText(), 'OpenCode');
      expect(find.text(_l10n.termuxProblemAccessNeeded), findsOneWidget);
      expect(
        find.byKey(const ValueKey('this-phone-termux-fix')),
        findsOneWidget,
      );
      expect(find.text('Allow access to Termux'), findsOneWidget);
      // One plain answer above the cause, no "Try again".
      expect(find.text(_l10n.thisPhoneAnswerTermux), findsOneWidget);
      expect(
        find.byKey(const ValueKey('this-phone-check-again')),
        findsNothing,
      );
      for (final key in [
        'this-phone-update',
        'this-phone-switch',
        'this-phone-add-tools',
        'local-agent-row',
        'termux-storage-row',
      ]) {
        expect(find.byKey(ValueKey(key)), findsNothing, reason: key);
      }
      expect(
        find.byKey(const ValueKey('this-phone-in-app-instead')),
        findsOneWidget,
      );

      await tester.tap(find.byKey(const ValueKey('this-phone-termux-fix')));
      await tester.pumpAndSettle();
      expect(phone.methods, contains('requestRunCommandAccess'));
      // Granted: the page reads Termux and its acts come back.
      expect(find.text(_l10n.termuxProblemAccessNeeded), findsNothing);
      expect(
        find.byKey(const ValueKey('this-phone-add-tools')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('this-phone-switch')), findsNothing);
      await unmountPhone(tester);
    });
  });
}
