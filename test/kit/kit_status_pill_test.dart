// KitStatusPill (the tonal server pill beside the filters): the status tint,
// the word shown only when not connected, RTL, 1.3x text and a long name,
// and the still-motion registration G8x requires of every kit part.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Widget _host(
  Widget child, {
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
  double width = 360,
}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, app) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: Directionality(textDirection: direction, child: app!),
  ),
  home: Scaffold(
    body: Align(
      alignment: AlignmentDirectional.topStart,
      child: SizedBox(width: width, child: child),
    ),
  ),
);

KitStatusPill _pill({
  String name = 'This phone',
  AppStatusTone tone = AppStatusTone.ok,
  String? word,
}) => KitStatusPill(
  label: name,
  semanticsLabel: '$name, ${word ?? 'Connected'}, Switch server',
  tone: tone,
  word: word,
  onPressed: () {},
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  kitMotionStillTests(
    'KitStatusPill',
    builds: {
      'connected': () => _pill(),
      'offline': () => _pill(tone: AppStatusTone.failure, word: 'Offline'),
    },
  );

  testWidgets('connected shows the name and no word; the label says it', (
    tester,
  ) async {
    await tester.pumpWidget(_host(_pill()));
    expect(find.text('This phone'), findsOneWidget);
    expect(find.textContaining('Connected'), findsNothing);
    expect(
      find.bySemanticsLabel('This phone, Connected, Switch server'),
      findsOneWidget,
    );
  });

  testWidgets('not connected says its word beside the name', (tester) async {
    await tester.pumpWidget(
      _host(_pill(tone: AppStatusTone.failure, word: 'Offline')),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('Offline'), findsOneWidget);
  });

  testWidgets('the pill is a 48 dp target around a 32 dp pill', (tester) async {
    await tester.pumpWidget(_host(_pill()));
    final target = tester.getSize(find.byType(KitStatusPill));
    expect(target.height, greaterThanOrEqualTo(48));
  });

  testWidgets('a long name is cut, never overflows, at 1.3x in RTL', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        _pill(
          name: 'Eslam’s workstation at the office (secure):4443',
          tone: AppStatusTone.progress,
          word: 'Connecting…',
        ),
        textScale: 1.3,
        direction: TextDirection.rtl,
        width: 180,
      ),
    );
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byType(KitStatusPill)).width, lessThan(181));
  });

  testWidgets('the dot sits at the reading start and flips in RTL', (
    tester,
  ) async {
    await tester.pumpWidget(_host(_pill(), width: 300));
    final ltr = tester.getTopLeft(find.byType(KitStatusPill)).dx;
    await tester.pumpWidget(
      _host(_pill(), width: 300, direction: TextDirection.rtl),
    );
    final rtl = tester.getTopRight(find.byType(KitStatusPill)).dx;
    expect(ltr, lessThan(10));
    expect(rtl, greaterThan(290));
  });
}
