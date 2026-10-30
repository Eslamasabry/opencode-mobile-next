import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/in_app_auto_update.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';

import 'support/fake_setup_engine.dart';

class _Host extends PhoneHost {
  @override
  PhoneHostKind get kind => PhoneHostKind.inApp;
  @override
  PhoneHostState state = PhoneHostState.running;
  @override
  TermuxRuntime runtime = TermuxRuntime.openCode2;
  @override
  String? version;
  @override
  PhoneHostProblem? problem;
  @override
  TermuxRuntime? switchTarget;
  @override
  int? get bytesUsed => null;
  @override
  String? get failure => null;
  @override
  ServerProfile? get profile => null;
  @override
  Future<void> refresh() async {}
  @override
  Future<void> start(AppLocalizations l10n) async {}
  @override
  Future<void> stop(AppLocalizations l10n) async {}
  @override
  Future<String> readLog() async => '';
  void changed() => notifyListeners();
}

Future<void> _settle() => Future<void>.delayed(Duration.zero);

void main() {
  const pinned = '2.1.1';

  bool decide({
    String? installed = '2.0.29',
    bool busy = false,
    bool engineRunning = false,
    bool switchPending = false,
    bool installProblem = false,
    bool attempted = false,
  }) => shouldAutoUpdateInApp(
    installedVersion: installed,
    pinnedVersion: pinned,
    busy: busy,
    engineRunning: engineRunning,
    switchPending: switchPending,
    installProblem: installProblem,
    attemptedThisRun: attempted,
  );

  group('shouldAutoUpdateInApp', () {
    test('up to date does nothing, with or without a leading v', () {
      expect(decide(installed: '2.1.1'), isFalse);
      expect(decide(installed: 'v2.1.1'), isFalse);
    });
    test('an unknown version does nothing', () {
      expect(decide(installed: null), isFalse);
    });
    test('behind and idle updates', () {
      expect(decide(), isTrue);
    });
    test('waits while work is running', () {
      expect(decide(busy: true), isFalse);
    });
    test('does nothing while a job runs', () {
      expect(decide(engineRunning: true), isFalse);
    });
    test('does nothing on a half-finished switch', () {
      expect(decide(switchPending: true), isFalse);
    });
    test('does nothing after a failed install', () {
      expect(decide(installProblem: true), isFalse);
    });
    test('tries once per run', () {
      expect(decide(attempted: true), isFalse);
    });
  });

  group('InAppAutoUpdater', () {
    late _Host host;
    late FakeSetupEngine engine;
    late _Work work;
    late bool busy;
    late InAppAutoUpdater updater;

    void start() {
      updater = InAppAutoUpdater(
        host: host,
        engine: engine,
        workChanges: work,
        workBusy: () => busy,
      );
    }

    setUp(() {
      host = _Host()..version = '2.0.29';
      engine = FakeSetupEngine();
      work = _Work();
      busy = false;
    });

    tearDown(() {
      updater.dispose();
    });

    test(
      'asks the engine for the current runtime when behind and idle',
      () async {
        start();
        await _settle();
        expect(engine.runs, [
          {'opencode'},
        ]);
        expect(engine.runParams.single, {
          'opencode': {'runtime': TermuxRuntime.openCode2.wireName},
        });
      },
    );

    test('waits while busy, then runs once when work ends', () async {
      busy = true;
      start();
      await _settle();
      expect(engine.runs, isEmpty);
      busy = false;
      work.changed();
      await _settle();
      expect(engine.runs, hasLength(1));
      work.changed();
      host.changed();
      await _settle();
      expect(engine.runs, hasLength(1));
    });

    test('does not run when already on the pinned version', () async {
      host.version = 'v$pinned';
      start();
      await _settle();
      expect(engine.runs, isEmpty);
    });

    test('does not retry after a failed attempt', () async {
      start();
      await _settle();
      engine.emit(
        const SetupProgress(
          state: SetupState.failed,
          components: [],
          overall: 0,
        ),
      );
      host.changed();
      await _settle();
      expect(engine.runs, hasLength(1));
    });
  });
}

/// Phone work that ends or starts, as the app reports it.
class _Work extends ChangeNotifier {
  void changed() => notifyListeners();
}
