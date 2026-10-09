// The conversation menu's hierarchy (slice-P10.2): one KitMenu with a
// "Go to" group then a "Do" group, every entry reachable at a narrow phone
// with very large text. The display toggles, retry, undo, shell, export and
// commands left the menu for the command sheet and Settings.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/session_menu.dart';

import '../tool/capture/fixtures.dart'
    show capturePng, captureTheme, loadCaptureFonts;

const _destinations = ['Changes', 'Timeline', 'Find', 'Subagents', 'Details'];

const _acts = [
  'Share conversation',
  'Compact context',
  'Fork conversation',
  'Rename conversation',
  'Continue on computer',
  'Open on another phone',
  'Archive conversation',
  'Delete conversation',
];

Future<void> _pumpMenu(
  WidgetTester tester, {
  double textScale = 1,
  GlobalKey? boundary,
  List<SessionMenuAction>? picked,
}) async {
  Widget app = MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: captureTheme(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: child!,
    ),
    home: Scaffold(
      body: Align(
        alignment: AlignmentDirectional.topEnd,
        child: Builder(
          builder: (context) => TextButton(
            key: const ValueKey('open-menu'),
            onPressed: () => showKitMenu(
              context,
              items: sessionMenuItems(
                AppLocalizations.of(context),
                SessionMenuOffer.of(
                  ServerCapabilities.allV1,
                  shared: false,
                  savedServer: true,
                ),
                onSelected: (action) => picked?.add(action),
              ),
            ),
            child: const Text('Menu'),
          ),
        ),
      ),
    ),
  );
  if (boundary != null) app = RepaintBoundary(key: boundary, child: app);
  await tester.pumpWidget(app);
  await tester.tap(find.byKey(const ValueKey('open-menu')));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('Go to leads, Do follows, and nothing else is in the menu', (
    tester,
  ) async {
    final picked = <SessionMenuAction>[];
    await _pumpMenu(tester, picked: picked);
    final goTo = tester.getTopLeft(find.text('Go to')).dy;
    final act = tester.getTopLeft(find.text('Do').first).dy;
    for (final title in _destinations) {
      final y = tester.getTopLeft(find.text(title)).dy;
      expect(y, greaterThan(goTo), reason: title);
      expect(y, lessThan(act), reason: title);
    }
    for (final title in _acts) {
      expect(
        tester.getTopLeft(find.text(title)).dy,
        greaterThan(act),
        reason: title,
      );
    }
    for (final gone in [
      'Display and context',
      'Retry last prompt',
      'Run shell command',
      'Commands',
      'Export this conversation',
      'Results',
    ]) {
      expect(find.text(gone), findsNothing, reason: gone);
    }
    await tester.tap(find.text('Fork conversation'));
    await tester.pumpAndSettle();
    expect(picked, [SessionMenuAction.fork]);
    expect(tester.takeException(), isNull);
  });

  testWidgets('320dp and 2.5x: every entry stays reachable', (tester) async {
    tester.view.physicalSize = const Size(320, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final captureDir = Platform.environment['E7_CHAT_CAPTURE_DIR'];
    if (captureDir != null) await loadCaptureFonts();
    final boundary = GlobalKey();
    await _pumpMenu(tester, textScale: 2.5, boundary: boundary);
    expect(tester.takeException(), isNull);
    if (captureDir != null) {
      final bytes = await capturePng(tester, boundary);
      File('$captureDir/menu-ltr-320-2.5x.png').writeAsBytesSync(bytes);
    }
    for (final title in [..._destinations, ..._acts]) {
      await tester.ensureVisible(find.text(title));
      await tester.pumpAndSettle();
      expect(find.text(title).hitTestable(), findsOneWidget, reason: title);
    }
    expect(find.byType(KitMenuPanel), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
