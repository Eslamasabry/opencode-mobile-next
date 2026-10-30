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
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_progress_screen.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
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
  Future<void> restore({SetupReadScope? scope}) async {
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

Finder get _action => find.byKey(const ValueKey('welcome-primary'));

Finder get _phone => find.byKey(const ValueKey('welcome-choice-phone'));

/// The phone card's line under its title.
String _line(WidgetTester tester) {
  final cards =
      tester.widget(find.byWidgetPredicate((w) => w is KitChoiceCards))
          as KitChoiceCards;
  return cards.choices.first.supporting!;
}

Future<void> _tapAction(WidgetTester tester) async {
  await tester.tap(_action);
  await tester.pumpAndSettle();
}

/// Answers the device probe with [info] (CPU, memory, free space).
void _device(Map<String, Object?> info) {
  const channel = MethodChannel('oc/voice');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(channel, (call) async {
    if (call.method == 'getDeviceInfo') return info;
    return null;
  });
  addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
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
    // Set up on this phone reads the device before anything downloads.
    answerVoiceDeviceProbe();
  });

  testWidgets('no job: the phone card says what setup costs, from the '
      "engine's own numbers", (tester) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    expect(engine.restores, 1, reason: 'the job is looked for');
    // The fake registry's default selection: 20+40+50+40+90 s and
    // 30+15+30+30+60 MB.
    expect(_line(tester), 'About 4 minutes · 165 MB download');
    expect(find.text('Set up on this phone'), findsOneWidget);
    expect(
      find.descendant(of: _welcome, matching: _welcomeChoice),
      findsNWidgets(2),
    );
  });

  testWidgets('Set up on this phone starts the default setup at once and '
      'opens its progress as a first setup', (tester) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

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

  testWidgets('a phone that cannot run it is told why on phone setup, '
      'before anything downloads', (tester) async {
    _device({
      'supportedAbis': ['armeabi-v7a'],
    });
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    await _tapAction(tester);
    expect(engine.runs, isEmpty);
    expect(find.byType(PhoneSetupStartScreen), findsOneWidget);
    expect(
      find.byKey(const ValueKey('phone-setup-start-preflight')),
      findsOneWidget,
    );
  });

  testWidgets('More ways to set up opens phone setup, and coming back '
      'reads the job again', (tester) async {
    await tester.binding.setSurfaceSize(const Size(412, 915));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    final moreWays = find.byKey(const ValueKey('welcome-more-ways'));
    await tester.ensureVisible(moreWays);
    await tester.pumpAndSettle();
    await tester.tap(moreWays);
    await tester.pumpAndSettle();
    expect(find.byType(PhoneSetupStartScreen), findsOneWidget);
    // Set up was tapped there, and the person came straight back.
    engine.emit(_job(SetupState.running, .1));
    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(_line(tester), 'Setting up · 10%');
  });

  testWidgets('a running setup says its percent on the card and the button '
      'opens its progress as a first setup', (tester) async {
    engine.emit(_job(SetupState.running, .42));
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    expect(_line(tester), 'Setting up · 42%');
    // The question and both answers stay: the card says where it stands.
    expect(find.byKey(const ValueKey('welcome-question')), findsOneWidget);
    expect(
      find.descendant(of: _welcome, matching: _welcomeChoice),
      findsNWidgets(2),
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
    testWidgets('a ${state.name} setup offers Continue setup, which resumes '
        'the same job as a first setup', (tester) async {
      engine.emit(_job(state, .6));
      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(store, controller));
      await tester.pumpAndSettle();

      expect(_line(tester), 'Setup is 60% done');
      expect(find.text('Continue setup'), findsOneWidget);

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

  testWidgets('never shows 100% while it still runs', (tester) async {
    engine.emit(_job(SetupState.running, 1));
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();
    expect(_line(tester), 'Setting up · 99%');
  });

  testWidgets(
    'done with nothing saved offers Start on this phone, which runs only '
    "the job's last step",
    (tester) async {
      engine
        ..emit(_job(SetupState.done, 1))
        ..afterRun = _job(SetupState.running, .98);
      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(store, controller));
      await tester.pumpAndSettle();

      expect(_line(tester), 'Installed and ready');
      expect(find.text('Start on this phone'), findsOneWidget);
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
    },
  );

  testWidgets('the welcome never waits for the job to be read', (tester) async {
    final slow = _SlowRestoreEngine()
      ..onRestore = _job(SetupState.interrupted, .3);
    PhoneSetup.engine = slow;
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pump();

    // The question and its answers are there at once, with the cost: the
    // job has not been read yet.
    expect(_phone, findsOneWidget);
    expect(_line(tester), startsWith('About'));

    slow.release.complete();
    await tester.pumpAndSettle();
    expect(_line(tester), 'Setup is 30% done');
  });

  testWidgets('a read that hangs gives up and shows what is known', (
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
    expect(_line(tester), startsWith('About'));

    await tester.pump(const Duration(seconds: 4));
    // Timed out: what the engine holds is shown, and nothing hangs.
    expect(_line(tester), 'Setup is 30% done');
    expect(tester.takeException(), isNull);
  });

  testWidgets('a failed Continue says so on the card and stays', (
    tester,
  ) async {
    final failing = _FailingEngine()..emit(_job(SetupState.failed, .5));
    PhoneSetup.engine = failing;
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.pumpAndSettle();

    await _tapAction(tester);
    expect(_line(tester), startsWith("That didn't work"));
    expect(find.byType(PhoneSetupProgressScreen), findsNothing);
    // The button still offers the same way on.
    expect(find.text('Continue setup'), findsOneWidget);
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
    expect(_line(tester), 'اكتمل الإعداد بنسبة 60٪');
    final rect = tester.getRect(_action);
    expect(rect.left, greaterThanOrEqualTo(0));
    expect(rect.right, lessThanOrEqualTo(320));
    expect(rect.height, greaterThanOrEqualTo(48));
    await tester.scrollUntilVisible(
      _phone,
      120,
      scrollable: find.byType(Scrollable).first,
    );
    final card = tester.getRect(_phone);
    expect(card.left, greaterThanOrEqualTo(0));
    expect(card.right, lessThanOrEqualTo(320));
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
