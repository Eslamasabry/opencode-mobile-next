// Work tab cleanup (docs/design/work-tab-cleanup-2026-09-24.md), item 10:
// the connecting card that survives the Work tab's removal. The Work-tab
// tests of this file went with the screen.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';

void _viewport(WidgetTester tester, {Size size = const Size(412, 915)}) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Widget _app(Widget home, {double scale = 1}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
    child: child!,
  ),
  onGenerateRoute: (settings) => MaterialPageRoute<void>(
    settings: settings,
    builder: (_) => Scaffold(body: Text('route ${settings.name}')),
  ),
  home: home,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('item 10: a server that does not answer', () {
    testWidgets('the connecting card says so after 8 s and offers the ways '
        'out', (tester) async {
      _viewport(tester);
      var restarts = 0;
      var retries = 0;
      var changes = 0;
      Widget card({required bool notAnswering}) => _app(
        Scaffold(
          body: SavedServerConnectionCard(
            profileName: 'This device (Termux)',
            baseUrl: 'http://127.0.0.1:4096',
            error: null,
            attempts: 1,
            supportsTermux: true,
            notAnswering: notAnswering,
            onChangeServer: () => changes++,
            onRetry: () => retries++,
            onStartPhoneServer: () => restarts++,
          ),
        ),
      );
      // slice-P4.4: the 8 s are the controller's (connectionStatus), shared
      // with every status line; the card itself never escalates.
      await tester.pumpWidget(card(notAnswering: false));
      await tester.pump(const Duration(seconds: 9));
      expect(find.text("OpenCode on this phone isn't answering"), findsNothing);
      await tester.pumpWidget(card(notAnswering: true));
      await tester.pump(const Duration(milliseconds: 300));
      expect(
        find.text("OpenCode on this phone isn't answering"),
        findsOneWidget,
      );
      expect(
        find.text('The app keeps trying in the background.'),
        findsOneWidget,
      );

      await tester.tap(find.text('Try again'));
      expect(retries, 1);
      await tester.tap(
        find.byKey(const ValueKey('saved-server-choose-another')),
      );
      expect(changes, 1);
      await tester.tap(find.text('Restart'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      // A confirm first: a turn in progress stops.
      expect(find.textContaining('A running agent turn will stop'), findsOne);
      expect(restarts, 0);
      await tester.tap(
        find.byKey(const ValueKey('work-server-restart-confirm')),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(restarts, 1);
    });
  });
}
