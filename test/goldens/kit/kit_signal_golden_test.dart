// Gallery for the Signal style (owner, 2026-10-30; concept CD): the
// Conversations list on its track (a red lamp and the printed slip for the
// conversation that needs you, a green one for the running one, hollow
// rings on the dashed siding for the rest, engraved section labels), and a
// conversation at work (the folded work line on its sleepers, the opened
// steps with their signals, the running command's segment timer, a
// printed slip with its two raised keys). Light and dark, at the phone
// size and in Arabic. Compare with the concept renders by eye.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_signal_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

import '../../../tool/capture/fixtures.dart';
import 'kit_gallery.dart';

final _now = DateTime(2026, 10, 30, 11, 6);

const _phone = Size(412, 915);

// As the app makes it (permissionRequestCard): a shell command's glyph,
// and on a list the answers asked as secondaries and no Details.
KitRequestCard _slip({bool inList = false}) => KitRequestCard.ask(
  kind: KitRequestKind.permission,
  icon: AppIconography.terminal,
  title: 'Push to origin?',
  who: 'OpenCode',
  reason: KitNeedsYouReason.decision,
  ifIgnored: 'The agent waits; nothing is lost.',
  announcement: 'Permission needed: Push to origin?',
  summary: 'git push origin main',
  since: _now.subtract(const Duration(minutes: 2)),
  inList: inList,
  onDetails: inList ? null : () {},
  answers: KitRequestDecide(
    allowLabel: 'Allow',
    rejectLabel: 'Reject',
    onAllow: () {},
    onReject: () {},
    secondary: inList,
  ),
);

/// The Conversations list, as the home arranges it.
Widget home(BuildContext context) {
  final tokens = KitTokens.of(context);
  KitFeedItem done(
    String title,
    String project,
    String time, {
    String? agent,
  }) => KitFeedItem(
    title: title,
    project: project,
    agent: agent,
    time: time,
    onTap: () {},
  );
  return Stack(
    children: [
      CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.symmetric(vertical: tokens.space2),
              child: const KitPageHeading(title: 'Conversations'),
            ),
          ),
          SliverToBoxAdapter(
            child: KitFilterChips(
              chips: [
                KitChip.summary(
                  label: 'All projects',
                  expanded: false,
                  onPressed: () {},
                ),
                KitFilterChip(
                  label: 'Running',
                  selected: false,
                  onPressed: () {},
                ),
              ],
            ),
          ),
          KitSliverRowGroup(
            label: 'Today',
            leadingIcons: false,
            children: [
              KitFeedItem(
                title: 'Push to origin?',
                project: 'settings-demo',
                state: 'Needs you',
                stateTone: KitFeedStateTone.needsYou,
                time: '2 min ago',
                onTap: () {},
                request: _slip(inList: true),
              ),
              KitFeedItem(
                title: 'Fix the TV focus ring',
                project: 'IPTV_King',
                agent: 'Claude Code',
                state: 'Working',
                stateTone: KitFeedStateTone.running,
                time: 'now',
                running: KitFeedRunning(
                  since: _now.subtract(const Duration(minutes: 3)),
                  command: 'flutter test',
                ),
                onTap: () {},
              ),
              done('Design review', 'IPTV_King', '1 h ago', agent: 'Codex'),
              done('Widget sizes', 'oc_app', '3 h ago'),
            ],
          ),
          KitSliverRowGroup(
            label: 'Earlier',
            leadingIcons: false,
            children: [done('Arabic search', 'IPTV_King', 'yesterday')],
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 96)),
        ],
      ),
      KitFloatingAction(
        label: 'New conversation',
        icon: AppIconography.add,
        onPressed: () {},
        child: const SizedBox.expand(),
      ),
    ],
  );
}

/// A conversation at work: the prompt, the folded work, the reply, the
/// running command with its timer, the opened steps and a slip.
Widget chat(BuildContext context, {required bool open}) {
  final tokens = KitTokens.of(context);
  String t(String en, String ar) =>
      Directionality.of(context) == TextDirection.rtl ? ar : en;
  return ListView(
    padding: EdgeInsets.symmetric(vertical: tokens.space4),
    children: [
      KitMessage.prompt(
        body: KitMarkdown(
          t(
            'The focus ring on the TV grid is cut off on the right edge. '
                'Fix it and run the tests.',
            'حلقة التركيز في شبكة التلفاز مقطوعة عند الحافة اليمنى. '
                'أصلحها وشغّل الاختبارات.',
          ),
          selectable: false,
        ),
      ),
      SizedBox(height: tokens.space3),
      Padding(
        padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
        child: KitStepTimeline(
          label: t(
            'Read 4 files · ran 3 commands',
            'قرأ 4 ملفات · شغّل 3 أوامر',
          ),
          icon: AppIconography.terminal,
          expanded: open,
          onPressed: () {},
          steps: [
            KitToolRow(
              kind: KitToolKind.read,
              title: t('Read', 'قراءة'),
              path: 'lib/ui/tv/tv_grid.dart',
              status: KitToolStatus.done,
              duration: const Duration(seconds: 1),
            ),
            KitMessage.thought(
              heading: t(
                'The ring is clipped by the padding',
                'الحلقة مقصوصة بسبب الحشوة',
              ),
              body: KitMarkdown(
                t('Moving the outline inside the tile.', 'أنقل الإطار للداخل.'),
                role: KitTextRole.secondary,
              ),
            ),
            KitToolRow(
              kind: KitToolKind.edit,
              title: t('Edit', 'تعديل'),
              path: 'lib/ui/tv/tv_tile.dart',
              status: KitToolStatus.done,
              added: 3,
            ),
            KitToolRow(
              kind: KitToolKind.shell,
              title: t('Run', 'تشغيل'),
              path: 'flutter test',
              status: KitToolStatus.running,
              startedAt: _now.subtract(const Duration(seconds: 72)),
            ),
          ],
        ),
      ),
      SizedBox(height: tokens.space3),
      Padding(
        padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
        child: KitMessage.reply(
          body: KitMarkdown(
            t(
              "The ring is clipped by the grid's padding. I moved the outline "
                  'inside the tile and added a test.',
              'الحلقة مقصوصة بسبب حشوة الشبكة. نقلت الإطار إلى داخل البلاطة '
                  'وأضفت اختباراً.',
            ),
          ),
        ),
      ),
      SizedBox(height: tokens.space3),
      _slip(),
    ],
  );
}

Future<void> _shot(
  WidgetTester tester, {
  required String name,
  required bool light,
  required Widget Function(BuildContext context) body,
  Locale locale = const Locale('en'),
}) async {
  tester.view.physicalSize = _phone * 3.0;
  tester.view.devicePixelRatio = 3.0;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  try {
    await withClock(Clock.fixed(_now), () async {
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: AppTheme.forLocale(captureTheme(light: light), locale),
            locale: locale,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: KitEffectsScope(
                effects: const KitEffects(surfaceStyle: KitSurfaceStyle.signal),
                child: kitStyleTheme(child: child!),
              ),
            ),
            home: Builder(
              builder: (context) => Scaffold(
                backgroundColor: KitTokens.of(context).roles.ground,
                body: SafeArea(child: Builder(builder: body)),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    });
    expect(tester.takeException(), isNull);
  } finally {
    debugDefaultTargetPlatformOverride = null;
  }
  await expectLater(find.byKey(boundary), matchesGoldenFile('$name.png'));
}

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [true, false]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('home · $mode', (tester) async {
      await _shot(
        tester,
        name: 'kit_signal_home_$mode',
        light: light,
        body: home,
      );
    });
    testWidgets('chat · $mode', (tester) async {
      await _shot(
        tester,
        name: 'kit_signal_chat_$mode',
        light: light,
        body: (context) => chat(context, open: true),
      );
    });
    testWidgets('chat folded · $mode', (tester) async {
      await _shot(
        tester,
        name: 'kit_signal_chat_folded_$mode',
        light: light,
        body: (context) => chat(context, open: false),
      );
    });
    testWidgets('home · Arabic · $mode', (tester) async {
      await _shot(
        tester,
        name: 'kit_signal_home_ar_$mode',
        light: light,
        locale: const Locale('ar'),
        body: home,
      );
    });
  }
}
