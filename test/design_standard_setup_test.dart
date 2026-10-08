// Phone setup and the "This phone" card on the design standard
// (docs/design/design-standard.md, §9 step 3): what the standard changes in
// behaviour, beyond the goldens.
//
// - §2 a button is never a status display: while OpenCode starts, the start
//   screen and the card say so (a title or status word, and progress), with
//   no spinning or disabled button standing in for it;
// - §3 the title never contradicts the progress: a stopped setup is not
//   "Setting up…";
// - §4 one bar per screen: no second, per-row bar;
// - §2 buttons stack full width: Create sits under the name, not beside it.
//
// Each of these fails on the code before the migration (dca366f1).
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart'
    show BuiltinServerRestoreRecipe;
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_ready_screen.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';

import 'support/phone_setup_scenes.dart';

/// OpenCode inside the app, whose start is held until [gate] completes.
class _HeldLinux extends SceneLinux {
  _HeldLinux() : super(running: false);

  final gate = Completer<void>();

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    await gate.future;
    running = true;
  }
}

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

Future<void> _pump(WidgetTester tester, SetupScene scene) async {
  _mockSecureStorage(tester);
  await pumpSetupScene(tester, scene, boundary: GlobalKey());
}

/// Lets the held start fail fast and unmounts, so nothing is left pending.
Future<void> _release(WidgetTester tester, _HeldLinux linux) async {
  linux.running = false;
  linux.gate.complete();
  await tester.pumpWidget(const SizedBox.shrink());
  for (var i = 0; i < 5; i++) {
    await tester.pump(const Duration(seconds: 1));
  }
}

void main() {
  setUp(() {
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.failure('refused');
  });
  tearDown(() => serverProbe = probeServerConnection);

  testWidgets('start screen: Open says it is starting, with no button '
      'standing in for it (§2)', (tester) async {
    final linux = _HeldLinux();
    await _pump(
      tester,
      SetupScene(
        'open',
        home: () => PhoneSetupStartScreen(
          termuxProbe: () async => const TermuxRunningServer.absent(),
          inAppProbe: () async => true,
          openProgress: (_) async {},
        ),
        linux: linux,
        profiles: [scenePhoneProfile],
      ),
    );
    expect(find.text('OpenCode is ready on this phone'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('phone-setup-start-primary')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Starting OpenCode on this phone…'), findsOneWidget);
    expect(find.text('OpenCode is ready on this phone'), findsNothing);
    expect(find.byType(FilledButton), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    await _release(tester, linux);
  });

  testWidgets('"This phone": Start shows Starting, with no disabled Start '
      'and no extra bar (§2, §4)', (tester) async {
    final linux = _HeldLinux();
    await _pump(
      tester,
      SetupScene(
        'card',
        home: setupScenes
            .firstWhere((scene) => scene.name == 'phone_card_stopped')
            .home,
        linux: linux,
        profiles: [scenePhoneProfile],
      ),
    );
    await tester.tap(find.byKey(const ValueKey('phone-server-start')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(
      find.descendant(
        of: find.byKey(const ValueKey('phone-server-status')),
        matching: find.text('Starting'),
      ),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('phone-server-start')), findsNothing);
    expect(find.byType(FilledButton), findsNothing);
    expect(find.byType(LinearProgressIndicator), findsNothing);
    await _release(tester, linux);
  });

  for (final state in [SetupState.interrupted, SetupState.cancelled]) {
    testWidgets('progress: a ${state.name} setup is not titled "Setting up" '
        '(§3)', (tester) async {
      await _pump(
        tester,
        SetupScene(
          'stopped',
          home: setupScenes
              .firstWhere((scene) => scene.name == 'setup_progress_running')
              .home,
          push: true,
          progress: sceneJob(
            state: state,
            overall: .5,
            done: {'linux', 'essentials', 'python'},
          ),
        ),
      );
      expect(find.text('Setting up OpenCode on this phone'), findsNothing);
      expect(find.text("Setup didn't finish"), findsOneWidget);
      expect(find.text('Continue setup'), findsOneWidget);
    });
  }

  testWidgets('progress: a stage-only step draws no second bar (§4)', (
    tester,
  ) async {
    await _pump(
      tester,
      SetupScene(
        'stage',
        home: setupScenes
            .firstWhere((scene) => scene.name == 'setup_progress_running')
            .home,
        push: true,
        progress: sceneJob(
          done: {'linux', 'essentials', 'python', 'node'},
          current: const ComponentProgress(
            id: 'opencode',
            state: ComponentState.running,
            stage: 'Installing OpenCode',
          ),
        ),
      ),
    );
    expect(find.text('Installing OpenCode'), findsOneWidget);
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
  });

  testWidgets('ready: Create stacks full width under the name (§2)', (
    tester,
  ) async {
    await _pump(
      tester,
      SetupScene(
        'ready',
        home: () => PhoneSetupReadyScreen(linux: SceneLinux()),
      ),
    );
    final field = tester.getRect(
      find.byKey(const ValueKey('phone-setup-ready-name')),
    );
    final create = tester.getRect(
      find.byKey(const ValueKey('phone-setup-ready-create')),
    );
    expect(create.top, greaterThan(field.bottom));
    expect(create.width, closeTo(field.width, 1));
  });
}
