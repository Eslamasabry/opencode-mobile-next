import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/network.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_progress_screen.dart';
import 'package:opencode_mobile/ui/widgets/setup_progress_view.dart';

import 'support/fake_setup_engine.dart';

const _ids = ['linux', 'essentials', 'python', 'node', 'opencode'];

/// A job of the fake registry's five components: those in [done] finished,
/// [current] as given, the rest pending.
SetupProgress _job({
  SetupState state = SetupState.running,
  double overall = .4,
  int? eta,
  Set<String> done = const {},
  ComponentProgress? current,
  String? error,
  String log = '',
  List<String> ids = _ids,
}) => SetupProgress(
  state: state,
  overall: overall,
  etaSeconds: eta,
  error: error,
  logTail: log,
  current: current?.id,
  components: [
    for (final id in ids)
      if (id == current?.id)
        current!
      else if (done.contains(id))
        ComponentProgress(
          id: id,
          state: ComponentState.done,
          version: id == 'linux' ? '24.04.5' : null,
        )
      else
        ComponentProgress(id: id, state: ComponentState.pending),
  ],
);

const _nodeDownloading = ComponentProgress(
  id: 'node',
  state: ComponentState.running,
  stage: 'Downloading',
  bytesDone: 18000000,
  bytesTotal: 30000000,
);

class _Harness {
  final engine = FakeSetupEngine();
  final navigatorKey = GlobalKey<NavigatorState>();
  var readyOpened = 0;
}

Future<_Harness> _pump(
  WidgetTester tester, {
  SetupProgress? initial,
  double textScale = 1,
  bool rtl = false,
  bool reduceMotion = false,
  Locale locale = const Locale('en'),
  bool pushReady = true,
  bool firstSetup = true,
  Future<bool> Function()? askNotifications,
  NetworkTransport transport = NetworkTransport.unknown,
}) async {
  PhoneSetupProgressScreen.resetNotificationAnswer();
  final h = _Harness();
  if (initial != null) h.engine.emit(initial);
  await tester.pumpWidget(
    MaterialApp(
      navigatorKey: h.navigatorKey,
      theme: AppTheme.light(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: reduceMotion,
        ),
        child: Directionality(
          textDirection: rtl || locale.languageCode == 'ar'
              ? TextDirection.rtl
              : TextDirection.ltr,
          child: child!,
        ),
      ),
      home: const Scaffold(body: Text('Home')),
    ),
  );
  h.navigatorKey.currentState!.push(
    MaterialPageRoute<void>(
      builder: (_) => PhoneSetupProgressScreen(
        engine: h.engine,
        firstSetup: firstSetup,
        askNotifications: askNotifications ?? () async => true,
        readNetwork: () async => NetworkReading(transport: transport),
        openReady: (context) async {
          h.readyOpened++;
          if (!pushReady) return;
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => const Scaffold(body: Text('Ready screen')),
            ),
          );
        },
      ),
    ),
  );
  await _settle(tester);
  return h;
}

/// The page scrolls at large text; a control may sit below the fold.
Future<void> _tapVisible(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pump();
  await tester.tap(finder);
}

/// Spinners and indeterminate lines never settle, so time is stepped
/// instead of pumpAndSettle.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

/// "18 of 30 MB", its figures isolated left to right.
final _bytes = '${KitBidi.ltr('18')} of ${KitBidi.ltr('30 MB')}';

KitProgressRing _ring(WidgetTester tester) =>
    tester.widget<KitProgressRing>(find.byType(KitProgressRing));

/// Opens Details: the job's every step and its log, in a sheet.
Future<void> _openDetails(WidgetTester tester) async {
  await _tapVisible(tester, find.byKey(const Key('setup-progress-details')));
  await _settle(tester);
}

/// The full view, as the Termux host shows it; its rows are also what the
/// Details sheet lists.
Future<void> _pumpView(
  WidgetTester tester,
  SetupProgress progress, {
  bool rtl = false,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Directionality(
        textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
        child: Scaffold(
          body: ListView(
            children: [
              SetupProgressView(
                progress: progress,
                components: FakeSetupEngine.fakeRegistry,
                checklistOnly: true,
              ),
            ],
          ),
        ),
      ),
    ),
  );
  await _settle(tester);
}

void main() {
  test('ownership recovery ignores running jobs and untrusted log lines', () {
    expect(
      SetupProgressView.needsOwnershipRecovery(
        _job(state: SetupState.running, error: 'runtime_ownership_unknown'),
      ),
      isFalse,
    );
    expect(
      SetupProgressView.needsOwnershipRecovery(
        _job(
          state: SetupState.failed,
          log: 'ownershipUnknown',
          error: 'download failed',
        ),
      ),
      isFalse,
    );
    expect(
      SetupProgressView.needsOwnershipRecovery(
        _job(state: SetupState.failed, error: 'runtime_ownership_unknown'),
      ),
      isTrue,
    );
  });

  testWidgets(
    'ownership failure opens Android settings instead of continuing setup',
    (tester) async {
      var settings = 0;
      const channel = MethodChannel('oc/termux');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'openAppSettings') settings++;
        return true;
      });
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      final h = await _pump(
        tester,
        initial: _job(
          state: SetupState.failed,
          current: const ComponentProgress(
            id: 'opencode',
            state: ComponentState.failed,
            error: 'runtime_ownership_unknown: ownershipUnknown',
          ),
        ),
      );
      expect(find.textContaining('ownershipUnknown'), findsNothing);
      expect(find.textContaining('Force stop'), findsOneWidget);
      expect(find.byKey(const Key('setup-progress-continue')), findsNothing);
      await _tapVisible(
        tester,
        find.byKey(const Key('setup-progress-recover-ownership')),
      );
      await _settle(tester);
      expect(settings, 1);
      expect(h.engine.runs, isEmpty);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  group('the ring shows only real figures', () {
    testWidgets('a measured download: its share, its bytes, the step', (
      tester,
    ) async {
      await _pump(
        tester,
        initial: _job(
          overall: .9,
          done: {'linux', 'essentials'},
          current: _nodeDownloading,
        ),
      );
      // The step's own measured share, not the engine's weighted blend.
      expect(_ring(tester).fraction, closeTo(.6, 1e-9));
      expect(find.text(_bytes), findsOneWidget);
      expect(find.text('Downloading Node.js'), findsOneWidget);
      expect(find.textContaining('90'), findsNothing);
      // No spinner and no bar: the ring is the one measure.
      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(find.byType(LinearProgressIndicator), findsNothing);
    });

    testWidgets('a reported percent, floored', (tester) async {
      await _pump(
        tester,
        initial: _job(
          done: {'linux'},
          current: const ComponentProgress(
            id: 'essentials',
            state: ComponentState.running,
            stage: 'Installing packages',
            percent: 62.7,
          ),
        ),
      );
      final semantics = tester.ensureSemantics();
      expect(
        tester.getSemantics(find.byKey(const Key('setup-progress-overall'))),
        isSemantics(label: 'Setup progress', value: '62%'),
      );
      semantics.dispose();
      expect(find.text('Installing Git and SSH'), findsOneWidget);
    });

    testWidgets('a step with no amount shows steps done, never a percent', (
      tester,
    ) async {
      await _pump(
        tester,
        initial: _job(
          overall: .8,
          done: {'linux', 'essentials', 'python', 'node'},
          current: const ComponentProgress(
            id: 'opencode',
            state: ComponentState.running,
            stage: 'Installing OpenCode',
          ),
        ),
      );
      expect(_ring(tester).done, 4);
      expect(_ring(tester).of, 5);
      expect(find.text('steps done'), findsOneWidget);
      expect(find.text('Installing OpenCode'), findsOneWidget);
      expect(find.textContaining('%'), findsNothing);
      expect(find.textContaining(' MB'), findsNothing);
    });

    testWidgets('within a stage it never goes back; a new stage starts over', (
      tester,
    ) async {
      final h = await _pump(tester, initial: _job(current: _nodeDownloading));
      expect(_ring(tester).fraction, closeTo(.6, 1e-9));
      ComponentProgress node(String stage, int done) => ComponentProgress(
        id: 'node',
        state: ComponentState.running,
        stage: stage,
        bytesDone: done,
        bytesTotal: 30000000,
      );
      h.engine.emit(_job(current: node('Downloading', 15000000)));
      await _settle(tester);
      expect(_ring(tester).fraction, closeTo(.6, 1e-9));
      h.engine.emit(_job(current: node('Unpacking Node.js', 3000000)));
      await _settle(tester);
      expect(_ring(tester).fraction, closeTo(.1, 1e-9));
      expect(find.text('Unpacking Node.js'), findsOneWidget);
    });

    testWidgets('checking first, then getting started', (tester) async {
      final h = await _pump(
        tester,
        initial: SetupProgress(
          state: SetupState.running,
          overall: 0,
          components: [
            for (final id in _ids)
              ComponentProgress(id: id, state: ComponentState.checking),
          ],
        ),
      );
      expect(find.text('Checking this phone'), findsOneWidget);
      expect(_ring(tester).done, 0);
      h.engine.emit(_job());
      await _settle(tester);
      expect(find.text('Getting started…'), findsOneWidget);
    });

    testWidgets('done: a full ring', (tester) async {
      await _pump(
        tester,
        initial: _job(state: SetupState.done, overall: 1, done: _ids.toSet()),
        pushReady: false,
      );
      expect(_ring(tester).fraction, 1);
      expect(find.text('All set'), findsOneWidget);
      expect(find.byKey(const Key('setup-progress-cancel')), findsNothing);
    });
  });

  group('cost line', () {
    testWidgets('time left only when the engine gives one', (tester) async {
      final h = await _pump(tester, initial: _job(current: _nodeDownloading));
      expect(find.byKey(const Key('phone-setup-progress-cost')), findsNothing);

      const before = {'linux', 'essentials', 'python'};
      h.engine.emit(_job(eta: 130, done: before, current: _nodeDownloading));
      await tester.pump();
      expect(find.text('About 2 min left'), findsOneWidget);
    });

    testWidgets('names Wi-Fi or mobile data, nothing when unknown', (
      tester,
    ) async {
      await _pump(
        tester,
        initial: _job(eta: 130, current: _nodeDownloading),
        transport: NetworkTransport.mobile,
      );
      expect(find.text('About 3 min left · Mobile data'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
      await _pump(
        tester,
        initial: _job(eta: 130, current: _nodeDownloading),
        transport: NetworkTransport.wifi,
      );
      expect(find.text('About 3 min left · Wi-Fi'), findsOneWidget);
    });

    // Emulator QA F11: "Less than a minute" at 30 %, then "~5 min left".
    testWidgets('never under a minute before the last step, and never up', (
      tester,
    ) async {
      final h = await _pump(tester, initial: _job(current: _nodeDownloading));
      const before = {'linux', 'essentials', 'python'};
      // A fast download: the engine's pace says 40 s, but OpenCode (90 s)
      // has not started, so the line says what is still to come.
      h.engine.emit(_job(eta: 40, done: before, current: _nodeDownloading));
      await tester.pump();
      expect(find.text('Less than a minute left'), findsNothing);
      expect(find.text('About 2 min left'), findsOneWidget);

      // The pace slows and the engine now says 5 min: the line holds.
      h.engine.emit(_job(eta: 300, done: before, current: _nodeDownloading));
      await tester.pump();
      expect(find.text('About 5 min left'), findsNothing);
      expect(find.text('About 2 min left'), findsOneWidget);

      // The last step: now it may say less than a minute.
      h.engine.emit(
        _job(
          eta: 40,
          done: {...before, 'node'},
          current: const ComponentProgress(
            id: 'opencode',
            state: ComponentState.running,
            stage: 'Installing',
          ),
        ),
      );
      await tester.pump();
      expect(find.text('Less than a minute left'), findsOneWidget);
    });

    testWidgets('a short estimate before the last step reads 1 min', (
      tester,
    ) async {
      const linux = ComponentProgress(
        id: 'linux',
        state: ComponentState.running,
        stage: 'Downloading',
      );
      final h = await _pump(
        tester,
        initial: _job(ids: const ['linux', 'essentials'], current: linux),
      );
      h.engine.emit(
        _job(eta: 10, ids: const ['linux', 'essentials'], current: linux),
      );
      await tester.pump();
      expect(find.text('Less than a minute left'), findsNothing);
      expect(find.text('About 1 min left'), findsOneWidget);
    });
  });

  group('failure', () {
    testWidgets('Python repair says why and keeps the log under Details', (
      tester,
    ) async {
      final en = lookupAppLocalizations(const Locale('en'));
      const detail = 'libonig5:amd64: install reinstreq half-installed';
      await _pump(
        tester,
        initial: _job(
          state: SetupState.failed,
          done: {'linux', 'essentials', 'node', 'opencode'},
          current: ComponentProgress(
            id: 'python',
            state: ComponentState.failed,
            error: en.phoneSetupErrorPackageRepair,
          ),
          error: en.phoneSetupErrorPackageRepair,
          log: detail,
        ),
      );
      expect(find.text(en.phoneSetupErrorPackageRepair), findsOneWidget);
      expect(find.textContaining(detail), findsNothing);
      await _openDetails(tester);
      await _tapVisible(tester, find.text('Details').last);
      await _settle(tester);
      expect(find.textContaining(detail), findsWidgets);
      expect(tester.takeException(), isNull);
    });

    testWidgets('says the stage in words, a neutral ring, Continue setup', (
      tester,
    ) async {
      final h = await _pump(
        tester,
        initial: _job(
          state: SetupState.failed,
          done: {'linux', 'essentials', 'python'},
          current: const ComponentProgress(
            id: 'node',
            state: ComponentState.failed,
            stage: 'Checking the download',
            error: 'The file was damaged',
          ),
        ),
      );
      // The stage in words; the job's own reason is under Details (no raw
      // errors; slice-close-servers).
      expect(
        find.text(
          'Stopped during: Checking the download. '
          'What went wrong is under Details.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('The file was damaged'), findsNothing);
      expect(find.text("Setup didn't finish"), findsOneWidget);
      // Never red: a halted ring is neutral, and shows steps done.
      expect(_ring(tester).halted, isTrue);
      expect(_ring(tester).done, 3);
      expect(find.byKey(const Key('setup-progress-cancel')), findsNothing);
      expect(find.byKey(const Key('setup-progress-details')), findsOneWidget);

      await _tapVisible(tester, find.text('Continue setup'));
      await tester.pump();
      expect(h.engine.runs, [_ids.toSet()]);
    });

    testWidgets('network errors ask to reconnect', (tester) async {
      await _pump(
        tester,
        initial: _job(
          state: SetupState.failed,
          done: {'linux', 'essentials', 'python'},
          current: const ComponentProgress(
            id: 'node',
            state: ComponentState.failed,
            stage: 'Downloading Node.js 24',
            error: 'Exit code 6',
          ),
          log: 'curl: (6) Could not resolve host: nodejs.org\n',
        ),
      );
      expect(
        find.text("No internet connection — Continue when you're back online"),
        findsOneWidget,
      );
      expect(find.textContaining('Exit code 6'), findsNothing);
    });

    testWidgets('a failure between components still says it stopped, with '
        'the reason under Details', (tester) async {
      await _pump(
        tester,
        initial: _job(
          state: SetupState.failed,
          overall: 1,
          done: _ids.toSet(),
          error: 'OpenCode did not start',
        ),
      );
      expect(
        find.text(
          'Setup stopped before it finished. What went wrong is under Details.',
        ),
        findsOneWidget,
      );
      expect(find.text('OpenCode did not start'), findsNothing);
      await _openDetails(tester);
      await _tapVisible(tester, find.text('Details').last);
      await _settle(tester);
      expect(find.textContaining('OpenCode did not start'), findsOneWidget);
    });

    testWidgets('interrupted and paused jobs offer Continue setup', (
      tester,
    ) async {
      final h = await _pump(
        tester,
        initial: _job(state: SetupState.interrupted, done: {'linux'}),
      );
      expect(find.text('Setup was interrupted'), findsOneWidget);
      const kept =
          'Finished steps stay, and downloads pick up where they left off.';
      expect(find.text(kept), findsOneWidget);
      h.engine.emit(_job(state: SetupState.cancelled, done: {'linux'}));
      await _settle(tester);
      expect(find.text('Setup is paused'), findsOneWidget);
      expect(find.text(kept), findsOneWidget);
      await _tapVisible(tester, find.text('Continue setup'));
      await tester.pump();
      expect(h.engine.runs.single, _ids.toSet());
    });
  });

  group('screen', () {
    testWidgets('restores on open; the bar names the place, with Back', (
      tester,
    ) async {
      final h = await _pump(tester, initial: _job(current: _nodeDownloading));
      expect(h.engine.restores, 1);
      expect(find.text('On this phone'), findsOneWidget);
      expect(find.byTooltip('Back'), findsOneWidget);
      expect(
        find.text("Safe to leave. We'll let you know when it's ready."),
        findsOneWidget,
      );
    });

    testWidgets('an Add tools job names what it adds in the bar', (
      tester,
    ) async {
      await _pump(
        tester,
        initial: SetupProgress(
          state: SetupState.running,
          overall: .2,
          adding: const ['python'],
          components: const [
            ComponentProgress(
              id: 'python',
              state: ComponentState.running,
              stage: 'Installing packages',
              percent: 20,
            ),
          ],
        ),
        firstSetup: false,
      );
      expect(find.text('Adding Python'), findsOneWidget);
    });

    testWidgets('asks Android for notifications when setup starts, once', (
      tester,
    ) async {
      var asked = 0;
      await _pump(
        tester,
        initial: _job(current: _nodeDownloading),
        askNotifications: () async {
          asked++;
          return true;
        },
      );
      expect(asked, 1);
    });

    testWidgets('declined notifications: setup stays and promises no '
        'notification', (tester) async {
      await _pump(
        tester,
        initial: _job(current: _nodeDownloading),
        askNotifications: () async => false,
      );
      expect(find.textContaining('let you know'), findsNothing);
      expect(
        find.text('Safe to leave. Setup keeps going in the background.'),
        findsOneWidget,
      );
    });

    testWidgets('Pause setup pauses at once: nothing is lost', (tester) async {
      final h = await _pump(tester, initial: _job(current: _nodeDownloading));
      expect(find.text('Pause setup'), findsOneWidget);
      await _tapVisible(tester, find.byKey(const Key('setup-progress-cancel')));
      await _settle(tester);
      expect(h.engine.cancels, 1);
      expect(find.text('Stop setup?'), findsNothing);
    });

    testWidgets('done hands over to screen C, replacing this screen', (
      tester,
    ) async {
      final h = await _pump(tester, initial: _job(current: _nodeDownloading));
      expect(h.readyOpened, 0);
      h.engine.emit(
        _job(state: SetupState.done, overall: 1, done: _ids.toSet()),
      );
      await _settle(tester);
      expect(h.readyOpened, 1);
      expect(find.text('Ready screen'), findsOneWidget);

      // Back from C skips the finished progress screen.
      h.navigatorKey.currentState!.pop();
      await _settle(tester);
      expect(find.text('Home'), findsOneWidget);
      expect(find.byType(PhoneSetupProgressScreen), findsNothing);

      // Later emits do not open C again.
      h.engine.emit(
        _job(state: SetupState.done, overall: 1, done: _ids.toSet()),
      );
      await _settle(tester);
      expect(h.readyOpened, 1);
    });

    testWidgets('an update or added tools end here, then close', (
      tester,
    ) async {
      final h = await _pump(
        tester,
        initial: _job(current: _nodeDownloading),
        firstSetup: false,
      );
      h.engine.emit(
        _job(state: SetupState.done, overall: 1, done: _ids.toSet()),
      );
      await _settle(tester);
      // The finished ring stays for a moment, never screen C.
      expect(h.readyOpened, 0);
      expect(find.byType(PhoneSetupProgressScreen), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 1300));
      await _settle(tester);
      expect(find.text('Home'), findsOneWidget);
      expect(find.byType(PhoneSetupProgressScreen), findsNothing);
    });

    testWidgets('Back while running just leaves; setup is not paused', (
      tester,
    ) async {
      final h = await _pump(tester, initial: _job(current: _nodeDownloading));
      await tester.pageBack();
      await _settle(tester);
      expect(find.text('Home'), findsOneWidget);
      expect(h.engine.cancels, 0);
    });
  });

  testWidgets('Details lists every step and follows the log', (tester) async {
    final h = await _pump(
      tester,
      initial: _job(
        current: _nodeDownloading,
        log: 'Get:1 http://ports.ubuntu.com noble InRelease\n',
      ),
    );
    const log = Key('setup-progress-log');
    expect(find.byKey(const Key('setup-progress-checklist')), findsNothing);
    await _openDetails(tester);
    expect(find.text('Setup details'), findsOneWidget);
    expect(find.byKey(const Key('setup-progress-checklist')), findsOneWidget);
    expect(find.text('Downloading · 18 of 30 MB'), findsOneWidget);
    expect(find.byKey(log), findsNothing);
    await _tapVisible(tester, find.text('Details').last);
    await _settle(tester);
    expect(find.byKey(log), findsOneWidget);
    expect(find.textContaining('noble InRelease'), findsOneWidget);

    final lines = [for (var i = 0; i < 80; i++) 'line $i'].join('\n');
    h.engine.emit(_job(current: _nodeDownloading, log: lines));
    await _settle(tester);
    // Tail-first: the newest line is on screen, the oldest folded away.
    expect(find.textContaining('line 79'), findsOneWidget);
    expect(find.textContaining('line 0\n'), findsNothing);
  });

  group('the steps under Details (SetupProgressView)', () {
    testWidgets('bytes as "18 of 30 MB" with the stage', (tester) async {
      await _pumpView(
        tester,
        _job(done: {'linux', 'essentials'}, current: _nodeDownloading),
      );
      expect(find.text('Downloading · 18 of 30 MB'), findsOneWidget);
    });

    testWidgets('percent as "62%"', (tester) async {
      await _pumpView(
        tester,
        _job(
          done: {'linux'},
          current: const ComponentProgress(
            id: 'essentials',
            state: ComponentState.running,
            stage: 'Installing packages',
            percent: 62.7,
          ),
        ),
      );
      // Rounded down: never more than the tool reported.
      expect(find.text('Installing packages · 62%'), findsOneWidget);
    });

    testWidgets('a stage alone shows its label and no figure', (tester) async {
      await _pumpView(
        tester,
        _job(
          done: {'linux', 'essentials', 'python', 'node'},
          current: const ComponentProgress(
            id: 'opencode',
            state: ComponentState.running,
            stage: 'Installing OpenCode',
          ),
        ),
      );
      expect(find.text('Installing OpenCode'), findsOneWidget);
      expect(find.textContaining('%'), findsNothing);
      expect(find.textContaining(' MB'), findsNothing);
    });

    testWidgets('a done row shows its version muted with a check', (
      tester,
    ) async {
      await _pumpView(tester, _job(done: {'linux'}, current: _nodeDownloading));
      final version = tester.widget<Text>(find.text('24.04.5'));
      final theme = Theme.of(tester.element(find.text('24.04.5')));
      expect(version.style?.color, AppTheme.mutedOf(theme));
      expect(find.text('Linux base'), findsOneWidget);
      expect(find.text('Git and SSH'), findsOneWidget);
    });

    test('byte formatting rounds done down and keeps the unit', () {
      expect(SetupProgressView.formatBytePair(18999999, 30000000), (
        '18',
        '30 MB',
      ));
      expect(SetupProgressView.formatBytePair(950000, 2500000), (
        '0.9',
        '2.5 MB',
      ));
      expect(SetupProgressView.formatBytePair(500, 800), ('500', '800 B'));
      expect(SetupProgressView.formatBytePair(40, 20), ('20', '20 B'));
    });

    // KitChecklist rows (TEST-19): the detail is the supporting line under
    // the title, from the start side, in both directions.
    for (final rtl in [false, true]) {
      testWidgets('the detail sits under the title (${rtl ? 'RTL' : 'LTR'})', (
        tester,
      ) async {
        await _pumpView(tester, _job(current: _nodeDownloading), rtl: rtl);
        final title = tester.getRect(find.text('Node.js'));
        final detail = tester.getRect(find.text('Downloading · 18 of 30 MB'));
        expect(detail.top, greaterThanOrEqualTo(title.bottom));
        if (rtl) {
          expect(detail.right, closeTo(title.right, 1));
        } else {
          expect(detail.left, closeTo(title.left, 1));
        }
      });
    }

    // KitChecklist (A11Y-3, TEST-19): each row is one node read as "Step n
    // of N, title, state, detail"; the job's summary is the live region,
    // not the rows, so a byte tick is never announced.
    testWidgets('each row is one node; the job summary is the live region', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pumpView(tester, _job(current: _nodeDownloading));
      expect(
        tester.getSemantics(
          find.byKey(const ValueKey('setup-progress-row-node')),
        ),
        isSemantics(
          label: 'Step 4 of 5, Node.js, Working, Downloading · 18 of 30 MB',
          isLiveRegion: false,
        ),
      );
      expect(
        tester.getSemantics(
          find.byKey(const ValueKey('setup-progress-row-linux')),
        ),
        isSemantics(label: 'Step 1 of 5, Linux base, Waiting'),
      );
      expect(
        tester.getSemantics(
          find.byKey(const ValueKey('kit-checklist-summary')),
        ),
        isSemantics(label: 'Step 4 of 5, Node.js, Working', isLiveRegion: true),
      );
      semantics.dispose();
    });
  });

  group('layout', () {
    for (final rtl in [false, true]) {
      testWidgets('320dp at 2.5x text fits (${rtl ? 'RTL' : 'LTR'})', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(320, 640);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final h = await _pump(
          tester,
          textScale: 2.5,
          rtl: rtl,
          initial: _job(
            eta: 200,
            done: {'linux', 'essentials'},
            current: _nodeDownloading,
          ),
          transport: NetworkTransport.wifi,
        );
        expect(tester.takeException(), isNull);
        final pause = find.byKey(const Key('setup-progress-cancel'));
        expect(tester.getSize(pause).height, greaterThanOrEqualTo(48));
        final details = find.byKey(const Key('setup-progress-details'));
        await tester.ensureVisible(details);
        await _settle(tester);
        expect(tester.getSize(details).height, greaterThanOrEqualTo(48));

        h.engine.emit(
          _job(
            state: SetupState.failed,
            done: {'linux', 'essentials', 'python'},
            current: const ComponentProgress(
              id: 'node',
              state: ComponentState.failed,
              stage: 'Downloading Node.js 24',
              error: 'The download stopped halfway through',
            ),
            log: 'curl: (56) Recv failure\n',
          ),
        );
        await _settle(tester);
        await _openDetails(tester);
        expect(tester.takeException(), isNull);
        await tester.tapAt(const Offset(10, 10));
        await _settle(tester);
        expect(
          tester
              .getSize(find.byKey(const Key('setup-progress-continue')))
              .height,
          greaterThanOrEqualTo(48),
        );
      });
    }

    testWidgets('Arabic at 2.5x on 320dp', (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await _pump(
        tester,
        textScale: 2.5,
        locale: const Locale('ar'),
        initial: _job(eta: 200, done: {'linux'}, current: _nodeDownloading),
      );
      expect(tester.takeException(), isNull);
      expect(find.text('جارٍ تنزيل Node.js'), findsOneWidget);
      // The figures keep their order inside the right-to-left line.
      expect(
        find.text('${KitBidi.ltr('18')} من ${KitBidi.ltr('30 MB')}'),
        findsOneWidget,
      );
    });
  });

  testWidgets('reduce motion changes instantly and settles', (tester) async {
    final h = await _pump(
      tester,
      reduceMotion: true,
      initial: _job(current: _nodeDownloading),
    );
    expect(find.byType(CircularProgressIndicator), findsNothing);
    h.engine.emit(
      _job(
        current: const ComponentProgress(
          id: 'node',
          state: ComponentState.running,
          stage: 'Downloading',
          bytesDone: 27000000,
          bytesTotal: 30000000,
        ),
      ),
    );
    await tester.pump();
    expect(_ring(tester).fraction, closeTo(.9, 1e-9));
    h.engine.emit(
      _job(
        done: {'linux', 'essentials', 'python', 'node'},
        current: const ComponentProgress(
          id: 'opencode',
          state: ComponentState.running,
          stage: 'Installing OpenCode',
        ),
      ),
    );
    // Everything settles: no looping animation is left running.
    await tester.pumpAndSettle();
    expect(find.text('Installing OpenCode'), findsOneWidget);
  });

  testWidgets('the ring is one node; the sentence is the live region', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await _pump(tester, initial: _job(current: _nodeDownloading));
    expect(
      tester.getSemantics(find.byKey(const Key('setup-progress-overall'))),
      isSemantics(label: 'Setup progress', value: '60%, $_bytes'),
    );
    expect(
      find.byWidgetPredicate(
        (w) => w is Semantics && (w.properties.liveRegion ?? false),
      ),
      findsWidgets,
    );
    semantics.dispose();
  });
}
