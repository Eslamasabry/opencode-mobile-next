// TEAM-106: the AI Team page — the manual-add form and its verdicts (tailnet
// rule, no network), a team found on the server (the intro's Turn on) and the
// dismissal memory, and the turn-off sheet's copy and effects. The page is
// pumped directly: Settings opens it from its own AI Team row (the Plugins
// page and its row are gone).

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/team_project_demo.dart'
    show teamProjectDemoConfig;
import 'package:opencode_mobile/ui/screens/settings/plugins_screen.dart'
    show teamRowSubtitle, teamFirstProbeGrace;
import 'package:opencode_mobile/ui/screens/team/team_page.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_intro_screen.dart';
import 'package:opencode_mobile/ui/widgets/team_discovery_card.dart';
import 'package:opencode_mobile/ui/widgets/team_host_form.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'team_open_settings.dart';

const _profileId = 'workstation';

Directory _findFixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

class _MemorySecureStorage extends FlutterSecureStorage {
  _MemorySecureStorage();
  final values = <String, String>{};

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async => values[key];

  @override
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (value == null) {
      values.remove(key);
    } else {
      values[key] = value;
    }
  }

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    values.remove(key);
  }

  @override
  Future<Map<String, String>> readAll({
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async => Map.of(values);
}

/// A scripted probe: records every call and answers from [verdicts] (by
/// URL, else [fallback]); an optional [gate] delays the answer.
class _FakeProbe {
  final Map<String, ProbeVerdict> verdicts = {};
  ProbeVerdict fallback = const ProbeUnreachable(error: 'no answer');
  final calls = <(String, String?)>[];
  Completer<void>? gate;

  Future<ProbeVerdict> call(String url, {String? city}) async {
    calls.add((url, city));
    final pending = gate;
    if (pending != null) await pending.future;
    return verdicts[url] ?? fallback;
  }
}

ProbeFound _found({
  String? city = 'bright-lights',
  bool readOnly = true,
  OrchestrationHostMode hostMode = OrchestrationHostMode.computer,
}) => ProbeFound(
  host: OrchestrationHostIdentity(
    provider: 'gascity',
    url: 'http://100.100.1.2:8372',
    hostMode: hostMode,
    version: '1.4.1',
    city: city,
  ),
  version: '1.4.1',
  city: city,
  readOnly: readOnly,
);

/// The fixture gateway with the read-only capability set of a host without
/// a front (and, optionally, a stream that never goes live).
class _ReadOnlyFixture extends FixtureOrchestrationGateway {
  _ReadOnlyFixture({required super.fixturePath, this.streamDies = false});

  final bool streamDies;

  @override
  OrchestrationCapabilities get capabilities => const OrchestrationCapabilities(
    runs: true,
    workGraph: true,
    agents: true,
  );

  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => streamDies
      ? (StreamController<OrchestrationEvent>()..addError(StateError('gone')))
            .stream
      : super.events(resumeFrom: resumeFrom);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late String fixturePath;
  late SharedPreferences prefs;
  late _MemorySecureStorage secure;
  late ProfileStore profiles;
  late _FakeProbe probe;
  final l10n = lookupAppLocalizations(const Locale('en'));

  ServerProfile profile({
    OrchestrationConfig? config,
    String baseUrl = 'http://100.100.1.2:4096',
  }) => ServerProfile(
    id: _profileId,
    name: 'Workstation',
    baseUrl: baseUrl,
    orchestration: config,
  );

  OrchestrationConfig fixtureConfig({
    bool front = false,
    OrchestrationHostKind? hostKind,
  }) => OrchestrationConfig(
    provider: OrchestrationProvider.fixture,
    url: fixturePath,
    city: 'bright-lights',
    hostMode: hostKind?.mode ?? OrchestrationHostMode.computer,
    hostKind: hostKind,
    front: front,
    enabledAt: DateTime.utc(2026, 9, 10),
  );

  setUp(() async {
    fixturePath = _findFixtureRoot().path;
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    secure = _MemorySecureStorage();
    profiles = ProfileStore(prefs: prefs, secure: secure);
    probe = _FakeProbe();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    for (final channel in ['oc/background', 'oc/shortcut']) {
      messenger.setMockMethodCallHandler(
        MethodChannel(channel),
        (_) async => null,
      );
      addTearDown(
        () => messenger.setMockMethodCallHandler(MethodChannel(channel), null),
      );
    }
  });

  Future<ConnectionController> boot(ServerProfile p) async {
    await profiles.upsert(p);
    await profiles.setActiveId(p.id);
    final controller = ConnectionController(profiles);
    addTearDown(controller.dispose);
    controller.adoptConnectedProfileForTesting(p);
    controller.syncOrchestration();
    return controller;
  }

  Future<void> pump(WidgetTester tester, ConnectionController controller) =>
      tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: TeamPage(
            connection: controller,
            probe: probe.call,
            now: () => DateTime.utc(2026, 9, 11, 12),
          ),
        ),
      );

  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
  }

  Set<String> dataKeys() => {
    for (final key in prefs.getKeys())
      if (key.startsWith(OrchestrationStore.prefix(_profileId)) &&
          key != OrchestrationStore.discoveryDismissedKey(_profileId))
        key,
  };

  /// Picks [item] from the team page's top bar menu.
  Future<void> menu(WidgetTester tester, String item) async {
    await openTeamSettingsFromHome(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(ValueKey(item)));
    await tester.pumpAndSettle();
  }

  /// The team page's speed line (how it runs where it runs).
  String speed(WidgetTester tester) {
    final text = tester.widget<Text>(
      find.byKey(const ValueKey('team-home-host-speed')),
    );
    return text.data ?? text.textSpan!.toPlainText();
  }

  group('team discovery and row subtitles', () {
    testWidgets('discovery prefers a front on 8373 (TEAM-202)', (tester) async {
      probe.verdicts['http://100.100.1.2:8373'] = ProbeFound(
        host: const OrchestrationHostIdentity(
          provider: 'gascity',
          url: 'http://100.100.1.2:8373',
          hostMode: OrchestrationHostMode.computer,
          version: '1.4.1',
          city: 'bright-lights',
        ),
        version: '1.4.1',
        city: 'bright-lights',
        front: true,
        identityLogin: 'you@example.com',
        identityAllowed: true,
        capabilities: OrchestrationCapabilities.gascityFront,
      );
      probe.verdicts['http://100.100.1.2:8372'] = _found();
      final controller = await boot(profile());
      await pump(tester, controller);
      await settle(tester);
      // Found on the front: the supervisor port is never asked.
      expect(probe.calls, [('http://100.100.1.2:8373', null)]);
      await tester.tap(find.byKey(const ValueKey('team-intro-turn-on')));
      await settle(tester);
      final config = controller.profile!.orchestration;
      expect(config?.url, 'http://100.100.1.2:8373');
      expect(config?.front, isTrue);
    });

    testWidgets('a public server host is never probed over plain http', (
      tester,
    ) async {
      final controller = await boot(
        profile(baseUrl: 'https://server.example:4096'),
      );
      await pump(tester, controller);
      await settle(tester);
      expect(probe.calls, isEmpty);
    });

    test(
      'a first unreachable answer reads as Checking, not as a failure',
      () async {
        final store = OrchestrationStore(prefs, secure: secure);
        final p = profile(config: fixtureConfig());
        final c = OrchestrationController(
          profile: p,
          config: p.orchestration!,
          store: store,
          probe: (config) => probe.call(config.url),
        );
        addTearDown(c.dispose);
        await c.start();
        expect(c.phase, OrchestrationPhase.failed);
        String line(DateTime now) => teamRowSubtitle(
          l10n,
          profile: p,
          orchestration: c,
          discovery: null,
          now: now,
        );
        final failedAt = c.failedAt!;
        expect(
          line(failedAt.add(const Duration(seconds: 5))),
          l10n.teamUiRowChecking,
        );
        // Still down after the grace: now it is a failure.
        expect(
          line(failedAt.add(teamFirstProbeGrace)),
          l10n.teamUiRowNotAvailableReason(l10n.teamUiReasonUnreachable),
        );
      },
    );

    test('the simulator Try the demo attaches reads Demo, never On', () {
      final p = profile(config: teamProjectDemoConfig);
      expect(
        teamRowSubtitle(
          l10n,
          profile: p,
          orchestration: null,
          discovery: null,
          now: DateTime.utc(2026, 10, 28),
        ),
        l10n.teamProjectDemo,
      );
    });

    test('read-only and unreachable states', () async {
      final store = OrchestrationStore(prefs, secure: secure);
      final p = profile(config: fixtureConfig());
      final readOnly = OrchestrationController(
        profile: p,
        config: p.orchestration!,
        store: store,
        gatewayFactory: (_, _) => _ReadOnlyFixture(fixturePath: fixturePath),
      );
      addTearDown(readOnly.dispose);
      await readOnly.start();
      final now = DateTime.utc(2026, 9, 11, 12);
      expect(
        teamRowSubtitle(
          l10n,
          profile: p,
          orchestration: readOnly,
          discovery: null,
          now: now,
        ),
        l10n.teamUiRowOnReadOnly('Workstation'),
      );

      final dying = OrchestrationController(
        profile: p,
        config: p.orchestration!,
        store: store,
        now: () => now.subtract(const Duration(minutes: 3)),
        gatewayFactory: (_, _) =>
            _ReadOnlyFixture(fixturePath: fixturePath, streamDies: true),
      );
      addTearDown(dying.dispose);
      await dying.start();
      await Future<void>.delayed(const Duration(milliseconds: 20));
      expect(dying.streamStatus, OrchestrationStreamStatus.reconnecting);
      expect(
        teamRowSubtitle(
          l10n,
          profile: p,
          orchestration: dying,
          discovery: null,
          now: now,
        ),
        l10n.teamUiRowUnreachable('3'),
      );
      expect(
        teamRowSubtitle(
          l10n,
          profile: p,
          orchestration: dying,
          discovery: null,
          now: now.subtract(const Duration(minutes: 3)),
        ),
        l10n.teamUiRowReconnecting,
      );
    });
  });

  group('manual add', () {
    // The row opens the team page, off: its address action is the form.
    Future<void> openForm(WidgetTester tester) async {
      expect(find.byType(TeamIntroScreen), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('team-intro-address')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('team-host-form')), findsOneWidget);
    }

    Future<void> submit(WidgetTester tester, String url) async {
      await tester.enterText(find.byKey(const ValueKey('team-host-url')), url);
      await tester.tap(find.byKey(const ValueKey('team-host-submit')));
      await tester.pumpAndSettle();
    }

    String verdict(WidgetTester tester) => tester
        .widget<Text>(
          find
              .descendant(
                of: find.byKey(const ValueKey('team-host-verdict')),
                matching: find.byType(Text),
              )
              .first,
        )
        .data!;

    testWidgets('refuses a public http:// address without probing', (
      tester,
    ) async {
      final controller = await boot(profile());
      await pump(tester, controller);
      await settle(tester);
      await openForm(tester);
      // What the page's discovery asked is not the form's.
      probe.calls.clear();
      await submit(tester, 'http://public.example:8372');
      expect(verdict(tester), l10n.teamUiTailnetRequired);
      expect(probe.calls, isEmpty);
      expect(controller.profile!.orchestration, isNull);
    });

    testWidgets('probes a tailnet address and turns the plugin on', (
      tester,
    ) async {
      probe.verdicts['http://100.100.1.2:8372'] = _found();
      final controller = await boot(
        profile(baseUrl: 'https://server.example:4096'),
      );
      await pump(tester, controller);
      await settle(tester);
      await openForm(tester);
      await tester.enterText(
        find.byKey(const ValueKey('team-host-city')),
        'bright-lights',
      );
      await submit(tester, 'http://100.100.1.2:8372');
      expect(probe.calls, [('http://100.100.1.2:8372', 'bright-lights')]);
      final config = controller.profile!.orchestration;
      expect(config, isNotNull);
      expect(config!.provider, OrchestrationProvider.gascity);
      expect(config.url, 'http://100.100.1.2:8372');
      expect(config.city, 'bright-lights');
      expect(config.front, isFalse);
      expect(config.enabledAt, isNotNull);
      // Persisted, and the sibling controller follows the config.
      final stored = ProfileStore(prefs: prefs, secure: secure);
      await stored.load();
      expect(stored.profiles.single.orchestration, config);
      expect(controller.orchestration?.config, config);
      expect(find.byKey(const ValueKey('team-host-form')), findsNothing);
      // The same page is now the team's.
      expect(find.byType(TeamIntroScreen), findsNothing);
      expect(find.byType(TeamHomeScreen), findsOneWidget);
    });

    testWidgets('probes a loopback address', (tester) async {
      probe.verdicts['http://127.0.0.1:8372'] = _found(readOnly: false);
      final controller = await boot(
        profile(baseUrl: 'https://server.example:4096'),
      );
      await pump(tester, controller);
      await settle(tester);
      await openForm(tester);
      await submit(tester, 'http://127.0.0.1:8372');
      expect(probe.calls, [('http://127.0.0.1:8372', null)]);
      final config = controller.profile!.orchestration!;
      expect(config.url, 'http://127.0.0.1:8372');
      expect(config.city, 'bright-lights');
      expect(config.front, isTrue);
    });

    testWidgets('shows the verdict copy for every failure', (tester) async {
      final controller = await boot(
        profile(baseUrl: 'https://server.example:4096'),
      );
      await pump(tester, controller);
      await settle(tester);
      await openForm(tester);

      probe.fallback = const ProbeNotGasCity(statusCode: 200, detail: 'html');
      await submit(tester, 'http://100.100.1.3:8372');
      expect(verdict(tester), l10n.teamUiVerdictNotGasCity);
      expect(
        find.byKey(const ValueKey('team-host-verdict-how')),
        findsOneWidget,
      );

      probe.fallback = const ProbeCityNotRunning(city: 'bright-lights');
      await submit(tester, 'http://100.100.1.3:8372');
      expect(verdict(tester), l10n.teamUiVerdictCityNotRunning);

      probe.fallback = const ProbeUnreachable(error: 'refused');
      await submit(tester, 'http://100.100.1.3:8372');
      expect(verdict(tester), l10n.teamUiVerdictUnreachable);
      expect(controller.profile!.orchestration, isNull);
    });

    testWidgets('the verdict copy never mentions HTTPS or tailscale serve', (
      tester,
    ) async {
      for (final text in [
        l10n.teamUiTailnetRequired,
        l10n.teamUiVerdictNotGasCity,
        l10n.teamUiVerdictCityNotRunning,
        l10n.teamUiVerdictUnreachable,
        l10n.teamUiEditorBody,
        l10n.teamUiHostGuideStep1,
        l10n.teamUiHostGuideStep2,
        l10n.teamUiHostGuideStep3,
        l10n.teamUiHostGuideStep4,
        l10n.teamUiHostGuideIntro,
      ]) {
        expect(text.toLowerCase(), isNot(contains('https')));
        expect(text.toLowerCase(), isNot(contains('tailscale serve')));
      }
    });

    // TEAM-206: the kind of computer, kept for the disclaimer only. Since
    // 19640c8b (shared-team-1 map fix) the form no longer asks for it: a
    // new host is a Desktop computer, a saved kind is carried through
    // Change address, and a host that reports a phone stays a phone.
    group('host kind', () {
      void expectNoKindQuestion() {
        expect(find.text(l10n.teamUiHostKindLabel), findsNothing);
        expect(find.text(l10n.teamUiHostKindHint), findsNothing);
        for (final kind in OrchestrationHostKind.values) {
          expect(
            find.byKey(ValueKey('team-host-kind-${kind.name}')),
            findsNothing,
          );
        }
      }

      testWidgets('the form asks no kind; a new host is a Desktop computer', (
        tester,
      ) async {
        probe.verdicts['http://100.100.1.2:8372'] = _found();
        final controller = await boot(
          profile(baseUrl: 'https://server.example:4096'),
        );
        await pump(tester, controller);
        await settle(tester);
        await openForm(tester);
        expectNoKindQuestion();
        await submit(tester, 'http://100.100.1.2:8372');
        final config = controller.profile!.orchestration!;
        expect(config.hostKind, OrchestrationHostKind.pc);
        expect(config.hostMode, OrchestrationHostMode.computer);
      });

      for (final kind in teamHostKindChoices) {
        testWidgets('a saved ${kind.name} persists through Change address', (
          tester,
        ) async {
          probe.verdicts['http://100.100.1.3:8372'] = _found();
          final controller = await boot(
            profile(
              config: OrchestrationConfig(
                provider: OrchestrationProvider.gascity,
                url: 'http://100.100.1.2:8372',
                city: 'bright-lights',
                hostKind: kind,
              ),
            ),
          );
          await pump(tester, controller);
          await settle(tester);
          // The team page's menu: Change address.
          await menu(tester, 'team-home-change-address');
          expect(find.byKey(const ValueKey('team-host-form')), findsOneWidget);
          expectNoKindQuestion();
          await submit(tester, 'http://100.100.1.3:8372');
          final config = controller.profile!.orchestration!;
          expect(config.url, 'http://100.100.1.3:8372');
          expect(config.hostKind, kind);
          expect(config.hostMode, OrchestrationHostMode.computer);
          final stored = ProfileStore(prefs: prefs, secure: secure);
          await stored.load();
          expect(stored.profiles.single.orchestration!.hostKind, kind);
          // The page is the team's now, at the saved address.
          expect(find.byType(TeamHomeScreen), findsOneWidget);
        });
      }

      testWidgets('a host that reports a phone keeps the phone kind', (
        tester,
      ) async {
        probe.verdicts['http://100.100.1.2:8372'] = _found(
          hostMode: OrchestrationHostMode.phone,
        );
        final controller = await boot(
          profile(baseUrl: 'https://server.example:4096'),
        );
        await pump(tester, controller);
        await settle(tester);
        await openForm(tester);
        await submit(tester, 'http://100.100.1.2:8372');
        final config = controller.profile!.orchestration!;
        expect(config.hostMode, OrchestrationHostMode.phone);
        expect(config.hostKind, OrchestrationHostKind.phone);
      });
    });
  });

  group('how it runs, per host kind, on the team page', () {
    for (final kind in OrchestrationHostKind.values) {
      testWidgets('${kind.name} shows its line', (tester) async {
        final controller = await boot(
          profile(config: fixtureConfig(hostKind: kind)),
        );
        await pump(tester, controller);
        await settle(tester);
        // The fixture reports the config's own mode, so the chosen kind
        // stands (a phone config is a phone gateway).
        expect(controller.orchestration?.phase, OrchestrationPhase.ready);
        await openTeamSettingsFromHome(tester);
        await tester.pumpAndSettle();
        expect(speed(tester), teamHostDisclaimer(l10n, kind));
        for (final other in OrchestrationHostKind.values) {
          if (other == kind) continue;
          expect(find.text(teamHostDisclaimer(l10n, other)), findsNothing);
        }
      });
    }

    testWidgets('a found-but-off host: the page offers Turn on, and turning '
        'on makes it the team\'s page', (tester) async {
      probe.verdicts['http://100.100.1.2:8372'] = _found();
      final controller = await boot(profile());
      await pump(tester, controller);
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('team-intro-turn-on')));
      await settle(tester);
      expect(controller.profile!.orchestration?.url, 'http://100.100.1.2:8372');
      expect(find.byType(TeamHomeScreen), findsOneWidget);
    });
  });

  group('turn off', () {
    // A phone window, as the team page is used most.
    void phone(WidgetTester tester) {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
    }

    testWidgets('sheet copy and effects', (tester) async {
      phone(tester);
      final p = profile(config: fixtureConfig());
      final controller = await boot(p);
      await pump(tester, controller);
      await settle(tester);
      final sibling = controller.orchestration!;
      expect(sibling.phase, OrchestrationPhase.ready);
      expect(dataKeys(), isNotEmpty);

      expect(find.byType(TeamHomeScreen), findsOneWidget);
      await menu(tester, 'team-home-turn-off');
      expect(find.byKey(const ValueKey('team-turn-off-sheet')), findsOneWidget);
      expect(find.text(l10n.teamUiTurnOffTitle('Workstation')), findsOneWidget);
      expect(find.text(l10n.teamUiTurnOffBody), findsOneWidget);
      expect(find.text(l10n.teamUiKeep), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('team-turn-off-confirm')));
      await settle(tester);
      expect(p.orchestration, isNull);
      expect(controller.orchestration, isNull);
      expect(sibling.phase, OrchestrationPhase.stopped);
      expect(dataKeys(), isEmpty);
      final stored = ProfileStore(prefs: prefs, secure: secure);
      await stored.load();
      expect(stored.profiles.single.orchestration, isNull);
      // §1.3: the offer is not re-shown.
      expect(
        OrchestrationStore(
          prefs,
          secure: secure,
        ).isDiscoveryDismissed(_profileId),
        isTrue,
      );
      // The same page, off: it says what the team does and sets it up.
      expect(find.byType(TeamHomeScreen), findsNothing);
      expect(find.byType(TeamIntroScreen), findsOneWidget);
    });

    testWidgets('keep leaves everything in place', (tester) async {
      phone(tester);
      final p = profile(config: fixtureConfig());
      final controller = await boot(p);
      await pump(tester, controller);
      await settle(tester);
      await menu(tester, 'team-home-turn-off');
      await tester.tap(find.text(l10n.teamUiKeep));
      await tester.pumpAndSettle();
      expect(p.orchestration, isNotNull);
      expect(controller.orchestration?.phase, OrchestrationPhase.ready);
      // Still on Team settings, which sits over the team's page.
      expect(find.byKey(const ValueKey('team-settings')), findsOneWidget);
    });
  });

  group('discovery helpers', () {
    test('teamDiscoveryUrlFor follows the tailnet rule', () {
      expect(
        teamDiscoveryUrlFor('http://100.100.1.2:4096'),
        'http://100.100.1.2:8372',
      );
      expect(
        teamDiscoveryUrlFor('https://pc.tail1234.ts.net'),
        'http://pc.tail1234.ts.net:8372',
      );
      expect(
        teamDiscoveryUrlFor('http://127.0.0.1:4096'),
        'http://127.0.0.1:8372',
      );
      expect(teamDiscoveryUrlFor('https://server.example:4096'), isNull);
      expect(teamDiscoveryUrlFor('not a url'), isNull);
    });

    test('dismissal expires after 30 days', () async {
      final store = OrchestrationStore(prefs, secure: secure);
      final now = DateTime.utc(2026, 9, 11);
      expect(store.isDiscoveryDismissed(_profileId, now: now), isFalse);
      await store.dismissDiscovery(_profileId, now: now);
      expect(
        store.isDiscoveryDismissed(
          _profileId,
          now: now.add(const Duration(days: 29)),
        ),
        isTrue,
      );
      expect(
        store.isDiscoveryDismissed(
          _profileId,
          now: now.add(const Duration(days: 31)),
        ),
        isFalse,
      );
    });

    test('TeamDiscovery exposes the found host to siblings', () async {
      probe.verdicts['http://100.100.1.2:8372'] = _found();
      final controller = await boot(profile());
      final discovery = TeamDiscovery(controller, probe: probe.call);
      addTearDown(discovery.dispose);
      await discovery.ensureProbed();
      expect(discovery.result?.found.version, '1.4.1');
      expect(discovery.result?.url, 'http://100.100.1.2:8372');
      await discovery.ensureProbed();
      // Front port first (nothing there), then the supervisor; a re-check
      // is free.
      expect(probe.calls, hasLength(2));
    });
  });
}
