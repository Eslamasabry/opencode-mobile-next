// Renders for slice P1.3-P1.5 (docs/qa/slice-P1.3-P1.5-2026-09-27/): This
// phone for OpenCode inside the app and for OpenCode in Termux, phone setup's
// start screen with its ways in, and the Termux host's checklist. 412x915
// dp, dark, real fonts.
//
//   flutter test -j 1 tool/capture/slice_p1_3_p1_5_test.dart
//
// before-builtin-server-setup.png was rendered from the old
// BuiltinServerScreen before it was deleted (commit parent of this slice).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_termux_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import '../../test/revamp/screen_phone_1_fixtures.dart';
import '../../test/support/termux_channel_fixture.dart';
import 'fixtures.dart';

const _out = 'docs/qa/slice-P1.3-P1.5-2026-09-27';

Future<void> _settle(WidgetTester tester, {int frames = 15}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _shot(WidgetTester tester, GlobalKey boundary, String name) async {
  expect(tester.takeException(), isNull);
  await writePng(
    '$_out/$name.png',
    await capturePng(tester, boundary, pixelRatio: 1),
  );
}

void main() {
  setUpAll(loadCaptureFonts);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('after: This phone, in the app', (tester) async {
    final boundary = GlobalKey();
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
      optionalInstalled: {'python'},
      boundary: boundary,
    );
    await _settle(tester);
    await _shot(tester, boundary, 'after-this-phone-inapp');
    await tester.drag(find.byType(Scrollable).first, const Offset(0, -600));
    await _settle(tester);
    await _shot(tester, boundary, 'after-this-phone-inapp-scrolled');
    await unmountPhone(tester);
  });

  testWidgets('after: This phone, in Termux', (tester) async {
    TermuxChannelFixture()
      ..inventoryOutput = 'ubuntu=installed\nversion=2.1.1\nruntime=opencode2\n'
      ..statusOutput = termuxSnapshot(phase: 'ready', version: '2.1.1')
      ..install();
    final boundary = GlobalKey();
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.termux),
      profiles: [
        ServerProfile(
          id: 'termux',
          name: 'This phone',
          baseUrl: TermuxBridge.managedServerUrl,
          password: 'synthetic-test-secret',
          serverVersion: '2.1.1',
        ),
      ],
      boundary: boundary,
    );
    await _settle(tester);
    await _shot(tester, boundary, 'after-this-phone-termux');
    await unmountPhone(tester);
  });

  testWidgets('after: phone setup start', (tester) async {
    final boundary = GlobalKey();
    await pumpPhone(tester, home: startScreen(), boundary: boundary);
    await _settle(tester);
    await _shot(tester, boundary, 'after-phone-setup-start');
    final otherWays = find.byKey(
      const ValueKey('phone-setup-start-other-ways'),
    );
    await tester.ensureVisible(otherWays);
    await tester.tap(otherWays);
    await _settle(tester);
    await tester.ensureVisible(
      find.byKey(const ValueKey('phone-setup-start-use-termux')),
    );
    await _settle(tester);
    await _shot(tester, boundary, 'after-phone-setup-start-other-ways');
    await unmountPhone(tester);
  });

  testWidgets('after: phone setup on the Termux host', (tester) async {
    TermuxChannelFixture()
      ..permissionGranted = false
      ..install();
    final boundary = GlobalKey();
    await pumpPhone(
      tester,
      home: PhoneSetupTermuxScreen(firstSetup: true, openLink: (_, _) async {}),
      boundary: boundary,
    );
    await _settle(tester);
    await _shot(tester, boundary, 'after-phone-setup-termux');
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 2));
    await unmountPhone(tester);
  });
}
