// The shell's server pill and round tools at the owner's concepts
// (2026-10-22), measured with the app's real fonts: the test font draws
// every glyph a full em wide, so it cannot tell whether "name · state"
// shares one line on a real phone.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderParagraph;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';

import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

Future<void> _pump(
  WidgetTester tester,
  KitShellControls controls, {
  Size size = const Size(412, 915),
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view.physicalSize = size * 3;
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: Directionality(textDirection: direction, child: child!),
      ),
      home: Scaffold(
        body: Column(
          children: [
            KitTopBar.shell(controls: controls),
            const Expanded(child: SizedBox()),
          ],
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

KitShellControls _controls(
  String server,
  String status, {
  AppStatusTone tone = AppStatusTone.ok,
}) => KitShellControls(
  server: server,
  serverStatus: status,
  serverTone: tone,
  onServer: () {},
  onSearch: () {},
  onSettings: () {},
  serverKey: const ValueKey('pill'),
);

bool _cut(WidgetTester tester, Finder text) =>
    tester.renderObject<RenderParagraph>(text).didExceedMaxLines;

void main() {
  setUpAll(loadKitGalleryFonts);

  testWidgets('412 dp: "On this phone · Connected" is one line in a 48 dp '
      'pill', (tester) async {
    await _pump(tester, _controls('On this phone', 'Connected'));
    final name = find.textContaining('On this phone');
    final status = find.text('Connected');
    expect(_cut(tester, name), isFalse);
    expect(tester.getCenter(status).dy, tester.getCenter(name).dy);
    expect(tester.getSize(find.byKey(const ValueKey('pill'))).height, 48);
  });

  testWidgets('412 dp in Arabic order: the state still follows the name', (
    tester,
  ) async {
    await _pump(
      tester,
      _controls('على هذا الهاتف', 'متصل'),
      direction: TextDirection.rtl,
    );
    final name = find.textContaining('على هذا الهاتف');
    final status = find.text('متصل');
    expect(tester.getCenter(status).dy, tester.getCenter(name).dy);
    expect(tester.getCenter(status).dx, lessThan(tester.getCenter(name).dx));
  });

  testWidgets('412 dp at 200 % text: "Reconnecting" stays whole, under the '
      'name when it cannot share the line', (tester) async {
    await _pump(
      tester,
      _controls('Laptop', 'Reconnecting', tone: AppStatusTone.attention),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    final status = find.text('Reconnecting');
    expect(_cut(tester, status), isFalse);
    expect(_cut(tester, find.textContaining('Laptop')), isFalse);
  });

  testWidgets('a long name is cut on the line, the state stays whole', (
    tester,
  ) async {
    await _pump(
      tester,
      _controls(
        'Office computer in the back room',
        'Reconnecting',
        tone: AppStatusTone.attention,
      ),
    );
    expect(tester.takeException(), isNull);
    expect(_cut(tester, find.textContaining('Office')), isTrue);
    expect(_cut(tester, find.text('Reconnecting')), isFalse);
  });

  // Impeccable audit 2026-10-28, #7: "On this ph… · Connected". The green
  // dot already says Connected (the pill's label says it too), so a name
  // that would be cut beside the word gets the room instead.
  testWidgets('Connected and a name that would be cut: the word gives way '
      'to the name', (tester) async {
    await _pump(
      tester,
      _controls('Office computer in the back room', 'Connected'),
    );
    expect(tester.takeException(), isNull);
    expect(find.text('Connected'), findsNothing);
  });

  testWidgets('320 dp with a long state: two lines, the state whole, no '
      'overflow', (tester) async {
    await _pump(
      tester,
      _controls(
        'Office computer',
        'Not answering',
        tone: AppStatusTone.failure,
      ),
      size: const Size(320, 640),
      textScale: 1.3,
    );
    expect(tester.takeException(), isNull);
    final status = find.text('Not answering');
    expect(_cut(tester, status), isFalse);
    expect(
      tester.getTopLeft(status).dy,
      greaterThanOrEqualTo(
        tester.getBottomLeft(find.textContaining('Office')).dy - 1,
      ),
    );
  });
}
