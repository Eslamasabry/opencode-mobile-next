import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'server_editor.dart';

/// Answers the first-run welcome's question with "On my computer" and
/// presses its main button ("Connect to my computer"), which opens Add
/// server at its first step. A tap on a card only chooses; the button acts.
///
/// Large text pushes the card below the fold, so it is brought on screen
/// before it is tapped.
Future<void> chooseComputerOnWelcome(WidgetTester tester) async {
  final computer = find.byKey(const ValueKey('welcome-choice-computer'));
  // The welcome is a lazy list: at large text the card is not built until
  // it is scrolled near.
  await tester.scrollUntilVisible(
    computer,
    120,
    scrollable: find.byType(Scrollable).first,
  );
  await tester.ensureVisible(computer);
  await tester.pumpAndSettle();
  await tester.tap(computer);
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey('welcome-primary')));
  await tester.pumpAndSettle();
}

/// Walks the first-run computer path from the welcome to the connect step:
/// "On my computer", then what runs there (Add server's first step, P3.9).
/// [agent] is `opencode`, `paseo` or `codex`, the suffix of the choice's
/// key.
Future<void> openFirstRunConnect(
  WidgetTester tester, {
  String agent = 'opencode',
}) async {
  await chooseComputerOnWelcome(tester);
  await chooseServerKind(tester, kind: agent);
}
