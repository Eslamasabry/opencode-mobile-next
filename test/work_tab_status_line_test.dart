// The Work tab's new parts (work-tab cleanup and design standard,
// 2026-09-24): the one status line and its priorities, the kit's loading bar,
// skeletons and button block. Old-code comparisons are in work_tab_cleanup_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';

import 'support/work_tab_fixture.dart';

Widget _app(Widget home) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: home),
);

Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('one status line, most urgent first', () {
    Future<WorkController> pumpWork(
      WidgetTester tester, {
      StreamStatus status = StreamStatus.connected,
      String? error,
      bool phone = true,
      Future<void> Function()? restart,
    }) async {
      final controller =
          await workController(
              status: status,
              sessions: workLoadedSessions(),
              name: phone ? 'This device (Termux)' : 'Laptop',
              baseUrl: phone
                  ? 'http://127.0.0.1:4096'
                  : 'http://100.64.0.7:4096',
            )
            ..lastError = error;
      controller.notifyListeners();
      await tester.pumpWidget(
        _app(
          ListenableBuilder(
            listenable: controller,
            builder: (context, _) => AppConditionsScope(
              conditions: [
                connectionKitStatus(
                  context,
                  controller,
                  serverOnThisPhone: phone,
                  onRestartServer: restart,
                ),
              ],
              child: KitScreen(body: const SizedBox.expand()),
            ),
          ),
        ),
      );
      await _settle(tester);
      return controller;
    }

    Finder line(String id) => find.byKey(
      ValueKey(id == 'server' ? 'connection-status-banner' : 'work-status-$id'),
    );

    testWidgets('a normal start says connecting in the shared slot', (
      tester,
    ) async {
      final controller = await pumpWork(
        tester,
        status: StreamStatus.connecting,
      );
      try {
        await tester.pump(const Duration(seconds: 7));
        expect(line('server'), findsOneWidget);
        expect(find.textContaining('Connecting to '), findsOneWidget);
        controller
          ..status = StreamStatus.connected
          ..notifyListeners();
        await tester.pump(const Duration(seconds: 5));
        await _settle(tester);
        expect(find.byType(KitStatusLine), findsNothing);
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });

    testWidgets('a failed attempt says so at once', (tester) async {
      final controller = await pumpWork(
        tester,
        status: StreamStatus.disconnected,
        error: 'Cannot reach http://127.0.0.1:4096: timed out',
      );
      try {
        expect(line('server'), findsOneWidget);
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });

    testWidgets('Restart asks first, and only then restarts', (tester) async {
      var restarts = 0;
      final controller = await pumpWork(
        tester,
        status: StreamStatus.reconnecting,
        restart: () async => restarts++,
      );
      try {
        await tester.pump(const Duration(seconds: 9));
        await _settle(tester);
        await tester.tap(
          find.byKey(const ValueKey('connection-banner-restart')),
        );
        await _settle(tester);
        expect(find.text('Restart OpenCode on this phone?'), findsOneWidget);
        await tester.tap(find.text('Cancel'));
        await _settle(tester);
        expect(restarts, 0);
        await tester.tap(
          find.byKey(const ValueKey('connection-banner-restart')),
        );
        await _settle(tester);
        await tester.tap(
          find.byKey(const ValueKey('work-server-restart-confirm')),
        );
        await _settle(tester);
        expect(restarts, 1);
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });

    testWidgets('a remote server is named, with Try again and Details and '
        'no Restart', (tester) async {
      final controller = await pumpWork(
        tester,
        status: StreamStatus.reconnecting,
        phone: false,
      );
      try {
        await tester.pump(const Duration(seconds: 9));
        await _settle(tester);
        expect(find.text("Laptop isn't answering"), findsOneWidget);
        expect(
          find.byKey(const ValueKey('connection-banner-retry')),
          findsOneWidget,
        );
        expect(
          find.byKey(const ValueKey('connection-banner-restart')),
          findsNothing,
        );
        await tester.tap(find.byKey(const ValueKey('kit-status-more')));
        await _settle(tester);
        expect(
          find.byKey(const ValueKey('connection-banner-details')),
          findsOneWidget,
        );
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });
  });

  group('kit', () {
    testWidgets('the loading bar is labelled; skeleton rows are not read', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        _app(
          const Column(
            children: [
              KitLoadingBar(loading: true, label: 'Loading'),
              KitSkeletonRows(),
            ],
          ),
        ),
      );
      expect(find.bySemanticsLabel('Loading'), findsOneWidget);
      expect(
        find.ancestor(
          of: find.byKey(const ValueKey('kit-skeleton-rows')),
          matching: find.byType(ExcludeSemantics),
        ),
        findsWidgets,
      );
      semantics.dispose();
    });

    testWidgets('the loading bar keeps its 2 dp when idle', (tester) async {
      await tester.pumpWidget(
        _app(const KitLoadingBar(loading: false, label: 'Loading')),
      );
      expect(tester.getSize(find.byType(KitLoadingBar)).height, 2);
      expect(find.byType(LinearProgressIndicator), findsNothing);
    });

    for (final (width, stacked) in [(412.0, true), (800.0, false)]) {
      testWidgets('actions at ${width.toInt()} dp: '
          '${stacked ? 'stacked, primary first' : 'one row, primary last'}', (
        tester,
      ) async {
        tester.view.physicalSize = Size(width, 900);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          _app(
            KitActionBlock(
              primary: KitAction(label: 'Start', onPressed: () {}),
              secondary: KitAction(label: 'Try again', onPressed: () {}),
              tertiary: [
                KitAction(label: 'Change server', onPressed: () {}),
                KitAction(label: 'Setup', onPressed: () {}),
                KitAction(label: 'Help', onPressed: () {}),
              ],
            ),
          ),
        );
        final start = tester.getRect(find.text('Start'));
        final retry = tester.getRect(find.text('Try again'));
        final change = tester.getRect(find.text('Change server'));
        // The third tertiary action goes behind More.
        expect(find.text('Help'), findsNothing);
        expect(find.byKey(const ValueKey('kit-actions-more')), findsOneWidget);
        if (stacked) {
          expect(start.top, lessThan(retry.top));
          expect(retry.top, lessThan(change.top));
          expect(
            tester.getSize(find.widgetWithText(FilledButton, 'Start')).width,
            width,
          );
        } else {
          expect(start.left, greaterThan(retry.left));
          expect(retry.left, greaterThan(change.left));
        }
      });
    }
  });
}
