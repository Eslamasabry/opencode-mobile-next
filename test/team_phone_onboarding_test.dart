// AI Team on this phone through phone setup v2 (programme P1.7), in the
// app's own Linux, over a fake setup engine and a fake in-app team.
//
// Covers: "Set up AI Team on this phone" opens Add tools with AI Team on
// and runs it as the in-app v2 job; installed already goes straight on; an
// unfinished job never opens the ready page; the ready page's stages and
// project choice; and its layout at 320 dp / 2.5x in LTR English and RTL
// Arabic.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/widgets/builtin_team_section.dart'
    show debugBuiltinTeam;
import 'package:opencode_mobile/ui/widgets/team_phone_onboarding.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_setup_engine.dart';

class _FakeChannel implements LiveEventChannel {
  _FakeChannel(this.onStatus);
  final void Function(StreamStatus status) onStatus;

  @override
  void start() => onStatus(StreamStatus.connected);

  @override
  Future<void> dispose() async {}
}

class _Gateway implements ServerGateway {
  String? _directory;
  String? _workspace;
  bool _closed = false;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(projectManagement: false);

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.0.0');

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => _FakeChannel(onStatus);

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => _FakeChannel(onStatus);

  @override
  Future<ServerPage<Session>> sessionPage({String? cursor, int limit = 100}) =>
      Future.value(const ServerPage(items: []));

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  String? get directory => _directory;

  @override
  String? get workspace => _workspace;

  @override
  bool get isClosed => _closed;

  @override
  void setLocation({String? directory, String? workspace}) {
    _directory = directory;
    _workspace = workspace;
  }

  @override
  void close() => _closed = true;

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

class _Operations implements ServerOperationsGateway {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

/// Profiles in memory: no secure-storage channel in widget tests.
class _MemoryStore extends ProfileStore {
  _MemoryStore({required super.prefs, List<ServerProfile> seeded = const []})
    : saved = List.of(seeded);

  final List<ServerProfile> saved;
  String? _activeId;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => _activeId;

  @override
  Future<void> setActiveId(String? id) async => _activeId = id;

  @override
  Future<void> upsert(ServerProfile profile) async {
    final i = saved.indexWhere((p) => p.id == profile.id);
    if (i < 0) {
      saved.add(profile);
    } else {
      saved[i] = profile;
    }
  }
}

/// The fake registry with AI Team.
final _registry = [
  ...FakeSetupEngine.fakeRegistry,
  const SetupComponent(
    id: SetupComponentIds.aiTeam,
    title: 'AI Team',
    shortTitle: 'AI Team',
    checkScript: 'true',
    installScript: 'true',
    dependsOn: ['essentials', 'opencode'],
    estimatedSeconds: 150,
    downloadBytes: 112000000,
  ),
];

/// An Add tools job that installed AI Team, ended in [state].
SetupProgress _teamJob(SetupState state) => SetupProgress(
  state: state,
  components: [
    ComponentProgress(
      id: SetupComponentIds.aiTeam,
      state: state == SetupState.done
          ? ComponentState.done
          : ComponentState.failed,
    ),
  ],
  overall: state == SetupState.done ? 1 : 0.5,
  jobId: 'job-team',
  adding: const [SetupComponentIds.aiTeam],
);

ServerProfile _inAppProfile() => ServerProfile(
  id: 'in-app',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4097',
  password: 'in-app-secret',
  flavor: ServerFlavor.v2,
);

/// The in-app team: turning on records the project and passes each stage,
/// waiting at the last one while [gate] is open.
class _BuiltinTeam extends BuiltinTeam {
  final turnedOn = <String>[];
  Completer<void>? gate;

  @override
  Future<void> turnOn(
    String path, {
    required String notice,
    void Function(BuiltinTeamStage stage)? onStage,
    Duration healthTimeout = const Duration(minutes: 6),
  }) async {
    turnedOn.add(path);
    for (final stage in BuiltinTeamStage.values) {
      onStage?.call(stage);
      if (stage == BuiltinTeamStage.values.last) await gate?.future;
    }
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late FakeSetupEngine inAppEngine;
  late _BuiltinTeam team;
  final l10n = lookupAppLocalizations(const Locale('en'));

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    // The secure-storage channel answers (a real call to it never returns
    // in a widget test); the plugin's sweep deletes the reserved grant key.
    for (final channel in [
      'oc/background',
      'oc/shortcut',
      'plugins.it_nomads.com/flutter_secure_storage',
    ]) {
      messenger.setMockMethodCallHandler(
        MethodChannel(channel),
        (call) async => call.method == 'readAll' ? <String, String>{} : null,
      );
      addTearDown(
        () => messenger.setMockMethodCallHandler(MethodChannel(channel), null),
      );
    }
    // Add tools' pre-flight asks the device at once, and nothing waits.
    messenger.setMockMethodCallHandler(
      const MethodChannel('oc/voice'),
      (call) async =>
          call.method == 'getDeviceInfo' ? <String, Object?>{} : null,
    );
    addTearDown(
      () => messenger.setMockMethodCallHandler(
        const MethodChannel('oc/voice'),
        null,
      ),
    );
    inAppEngine = FakeSetupEngine(registry: _registry);
    PhoneSetup.engine = inAppEngine;
    team = _BuiltinTeam();
    debugBuiltinTeam = team;
    addTearDown(() => debugBuiltinTeam = null);
    addTearDown(() => debugTeamPhoneRunJob = null);
  });

  /// A connection over the fake v2 gateway, connected to [profile].
  Future<(ConnectionController, _MemoryStore)> connect(
    ServerProfile profile,
  ) async {
    final store = _MemoryStore(prefs: prefs, seeded: [profile]);
    final controller = ConnectionController(
      store,
      v2GatewayFactory: (_) => (gateway: _Gateway(), operations: _Operations()),
    );
    addTearDown(controller.dispose);
    await controller.connect(profile);
    return (controller, store);
  }

  Widget app(
    Widget home, {
    required ConnectionController controller,
    required ProfileStore store,
    Locale locale = const Locale('en'),
    double textScale = 1,
    bool rtl = false,
  }) => ProviderScope(
    overrides: [
      bootstrapProvider.overrideWithValue(AppBootstrap(store)),
      connProvider.overrideWithValue(controller),
    ],
    child: MaterialApp(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: true,
        ),
        child: Directionality(
          textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
          child: child!,
        ),
      ),
      routes: {
        '/home': (_) => const Scaffold(body: Text('home')),
        '/this-phone': (_) => const Scaffold(body: Text('this phone')),
      },
      home: home,
    ),
  );

  /// Frames without waiting on timers: the fakes answer at once and the
  /// steps view's 2 s poll must not stall the test.
  Future<void> settle(WidgetTester tester, {int frames = 12}) async {
    for (var i = 0; i < frames; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  /// Scrolls the outer list until [finder] is built and on screen: the
  /// setup screen's list builds its children lazily, so a block below the
  /// fold has no element until the list reaches it.
  Future<void> reveal(WidgetTester tester, Finder finder) async {
    if (finder.evaluate().isEmpty || finder.hitTestable().evaluate().isEmpty) {
      await tester.scrollUntilVisible(
        finder,
        120,
        scrollable: find.byType(Scrollable).first,
        maxScrolls: 120,
      );
      await tester.pump();
    }
    await tester.ensureVisible(finder);
    await settle(tester, frames: 2);
    expect(finder.hitTestable(), findsOneWidget);
  }

  Future<void> tapRevealed(WidgetTester tester, Finder finder) async {
    await reveal(tester, finder);
    await tester.tap(finder);
    await settle(tester);
  }

  Future<void> teardown(
    WidgetTester tester,
    ConnectionController controller,
  ) async {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  }

  /// A page whose one button is "Set up AI Team on this phone".
  Future<(ConnectionController, _MemoryStore)> pumpDoor(
    WidgetTester tester, {
    String? directory = '/root/projects/calc',
  }) async {
    final (controller, store) = await connect(_inAppProfile());
    controller.directory = directory;
    await tester.pumpWidget(
      app(
        Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () => openTeamOnThisPhone(context, controller),
                child: const Text('set up'),
              ),
            ),
          ),
        ),
        controller: controller,
        store: store,
      ),
    );
    await settle(tester);
    return (controller, store);
  }

  group('Set up AI Team on this phone (v2)', () {
    testWidgets('installed already: straight to the ready page', (
      tester,
    ) async {
      var jobRan = false;
      debugTeamPhoneRunJob = (_, _) async => jobRan = true;
      inAppEngine.optionalInstalled = {SetupComponentIds.aiTeam};
      final (controller, _) = await pumpDoor(tester);
      await tester.tap(find.text('set up'));
      await settle(tester);
      expect(
        find.byKey(const ValueKey('phone-setup-customize-sheet')),
        findsNothing,
      );
      expect(jobRan, isFalse);
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      expect(team.turnedOn, ['/root/projects/calc']);
      await teardown(tester, controller);
    });

    testWidgets('a job that did not finish never opens the ready page', (
      tester,
    ) async {
      debugTeamPhoneRunJob = (_, _) async =>
          inAppEngine.emit(_teamJob(SetupState.failed));
      final (controller, _) = await pumpDoor(tester);
      await tester.tap(find.text('set up'));
      await settle(tester);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      expect(find.byKey(const ValueKey('team-phone-ready')), findsNothing);
      expect(team.turnedOn, isEmpty);
      expect(controller.orchestration, isNull);
      await teardown(tester, controller);
    });

    testWidgets('in-app OC2 Add tools sends its runtime to the real job path', (
      tester,
    ) async {
      // Do not replace debugTeamPhoneRunJob: exercise the same params that
      // the production entry point gives the setup engine.
      inAppEngine.afterRun = _teamJob(SetupState.running);
      final (controller, _) = await pumpDoor(tester);
      await tester.tap(find.text('set up'));
      await settle(tester);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      expect(inAppEngine.runs.single, contains(SetupComponentIds.aiTeam));
      expect(inAppEngine.runParams.single, {
        ...SetupJobParams.adding(inAppEngine.runs.single),
        'opencode': {'runtime': 'opencode2'},
      });
      await teardown(tester, controller);
    });

    testWidgets('OpenCode inside the app: the in-app engine and team', (
      tester,
    ) async {
      final jobs = <Set<String>>[];
      debugTeamPhoneRunJob = (context, ids) async {
        jobs.add(ids);
        inAppEngine.emit(_teamJob(SetupState.done));
      };
      final (controller, store) = await pumpDoor(tester);
      await tester.tap(find.text('set up'));
      await settle(tester);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      expect(jobs.single, contains(SetupComponentIds.aiTeam));
      expect(team.turnedOn, ['/root/projects/calc']);
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      expect(
        BuiltinTeam.isBuiltinConfig(store.saved.single.orchestration),
        isTrue,
      );
      await teardown(tester, controller);
    });
  });

  group('the ready page', () {
    Future<(ConnectionController, _MemoryStore)> pumpReady(
      WidgetTester tester, {
      String? directory = '/root/projects/calc',
      Future<String?> Function(BuildContext context)? chooseProject,
      double textScale = 1,
      bool rtl = false,
      Locale locale = const Locale('en'),
    }) async {
      final (controller, store) = await connect(_inAppProfile());
      controller.directory = directory;
      await tester.pumpWidget(
        app(
          TeamPhoneReadyScreen(
            connection: controller,
            team: team,
            chooseProject: chooseProject,
          ),
          controller: controller,
          store: store,
          textScale: textScale,
          rtl: rtl,
          locale: locale,
        ),
      );
      await settle(tester);
      return (controller, store);
    }

    testWidgets('shows the stages while it turns on', (tester) async {
      team.gate = Completer<void>();
      final (controller, _) = await pumpReady(tester);
      expect(find.text(l10n.teamPhoneReadyTurningOnTitle), findsOneWidget);
      expect(
        find.text(l10n.aiteamComponentStageProject('calc')),
        findsOneWidget,
      );
      team.gate!.complete();
      await settle(tester);
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      await teardown(tester, controller);
    });

    testWidgets('no project open: it asks for one first', (tester) async {
      final (controller, _) = await pumpReady(
        tester,
        directory: null,
        chooseProject: (_) async => '/root/projects/notes',
      );
      expect(team.turnedOn, isEmpty);
      expect(find.text(l10n.teamPhoneReadyChooseTitle), findsOneWidget);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-ready-choose-project')),
      );
      expect(team.turnedOn, ['/root/projects/notes']);
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      await teardown(tester, controller);
    });

    for (final rtl in [false, true]) {
      final locale = Locale(rtl ? 'ar' : 'en');
      final label = rtl ? 'RTL ar' : 'LTR en';
      testWidgets('turning on and ready fit 320 dp at 2.5x · $label', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(320, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        team.gate = Completer<void>();
        final (controller, _) = await pumpReady(
          tester,
          textScale: 2.5,
          rtl: rtl,
          locale: locale,
        );
        expect(tester.takeException(), isNull);
        team.gate!.complete();
        await settle(tester);
        expect(tester.takeException(), isNull);
        await reveal(
          tester,
          find.byKey(const ValueKey('team-phone-ready-first-task')),
        );
        await teardown(tester, controller);
      });
    }
  });
}
