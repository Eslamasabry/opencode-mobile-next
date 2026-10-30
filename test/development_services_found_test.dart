import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/managed_shell.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/development_services_screen.dart';

import 'development_services_screen_test.dart' show connectionFor, seed;
import 'support/development_service_fakes.dart';
import 'support/fake_webview_platform.dart';

const _tcp =
    '   0: 0100007F:1435 00000000:0000 0A 00000000:00000000 00:00000000 '
    '00000000  1000        0 5001 1 0000000000000000 100 0 0 10 0';

/// What the phone's listing script prints for one dev server on 5173 whose
/// output goes to /work/shopfront/dev.log, plus one with no log file.
String _phone({bool vite = true, bool other = false}) =>
    '''
#T4
${vite ? _tcp : ''}
${other ? _tcp.replaceFirst('1435', '1F90').replaceFirst('5001', '5002') : ''}
#S
${vite ? 'lrwx------ 1 root root 64 Oct 16 10:00 /proc/41/fd/3 -> socket:[5001]' : ''}
${other ? 'lrwx------ 1 root root 64 Oct 16 10:00 /proc/42/fd/3 -> socket:[5002]' : ''}
#C
${vite ? '41\tnode /work/shopfront/node_modules/.bin/vite --port 5173 \t/work/shopfront\t/work/shopfront/dev.log' : ''}
${other ? '42\tpython3 -m http.server 8080 \t/work/shopfront\t/dev/pts/0' : ''}
#P
''';

class _Phone {
  String listing = _phone();
  String log = 'VITE v5 ready\n\x1B[32mLocal\x1B[0m: http://localhost:5173/\n';
  int killExit = 0;
  final scripts = <String>[];

  Future<({int exitCode, String output})> run(String script) async {
    scripts.add(script);
    if (script.contains('/proc/net/tcp')) {
      return (exitCode: 0, output: listing);
    }
    if (script.contains('tail -c')) return (exitCode: 0, output: log);
    if (script.contains('kill -TERM')) {
      if (killExit == 0) listing = _phone(vite: false);
      return (exitCode: killExit, output: '');
    }
    return (exitCode: 0, output: '');
  }
}

Finder _text(String text) => find.textContaining(text, findRichText: true);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    FakeWebViewPlatform.install();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  Future<void> open(
    WidgetTester tester,
    ServicesConnection connection, {
    _Phone? phone,
    double textScale = 1,
    TextDirection? direction,
    Locale locale = const Locale('en'),
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        locale: locale,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: DevelopmentServicesScreen(
          controller: connection,
          runOnPhone: phone?.run,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> done(WidgetTester tester) async {
    KitUndo.commitPending();
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  }

  testWidgets('a server started some other way is listed with its port', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone();
    await open(tester, connection, phone: phone);
    expect(find.text('Also running'), findsOneWidget);
    expect(find.text('vite'), findsOneWidget);
    expect(_text('Running command · Port 5173'), findsOneWidget);
    expect(
      _text('node /work/shopfront/node_modules/.bin/vite'),
      findsOneWidget,
    );
    expect(
      find.text(
        'Found by the ports they listen on, however they were started.',
      ),
      findsOneWidget,
    );
    expect(find.byTooltip('Preview vite'), findsOneWidget);
    expect(find.byTooltip('Stop vite'), findsOneWidget);
    await done(tester);
  });

  testWidgets('Open preview opens the preview with Logs for that server', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone();
    await open(tester, connection, phone: phone);
    await tester.tap(find.byTooltip('Preview vite'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    // The title says "Preview"; the address and Logs sit in its Details
    // sheet (vision gaps, 2026-10-21).
    await tester.tap(find.byKey(const ValueKey('dev-preview-title')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.byKey(const ValueKey('dev-preview-logs')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('dev-preview-logs')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.text('vite · Logs'), findsOneWidget);
    expect(_text('VITE v5 ready'), findsOneWidget);
    expect(_text('\x1B'), findsNothing, reason: 'colour codes are stripped');
    expect(find.byKey(const ValueKey('kit-log-search')), findsOneWidget);
    await done(tester);
  });

  testWidgets('tapping the row reads its log and search marks a word', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone();
    await open(tester, connection, phone: phone);
    await tester.tap(find.text('vite'));
    await tester.pumpAndSettle();
    expect(_text('Local: http://localhost:5173/'), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('kit-log-search')),
      'ready',
    );
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pump();
    expect(find.text('1 result'), findsOneWidget);
    await done(tester);
  });

  testWidgets(
    'a program with no log file says why instead of showing nothing',
    (tester) async {
      final connection = await connectionFor(ServiceRepository());
      addTearDown(connection.dispose);
      final phone = _Phone()..listing = _phone(vite: false, other: true);
      await open(tester, connection, phone: phone);
      await tester.tap(find.text('http.server'));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('dev-server-logs-unavailable')),
        findsOneWidget,
      );
      // The way forward names the menu entry that exists on the row.
      expect(find.textContaining('Save as command'), findsOneWidget);
      await done(tester);
    },
  );

  testWidgets('Stop asks first, then ends that program and the row goes', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone();
    await open(tester, connection, phone: phone);
    await tester.tap(find.byTooltip('Stop vite'));
    await tester.pumpAndSettle();
    expect(find.text('Stop vite?'), findsOneWidget);
    expect(phone.scripts.where((s) => s.contains('kill -TERM')), isEmpty);
    await tester.tap(
      find.byKey(const ValueKey('development-services-confirm')),
    );
    await tester.pumpAndSettle();
    final kills = phone.scripts.where((s) => s.contains('kill -TERM')).toList();
    expect(kills, hasLength(1));
    expect(kills.single, contains('kill -TERM 41'));
    expect(find.text('vite'), findsNothing);
    await done(tester);
  });

  testWidgets('a stop that fails keeps the row and asks again', (tester) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone()..killExit = 5;
    await open(tester, connection, phone: phone);
    await tester.tap(find.byTooltip('Stop vite'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('development-services-confirm')),
    );
    await tester.pumpAndSettle();
    expect(find.text('vite'), findsOneWidget);
    await done(tester);
  });

  testWidgets('Save as command keeps a found server as a saved command', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone();
    await open(tester, connection, phone: phone);
    await tester.longPress(find.text('vite'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Save as command'));
    await tester.pumpAndSettle();
    final saved = connection.store.prefs.getString(
      'oc.developmentServices.laptop',
    );
    expect(saved, contains('vite --port 5173'));
    expect(saved, contains('http://localhost:5173'));
    // Saved, it is no longer offered to save again.
    await tester.longPress(find.text('vite').last);
    await tester.pumpAndSettle();
    expect(find.text('Save as command'), findsNothing);
    await done(tester);
  });

  testWidgets('a command the server runs is listed when there is no phone', (
    tester,
  ) async {
    final repo = ServiceRepository();
    repo.shells['sh_9'] = ManagedShell(
      id: 'sh_9',
      command: 'npm run dev',
      status: ManagedShellStatus.running,
      startedAt: DateTime.fromMillisecondsSinceEpoch(5000),
      directory: '/work/shopfront',
    );
    final connection = await connectionFor(repo);
    addTearDown(connection.dispose);
    await open(tester, connection);
    expect(find.text('Also running'), findsOneWidget);
    expect(find.text('npm run dev'), findsWidgets);
    expect(_text('Port 5173'), findsOneWidget);
    expect(
      _text('A server started in a terminal on it is not found here'),
      findsOneWidget,
    );
    await done(tester);
  });

  testWidgets('a secret in a command line is masked and never saved', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone()
      ..listing = _phone().replaceFirst(
        'vite --port 5173',
        'vite --api-key sk-ant-api03-abcdefghijklmnopqrstuvwxyz0123',
      );
    await open(tester, connection, phone: phone);
    expect(_text('abcdefghijklmnopqrstuvwxyz'), findsNothing);
    expect(_text('sk-ant-'), findsOneWidget);
    await tester.longPress(find.text('vite'));
    await tester.pumpAndSettle();
    expect(find.text('Save as command'), findsNothing);
    await done(tester);
  });

  testWidgets('nothing is listed when nothing else is running', (tester) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone()..listing = _phone(vite: false);
    await open(tester, connection, phone: phone);
    expect(find.text('Also running'), findsNothing);
    await done(tester);
  });

  testWidgets('a saved command that runs shows its port and a preview button', (
    tester,
  ) async {
    final repo = ServiceRepository();
    final connection = await connectionFor(repo);
    addTearDown(connection.dispose);
    await seed(connection);
    await open(tester, connection);
    expect(find.byTooltip('Preview Shopfront preview'), findsNothing);
    await tester.tap(find.byTooltip('Start Shopfront preview'));
    await tester.pumpAndSettle();
    expect(_text('Port 5173'), findsOneWidget);
    expect(find.byTooltip('Preview Shopfront preview'), findsOneWidget);
    // Its own shell is not listed a second time.
    await tester.pump(const Duration(seconds: 11));
    expect(find.text('Also running'), findsNothing);
    await done(tester);
  });

  testWidgets('at 2.5x text the rows and their buttons fit', (tester) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    await seed(connection);
    final phone = _Phone()..listing = _phone(other: true);
    await open(tester, connection, phone: phone, textScale: 2.5);
    expect(tester.takeException(), isNull);
    await tester.drag(find.byType(Scrollable).first, const Offset(0, -400));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await done(tester);
  });

  testWidgets('right to left lays the rows out without overflow', (
    tester,
  ) async {
    final connection = await connectionFor(ServiceRepository());
    addTearDown(connection.dispose);
    final phone = _Phone()..listing = _phone(other: true);
    await open(tester, connection, phone: phone, locale: const Locale('ar'));
    expect(tester.takeException(), isNull);
    expect(find.text('قيد التشغيل أيضًا'), findsOneWidget);
    await done(tester);
  });
}
