import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_progress_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_setup_engine.dart';
import 'support/voice_device_channel.dart';

// Open point 1 of phone setup v2: the first-run welcome knows about a setup
// on this phone. Someone who left a setup part way comes back to the welcome
// (nothing is saved until setup ends), and must find their way back to it
// there, not two screens deep.

/// An engine whose persisted job is only read once [release] completes, so
/// a test can see the welcome before the job arrives.
class _SlowRestoreEngine extends FakeSetupEngine {
  final release = Completer<void>();
  SetupProgress? onRestore;

  @override
  Future<void> restore() async {
    await super.restore();
    await release.future;
    if (onRestore != null) emit(onRestore!);
  }
}

Future<(ProfileStore, ConnectionController)> _state() async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return (store, ConnectionController(store));
}

Widget _app(ProfileStore store, ConnectionController controller) =>
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(store)),
        connProvider.overrideWithValue(controller),
      ],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const ServersScreen(),
      ),
    );

const _jobIds = {'linux', 'essentials', 'python', 'node', 'opencode'};

SetupProgress _job(SetupState state, double overall) => SetupProgress(
  jobId: 'job-1',
  state: state,
  overall: overall,
  components: [
    for (final id in _jobIds)
      ComponentProgress(id: id, state: ComponentState.pending),
  ],
);

Finder get _welcome => find.byKey(const ValueKey('first-run-welcome'));

Finder get _action => find.byKey(const ValueKey('welcome-phone-setup-action'));

Future<void> _tapAction(WidgetTester tester) async {
  await tester.ensureVisible(_action);
  await tester.pumpAndSettle();
  await tester.tap(_action);
  await tester.pumpAndSettle();
}

void main() {
  late FakeSetupEngine engine;

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
    engine = FakeSetupEngine();
    PhoneSetup.engine = engine;
    // No Termux job: phone setup's start screen also reads Termux's engine
    // (320269a2, P1.7), which must not reach the platform channel here.
    final previousTermux = PhoneSetup.termux;
    PhoneSetup.termux = FakeSetupEngine();
    addTearDown(() => PhoneSetup.termux = previousTermux);
    // The welcome's phone choice opens phone setup, whose pre-flight reads
    // the device.
    answerVoiceDeviceProbe();
  });

  testWidgets('no job: nothing extra, and the three choices stay', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    expect(engine.restores, 1, reason: 'the job is looked for');
    expect(
      find.byKey(const ValueKey('welcome-phone-setup-title')),
      findsNothing,
    );
    expect(
      find.descendant(of: _welcome, matching: _welcomeChoice),
      findsNWidgets(3),
    );
  });

  testWidgets('a running setup leads with its percent and opens its '
      'progress as a first setup', (tester) async {
    // A phone-sized page, so the whole lazy list (line, question, the
    // recommended choice and "Other ways") is built.
    await tester.binding.setSurfaceSize(const Size(412, 1200));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    engine.emit(_job(SetupState.running, .42));
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    expect(
      find.text('Setting up OpenCode on this phone · 42%'),
      findsOneWidget,
    );
    // Above the question, and the choices are still there underneath.
    expect(
      tester
          .getTopLeft(find.text('Setting up OpenCode on this phone · 42%'))
          .dy,
      lessThan(
        tester.getTopLeft(find.byKey(const ValueKey('welcome-question'))).dy,
      ),
    );
    expect(
      find.descendant(of: _welcome, matching: _welcomeChoice),
      findsNWidgets(3),
    );
    expect(find.text('Show progress'), findsOneWidget);

    await _tapAction(tester);
    // Watching a running job never starts another.
    expect(engine.runs, isEmpty);
    final screen = tester.widget<PhoneSetupProgressScreen>(
      find.byType(PhoneSetupProgressScreen),
    );
    expect(screen.firstSetup, isTrue);
  });

  for (final state in [
    SetupState.interrupted,
    SetupState.failed,
    SetupState.cancelled,
  ]) {
    testWidgets('a ${state.name} setup offers Continue, which resumes the '
        'same job as a first setup', (tester) async {
      engine.emit(_job(state, .6));
      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(store, controller));
      await tester.pumpAndSettle();

      expect(find.text('Setup on this phone is 60% done'), findsOneWidget);
      expect(
        find.text('Continuing picks up where it left off.'),
        findsOneWidget,
      );
      expect(find.text('Continue'), findsOneWidget);

      await _tapAction(tester);
      expect(engine.runs, [_jobIds]);
      expect(engine.runParams.single, SetupJobParams.firstSetup);
      expect(
        tester
            .widget<PhoneSetupProgressScreen>(
              find.byType(PhoneSetupProgressScreen),
            )
            .firstSetup,
        isTrue,
      );
    });
  }

  testWidgets('coming back from phone setup reads the job again', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('welcome-phone-setup-title')),
      findsNothing,
    );

    // The welcome opens with its drawing; on this short surface the choice
    // is below the fold.
    await tester.ensureVisible(
      find.byKey(const ValueKey('welcome-choice-phone')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('welcome-choice-phone')));
    await tester.pumpAndSettle();
    // Set up was tapped there, and the person came straight back.
    engine.emit(_job(SetupState.running, .1));
    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(
      find.text('Setting up OpenCode on this phone · 10%'),
      findsOneWidget,
    );
  });

  testWidgets('never shows 100% while it still runs', (tester) async {
    engine.emit(_job(SetupState.running, 1));
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();
    expect(
      find.text('Setting up OpenCode on this phone · 99%'),
      findsOneWidget,
    );
  });

  testWidgets('done with nothing saved offers Open, which runs only the '
      "job's last step", (tester) async {
    engine
      ..emit(_job(SetupState.done, 1))
      ..afterRun = _job(SetupState.running, .98);
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    expect(find.text('OpenCode is ready on this phone'), findsOneWidget);
    await _tapAction(tester);
    // The same job again: every check passes, so only "start and connect"
    // runs, and it ends on "name your first project".
    expect(engine.runs, [_jobIds]);
    expect(engine.runParams.single, SetupJobParams.firstSetup);
    expect(
      tester
          .widget<PhoneSetupProgressScreen>(
            find.byType(PhoneSetupProgressScreen),
          )
          .firstSetup,
      isTrue,
    );
  });

  testWidgets('the welcome never waits for the job to be read', (tester) async {
    final slow = _SlowRestoreEngine()
      ..onRestore = _job(SetupState.interrupted, .3);
    PhoneSetup.engine = slow;
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pump();

    // The question and its choices are there at once; the setup line is
    // not, because the job has not been read.
    expect(
      find.descendant(of: _welcome, matching: _welcomeChoice),
      findsNWidgets(3),
    );
    expect(
      find.byKey(const ValueKey('welcome-phone-setup-title')),
      findsNothing,
    );

    slow.release.complete();
    await tester.pumpAndSettle();
    expect(find.text('Setup on this phone is 30% done'), findsOneWidget);
  });

  testWidgets('a read that hangs gives up and leaves the welcome as it is', (
    tester,
  ) async {
    final slow = _SlowRestoreEngine();
    PhoneSetup.engine = slow;
    // An old job's progress in memory must not show without a read.
    slow.emit(_job(SetupState.interrupted, .3));
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pump();
    expect(
      find.byKey(const ValueKey('welcome-phone-setup-title')),
      findsNothing,
    );

    await tester.pump(const Duration(seconds: 4));
    // Timed out: what the engine holds is shown, and nothing hangs.
    expect(find.text('Setup on this phone is 30% done'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a failed Continue says so and keeps the line', (tester) async {
    final failing = _FailingEngine()..emit(_job(SetupState.failed, .5));
    PhoneSetup.engine = failing;
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    await _tapAction(tester);
    expect(
      find.byKey(const ValueKey('welcome-phone-setup-failure')),
      findsOneWidget,
    );
    expect(find.byType(PhoneSetupProgressScreen), findsNothing);
  });

  testWidgets('Arabic words and 320dp at 2.5x text fit', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    engine.emit(_job(SetupState.interrupted, .6));
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(controller),
        ],
        child: MaterialApp(
          locale: const Locale('ar'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2.5)),
            child: child!,
          ),
          home: const ServersScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    // The welcome is lazy; at large text the setup line is below its hero.
    await tester.scrollUntilVisible(
      _action,
      120,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    expect(find.text('اكتمل الإعداد على هذا الهاتف بنسبة 60٪'), findsOneWidget);
    await tester.ensureVisible(_action);
    await tester.pumpAndSettle();
    final rect = tester.getRect(_action);
    expect(rect.left, greaterThanOrEqualTo(0));
    expect(rect.right, lessThanOrEqualTo(320));
    expect(rect.height, greaterThanOrEqualTo(48));
  });
}

class _FailingEngine extends FakeSetupEngine {
  @override
  Future<void> run(
    Set<String> ids, {
    Map<String, Map<String, String>> params = const {},
  }) async {
    throw StateError('no space');
  }
}

/// One of the welcome's answers (its rows are keyed `welcome-choice-*`).
final _welcomeChoice = find.byWidgetPredicate(
  (w) =>
      w.key is ValueKey<String> &&
      (w.key! as ValueKey<String>).value.startsWith('welcome-choice-'),
);
