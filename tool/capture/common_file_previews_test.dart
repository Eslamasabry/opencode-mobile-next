import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/local_pdf.dart';
import 'package:opencode_mobile/ui/widgets/file_preview.dart';
import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(
    () => TestWidgetsFlutterBinding.instance.handleAppLifecycleStateChanged(
      AppLifecycleState.resumed,
    ),
  );
  setUpAll(loadCaptureFonts);
  for (final mode in ['light', 'dark', 'narrow', 'rtl']) {
    for (final format in ['csv', 'svg', 'pdf']) {
      testWidgets('local $format preview $mode', (tester) async {
        final narrow = mode == 'narrow';
        tester.view.physicalSize = Size(narrow ? 320 : 390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final textData = FilePreviewData(
          name: format == 'csv' ? 'recorded-checks.csv' : 'checkout-flow.svg',
          text: format == 'csv'
              ? 'File,Recorded checks,Result\r\ncheckout.dart,12,Passed\r\npayment_test.dart,8,"Needs a closer look"\r\n'
              : '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 340"><rect width="300" height="340" fill="#f5f8f6"/><rect x="25" y="40" width="250" height="90" rx="14" fill="#196b51"/><text x="150" y="94" text-anchor="middle" font-size="21" font-family="Roboto" fill="#fff">Review basket</text><path d="M 150 145 L 150 195 M 138 183 L 150 195 L 162 183" stroke="#196b51" stroke-width="5" fill="none"/><rect x="25" y="215" width="250" height="90" rx="14" fill="#d9ece2"/><text x="150" y="269" text-anchor="middle" font-size="21" font-family="Roboto" fill="#163428">Confirm checkout</text></svg>',
        );
        final data = format == 'pdf'
            ? FilePreviewData(
                name: 'local-report.pdf',
                bytes: Uint8List.fromList('%PDF-transport-fixture'.codeUnits),
              )
            : textData;
        if (format == 'pdf') {
          final png = await _pdfRaster(tester);
          tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
            LocalPdf.channel,
            (call) async => call.method == 'render'
                ? {
                    'png': png,
                    'page': (call.arguments as Map)['page'],
                    'pageCount': 2,
                    'width': 400,
                    'height': 600,
                  }
                : null,
          );
          addTearDown(
            () => tester.binding.defaultBinaryMessenger
                .setMockMethodCallHandler(LocalPdf.channel, null),
          );
        }
        final key = GlobalKey();
        await tester.pumpWidget(
          RepaintBoundary(
            key: key,
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: captureTheme(light: mode != 'dark'),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(narrow ? 2 : 1)),
                child: Directionality(
                  textDirection: mode == 'rtl'
                      ? TextDirection.rtl
                      : TextDirection.ltr,
                  child: child!,
                ),
              ),
              home: Scaffold(
                appBar: AppBar(title: Text(data.name)),
                body: FilePreviewBody(data: data),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        if (format == 'pdf') {
          await _settlePage(tester);
        }
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/common-file-previews/$mode-$format.png',
          await capturePng(tester, key),
        );
        if (format == 'csv') {
          await tester.drag(find.text('Column 1'), const Offset(-500, 0));
          await tester.pumpAndSettle();
          final horizontal = tester.widget<SingleChildScrollView>(
            find.byWidgetPredicate(
              (widget) =>
                  widget is SingleChildScrollView &&
                  widget.scrollDirection == Axis.horizontal,
            ),
          );
          expect(horizontal.controller!.offset, greaterThan(0));
          expect(tester.takeException(), isNull);
          await writePng(
            'docs/qa/common-file-previews/$mode-csv-end.png',
            await capturePng(tester, key),
          );
        }
        await tester.tap(find.text(format == 'pdf' ? 'Next' : 'Source'));
        await tester.pumpAndSettle();
        if (format == 'pdf') await _settlePage(tester);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/common-file-previews/$mode-$format-${format == 'pdf' ? 'next' : 'source'}.png',
          await capturePng(tester, key),
        );
        // Export must never use reconstructed CSV rows or a sanitized SVG.
        if (format == 'pdf') {
          expect(find.text('Page 2 of 2'), findsOneWidget);
        } else {
          expect(utf8.decode(data.exportBytes!), data.copyText);
        }
        await tester.pumpWidget(const SizedBox());
      });
    }
  }
}

Future<void> _settlePage(WidgetTester tester) async {
  final raster = find.byType(Image);
  await tester.runAsync(
    () => precacheImage(
      tester.widget<Image>(raster).image,
      tester.element(raster),
    ),
  );
  await tester.pumpAndSettle();
}

/// Synthetic raster transport fixture, not evidence of Android PDF rendering.
Future<Uint8List> _pdfRaster(WidgetTester tester) async {
  Uint8List? bytes;
  await tester.runAsync(() async {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder)..drawColor(Colors.white, BlendMode.src);
    canvas.drawRect(
      const Rect.fromLTWH(30, 40, 340, 100),
      Paint()..color = const Color(0xff196b51),
    );
    final text = TextPainter(
      text: const TextSpan(
        text:
            'Recorded checks\n\n12 checks passed\n2 pages in this local report\n\nSynthetic page fixture',
        style: TextStyle(
          color: Color(0xff163428),
          fontFamily: 'Roboto',
          fontSize: 22,
          height: 1.5,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: 340);
    text.paint(canvas, const Offset(30, 175));
    text.dispose();
    final picture = recorder.endRecording();
    final image = await picture.toImage(400, 600);
    bytes = (await image.toByteData(
      format: ui.ImageByteFormat.png,
    ))!.buffer.asUint8List();
    image.dispose();
    picture.dispose();
  });
  return bytes!;
}
