// slice-qa-phone-setup (emulator QA 2026-09-28): B6 — while the page is the
// phone's own setup, a saved server's "not answering" line stays on screen
// as one line (the words and More), its Reconnect folded into More, instead
// of a two-row banner with "Reconnect to 127.0.0.1" on top of every step.
// B2's thresholds are in test/setup_preflight_test.dart and its start page
// copy in test/phone_setup_start_screen_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import '../support/phone_setup_scenes.dart' show sceneJob, sceneNodeDownloading;
import 'screen_phone_1_fixtures.dart';
import 'slice_qa_phone_setup_fixtures.dart';

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (_) async => null,
    );
    addTearDown(
      () => messenger.setMockMethodCallHandler(
        const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
        null,
      ),
    );
  });

  final pages = <String, (Widget Function(), SetupProgress?)>{
    'start': (startOnPhone, null),
    'progress': (
      progressOnPhone,
      sceneJob(eta: 300, done: {'linux'}, current: sceneNodeDownloading),
    ),
  };

  for (final MapEntry(key: name, value: (page, progress)) in pages.entries) {
    testWidgets('B6 $name: the other server\'s line is one line, Reconnect '
        'behind More', (tester) async {
      var reconnects = 0;
      try {
        await pumpPhone(
          tester,
          home: underAppConditions([
            otherServerNotAnswering(onReconnect: () => reconnects++),
          ], page()),
          progress: progress,
        );
        await _settle(tester);

        // Still told, once.
        expect(find.text("127.0.0.1 isn't answering"), findsOneWidget);
        expect(find.byType(KitStatusLine), findsOneWidget);
        // No "Reconnect to 127.0.0.1" link sitting on the setup step.
        expect(
          find.byKey(const ValueKey('connection-banner-retry')),
          findsNothing,
        );
        expect(find.text('Reconnect to 127.0.0.1'), findsNothing);
        // The line is one row: as tall as its More button, no second row.
        final line = tester.getSize(find.byType(KitStatusLine));
        expect(line.height, lessThanOrEqualTo(64));

        // The way out is one tap away, first in More.
        await tester.tap(find.byKey(const ValueKey('kit-status-more')));
        await _settle(tester);
        expect(find.text('Reconnect to 127.0.0.1'), findsOneWidget);
        expect(find.text('Switch server'), findsOneWidget);
        await tester.tap(find.text('Reconnect to 127.0.0.1'));
        await _settle(tester);
        expect(reconnects, 1);
      } finally {
        await unmountPhone(tester);
      }
    });
  }

  testWidgets('B6: with no other server in trouble, no line at all', (
    tester,
  ) async {
    try {
      await pumpPhone(tester, home: underAppConditions([], startOnPhone()));
      await _settle(tester);
      expect(find.byType(KitStatusLine), findsNothing);
    } finally {
      await unmountPhone(tester);
    }
  });

  testWidgets('B2 + B6 together: a 1,972 MB phone shows the slow note and '
      'the one-line status, and Set up stays available', (tester) async {
    try {
      await pumpPhone(
        tester,
        home: underAppConditions([
          otherServerNotAnswering(),
        ], startOnPhone(memoryMb: 1972)),
      );
      await _settle(tester);
      expect(
        find.text(
          'It may be slow on this phone, which has 1,972 MB of memory.',
        ),
        findsOneWidget,
      );
      expect(find.text("127.0.0.1 isn't answering"), findsOneWidget);
      final primary = tester.widget<FilledButton>(
        find.descendant(
          of: find.byKey(const ValueKey('phone-setup-start-primary')),
          matching: find.byType(FilledButton),
        ),
      );
      expect(primary.onPressed, isNotNull);
    } finally {
      await unmountPhone(tester);
    }
  });
}
