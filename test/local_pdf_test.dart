import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/local_pdf.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/widgets/pdf_file_preview.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';

final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
);
Map<String, Object> page(int index, {int count = 3}) => {
  'page': index,
  'pageCount': count,
  'width': 1,
  'height': 1,
  'png': _png,
};
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final bytes = Uint8List.fromList('%PDF-fixture'.codeUnits);
  final calls = <MethodCall>[];
  setUp(() {
    calls.clear();
    TestWidgetsFlutterBinding.instance.handleAppLifecycleStateChanged(
      AppLifecycleState.resumed,
    );
  });
  tearDown(() {
    TestWidgetsFlutterBinding.instance.handleAppLifecycleStateChanged(
      AppLifecycleState.resumed,
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(LocalPdf.channel, null);
    debugPlatformCapabilities = null;
  });
  void handler(Future<Object?> Function(MethodCall) action) {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(LocalPdf.channel, (call) {
          calls.add(call);
          return action(call);
        });
  }

  Future<void> pump(WidgetTester tester, Widget child) =>
      tester.pumpWidget(MaterialApp(home: Scaffold(body: child)));
  test(
    'channel input/output budgets reject invalid transport results',
    () async {
      handler((call) async => page(0));
      await expectLater(
        LocalPdf.render(Uint8List(LocalPdf.maxBytes + 1), 0, 'big'),
        throwsA(isA<PlatformException>()),
      );
      expect(calls, isEmpty);
      handler((call) async => {...page(0), 'width': 2000});
      await expectLater(
        LocalPdf.render(bytes, 0, 'bad'),
        throwsA(isA<PlatformException>()),
      );
      final forged = Uint8List.fromList(_png);
      ByteData.sublistView(forged).setUint32(16, 100000);
      handler((call) async => {...page(0), 'png': forged});
      await expectLater(
        LocalPdf.render(bytes, 0, 'forged'),
        throwsA(isA<PlatformException>()),
      );
    },
  );
  testWidgets(
    'PDF page controls browse local rasters and stop at declared limit',
    (tester) async {
      handler(
        (call) async => call.method == 'render'
            ? page((call.arguments as Map)['page'] as int, count: 201)
            : null,
      );
      await pump(tester, PdfFilePreview(bytes: bytes));
      await tester.pumpAndSettle();
      expect(find.text('Page 1 of 201'), findsOneWidget);
      expect(find.textContaining('Only the first 200 pages'), findsOneWidget);
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      expect(find.text('Page 2 of 201'), findsOneWidget);
      await tester.tap(find.text('Previous'));
      await tester.pumpAndSettle();
      expect(find.text('Page 1 of 201'), findsOneWidget);
    },
  );
  testWidgets('scope-retired PDF requests cannot install late pages', (
    tester,
  ) async {
    final pending = Completer<Object?>();
    handler(
      (call) => call.method == 'render' ? pending.future : Future.value(),
    );
    final enabled = ValueNotifier(true);
    addTearDown(enabled.dispose);
    await pump(
      tester,
      ValueListenableBuilder<bool>(
        valueListenable: enabled,
        builder: (_, value, _) => MarkdownInteractionScope(
          enabled: value,
          child: PdfFilePreview(bytes: bytes),
        ),
      ),
    );
    enabled.value = false;
    await tester.pump();
    expect(calls.where((call) => call.method == 'cancel'), hasLength(1));
    pending.complete(page(0));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 3'), findsNothing);
    expect(find.text('Retry'), findsNothing);
    await pump(tester, const SizedBox());
    expect(tester.takeException(), isNull);
  });
  testWidgets('disposing a PDF view cancels and ignores a held page', (
    tester,
  ) async {
    final pending = Completer<Object?>();
    handler(
      (call) => call.method == 'render' ? pending.future : Future.value(),
    );
    await pump(tester, PdfFilePreview(bytes: bytes));
    await pump(tester, const SizedBox());
    expect(calls.where((call) => call.method == 'cancel'), hasLength(1));
    pending.complete(page(0));
    await tester.pumpAndSettle();
    expect(find.byType(Image), findsNothing);
    expect(tester.takeException(), isNull);
  });
  testWidgets(
    'replacing a document retires the old response and resets to page one',
    (tester) async {
      final first = Completer<Object?>(), second = Completer<Object?>();
      var renders = 0;
      handler(
        (call) => call.method == 'render'
            ? (++renders == 1 ? first.future : second.future)
            : Future.value(),
      );
      await pump(tester, PdfFilePreview(bytes: bytes));
      await pump(
        tester,
        PdfFilePreview(bytes: Uint8List.fromList('another PDF'.codeUnits)),
      );
      expect(calls.where((call) => call.method == 'cancel'), hasLength(1));
      second.complete(page(0, count: 7));
      await tester.pumpAndSettle();
      first.complete(page(0, count: 3));
      await tester.pumpAndSettle();
      expect(find.text('Page 1 of 7'), findsOneWidget);
      expect(find.text('Page 1 of 3'), findsNothing);
    },
  );
  testWidgets('backgrounding cancels PDF work and requires an explicit retry', (
    tester,
  ) async {
    final pending = Completer<Object?>();
    handler(
      (call) => call.method == 'render' ? pending.future : Future.value(),
    );
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await pump(tester, PdfFilePreview(bytes: bytes));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    await tester.pump();
    expect(calls.where((call) => call.method == 'cancel'), hasLength(1));
    pending.complete(page(0));
    await tester.pumpAndSettle();
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(calls.where((call) => call.method == 'render'), hasLength(1));
    expect(find.text('Retry'), findsOneWidget);
    expect(find.text('Page 1 of 3'), findsNothing);
  });
  testWidgets('cancel and renderer death recover without retaining a spinner', (
    tester,
  ) async {
    var pending = Completer<Object?>();
    handler(
      (call) => call.method == 'render' ? pending.future : Future.value(),
    );
    await pump(tester, PdfFilePreview(bytes: bytes));
    await tester.tap(find.text('Cancel'));
    await tester.pump();
    pending.complete(page(0));
    await tester.pumpAndSettle();
    expect(
      find.text('PDF loading cancelled. Retry when you are ready.'),
      findsOneWidget,
    );
    pending = Completer<Object?>();
    await tester.tap(find.text('Retry'));
    await tester.pump();
    pending.completeError(PlatformException(code: 'service_died'));
    await tester.pumpAndSettle();
    expect(find.textContaining('This PDF page could not'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
  });
  testWidgets('initial inactive PDF mount waits for explicit resumed retry', (
    tester,
  ) async {
    handler((call) async => call.method == 'render' ? page(0) : null);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    await pump(tester, PdfFilePreview(bytes: bytes));
    expect(calls, isEmpty);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(calls, isEmpty);
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 3'), findsOneWidget);
  });
  testWidgets('inactive document replacement cannot start another render', (
    tester,
  ) async {
    final pending = Completer<Object?>();
    handler(
      (call) => call.method == 'render' ? pending.future : Future.value(),
    );
    await pump(tester, PdfFilePreview(bytes: bytes));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    final replacement = Uint8List.fromList('new PDF'.codeUnits);
    await pump(tester, PdfFilePreview(bytes: replacement));
    pending.complete(page(0));
    await tester.pumpAndSettle();
    expect(calls.where((call) => call.method == 'render'), hasLength(1));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(calls.where((call) => call.method == 'render'), hasLength(1));
    handler((call) async => call.method == 'render' ? page(0, count: 7) : null);
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 7'), findsOneWidget);
    final last = calls.last.arguments as Map;
    expect(last['bytes'], replacement);
    expect(last['page'], 0);
  });
  testWidgets('desktop and encrypted PDF give useful original-save guidance', (
    tester,
  ) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    await pump(tester, PdfFilePreview(bytes: bytes));
    expect(find.textContaining('Android 10 or newer'), findsOneWidget);
    expect(calls, isEmpty);
    await pump(tester, const SizedBox());
    debugPlatformCapabilities = const PlatformCapabilities.android();
    handler((call) async => throw PlatformException(code: 'encrypted'));
    await pump(tester, PdfFilePreview(bytes: bytes));
    await tester.pumpAndSettle();
    expect(find.textContaining('requires a password'), findsOneWidget);
  });
}
