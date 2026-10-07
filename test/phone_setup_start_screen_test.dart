import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_customize_sheet.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_routes.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_selection.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_termux_job_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/voice/device.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_setup_engine.dart';

/// Saved profiles without the real secure-storage channel.
class _SeededStore extends ProfileStore {
  _SeededStore({required super.prefs, this.seeded = const []});
  final List<ServerProfile> seeded;
  @override
  List<ServerProfile> get profiles => List.unmodifiable(seeded);
  @override
  String? get activeId => null;
}

Future<ConnectionController> _controller([
  List<ServerProfile> profiles = const [],
]) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(_SeededStore(prefs: prefs, seeded: profiles));
}

/// Shows where a pushed route landed and with what argument.
class _RouteProbe extends StatelessWidget {
  const _RouteProbe(this.name);
  final String name;

  @override
  Widget build(BuildContext context) {
    final args = ModalRoute.of(context)!.settings.arguments;
    final described = args is ServersRouteRequest
        ? '${args.kind.name}:${args.profileID}'
        : '$args';
    return Scaffold(appBar: AppBar(), body: Text('$name $described'));
  }
}

Widget _app(
  ConnectionController controller, {
  Widget? home,
  double scale = 1,
  Locale? locale,
  bool reduceMotion = false,
}) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
    connProvider.overrideWithValue(controller),
  ],
  child: MaterialApp(
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: TextScaler.linear(scale),
        disableAnimations: reduceMotion,
      ),
      child: child!,
    ),
    routes: {
      '/servers': (_) => const _RouteProbe('servers'),
      '/home': (_) => const _RouteProbe('home'),
    },
    home: home,
  ),
);

/// Screen A with Termux and the in-app install stood in for.
class _Harness {
  _Harness({
    TermuxRunningServer termux = const TermuxRunningServer.absent(),
    bool inApp = false,
    List<SetupComponent>? registry,
    VoiceDeviceInfo? device,
  }) : engine = FakeSetupEngine(registry: registry) {
    PhoneSetup.engine = engine;
    // "Use Termux instead" opens the Termux host's job: a fake here too.
    PhoneSetup.termux = FakeSetupEngine(registry: registry);
    screen = PhoneSetupStartScreen(
      termuxProbe: () async => termux,
      inAppProbe: () async => inApp,
      deviceProbe: () async => device ?? _okDevice,
      openProgress: (_) async => progressOpened++,
    );
  }

  final FakeSetupEngine engine;
  late final PhoneSetupStartScreen screen;
  var progressOpened = 0;
}

const _defaultIds = {'linux', 'essentials', 'python', 'node', 'opencode'};

/// A device that clears every P0.8 pre-flight check: a supported ABI, well
/// over the RAM floor, and well over the space the default registry needs
/// (165 MB download → 330 MB required with the 2x margin).
const _okDevice = VoiceDeviceInfo(
  availableStorageBytes: 2000000000,
  memoryClassMb: 256,
  totalMemoryMb: 4096,
  supportedAbis: ['arm64-v8a', 'armeabi-v7a'],
  hasMicrophone: false,
);

List<ComponentProgress> _job(ComponentState node) => [
  const ComponentProgress(id: 'linux', state: ComponentState.done),
  const ComponentProgress(id: 'essentials', state: ComponentState.done),
  ComponentProgress(id: 'node', state: node, stage: 'Downloading Node.js'),
  const ComponentProgress(id: 'opencode', state: ComponentState.pending),
];

Iterable<String> _visibleTexts(WidgetTester tester) => [
  for (final text in tester.widgetList<Text>(find.byType(Text)))
    text.data ?? text.textSpan?.toPlainText() ?? '',
];

Future<void> _expandOtherWays(WidgetTester tester) async {
  // A toggle like a state's Details (design standard §3), not a ListTile.
  final otherWays = find.byKey(const ValueKey('phone-setup-start-other-ways'));
  await tester.ensureVisible(otherWays);
  await tester.pumpAndSettle();
  await tester.tap(otherWays);
  await tester.pumpAndSettle();
}

/// Rows under Other ways sit below the fold on a small test surface.
Future<void> _tapVisible(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  setUp(() {
    // Every _Harness pumps its own deviceProbe, but a few tests build
    // PhoneSetupStartScreen straight from its route (no override point): the
    // unmocked oc/voice channel must answer at once, or P0.8's pre-flight
    // probe leaves a pending timer past the test's teardown.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('oc/voice'), (
          call,
        ) async {
          if (call.method == 'getDeviceInfo') return <String, Object?>{};
          return null;
        });
  });

  testWidgets(
    'a fresh phone gets one promise with real totals and one button',
    (tester) async {
      final harness = _Harness();
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, home: harness.screen));
      await tester.pumpAndSettle();

      expect(harness.engine.restores, 1);
      expect(find.text('Run a coding agent right here'), findsOneWidget);
      // 20+40+50+40+90 s and 30+15+30+30+60 MB from the registry.
      expect(
        find.text(
          'No computer and no other apps. ~165 MB to download the first time.',
        ),
        findsOneWidget,
      );
      // FB2: the whole journey in one line, with the registry's time said
      // once, under the promise.
      expect(
        find.text('Install, name a project, chat · about 4 min'),
        findsOneWidget,
      );
      expect(
        tester
            .getTopLeft(find.byKey(const ValueKey('phone-setup-start-body')))
            .dy,
        lessThan(
          tester
              .getTopLeft(find.byKey(const ValueKey('phone-setup-start-steps')))
              .dy,
        ),
      );
      expect(
        find.text('Includes Git and SSH, Python and Node.js.'),
        findsOneWidget,
      );
      expect(find.byType(FilledButton), findsOneWidget);
      expect(
        find.widgetWithText(FilledButton, 'Set up OpenCode on this phone'),
        findsOneWidget,
      );
      // Other ways starts folded.
      expect(find.text('Use Termux instead'), findsNothing);

      await _expandOtherWays(tester);
      expect(find.text('Use Termux instead'), findsOneWidget);
      expect(find.text('Connect to a computer by address'), findsOneWidget);
      // The words the design bans from this screen, everywhere on it.
      for (final text in _visibleTexts(tester)) {
        for (final banned in ['ubuntu', 'server', 'proot', 'experimental']) {
          expect(text.toLowerCase(), isNot(contains(banned)), reason: text);
        }
      }
    },
  );

  testWidgets('Set up runs the default selection, then opens the progress', (
    tester,
  ) async {
    final harness = _Harness();
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, home: harness.screen));
    await tester.pumpAndSettle();

    // The 800x600 test surface is shorter than the page.
    await _tapVisible(
      tester,
      find.byKey(const ValueKey('phone-setup-start-primary')),
    );

    expect(harness.engine.runs, [_defaultIds]);
    expect(harness.progressOpened, 1);
  });

  testWidgets('a running setup shows its real percent and Continue opens it', (
    tester,
  ) async {
    final harness = _Harness();
    harness.engine.emit(
      SetupProgress(
        state: SetupState.running,
        components: _job(ComponentState.running),
        overall: .426,
      ),
    );
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, home: harness.screen));
    await tester.pumpAndSettle();

    expect(find.text('Setup is 42% done'), findsOneWidget);
    expect(
      find.text('It keeps going while you use other apps.'),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('phone-setup-start-meter')), findsOne);
    expect(
      find.text('Includes Git and SSH, Python and Node.js.'),
      findsNothing,
    );

    await tester.tap(find.widgetWithText(FilledButton, 'Continue setup'));
    await tester.pumpAndSettle();
    // Already running: nothing to restart, only the progress to show.
    expect(harness.engine.runs, isEmpty);
    expect(harness.progressOpened, 1);
  });

  for (final state in [
    SetupState.interrupted,
    SetupState.failed,
    SetupState.cancelled,
  ]) {
    testWidgets('a ${state.name} setup continues the same job', (tester) async {
      final harness = _Harness();
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, home: harness.screen));
      // Restored from disk after the screen opened, as after an app restart.
      harness.engine.emit(
        SetupProgress(
          state: state,
          components: _job(ComponentState.failed),
          overall: .5,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Setup is 50% done'), findsOneWidget);
      expect(
        find.text(
          'It stopped before finishing. Continuing picks up where it left '
          'off.',
        ),
        findsOneWidget,
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Continue setup'));
      await tester.pumpAndSettle();
      expect(harness.engine.runs, [
        {'linux', 'essentials', 'node', 'opencode'},
      ]);
      expect(harness.progressOpened, 1);
    });
  }

  testWidgets('a finished setup says ready and Open finishes the job', (
    tester,
  ) async {
    final harness = _Harness();
    harness.engine.emit(
      SetupProgress(
        state: SetupState.done,
        components: _job(ComponentState.done),
        overall: 1,
      ),
    );
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, home: harness.screen));
    await tester.pumpAndSettle();

    expect(find.text('OpenCode is ready on this phone'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Open'), findsOneWidget);
    // No saved in-app connection: the engine's last step (start and
    // connect) is what is missing, and running the job again does just it.
    await tester.tap(find.widgetWithText(FilledButton, 'Open'));
    await tester.pumpAndSettle();
    expect(harness.engine.runs, [
      {'linux', 'essentials', 'node', 'opencode'},
    ]);
    expect(harness.progressOpened, 1);
  });

  testWidgets('an in-app OpenCode from before setup v2 also reads as ready', (
    tester,
  ) async {
    final harness = _Harness(inApp: true);
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, home: harness.screen));
    await tester.pumpAndSettle();

    expect(find.text('OpenCode is ready on this phone'), findsOneWidget);
  });

  group('Termux already runs OpenCode', () {
    final running = TermuxRunningServer.running(
      runtime: TermuxRuntime.openCode1,
      version: '1.18.29',
      observedAt: DateTime(2026, 9, 24),
    );

    testWidgets('it leads with Connect and keeps the in-app setup below', (
      tester,
    ) async {
      final harness = _Harness(termux: running);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, home: harness.screen));
      await tester.pumpAndSettle();

      expect(find.text('OpenCode is already set up in Termux'), findsOneWidget);
      expect(find.byType(FilledButton), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Connect'), findsOneWidget);

      await _expandOtherWays(tester);
      expect(find.text('Use Termux instead'), findsNothing);
      await _tapVisible(
        tester,
        find.byKey(const ValueKey('phone-setup-start-set-up-here')),
      );
      expect(harness.engine.runs, [_defaultIds]);
      expect(harness.progressOpened, 1);
    });

    testWidgets('Connect hands a saved Termux profile to the connect flow', (
      tester,
    ) async {
      final harness = _Harness(termux: running);
      final controller = await _controller([
        ServerProfile(
          id: 'termux',
          name: 'This phone',
          baseUrl: TermuxBridge.managedServerUrl,
        ),
      ]);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, home: harness.screen));
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(FilledButton, 'Connect'));
      await tester.pumpAndSettle();
      expect(find.text('servers connect:termux'), findsOneWidget);
    });

    testWidgets('Connect with no saved profile restores the phone password', (
      tester,
    ) async {
      final harness = _Harness(termux: running);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, home: harness.screen));
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(FilledButton, 'Connect'));
      await tester.pumpAndSettle();
      expect(find.text('servers enterPhoneCredentials:null'), findsOneWidget);
    });
  });

  testWidgets('Other ways open Termux and the address form', (tester) async {
    final harness = _Harness();
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, home: harness.screen));
    await tester.pumpAndSettle();
    await _expandOtherWays(tester);

    final termux = find.byKey(const ValueKey('phone-setup-start-use-termux'));
    await tester.ensureVisible(termux);
    await tester.pumpAndSettle();
    await tester.tap(termux);
    // Termux's own checks keep a working mark moving: frames, not settle.
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    // Termux is a host of phone setup (P1.3): its checklist, not a wizard.
    expect(find.byType(PhoneSetupTermuxJobScreen), findsOneWidget);
    await tester.pageBack();
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    await _tapVisible(
      tester,
      find.byKey(const ValueKey('phone-setup-start-by-address')),
    );
    expect(find.text('servers add:null'), findsOneWidget);
  });

  testWidgets('Customize totals follow the switches and shape Set up', (
    tester,
  ) async {
    final harness = _Harness();
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, home: harness.screen));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('phone-setup-start-customize')));
    await tester.pumpAndSettle();
    final totals = find.byKey(const ValueKey('phone-setup-customize-totals'));
    expect(_words(tester, totals), 'About 4 minutes · ~165 MB');

    // Required: shown on and locked, said once by the lock (nothing
    // repeats "needed" under every row), before the optional tools.
    final node = tester.widget<KitSwitchRow>(
      find.byKey(const ValueKey('phone-setup-customize-node')),
    );
    expect(node.value, isTrue);
    expect(node.onChanged, isNull);
    expect(node.supporting, isNull);
    expect(find.text('Required'), findsNWidgets(4));
    expect(find.text('Needed for the agent to run'), findsNothing);
    expect(
      tester
          .getTopLeft(find.byKey(const ValueKey('phone-setup-customize-node')))
          .dy,
      lessThan(
        tester
            .getTopLeft(
              find.byKey(const ValueKey('phone-setup-customize-python')),
            )
            .dy,
      ),
    );

    await tester.tap(
      find.byKey(const ValueKey('phone-setup-customize-python')),
    );
    await tester.pumpAndSettle();
    expect(_words(tester, totals), 'About 3 minutes · ~135 MB');

    await tester.tap(find.byKey(const ValueKey('phone-setup-customize-done')));
    await tester.pumpAndSettle();
    expect(find.text('Includes Git and SSH and Node.js.'), findsOneWidget);
    expect(
      find.text(
        'No computer and no other apps. ~135 MB to download the first time.',
      ),
      findsOneWidget,
    );
    // The step line's time follows the selection, like the size.
    expect(
      find.text('Install, name a project, chat · about 3 min'),
      findsOneWidget,
    );

    // The 800x600 test surface is shorter than the page.
    await _tapVisible(
      tester,
      find.byKey(const ValueKey('phone-setup-start-primary')),
    );
    expect(harness.engine.runs, [
      {'linux', 'essentials', 'node', 'opencode'},
    ]);
  });

  group('P0.8 pre-flight', () {
    testWidgets('a 32-bit-only phone is told why before anything downloads', (
      tester,
    ) async {
      final harness = _Harness(
        device: const VoiceDeviceInfo(
          availableStorageBytes: 2000000000,
          memoryClassMb: 256,
          totalMemoryMb: 4096,
          supportedAbis: ['armeabi-v7a'],
          hasMicrophone: false,
        ),
      );
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, home: harness.screen));
      await tester.pumpAndSettle();

      expect(find.text("This phone can't run it"), findsOneWidget);
      expect(
        find.text(
          "This app's Ubuntu only runs on a 64-bit Arm or Intel phone; "
          'this one reports armeabi-v7a.',
        ),
        findsOneWidget,
      );
      final primary = tester.widget<FilledButton>(
        find.descendant(
          of: find.byKey(const ValueKey('phone-setup-start-primary')),
          matching: find.byType(FilledButton),
        ),
      );
      expect(primary.onPressed, isNull);
      // A journey this phone cannot take is not promised (FB2).
      expect(
        find.byKey(const ValueKey('phone-setup-start-steps')),
        findsNothing,
      );
      // Nothing downloaded: Set up never ran.
      expect(harness.engine.runs, isEmpty);
    });

    testWidgets(
      'low space names how much to free and offers Storage settings',
      (tester) async {
        final harness = _Harness(
          device: const VoiceDeviceInfo(
            availableStorageBytes: 100000000,
            memoryClassMb: 256,
            totalMemoryMb: 4096,
            supportedAbis: ['arm64-v8a'],
            hasMicrophone: false,
          ),
        );
        final controller = await _controller();
        addTearDown(controller.dispose);
        await tester.pumpWidget(_app(controller, home: harness.screen));
        await tester.pumpAndSettle();

        expect(find.text('Not enough free space'), findsOneWidget);
        // 165 MB download → 330 MB required (2x margin) − 100 MB free.
        expect(
          find.text(
            'Free about 230 MB on this phone, then come back to set this '
            'up.',
          ),
          findsOneWidget,
        );
        final primary = tester.widget<FilledButton>(
          find.descendant(
            of: find.byKey(const ValueKey('phone-setup-start-primary')),
            matching: find.byType(FilledButton),
          ),
        );
        expect(primary.onPressed, isNull);
        expect(
          find.byKey(const ValueKey('phone-setup-start-open-storage')),
          findsOneWidget,
        );
      },
    );

    Future<_Harness> pumpWithMemory(WidgetTester tester, int memoryMb) async {
      final harness = _Harness(
        device: VoiceDeviceInfo(
          availableStorageBytes: 2000000000,
          memoryClassMb: 256,
          totalMemoryMb: memoryMb,
          supportedAbis: const ['arm64-v8a'],
          hasMicrophone: false,
        ),
      );
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, home: harness.screen));
      await tester.pumpAndSettle();
      return harness;
    }

    FilledButton primaryButton(WidgetTester tester) =>
        tester.widget<FilledButton>(
          find.descendant(
            of: find.byKey(const ValueKey('phone-setup-start-primary')),
            matching: find.byType(FilledButton),
          ),
        );

    // B2: under 1,800 MB of total RAM setup is refused, with the way
    // forward (a computer), before anything downloads.
    testWidgets('a 1,700 MB phone is told why and where to go instead', (
      tester,
    ) async {
      final harness = await pumpWithMemory(tester, 1700);

      expect(
        find.text("This phone doesn't have enough memory"),
        findsOneWidget,
      );
      expect(
        find.text(
          'OpenCode needs a phone with at least 1,800 MB of memory; this one '
          'has 1,700 MB. Run it on a computer instead and connect this phone '
          'to it.',
        ),
        findsOneWidget,
      );
      expect(primaryButton(tester).onPressed, isNull);
      expect(
        find.byKey(const ValueKey('phone-setup-start-may-be-slow')),
        findsNothing,
      );
      expect(harness.engine.runs, isEmpty);
    });

    // B2: a nominal 2 GB phone reports 1,972 MB; it may set up, told once
    // and plainly that it may be slow, naming the amount.
    for (final memory in [1972, 2900]) {
      testWidgets('a $memory MB phone may set up, with a plain slow note', (
        tester,
      ) async {
        final harness = await pumpWithMemory(tester, memory);
        final amount = memory == 1972 ? '1,972' : '2,900';

        expect(find.text('Run a coding agent right here'), findsOneWidget);
        expect(
          find.text("This phone doesn't have enough memory"),
          findsNothing,
        );
        expect(
          find.text(
            'It may be slow on this phone, which has $amount MB of memory.',
          ),
          findsOneWidget,
        );
        expect(primaryButton(tester).onPressed, isNotNull);
        final primary = find.byKey(const ValueKey('phone-setup-start-primary'));
        await tester.ensureVisible(primary);
        await tester.pumpAndSettle();
        await tester.tap(primary);
        await tester.pumpAndSettle();
        expect(harness.engine.runs, hasLength(1));
      });
    }

    testWidgets('a 4,096 MB phone has no memory note', (tester) async {
      await pumpWithMemory(tester, 4096);
      expect(find.text('Run a coding agent right here'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('phone-setup-start-may-be-slow')),
        findsNothing,
      );
      expect(primaryButton(tester).onPressed, isNotNull);
    });

    testWidgets('a supported phone with room keeps the plain promise', (
      tester,
    ) async {
      final harness = _Harness();
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller, home: harness.screen));
      await tester.pumpAndSettle();

      expect(find.text('Run a coding agent right here'), findsOneWidget);
      final primary = tester.widget<FilledButton>(
        find.descendant(
          of: find.byKey(const ValueKey('phone-setup-start-primary')),
          matching: find.byType(FilledButton),
        ),
      );
      expect(primary.onPressed, isNotNull);
    });
  });

  group('showPhoneSetupCustomize', () {
    Widget launcher({
      bool addMode = false,
      required ValueSetter<Set<String>?> onResult,
    }) => Builder(
      builder: (context) => Scaffold(
        body: Center(
          child: TextButton(
            onPressed: () async => onResult(
              await showPhoneSetupCustomize(context, addMode: addMode),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );

    testWidgets('returns the chosen ids for first setup', (tester) async {
      _Harness();
      final controller = await _controller();
      addTearDown(controller.dispose);
      Set<String>? result;
      await tester.pumpWidget(
        _app(controller, home: launcher(onResult: (ids) => result = ids)),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('phone-setup-customize-python')),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      await tester.pumpAndSettle();
      expect(result, {'linux', 'essentials', 'node', 'opencode'});
    });

    testWidgets(
      'add mode offers only optional tools and returns the new ones',
      (tester) async {
        final harness = _Harness(
          registry: [
            ...FakeSetupEngine.fakeRegistry,
            const SetupComponent(
              id: 'lfs',
              title: 'Git LFS',
              shortTitle: 'Git LFS',
              checkScript: 'true',
              installScript: 'true',
              dependsOn: ['essentials'],
              estimatedSeconds: 30,
              downloadBytes: 10000000,
            ),
            const SetupComponent(
              id: 'aiteam',
              title: 'AI Team',
              shortTitle: 'AI Team',
              checkScript: 'true',
              installScript: 'true',
              dependsOn: ['lfs', 'opencode'],
              estimatedSeconds: 90,
              downloadBytes: 40000000,
            ),
          ],
        );
        harness.engine.optionalInstalled = {'python'};
        final controller = await _controller();
        addTearDown(controller.dispose);
        Set<String>? result;
        await tester.pumpWidget(
          _app(
            controller,
            home: launcher(addMode: true, onResult: (ids) => result = ids),
          ),
        );
        await tester.tap(find.text('open'));
        await tester.pumpAndSettle();

        expect(find.text('Add tools'), findsOneWidget);
        // Required parts are the phone's base, not tools to add.
        expect(
          find.byKey(const ValueKey('phone-setup-customize-node')),
          findsNothing,
        );
        final python = find.byKey(
          const ValueKey('phone-setup-customize-python'),
        );
        expect(
          find.descendant(of: python, matching: find.text('Installed')),
          findsOneWidget,
        );
        expect(
          find.descendant(of: python, matching: find.byType(Switch)),
          findsNothing,
        );
        final totals = find.byKey(
          const ValueKey('phone-setup-customize-totals'),
        );
        expect(_words(tester, totals), 'Nothing chosen yet');
        final add = find.descendant(
          of: find.byKey(const ValueKey('phone-setup-customize-done')),
          matching: find.byType(FilledButton),
        );
        expect(tester.widget<FilledButton>(add).onPressed, isNull);

        // AI Team needs Git LFS, so choosing it turns that on too.
        await tester.tap(
          find.byKey(const ValueKey('phone-setup-customize-aiteam')),
        );
        await tester.pumpAndSettle();
        expect(
          tester
              .widget<KitSwitchRow>(
                find.byKey(const ValueKey('phone-setup-customize-lfs')),
              )
              .value,
          isTrue,
        );
        expect(_words(tester, totals), 'About 2 minutes · ~50 MB');

        await tester.tap(add);
        await tester.pumpAndSettle();
        expect(result, {'lfs', 'aiteam'});
      },
    );
  });

  group('P0.8 pre-flight in Add tools', () {
    /// The sheet on its own (not through [showPhoneSetupCustomize], which
    /// has no override point): "Add" is the button that starts the
    /// download here, so it is the one this group blocks.
    Widget sheet({required VoiceDeviceInfo device, bool addMode = true}) =>
        Directionality(
          textDirection: TextDirection.ltr,
          child: Localizations(
            locale: const Locale('en'),
            delegates: AppLocalizations.localizationsDelegates,
            child: MediaQuery(
              data: const MediaQueryData(),
              child: Material(
                child: SetupCustomizeSheet(
                  registry: installableComponents(FakeSetupEngine.fakeRegistry),
                  addMode: addMode,
                  selected: addMode ? const {'python'} : null,
                  installedOptional: addMode
                      ? Future.value(const <String>{})
                      : null,
                  deviceProbe: () async => device,
                ),
              ),
            ),
          ),
        );

    testWidgets('a 32-bit-only phone is told why before Add downloads', (
      tester,
    ) async {
      await tester.pumpWidget(
        sheet(
          device: const VoiceDeviceInfo(
            availableStorageBytes: 2000000000,
            memoryClassMb: 256,
            totalMemoryMb: 4096,
            supportedAbis: ['armeabi-v7a'],
            hasMicrophone: false,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        _words(
          tester,
          find.byKey(const ValueKey('phone-setup-customize-totals')),
        ),
        "This app's Ubuntu only runs on a 64-bit Arm or Intel phone; this "
        'one reports armeabi-v7a.',
      );
      final add = find.descendant(
        of: find.byKey(const ValueKey('phone-setup-customize-done')),
        matching: find.byType(FilledButton),
      );
      expect(tester.widget<FilledButton>(add).onPressed, isNull);
    });

    testWidgets('low space names how much to free and offers Storage '
        'settings', (tester) async {
      await tester.pumpWidget(
        sheet(
          device: const VoiceDeviceInfo(
            availableStorageBytes: 250000000,
            memoryClassMb: 256,
            totalMemoryMb: 4096,
            supportedAbis: ['arm64-v8a'],
            hasMicrophone: false,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // python (30 MB) is the only tool chosen: the 300 MB floor applies
      // (2x margin would be only 60 MB), 250 MB free.
      expect(
        _words(
          tester,
          find.byKey(const ValueKey('phone-setup-customize-totals')),
        ),
        'Free about 50 MB on this phone, then come back to set this up.',
      );
      final add = find.descendant(
        of: find.byKey(const ValueKey('phone-setup-customize-done')),
        matching: find.byType(FilledButton),
      );
      expect(tester.widget<FilledButton>(add).onPressed, isNull);
      expect(
        find.byKey(const ValueKey('phone-setup-customize-open-storage')),
        findsOneWidget,
      );
    });

    testWidgets('a supported phone with room keeps the plain totals', (
      tester,
    ) async {
      await tester.pumpWidget(
        sheet(
          device: const VoiceDeviceInfo(
            availableStorageBytes: 2000000000,
            memoryClassMb: 256,
            totalMemoryMb: 4096,
            supportedAbis: ['arm64-v8a'],
            hasMicrophone: false,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        _words(
          tester,
          find.byKey(const ValueKey('phone-setup-customize-totals')),
        ),
        'About a minute · ~30 MB',
      );
      final add = find.descendant(
        of: find.byKey(const ValueKey('phone-setup-customize-done')),
        matching: find.byType(FilledButton),
      );
      expect(tester.widget<FilledButton>(add).onPressed, isNotNull);
      expect(
        find.byKey(const ValueKey('phone-setup-customize-open-storage')),
        findsNothing,
      );
    });
  });

  group('small screens and big text', () {
    Future<void> useSmallPhone(WidgetTester tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
    }

    testWidgets('the fresh screen fits 320 dp at 2.5x with 48 dp targets', (
      tester,
    ) async {
      await useSmallPhone(tester);
      final harness = _Harness();
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(controller, home: harness.screen, scale: 2.5),
      );
      await tester.pumpAndSettle();
      await _expandOtherWays(tester);
      expect(tester.takeException(), isNull);

      for (final key in [
        'phone-setup-start-primary',
        'phone-setup-start-customize',
        'phone-setup-start-use-termux',
        'phone-setup-start-by-address',
      ]) {
        final size = tester.getSize(find.byKey(ValueKey(key)));
        expect(size.height, greaterThanOrEqualTo(48), reason: key);
        expect(size.width, greaterThanOrEqualTo(48), reason: key);
      }

      await tester.ensureVisible(
        find.byKey(const ValueKey('phone-setup-start-customize')),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('phone-setup-start-customize')),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      // The whole sheet scrolls, so its button is reachable at any size.
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('phone-setup-customize-done')),
        120,
        // The kit sheet owns scrolling around its framed content.
        scrollable: find
            .ancestor(
              of: find.byKey(const ValueKey('phone-setup-customize-sheet')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('phone-setup-customize-done')).hitTestable(),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets(
      'the unsupported-ABI and low-space pre-flight states fit 320 dp at 2.5x',
      (tester) async {
        await useSmallPhone(tester);
        for (final device in [
          const VoiceDeviceInfo(
            availableStorageBytes: 2000000000,
            memoryClassMb: 256,
            totalMemoryMb: 4096,
            supportedAbis: ['armeabi-v7a'],
            hasMicrophone: false,
          ),
          const VoiceDeviceInfo(
            availableStorageBytes: 100000000,
            memoryClassMb: 256,
            totalMemoryMb: 4096,
            supportedAbis: ['arm64-v8a'],
            hasMicrophone: false,
          ),
        ]) {
          final harness = _Harness(device: device);
          final controller = await _controller();
          await tester.pumpWidget(
            _app(controller, home: harness.screen, scale: 2.5),
          );
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
          final primary = tester.getSize(
            find.byKey(const ValueKey('phone-setup-start-primary')),
          );
          expect(primary.height, greaterThanOrEqualTo(48));
          controller.dispose();
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pump();
        }
      },
    );

    testWidgets('the progress and Termux heroes fit 320 dp at 2.5x', (
      tester,
    ) async {
      await useSmallPhone(tester);
      final harness = _Harness(
        termux: TermuxRunningServer.running(
          runtime: TermuxRuntime.openCode1,
          version: '1.18.29',
          observedAt: DateTime(2026, 9, 24),
        ),
      );
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(controller, home: harness.screen, scale: 2.5),
      );
      await tester.pumpAndSettle();
      expect(find.text('OpenCode is already set up in Termux'), findsOneWidget);
      await _expandOtherWays(tester);
      expect(tester.takeException(), isNull);

      harness.engine.emit(
        SetupProgress(
          state: SetupState.interrupted,
          components: _job(ComponentState.failed),
          overall: .3,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Setup is 30% done'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('Arabic reads right to left with the same real totals', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final harness = _Harness();
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(controller, home: harness.screen, locale: const Locale('ar')),
    );
    await tester.pumpAndSettle();

    expect(find.text('شغّل وكيل برمجة هنا مباشرة'), findsOneWidget);
    expect(
      find.text(
        'لا حاجة إلى حاسوب أو تطبيقات أخرى. ~165 ميغابايت للتنزيل في '
        'المرة الأولى.',
      ),
      findsOneWidget,
    );
    expect(
      find.text('ثبّت، سمِّ مشروعًا، تحدّث · نحو 4 دقائق'),
      findsOneWidget,
    );
    expect(find.text('يشمل Git and SSH، Python وNode.js.'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'ابدأ الإعداد'), findsOneWidget);

    await _expandOtherWays(tester);
    final termux = find.byKey(const ValueKey('phone-setup-start-use-termux'));
    final icon = find.descendant(of: termux, matching: find.byType(Icon)).first;
    // The leading icon sits at the start, which is the right in Arabic.
    expect(tester.getCenter(icon).dx, greaterThan(tester.getCenter(termux).dx));
    expect(tester.takeException(), isNull);
  });

  for (final reduceMotion in [false, true]) {
    testWidgets(
      'the entrance ${reduceMotion ? 'is instant with' : 'eases in without'} reduce motion',
      (tester) async {
        final harness = _Harness();
        final controller = await _controller();
        addTearDown(controller.dispose);
        await tester.pumpWidget(
          _app(controller, home: harness.screen, reduceMotion: reduceMotion),
        );
        await tester.pump();
        await tester.pump();
        final fades = tester
            .widgetList<FadeTransition>(
              find.ancestor(
                of: find.byKey(const ValueKey('phone-setup-start-primary')),
                matching: find.byType(FadeTransition),
              ),
            )
            .map((fade) => fade.opacity.value);
        if (reduceMotion) {
          expect(fades.every((value) => value == 1), isTrue);
        } else {
          expect(fades.any((value) => value < 1), isTrue);
        }
        await tester.pumpAndSettle();
      },
    );
  }

  testWidgets('openPhoneSetupStart pushes screen A', (tester) async {
    _Harness();
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        controller,
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => openPhoneSetupStart(context),
              child: const Text('go'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
    expect(find.byType(PhoneSetupStartScreen), findsOneWidget);
  });
}

// The words a KitText (or a notice's message) draws, read from its Text.
String? _words(WidgetTester tester, Finder finder) {
  final text = tester.widget<Text>(
    find
        .descendant(of: finder, matching: find.byType(Text), matchRoot: true)
        .first,
  );
  return text.data ?? text.textSpan?.toPlainText();
}
