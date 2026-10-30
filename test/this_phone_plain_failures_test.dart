// slice-close-servers (review-board closure, 2026-09-28): This phone says
// what went wrong in plain words and offers the act that fixes it. A failed
// install is installed again, a failed start is started again, a half-way
// switch says the conversations are kept, the manager's own text waits under
// Details, and the Update question speaks without engine words. An install
// that is already the pinned version says "Up to date".
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_termux_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import 'revamp/screen_phone_1_fixtures.dart';
import 'support/termux_channel_fixture.dart';

class _OwnershipUnknownLinux extends PhoneLinux {
  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    starts++;
    throw const BuiltinLinuxException(
      'The app cannot safely identify previous work.',
      code: 'runtime_ownership_unknown',
      details: 'IllegalStateException: ownershipUnknown',
    );
  }
}

final _termuxProfile = ServerProfile(
  id: 'termux',
  name: 'This phone',
  baseUrl: TermuxBridge.managedServerUrl,
  password: 'synthetic-test-secret',
  serverVersion: '2.1.1',
);

Future<void> _settle(WidgetTester tester, {int frames = 10}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

String _text(WidgetTester tester, String key) => tester
    .widget<RichText>(
      find
          .descendant(
            of: find.byKey(ValueKey(key)),
            matching: find.byType(RichText),
            matchRoot: true,
          )
          .first,
    )
    .text
    .toPlainText();

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  Future<TermuxChannelFixture> termux(
    WidgetTester tester, {
    required String status,
    String version = '2.1.1',
  }) async {
    final channel = TermuxChannelFixture()
      ..inventoryOutput =
          'ubuntu=installed\nversion=$version\nruntime=opencode2\n'
      ..statusOutput = status;
    channel.install();
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.termux),
      profiles: [_termuxProfile],
    );
    await _settle(tester);
    return channel;
  }

  Future<void> done(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 2));
    await unmountPhone(tester);
  }

  testWidgets('ownership failure offers settings instead of another start', (
    tester,
  ) async {
    final linux = _OwnershipUnknownLinux();
    final profile = ServerProfile(
      id: 'builtin',
      name: 'This phone',
      baseUrl: BuiltinLinux.serverUrl,
      flavor: ServerFlavor.v2,
      password: 'synthetic-test-secret',
    );
    var settings = 0;
    const channel = MethodChannel('oc/termux');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'openAppSettings') settings++;
      return true;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
      profiles: [profile],
      linux: linux,
    );
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('this-phone-start')));
    await _settle(tester);
    expect(
      find.text(AppLocalizationsEn().inAppServerOwnershipRecoveryBody),
      findsOneWidget,
    );
    expect(find.textContaining('ownershipUnknown'), findsNothing);
    expect(find.byKey(const ValueKey('this-phone-start')), findsNothing);
    expect(find.byKey(const ValueKey('this-phone-reinstall')), findsNothing);
    await tester.tap(
      find.byKey(const ValueKey('this-phone-recover-ownership')),
    );
    await _settle(tester);
    expect(settings, 1);
    expect(linux.starts, 1);
    await done(tester);
  });

  group('the manager\'s failure, classified', () {
    test('a crash or a server that never answered is a start', () {
      expect(
        termuxProblemOf('crash', 'OpenCode server exited (code 1)'),
        PhoneHostProblem.start,
      );
      expect(
        termuxProblemOf(
          '',
          'OpenCode server did not become authenticated and ready within 30 '
              'seconds',
        ),
        PhoneHostProblem.start,
      );
      expect(
        termuxProblemOf(
          '',
          'The local server port is still in use; no replacement was started',
        ),
        PhoneHostProblem.start,
      );
    });

    test('anything else the manager failed at is an install', () {
      expect(
        termuxProblemOf('', 'Could not install the Termux dependencies'),
        PhoneHostProblem.install,
      );
      expect(
        termuxProblemOf('', 'OpenCode installed but did not report a version'),
        PhoneHostProblem.install,
      );
    });
  });

  testWidgets('a failed start says so in plain words, offers Start again, and '
      'keeps the manager\'s text under Details', (tester) async {
    await termux(
      tester,
      status: termuxSnapshot(
        phase: 'failed',
        message: 'OpenCode server exited (code 137)',
        version: '2.1.1',
        extra: 'failure_kind=crash\n',
      ),
    );
    // One plain answer, the way forward under it; the row stays a fact.
    expect(_text(tester, 'this-phone-state'), "The server didn't start");
    expect(
      _text(tester, 'this-phone-failure'),
      'Start it again; Details below says what went wrong.',
    );
    expect(_text(tester, 'this-phone-detail'), isNot(contains('Needs you')));
    expect(find.text('Start again'), findsOneWidget);
    expect(find.byKey(const ValueKey('this-phone-reinstall')), findsNothing);
    // The raw text is not copy: only Details holds it.
    expect(find.textContaining('code 137', findRichText: true), findsNothing);
    final details = find.byKey(const ValueKey('this-phone-details'));
    await tester.ensureVisible(details);
    await _settle(tester);
    await tester.tap(details);
    await _settle(tester);
    expect(
      find.byKey(const ValueKey('this-phone-failure-details')),
      findsOneWidget,
    );
    expect(find.textContaining('code 137', findRichText: true), findsOneWidget);
    await done(tester);
  });

  testWidgets('a failed install offers Install again, which runs the update '
      'job, and Start beside it', (tester) async {
    final channel = await termux(
      tester,
      status: termuxSnapshot(
        phase: 'failed',
        message: 'Could not install the Termux dependencies',
        version: '2.1.1',
      ),
    );
    expect(
      _text(tester, 'this-phone-failure'),
      "Installing OpenCode 2 didn't finish. Install it again; your "
      'conversations are kept.',
    );
    expect(
      find.textContaining('Termux dependencies', findRichText: true),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('this-phone-start')), findsOneWidget);
    expect(find.text('Start again'), findsNothing);
    // Install again is the update job: it is not offered a second time.
    expect(find.byKey(const ValueKey('this-phone-update')), findsNothing);
    channel.statusOutput = null;
    await tester.tap(find.byKey(const ValueKey('this-phone-reinstall')));
    await _settle(tester, frames: 4);
    expect(find.byType(PhoneSetupTermuxScreen), findsOneWidget);
    await done(tester);
  });

  testWidgets('a switch stopped half way says the conversations are kept', (
    tester,
  ) async {
    await termux(
      tester,
      version: TermuxRuntime.openCode2.pinnedVersion,
      status: termuxSnapshot(
        phase: 'failed',
        message: 'OpenCode server exited (code 1)',
        version: TermuxRuntime.openCode2.pinnedVersion,
        runtime: TermuxRuntime.openCode2,
        extra:
            'failure_kind=crash\nswitch_previous=opencode1\n'
            'switch_target=opencode2\nswitch_phase=starting\n'
            'switch_return=opencode1\n',
      ),
    );
    expect(
      _text(tester, 'this-phone-failure'),
      "OpenCode 2 didn't start after the switch. Your conversations are "
      'kept.',
    );
    expect(
      find.byKey(const ValueKey('this-phone-switch-retry')),
      findsOneWidget,
    );
    await done(tester);
  });

  testWidgets('the Update question has no engine words', (tester) async {
    await termux(
      tester,
      version: '2.0.20',
      status: termuxSnapshot(phase: 'ready', version: '2.0.20'),
    );
    await tester.ensureVisible(find.byKey(const ValueKey('this-phone-update')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('this-phone-update')));
    await _settle(tester);
    expect(find.text('Update OpenCode 2?'), findsOneWidget);
    expect(find.textContaining('managed'), findsNothing);
    expect(find.textContaining('generation'), findsNothing);
    expect(find.textContaining('Your conversations are kept.'), findsOneWidget);
    await done(tester);
  });

  testWidgets('an install already at the pinned version says Up to date, '
      'with no Update', (tester) async {
    final pinned = TermuxRuntime.openCode2.pinnedVersion;
    await termux(
      tester,
      version: pinned,
      status: termuxSnapshot(phase: 'ready', version: pinned),
    );
    expect(find.byKey(const ValueKey('this-phone-update')), findsNothing);
    expect(find.text('Up to date · $pinned'), findsOneWidget);
    await done(tester);
  });
}
