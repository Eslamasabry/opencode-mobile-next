// P3.4 "The team page is one page": every door opens one AI Team page, on
// or off. Off, the same page says what the team does and sets it up; on,
// it is the team, with its switches (Change address, Turn off) in its own
// menu — the AI Team sheet is gone. A pause for heat is never the person's
// pause (persona row 20), and what the team spent today is said only when
// the host reports it.

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart'
    show BuiltinLinuxException;
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/builtin/thermal_guard.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/platform/thermal.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart' show AppStatusTone;
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_settings_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_intro_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_page.dart';
import 'package:opencode_mobile/ui/screens/team/team_states.dart';
import 'package:opencode_mobile/ui/widgets/builtin_team_section.dart';
import 'package:opencode_mobile/ui/widgets/team_host_form.dart';
import 'package:opencode_mobile/ui/widgets/team_switch.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'team_open_settings.dart';

final _en = lookupAppLocalizations(const Locale('en'));
final _clock = DateTime.utc(2026, 9, 27, 14, 2);

Finder _key(String key) => find.byKey(ValueKey(key));

String _fixturePath() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate.path;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

/// The recorded city, with the agents and usage a test needs.
class _Gateway extends FixtureOrchestrationGateway {
  _Gateway({super.hostMode, super.url, this.agentList, this.usageFigure})
    : super(fixturePath: _fixturePath());

  final List<OrchestrationAgent>? agentList;
  final OrchestrationUsage? usageFigure;

  @override
  Future<List<OrchestrationAgent>> agents() async =>
      agentList ?? await super.agents();

  @override
  Future<OrchestrationUsage?> usage() async => usageFigure;
}

/// A heat guard that holds what the test says; nothing reaches Android.
class _Guard extends ThermalGuard {
  _Guard(SharedPreferences prefs, this.held)
    : super(bridge: ThermalBridge(), port: _NoTeams(), prefs: prefs);

  final Map<String, ThermalTeamHold> held;

  @override
  Map<String, ThermalTeamHold> get holds => held;
}

class _NoTeams implements ThermalTeamPort {
  @override
  Future<List<ThermalTeam>> runningHere() async => const [];
  @override
  Future<ThermalTeamHold?> pause(ThermalTeam team, {required DateTime now}) =>
      throw UnimplementedError();
  @override
  Future<ThermalTeamHold> stop(ThermalTeamHold hold) =>
      throw UnimplementedError();
  @override
  Future<bool> resume(ThermalTeamHold hold) => throw UnimplementedError();
}

/// The team inside the app, whose turn-off the test scripts.
class _BuiltinTeam extends BuiltinTeam {
  bool fail = false;
  int turnOffs = 0;

  @override
  Future<void> turnOff() async {
    turnOffs++;
    if (fail) throw const BuiltinLinuxException('disable failed');
  }
}

/// Probes answer that nothing is there.
Future<ProbeVerdict> _nothing(String url, {String? city}) async =>
    const ProbeUnreachable(error: 'no answer');

void _mockChannels() {
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  for (final channel in [
    'plugins.it_nomads.com/flutter_secure_storage',
    'oc/background',
    'oc/shortcut',
  ]) {
    messenger.setMockMethodCallHandler(
      MethodChannel(channel),
      (call) async => call.method == 'readAll' ? <String, String>{} : null,
    );
    addTearDown(
      () => messenger.setMockMethodCallHandler(MethodChannel(channel), null),
    );
  }
}

/// A team over the recorded city: [url] is the fixture's own folder for a
/// team the connection starts itself.
OrchestrationConfig _fixtureConfig({
  String url = 'http://dev-pc:7000',
  OrchestrationHostMode hostMode = OrchestrationHostMode.computer,
}) => OrchestrationConfig(
  provider: OrchestrationProvider.fixture,
  url: url,
  city: 'bright-lights',
  hostMode: hostMode,
  enabledAt: DateTime.utc(2026, 9, 10),
);

ServerProfile _computer({OrchestrationConfig? config}) => ServerProfile(
  id: 'workstation',
  name: 'Workstation',
  baseUrl: 'https://server.example:4096',
  orchestration: config,
);

/// The app's connection on one saved server, its team (if any) started
/// over the recorded city.
Future<ConnectionController> _connect(ServerProfile profile) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.upsert(profile);
  await store.setActiveId(profile.id);
  final connection = ConnectionController(store);
  addTearDown(connection.dispose);
  connection.adoptConnectedProfileForTesting(profile);
  connection.syncOrchestration();
  return connection;
}

/// A started team with the given agents and usage, over the recorded city.
Future<OrchestrationController> _team({
  String profileId = 'phone',
  OrchestrationHostMode hostMode = OrchestrationHostMode.phone,
  String url = 'http://127.0.0.1:8472',
  List<OrchestrationAgent>? agents,
  OrchestrationUsage? usage,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final config = _fixtureConfig(url: url, hostMode: hostMode);
  final team = OrchestrationController(
    profile: ServerProfile(
      id: profileId,
      name: 'This phone',
      baseUrl: 'http://127.0.0.1:4097',
      orchestration: config,
    ),
    config: config,
    store: OrchestrationStore(prefs),
    gatewayFactory: (_, _) => _Gateway(
      hostMode: hostMode,
      url: url,
      agentList: agents,
      usageFigure: usage,
    ),
    now: () => _clock,
  );
  addTearDown(team.dispose);
  await team.start();
  return team;
}

Widget _app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: true),
    child: child!,
  ),
  home: home,
);

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 2000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 15; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

/// Picks [item] from the team page's top bar menu.
Future<void> _menu(WidgetTester tester, String item) async {
  await openTeamSettingsFromHome(tester);
  await _settle(tester);
  await tester.tap(_key(item));
  await _settle(tester);
}

String _subtitle(WidgetTester tester) =>
    tester.widget<KitTopBar>(find.byType(KitTopBar)).subtitle!;

OrchestrationAgent _agent(String id, {bool suspended = false}) =>
    OrchestrationAgent(
      id: id,
      name: id,
      state: suspended ? AgentState.stopped : AgentState.idle,
      rawState: suspended ? 'suspended' : 'idle',
      suspended: suspended,
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => teamHostProbe = _nothing);
  tearDown(() {
    teamHostProbe = defaultTeamHostProbe;
    debugBuiltinTeam = null;
  });

  group('one page, on or off', () {
    testWidgets('off, the page says what the team does and why nothing was '
        'found; a saved address makes the same page the team\'s', (
      tester,
    ) async {
      _phone(tester);
      _mockChannels();
      final connection = await _connect(
        ServerProfile(
          id: 'workstation',
          name: 'Workstation',
          baseUrl: 'http://100.100.1.2:4096',
        ),
      );
      await tester.pumpWidget(
        _app(TeamPage(connection: connection, probe: _nothing)),
      );
      await _settle(tester);
      expect(find.byType(TeamIntroScreen), findsOneWidget);
      // "Off" is said once, as the page's state.
      expect(_subtitle(tester), _en.teamUiRowOff);
      await tester.scrollUntilVisible(_key('team-intro-miss'), 200);
      expect(find.text(_en.teamIntroNotFound('Workstation')), findsOneWidget);
      expect(find.text(_en.teamUiVerdictUnreachable), findsOneWidget);
      // Enter its address is offered whatever discovery found.
      expect(_key('team-intro-address'), findsOneWidget);

      await saveTeamHost(
        connection,
        connection.profile!,
        _fixtureConfig(url: _fixturePath()),
      );
      await _settle(tester);
      expect(find.byType(TeamIntroScreen), findsNothing);
      expect(find.byType(TeamHomeScreen), findsOneWidget);
      expect(_subtitle(tester), isNot(_en.teamUiRowOff));
      expect(_subtitle(tester), startsWith('On '));
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('Turn off asks first; then the same page is off', (
      tester,
    ) async {
      _phone(tester);
      _mockChannels();
      final connection = await _connect(
        _computer(config: _fixtureConfig(url: _fixturePath())),
      );
      await tester.pumpWidget(
        _app(TeamPage(connection: connection, probe: _nothing)),
      );
      await _settle(tester);
      expect(find.byType(TeamHomeScreen), findsOneWidget);
      // No Change address for a team the app runs itself; here there is.
      await openTeamSettingsFromHome(tester);
      await _settle(tester);
      expect(_key('team-home-change-address'), findsOneWidget);
      await tester.tap(_key('team-home-turn-off'));
      await _settle(tester);
      expect(_key('team-turn-off-sheet'), findsOneWidget);
      expect(find.text(_en.teamUiTurnOffTitle('Workstation')), findsOneWidget);
      await tester.tap(_key('team-turn-off-confirm'));
      await _settle(tester);
      expect(connection.profile!.orchestration, isNull);
      expect(connection.orchestration, isNull);
      expect(find.byType(TeamHomeScreen), findsNothing);
      expect(find.byType(TeamIntroScreen), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('Change address opens the form with the saved address', (
      tester,
    ) async {
      _phone(tester);
      _mockChannels();
      final connection = await _connect(
        _computer(config: _fixtureConfig(url: _fixturePath())),
      );
      await tester.pumpWidget(
        _app(TeamPage(connection: connection, probe: _nothing)),
      );
      await _settle(tester);
      await _menu(tester, 'team-home-change-address');
      expect(find.byType(TeamHostForm), findsOneWidget);
      expect(find.text(_fixturePath()), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('the team inside the app is stopped before it is turned '
        'off; when it cannot be, it stays on and the page says so', (
      tester,
    ) async {
      _phone(tester);
      _mockChannels();
      final builtin = debugBuiltinTeam = _BuiltinTeam()..fail = true;
      final connection = await _connect(
        ServerProfile(
          id: 'in-app',
          name: 'This phone',
          baseUrl: 'http://127.0.0.1:4097',
          orchestration: BuiltinTeam.config(),
        ),
      );
      await tester.pumpWidget(
        _app(
          TeamPage(
            connection: connection,
            probe: _nothing,
            deviceProbe: () async => const VoiceDeviceInfo.unknown(),
          ),
        ),
      );
      await _settle(tester);
      // The old in-app team stays On after the update: the person keeps it
      // first (the migration screen), then it is the team.
      await tester.tap(_key('team-migration-keep'));
      await _settle(tester);
      expect(find.byType(TeamHomeScreen), findsOneWidget);
      await openTeamSettingsFromHome(tester);
      await _settle(tester);
      // Its address is the app's own: nothing to change.
      expect(_key('team-home-change-address'), findsNothing);
      await tester.tap(_key('team-home-turn-off'));
      await _settle(tester);
      await tester.tap(_key('team-turn-off-confirm'));
      await _settle(tester);
      expect(builtin.turnOffs, 1);
      expect(connection.profile!.orchestration, isNotNull);
      expect(_key('team-home-turn-off-failed'), findsOneWidget);
      expect(find.text(_en.teamHomeTurnOffFailed), findsOneWidget);

      builtin.fail = false;
      // A failed turn-off leaves Team settings open: try again from there.
      await tester.tap(_key('team-home-turn-off'));
      await _settle(tester);
      await tester.tap(_key('team-turn-off-confirm'));
      await _settle(tester);
      expect(builtin.turnOffs, 2);
      expect(connection.profile!.orchestration, isNull);
      expect(find.byType(TeamIntroScreen), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('opened on its own over a task, a turned-off team goes back '
        'to the start, not to a page of a team that is gone', (tester) async {
      _phone(tester);
      _mockChannels();
      final connection = await _connect(
        _computer(config: _fixtureConfig(url: _fixturePath())),
      );
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => Center(
              child: TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => TeamHomeScreen(
                      controller: connection.orchestration!,
                      connection: connection,
                      now: () => _clock,
                    ),
                  ),
                ),
                child: const Text('start'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('start'));
      await _settle(tester);
      expect(find.byType(TeamHomeScreen), findsOneWidget);
      await _menu(tester, 'team-home-turn-off');
      await tester.tap(_key('team-turn-off-confirm'));
      await _settle(tester);
      expect(find.byType(TeamHomeScreen), findsNothing);
      expect(find.text('start'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('a pause for heat is not the person\'s pause (row 20)', () {
    Future<void> pump(
      WidgetTester tester,
      OrchestrationController team, {
      Map<String, ThermalTeamHold> held = const {},
    }) async {
      _phone(tester);
      final prefs = await SharedPreferences.getInstance();
      final guard = _Guard(prefs, held);
      addTearDown(guard.dispose);
      await tester.pumpWidget(
        _app(
          TeamHomeScreen(
            controller: team,
            now: () => _clock,
            thermalGuard: ValueNotifier<ThermalGuard?>(guard),
          ),
        ),
      );
      await _settle(tester);
    }

    ThermalTeamHold hold(
      OrchestrationController team, {
      bool stopped = false,
      String? city,
    }) => ThermalTeamHold(
      team: ThermalTeam(
        id: team.profileId,
        url: team.host!.url,
        city: city ?? team.host!.city!,
        builtin: true,
      ),
      since: _clock.subtract(const Duration(minutes: 3)),
      serviceStopped: stopped,
      status: ThermalStatus.severe,
    );

    final suspended = [
      _agent('mayor', suspended: true),
      _agent('polecat', suspended: true),
    ];

    testWidgets('paused by the person: "Paused", with Resume', (tester) async {
      final team = await _team(agents: suspended);
      await pump(tester, team);
      expect(
        _subtitle(tester),
        '${_en.teamUiHostPhrasePhone} · ${_en.teamUiHostPhrasePaused}',
      );
      expect(_key('team-home-heat'), findsNothing);
      expect(_key('team-home-now-paused'), findsOneWidget);
      expect(_key('team-home-now-wake'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('paused by the heat guard: "Cooling down", why and that it '
        'carries on by itself, and no Resume', (tester) async {
      // The guard suspends the sessions, so the agents look suspended too.
      final team = await _team(agents: suspended);
      await pump(tester, team, held: {team.profileId: hold(team)});
      expect(
        _subtitle(tester),
        '${_en.teamUiHostPhrasePhone} · ${_en.teamHomeHostCooling}',
      );
      final line = tester.widget<KitStatusLine>(_key('team-home-heat'));
      // Nothing here waits on the person: neutral, never amber (LOOK-4).
      expect(line.tone, AppStatusTone.neutral);
      expect(line.message, contains('so the team paused'));
      expect(line.message, contains('It carries on by itself'));
      expect(_key('team-home-now-paused'), findsNothing);
      expect(_key('team-home-now-wake'), findsNothing);
      expect(find.text(_en.teamUiControlResume), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('stopped by the heat guard: "Stopped to cool down"', (
      tester,
    ) async {
      final team = await _team(agents: suspended);
      await pump(
        tester,
        team,
        held: {team.profileId: hold(team, stopped: true)},
      );
      expect(
        _subtitle(tester),
        '${_en.teamUiHostPhrasePhone} · ${_en.teamHomeHostStoppedForHeat}',
      );
      expect(
        tester.widget<KitStatusLine>(_key('team-home-heat')).message,
        contains('Its work is kept'),
      );
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('a hold on another team changes nothing here', (tester) async {
      final team = await _team(agents: suspended);
      await pump(
        tester,
        team,
        held: {team.profileId: hold(team, city: 'another-city')},
      );
      expect(_key('team-home-heat'), findsNothing);
      expect(
        _subtitle(tester),
        '${_en.teamUiHostPhrasePhone} · ${_en.teamUiHostPhrasePaused}',
      );
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('what the team spent today', () {
    OrchestrationUsage usage({int? unpriced}) => OrchestrationUsage(
      evidence: OrchestrationUsageEvidence(
        available: true,
        recording: true,
        isEstimated: true,
        partial: false,
        today: OrchestrationUsageTotals(
          inputTokens: 10000,
          outputTokens: 2400,
          costUsdEstimate: 0.42,
          unpriced: unpriced,
        ),
      ),
    );

    Future<void> pump(WidgetTester tester, OrchestrationController team) async {
      _phone(tester);
      await tester.pumpWidget(
        _app(TeamSettingsScreen(controller: team, now: () => _clock)),
      );
      await _settle(tester);
    }

    testWidgets('the whole team\'s estimate, once, with what it covers', (
      tester,
    ) async {
      final team = await _team(usage: usage());
      await pump(tester, team);
      final figure = find.text(
        _en.teamHomeSpentToday(
          '${_en.teamUiUsageCostEstimated(r'$0.42')} · '
          '${_en.teamUiUsageTokens('12.4k')}',
        ),
      );
      expect(figure, findsOneWidget);
      expect(find.text(_en.teamHomeSpentHint), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('use with no price yet says the figure is a floor', (
      tester,
    ) async {
      final team = await _team(usage: usage(unpriced: 3));
      await pump(tester, team);
      expect(_key('team-home-spent'), findsOneWidget);
      expect(find.textContaining(_en.teamHomeSpentPartial), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('nothing reported: no row, never "\$0"', (tester) async {
      final team = await _team();
      await pump(tester, team);
      expect(_key('team-home-spent'), findsNothing);
      expect(find.textContaining(r'$0'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('how it runs', () {
    testWidgets('the speed line opens Technical details', (tester) async {
      final team = await _team();
      _phone(tester);
      await tester.pumpWidget(
        _app(TeamHomeScreen(controller: team, now: () => _clock)),
      );
      await _settle(tester);
      expect(_key('team-home-info'), findsNothing);
      // The work page no longer carries the speed line; Team settings does.
      expect(find.text(_en.teamUiDisclaimerPhone), findsNothing);
      await openTeamSettingsFromHome(tester);
      await _settle(tester);
      expect(find.text(_en.teamUiDisclaimerPhone), findsOneWidget);
      await tester.tap(_key('team-home-host-row'));
      await _settle(tester);
      expect(_key('team-home-host-sheet'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    test('not answering says what to check where the team runs', () async {
      final phone = await _team();
      expect(
        teamNotAnsweringBody(_en, phone),
        _en.teamUiStateNotAnsweringPhone,
      );
      final computer = await _team(
        profileId: 'pc',
        hostMode: OrchestrationHostMode.computer,
        url: 'http://dev-pc:7000',
      );
      expect(
        teamNotAnsweringBody(_en, computer),
        _en.teamUiStateNotAnsweringComputerNamed('dev-pc'),
      );
      final address = await _team(
        profileId: 'ip',
        hostMode: OrchestrationHostMode.computer,
        url: 'http://100.100.1.2:7000',
      );
      expect(
        teamNotAnsweringBody(_en, address),
        _en.teamUiStateNotAnsweringComputer,
      );
    });
  });
}
