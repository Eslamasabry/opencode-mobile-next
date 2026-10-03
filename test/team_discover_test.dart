// Finding the AI Team while it is off (docs/qa/team-discover-2026-09-25):
// the Work tab's entry and its folded row, the intro and where its one
// primary action leads for each kind of server, Settings' AI Team row, the
// empty home's drawing and the merged celebration's length; New
// conversation's Solo · Team and the team's tasks in the Work tab's lists
// (docs/design/team-conversation-2026-09-26.md).

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:opencode_mobile/domain/phone_project_engine.dart';
import 'package:opencode_mobile/state/phone_team_setup.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/aiteam_scripts.dart'
    show AiTeamPins;
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_selection.dart'
    show setupSizeText;
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/team_runtime.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_settings_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_intro_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_page.dart';
import 'package:opencode_mobile/ui/widgets/builtin_team_section.dart';
import 'package:opencode_mobile/ui/widgets/team_discover.dart';
import 'package:opencode_mobile/ui/widgets/team_host_form.dart';
import 'package:opencode_mobile/ui/widgets/team_phone_onboarding.dart'
    show debugTeamPhoneRunJob;
import 'package:opencode_mobile/voice/device.dart';
import 'package:opencode_mobile/ui/widgets/team_moments.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_setup_engine.dart';
import 'support/team_golden_fixture.dart';

final _en = lookupAppLocalizations(const Locale('en'));

Finder _key(String key) => find.byKey(ValueKey(key));

/// A scripted Gas City probe: answers [found] for every address, or
/// unreachable.
class _Probe {
  ProbeFound? found;
  final calls = <String>[];

  Future<ProbeVerdict> call(String url, {String? city}) async {
    calls.add(url);
    return found ?? const ProbeUnreachable(error: 'no answer');
  }
}

ProbeFound _found() => const ProbeFound(
  host: OrchestrationHostIdentity(
    provider: 'gascity',
    url: 'http://100.100.1.2:8372',
    hostMode: OrchestrationHostMode.computer,
    version: '1.4.1',
    city: 'bright-lights',
  ),
  version: '1.4.1',
  city: 'bright-lights',
  readOnly: true,
);

/// A Termux team runtime that answers whether this phone can run a team.
class _Runtime extends TermuxTeamRuntime {
  _Runtime({required this.supported})
    : super(
        runner: (_, {timeout = Duration.zero}) async => '',
        manifestLoader: () async => null,
        archProbe: () async => 'aarch64',
      );

  final bool supported;

  @override
  Future<bool> get supportsAiTeam async => supported;

  @override
  Future<TeamRuntimeManifest?> manifest() async => null;

  @override
  Future<TeamRuntimeStatus> status() async =>
      const TeamRuntimeStatus(phase: TeamRuntimePhase.idle);
}

/// The fake registry with AI Team, as a host's engine lists it.
final _teamRegistry = [
  ...FakeSetupEngine.fakeRegistry,
  const SetupComponent(
    id: SetupComponentIds.aiTeam,
    title: 'AI Team',
    shortTitle: 'AI Team',
    checkScript: 'true',
    installScript: 'true',
    dependsOn: ['essentials', 'opencode'],
  ),
];

/// The in-app team, not installed; nothing reaches the phone's Linux.
class _BuiltinTeam extends BuiltinTeam {
  @override
  Future<BuiltinTeamState> status() async => const BuiltinTeamState();
}

void _mockChannels() {
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  for (final channel in [
    'plugins.it_nomads.com/flutter_secure_storage',
    'oc/background',
    'oc/shortcut',
    'oc/voice',
  ]) {
    messenger.setMockMethodCallHandler(
      MethodChannel(channel),
      (call) async => switch (call.method) {
        'readAll' => <String, String>{},
        'getDeviceInfo' => <String, Object?>{},
        _ => null,
      },
    );
    addTearDown(
      () => messenger.setMockMethodCallHandler(MethodChannel(channel), null),
    );
  }
}

/// A controller on one saved server, as the app has it after connecting.
Future<ConnectionController> _boot(ServerProfile profile) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.upsert(profile);
  await store.setActiveId(profile.id);
  final controller = ConnectionController(store);
  addTearDown(controller.dispose);
  controller.adoptConnectedProfileForTesting(profile);
  controller.syncOrchestration();
  return controller;
}

ServerProfile _computer() => ServerProfile(
  id: 'workstation',
  name: 'Workstation',
  baseUrl: 'http://100.100.1.2:4096',
);

ServerProfile _termux() => ServerProfile(
  id: 'termux',
  name: 'This device (Termux)',
  baseUrl: 'http://127.0.0.1:4096',
);

ServerProfile _inApp() => ServerProfile(
  id: 'in-app',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4097',
);

Widget _app(Widget home, {Map<String, WidgetBuilder> routes = const {}}) =>
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      routes: routes,
      home: home,
    );

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void _tallScreen(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _Probe probe;

  setUp(() {
    probe = _Probe();
    teamHostProbe = probe.call;
  });
  tearDown(() {
    teamHostProbe = defaultTeamHostProbe;
    debugPlatformCapabilities = null;
    debugBuiltinTeam = null;
  });

  group('the intro', () {
    /// Brings [finder] into the intro's list (built lazily).
    Future<void> reveal(WidgetTester tester, Finder finder) async {
      await tester.scrollUntilVisible(
        finder,
        200,
        scrollable: find.descendant(
          of: _key('team-intro'),
          matching: find.byType(Scrollable),
        ),
      );
      await tester.ensureVisible(finder);
      await _settle(tester);
    }

    /// A 64-bit phone with room and memory: its pre-flight passes.
    Future<VoiceDeviceInfo> goodPhone() async => const VoiceDeviceInfo(
      supportedAbis: ['arm64-v8a'],
      totalMemoryMb: 8192,
      memoryClassMb: 512,
      hasMicrophone: true,
      availableStorageBytes: 20000000000,
    );

    Future<void> pumpIntro(
      WidgetTester tester,
      ConnectionController controller, {
      TermuxTeamRuntime? runtime,
      Future<VoiceDeviceInfo> Function()? device,
    }) async {
      _tallScreen(tester);
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: TextButton(
                  onPressed: () => openTeamPage(
                    context,
                    controller,
                    runtime: runtime,
                    deviceProbe: device ?? goodPhone,
                  ),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
          routes: {
            '/this-phone': (_) =>
                const Scaffold(body: Text('termux setup screen')),
          },
        ),
      );
      await tester.tap(find.text('open'));
      await _settle(tester);
    }

    testWidgets('says what the team does, in four steps', (tester) async {
      _mockChannels();
      final controller = await _boot(_computer());
      await pumpIntro(tester, controller);
      for (final title in [
        _en.teamDiscoverStepPlanTitle,
        _en.teamDiscoverStepWorkTitle,
        _en.teamDiscoverStepCheckTitle,
        _en.teamDiscoverStepMergeTitle,
      ]) {
        expect(find.text(title), findsOneWidget, reason: title);
      }
      expect(_key('team-intro-drawing'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('a computer where Gas City answers: Turn on turns it on', (
      tester,
    ) async {
      _mockChannels();
      probe.found = _found();
      final controller = await _boot(_computer());
      await pumpIntro(tester, controller);
      expect(probe.calls, isNotEmpty);
      await reveal(tester, _key('team-intro-found'));
      expect(find.text(_en.pluginsTeamRowFound('Workstation')), findsOneWidget);
      await tester.tap(_key('team-intro-turn-on'));
      await _settle(tester);
      expect(controller.profile!.orchestration, isNotNull);
      // The same page is now the team's (P3.4): no hop back, no second
      // page.
      expect(find.byType(TeamIntroScreen), findsNothing);
      expect(find.byType(TeamPage), findsOneWidget);
      expect(find.byType(TeamHomeScreen), findsOneWidget);
      expect(find.text('open'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('a computer without it: Set it up shows how, then looks '
        'again; the address is the other way', (tester) async {
      _mockChannels();
      final controller = await _boot(_computer());
      await pumpIntro(tester, controller);
      await reveal(tester, _key('team-intro-needs'));
      expect(
        find.text(_en.teamDiscoverNeedsServer('Workstation')),
        findsOneWidget,
      );
      expect(_key('team-intro-found'), findsNothing);
      // Why nothing was found, in words (P3.4).
      await reveal(tester, _key('team-intro-miss'));
      expect(find.text(_en.teamIntroNotFound('Workstation')), findsOneWidget);
      expect(find.text(_en.teamUiVerdictUnreachable), findsOneWidget);
      final before = probe.calls.length;
      await tester.tap(_key('team-intro-set-up'));
      await _settle(tester);
      expect(_key('team-host-guide'), findsOneWidget);
      await tester.tapAt(const Offset(200, 40));
      await _settle(tester);
      expect(probe.calls.length, greaterThan(before));

      await tester.tap(_key('team-intro-address'));
      await _settle(tester);
      expect(find.byType(TeamHostForm), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('the host guide ends in its next step: Enter the address '
        'closes it and opens the address form (team-host-guide-sheet)', (
      tester,
    ) async {
      _mockChannels();
      final controller = await _boot(_computer());
      await pumpIntro(tester, controller);
      await reveal(tester, _key('team-intro-set-up'));
      await tester.tap(_key('team-intro-set-up'));
      await _settle(tester);
      expect(_key('team-host-guide'), findsOneWidget);
      final next = _key('team-host-guide-enter-address');
      expect(
        find.descendant(of: next, matching: find.text('Enter the address')),
        findsOneWidget,
      );
      await tester.tap(next);
      await _settle(tester);
      expect(_key('team-host-guide'), findsNothing);
      expect(find.byType(TeamHostForm), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('OpenCode inside the app: Turn on is the one-tap setup page '
        'of the phone engine', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      debugBuiltinTeam = _BuiltinTeam();
      final engine = FakeSetupEngine(registry: _teamRegistry);
      PhoneSetup.engine = engine;
      final phone = _WaitingPhone();
      PhoneTeamSetup.debugPorts = (_) => phone;
      addTearDown(() => PhoneTeamSetup.debugPorts = null);
      _mockChannels();
      final controller = await _boot(_inApp());
      expect(teamServerKindOf(controller.profile!), TeamServerKind.inApp);
      await pumpIntro(tester, controller);
      await reveal(tester, find.text(_en.teamDiscoverBatteryTitle));
      expect(find.text(_en.teamDiscoverNeedsPhone), findsOneWidget);
      await tester.tap(_key('team-intro-set-up'));
      await _settle(tester);
      expect(_key('phone-team-steps'), findsOneWidget);
      expect(find.textContaining(_en.phoneTeamReplyWaiting), findsOneWidget);
      // The reply ends, so the page's wait (and its polling timer) ends too.
      phone.endReply();
      await _settle(tester);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('AI Team installed but not on: no download offer, Turn on '
        'is the action (Add tools left it installed)', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      debugBuiltinTeam = _BuiltinTeam();
      final engine = FakeSetupEngine(registry: _teamRegistry)
        ..optionalInstalled = {'aiteam'};
      PhoneSetup.engine = engine;
      _mockChannels();
      final controller = await _boot(_inApp());
      await pumpIntro(tester, controller);
      await reveal(tester, find.text(_en.teamDiscoverBatteryTitle));
      expect(find.byKey(const ValueKey('team-intro-cost')), findsNothing);
      expect(
        find.text(
          _en.teamDiscoverDownloadTitle(
            setupSizeText(_en, AiTeamPins.deviceDownloadBytes),
          ),
        ),
        findsNothing,
      );
      expect(find.text(_en.teamIntroInstalledTitle), findsOneWidget);
      expect(find.text(_en.teamIntroTurnOnPhone), findsOneWidget);
      expect(find.text(_en.teamIntroSetUpPhone), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('Termux: Set it up is Add tools › AI Team on the Termux host', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      final hosts = <SetupHostKind>[];
      debugTeamPhoneRunJob = (_, host, _) async => hosts.add(host);
      addTearDown(() => debugTeamPhoneRunJob = null);
      PhoneSetup.termux = FakeSetupEngine(registry: _teamRegistry);
      _mockChannels();
      final controller = await _boot(_termux());
      await pumpIntro(tester, controller, runtime: _Runtime(supported: true));
      await reveal(tester, find.text(_en.teamDiscoverTermuxBatteryBody));
      await tester.tap(_key('team-intro-set-up'));
      await _settle(tester);
      expect(_key('phone-setup-customize-sheet'), findsOneWidget);
      await tester.tap(_key('phone-setup-customize-done'));
      await _settle(tester);
      expect(hosts, [SetupHostKind.termux]);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('a 32-bit phone is told why by the pre-flight, never '
        'hidden, and offered a computer', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      _mockChannels();
      final controller = await _boot(_termux());
      // New conversation still offers Team on this server.
      expect(await teamPossibleOn(controller.profile!), isTrue);
      await pumpIntro(
        tester,
        controller,
        device: () async => const VoiceDeviceInfo(
          supportedAbis: ['armeabi-v7a'],
          totalMemoryMb: 4096,
          memoryClassMb: 256,
          hasMicrophone: true,
          availableStorageBytes: 20000000000,
        ),
      );
      await reveal(tester, _key('team-intro-on-computer'));
      expect(_key('team-intro-unsupported'), findsOneWidget);
      expect(
        find.text(_en.phoneSetupPreflightUnsupportedHeadline),
        findsOneWidget,
      );
      expect(
        find.text(_en.phoneSetupPreflightUnsupportedBody('armeabi-v7a')),
        findsOneWidget,
      );
      expect(_key('team-intro-set-up'), findsNothing);
      await tester.tap(_key('team-intro-on-computer'));
      await _settle(tester);
      expect(_key('team-host-guide'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('a phone short of space is told how much to free', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      _mockChannels();
      final controller = await _boot(_inApp());
      await pumpIntro(
        tester,
        controller,
        device: () async => const VoiceDeviceInfo(
          supportedAbis: ['arm64-v8a'],
          totalMemoryMb: 8192,
          memoryClassMb: 512,
          hasMicrophone: true,
          availableStorageBytes: 100000000,
        ),
      );
      await reveal(tester, _key('team-intro-unsupported'));
      expect(
        find.text(_en.phoneSetupPreflightLowSpaceHeadline),
        findsOneWidget,
      );
      expect(_key('team-intro-set-up'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('Settings', () {
    Future<void> pumpSettings(
      WidgetTester tester,
      ConnectionController controller,
    ) async {
      _tallScreen(tester);
      await tester.pumpWidget(_app(SettingsScreen(controller: controller)));
      await _settle(tester);
    }

    // The state is the row's short value at its end (canvas Settings.png).
    String line(WidgetTester tester) => tester
        .widget<KitRowValue>(
          find.descendant(
            of: _key('settings-ai-team'),
            matching: find.byType(KitRowValue),
          ),
        )
        .value;

    testWidgets('has an AI Team row that reads Off and what it is for, and '
        'opens the intro', (tester) async {
      _mockChannels();
      final controller = await _boot(_computer());
      await pumpSettings(tester, controller);
      await tester.ensureVisible(_key('settings-ai-team'));
      await _settle(tester);
      expect(line(tester), startsWith(_en.teamUiRowOff));
      await tester.tap(_key('settings-ai-team'));
      await _settle(tester);
      expect(find.byType(TeamIntroScreen), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('reads On · the server once it is on', (tester) async {
      _mockChannels();
      final profile = _computer()
        ..orchestration = OrchestrationConfig(
          provider: OrchestrationProvider.gascity,
          url: 'https://team.example',
          city: 'city',
          hostMode: OrchestrationHostMode.computer,
          enabledAt: DateTime.utc(2026, 9, 25),
        );
      final controller = await _boot(profile);
      await pumpSettings(tester, controller);
      await tester.ensureVisible(_key('settings-ai-team'));
      await _settle(tester);
      // The Plugins row's words, whatever the team's state is now.
      expect(line(tester), teamStateLine(_en, controller));
      expect(line(tester), isNot(startsWith(_en.teamUiRowOff)));
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('tapping it while on opens Team settings (setup only)', (
      tester,
    ) async {
      _mockChannels();
      final profile = _computer()
        ..orchestration = OrchestrationConfig(
          provider: OrchestrationProvider.gascity,
          url: 'https://team.example',
          city: 'city',
          hostMode: OrchestrationHostMode.computer,
          enabledAt: DateTime.utc(2026, 9, 25),
        );
      final controller = await _boot(profile);
      await pumpSettings(tester, controller);
      await tester.ensureVisible(_key('settings-ai-team'));
      await _settle(tester);
      await tester.tap(_key('settings-ai-team'));
      await _settle(tester);
      expect(find.byType(TeamSettingsScreen), findsOneWidget);
      expect(find.byType(TeamHomeScreen), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('one AI Team per page (OpenCode inside the app)', () {
    testWidgets('no contradicting AI Team rows while turning on', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      debugBuiltinTeam = _BuiltinTeam();
      final engine = FakeSetupEngine();
      PhoneSetup.engine = engine;
      _mockChannels();
      _tallScreen(tester);
      final controller = await _boot(_inApp());
      // Add tools with AI Team is installing it.
      engine.emit(
        const SetupProgress(
          state: SetupState.running,
          components: [
            ComponentProgress(
              id: SetupComponentIds.aiTeam,
              state: ComponentState.running,
            ),
          ],
          overall: .4,
        ),
      );

      // Settings says turning on, never Off.
      await tester.pumpWidget(_app(SettingsScreen(controller: controller)));
      await _settle(tester);
      await tester.ensureVisible(_key('settings-ai-team'));
      await _settle(tester);
      String line() => tester
          .widget<KitRowValue>(
            find.descendant(
              of: _key('settings-ai-team'),
              matching: find.byType(KitRowValue),
            ),
          )
          .value;
      expect(line(), _en.teamDiscoverTurningOn);

      // Done installing: the card offers to turn it on; Settings reads Off.
      engine.emit(SetupProgress.idle);
      await _settle(tester);
      expect(line(), startsWith(_en.teamUiRowOff));
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('once the phone team is on, one state: On · This phone', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      debugBuiltinTeam = _BuiltinTeam();
      PhoneSetup.engine = FakeSetupEngine();
      _mockChannels();
      _tallScreen(tester);
      final controller = await _boot(
        _inApp()..orchestration = BuiltinTeam.config(),
      );
      await tester.pumpWidget(_app(SettingsScreen(controller: controller)));
      await _settle(tester);
      await tester.ensureVisible(_key('settings-ai-team'));
      await _settle(tester);
      final line = tester
          .widget<KitRowValue>(
            find.descendant(
              of: _key('settings-ai-team'),
              matching: find.byType(KitRowValue),
            ),
          )
          .value;
      expect(line, teamStateLine(_en, controller));
      expect(line, isNot(startsWith(_en.teamUiRowOff)));
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('the team screens', () {
    testWidgets('the empty home gives its drawing room', (tester) async {
      _tallScreen(tester);
      final team = await teamSceneController(TeamScene.empty);
      addTearDown(team.dispose);
      await tester.pumpWidget(
        _app(TeamHomeScreen(controller: team, now: () => teamSceneClock)),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pump(KitMotion.entrance);
      final drawing = find.descendant(
        of: _key('team-home-runs-empty'),
        matching: find.byType(KitIllustration),
      );
      // The board and its three agents read at this size; 88 dp cramped
      // them.
      expect(tester.getSize(drawing).width, greaterThanOrEqualTo(160));
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('the merged celebration plays for a celebration', (
      tester,
    ) async {
      TeamCelebrations.forgetSession();
      SharedPreferences.setMockInitialValues({});
      await tester.pumpWidget(
        _app(
          const Scaffold(
            body: TeamMergedCelebration(
              profileId: 'p',
              runId: 'merged-run',
              merged: true,
            ),
          ),
        ),
      );
      await _settle(tester);
      final drawing = tester.widget<KitIllustration>(
        _key('team-run-celebration'),
      );
      expect(drawing.entranceDuration, KitMotion.celebration);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });
}

/// A phone with a reply still running: the setup page waits, changes nothing.
class _WaitingPhone extends ChangeNotifier implements PhoneTeamSetupPorts {
  bool replying = true;

  /// The reply ends: the setup stops polling for it.
  void endReply() {
    replying = false;
    notifyListeners();
  }

  @override
  bool get hasServer => true;
  @override
  bool get wasOn => false;
  @override
  bool get replyRunning => replying;
  @override
  Listenable get replyChanges => this;
  @override
  Future<PhoneTeamHostState> inspect() async => const PhoneTeamHostState();
  @override
  Future<void> stopServer() async {}
  @override
  Future<void> closeTerminals() async {}
  @override
  Future<PhoneEngineHealth> startEngine() => throw UnimplementedError();
  @override
  Future<PhoneEngineHealth> probeEngine() => throw UnimplementedError();
  @override
  Future<String?> startServer() async => null;
  @override
  Future<bool> restoreServer() async => true;
  @override
  Future<void> attach() async {}
}
