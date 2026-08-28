import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('diagnostics redact secrets, URLs, and long token-like values', () {
    final diagnostics = AppDiagnosticsController();
    addTearDown(diagnostics.dispose);
    const bearer = 'abcdefghijklmnopqrstuvwxyz0123456789TOKEN';
    const apiKey = 'super-secret-api-key-value-123456789';

    diagnostics.record(
      StateError(
        'Authorization: Bearer $bearer\n'
        'password=hunter2 api_key="$apiKey" '
        'https://user:pass@example.com/private?q=chat&token=hidden#frag',
      ),
      StackTrace.fromString('at safe_function (lib/main.dart:10:2)'),
      source: 'flutter',
    );

    final report = diagnostics.reportText();
    expect(report, isNot(contains(bearer)));
    expect(report, isNot(contains(apiKey)));
    expect(report, isNot(contains('hunter2')));
    expect(report, isNot(contains('user:pass')));
    expect(report, isNot(contains('q=chat')));
    expect(report, contains('example.com/private'));
    expect(report, contains('safe_function'));
  });

  test('diagnostics coalesce bursts and keep a bounded ring', () {
    final diagnostics = AppDiagnosticsController(maxEntries: 3);
    addTearDown(diagnostics.dispose);
    final start = DateTime.utc(2026, 8, 28, 10);

    diagnostics.record(StateError('same'), null, source: 'flutter', at: start);
    diagnostics.record(
      StateError('same'),
      null,
      source: 'flutter',
      at: start.add(const Duration(seconds: 1)),
    );
    expect(diagnostics.entries.single.occurrences, 2);

    for (var index = 0; index < 4; index++) {
      diagnostics.record(
        StateError('error-$index'),
        null,
        source: 'platform',
        at: start.add(Duration(minutes: index + 1)),
      );
    }
    expect(diagnostics.count, 3);
    expect(diagnostics.entries.map((entry) => entry.message), [
      contains('error-1'),
      contains('error-2'),
      contains('error-3'),
    ]);
  });

  testWidgets('error widget hides raw exceptions and records a report', (
    tester,
  ) async {
    final diagnostics = AppDiagnosticsController();
    addTearDown(diagnostics.dispose);
    final originalFlutter = FlutterError.onError;
    FlutterError.onError = (_) {};
    final capture = installAppErrorCapture(diagnostics);
    addTearDown(() {
      capture.restore();
      FlutterError.onError = originalFlutter;
    });

    await tester.pumpWidget(
      ErrorWidget.builder(
        FlutterErrorDetails(exception: StateError('private widget detail')),
      ),
    );

    expect(find.textContaining('This part of OpenCode hit an error'), findsOne);
    expect(find.textContaining('private widget detail'), findsNothing);
    expect(diagnostics.reportText(), contains('private widget detail'));
  });

  testWidgets('bootstrap failure is visible and can be retried', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final diagnostics = AppDiagnosticsController();
    addTearDown(diagnostics.dispose);
    var attempts = 0;

    Future<AppBootstrap> loader() async {
      attempts++;
      if (attempts == 1) throw StateError('preferences unavailable');
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      await store.load();
      return AppBootstrap(store);
    }

    await tester.pumpWidget(
      AppBootstrapGate(diagnostics: diagnostics, loader: loader),
    );
    await tester.pumpAndSettle();

    expect(find.text('OpenCode could not start'), findsOneWidget);
    expect(find.textContaining('preferences unavailable'), findsOneWidget);
    expect(diagnostics.count, 1);

    await tester.tap(find.byKey(const Key('retry-app-bootstrap')));
    await tester.pumpAndSettle();

    expect(attempts, 2);
    expect(find.byType(ServersScreen), findsOneWidget);
  });
}
