import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

/// The composer's "+" (plus-menu concept C, owner 20 Oct 2026) opens its
/// tray in place: Photos, Camera, File and More. This taps "+" and lets the
/// tray unfold; a server that takes no files has no tray, and "+" opens the
/// tools sheet straight away.
Future<void> openComposerTray(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('composer-tools-button')));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

/// The tools sheet, as a person reaches it: "+", then the tray's More (or
/// "+" alone where there is no tray). The caller pumps the sheet open, as
/// it did after a tap on "+".
Future<void> openComposerTools(WidgetTester tester) async {
  await openComposerTray(tester);
  final more = find.byKey(const Key('composer-tray-more'));
  if (more.evaluate().isNotEmpty) await tester.tap(more);
}
