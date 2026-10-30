// Gallery for slice S5 of the style cleanup (style coherence audit
// 2026-10-21 §6): the icon tiles and the inset that used to paint their own
// look now ask the style resolver, so they match the row icons in every
// style. Each style, light and dark:
// - kit_surfaces_tiles_*: row icons beside the empty-state tile, a notice
//   card's tile, the step lights and the consequences inset;
// - kit_surfaces_tiles_confirm_*: a confirmation sheet (its header tile and
//   consequences) over a page of rows;
// - kit_surfaces_tiles_alert_*: an alert dialog's tile over the same page.
// Solid here is today's look; the parts' own galleries keep it.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_surfaces_tiles_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

import '../../../tool/capture/fixtures.dart' show captureTheme;
import 'kit_gallery.dart';

const _size = Size(412, 915);

/// The rows whose icons every other tile should match.
Widget _rows(BuildContext context) => KitRowGroup(
  label: 'Conversations',
  margin: EdgeInsets.zero,
  children: [
    KitRow(
      title: 'Fix flaky checkout test',
      supporting: const TextSpan(text: 'Working · 2 min'),
      leading: KitRow.icon(context, AppIconography.terminal),
      onTap: () {},
    ),
    KitRow(
      title: 'Tidy up the settings page',
      supporting: const TextSpan(text: 'Done · 1 h ago'),
      leading: KitRow.icon(context, AppIconography.check),
      onTap: () {},
    ),
  ],
);

class _Parts extends StatelessWidget {
  const _Parts();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _rows(context),
          SizedBox(height: tokens.space4),
          const KitStateView(
            size: KitStateSize.inline,
            icon: AppIconography.branch,
            title: 'No worktrees yet',
            body: 'A worktree lets an agent work on a branch.',
          ),
          SizedBox(height: tokens.space4),
          const KitNotice.card(
            tone: AppStatusTone.neutral,
            title: 'Laptop is asleep',
            message: 'Wake it to keep working.',
          ),
          SizedBox(height: tokens.space4),
          KitStepTimeline(
            label: 'Ran 3 commands',
            icon: AppIconography.terminal,
            expanded: true,
            onPressed: () {},
            steps: const [
              KitToolRow(
                kind: KitToolKind.read,
                title: 'Read',
                path: 'lib/a.dart',
                status: KitToolStatus.done,
              ),
              KitToolRow(
                kind: KitToolKind.shell,
                title: 'Shell',
                detail: 'flutter test',
                status: KitToolStatus.running,
              ),
              KitToolRow(
                kind: KitToolKind.edit,
                title: 'Edit',
                path: 'lib/b.dart',
                status: KitToolStatus.pending,
              ),
            ],
          ),
          SizedBox(height: tokens.space4),
          KitConsequences(
            items: const [
              KitConsequence(
                '3 queued prompts will be deleted',
                mark: KitConsequenceMark.lost,
              ),
              KitConsequence(
                'Changed files stay on the computer',
                mark: KitConsequenceMark.kept,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// A styled app, as main.dart places the style (under the effects scope,
/// above the navigator), with [open] run over a page of rows.
Future<void> _modalShot(
  WidgetTester tester, {
  required String name,
  required KitSurfaceStyle style,
  required bool light,
  required FutureOr<void> Function(BuildContext context) open,
}) async {
  tester.view.physicalSize = _size * 3.0;
  tester.view.devicePixelRatio = 3.0;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  late BuildContext context;
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, navigator) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: KitEffectsScope(
              effects: KitEffects(surfaceStyle: style),
              child: kitStyleTheme(child: navigator!),
            ),
          ),
          home: Scaffold(
            body: SafeArea(
              child: Builder(
                builder: (inner) {
                  context = inner;
                  return Padding(
                    padding: EdgeInsets.all(KitTokens.of(inner).gutter),
                    child: _rows(inner),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
    unawaited(Future.sync(() => open(context)));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  } finally {
    debugDefaultTargetPlatformOverride = null;
  }
  await expectLater(find.byKey(boundary), matchesGoldenFile('$name.png'));
}

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      final mode = light ? 'light' : 'dark';

      testWidgets('surfaces tiles · ${style.name} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_tiles_${style.name}',
            _size,
            light: light,
          ),
          size: _size,
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            // As main.dart: the style's own roles (hook H6).
            child: kitStyleTheme(child: const _Parts()),
          ),
        );
      });

      testWidgets('surfaces tiles confirm · ${style.name} · $mode', (
        tester,
      ) async {
        await _modalShot(
          tester,
          name: 'kit_surfaces_tiles_confirm_${style.name}_$mode',
          style: style,
          light: light,
          open: (context) => showKitConfirm(
            context,
            title: 'Delete this conversation?',
            body: 'The messages leave this server for good.',
            confirmLabel: 'Delete',
            kind: KitConfirmKind.destructive,
            consequences: const ['3 changed files stay on the computer'],
          ),
        );
      });

      testWidgets('surfaces tiles alert · ${style.name} · $mode', (
        tester,
      ) async {
        await _modalShot(
          tester,
          name: 'kit_surfaces_tiles_alert_${style.name}_$mode',
          style: style,
          light: light,
          open: (context) => showKitAlert(
            context,
            title: 'Could not add file',
            body: 'The file is larger than the server accepts.',
            icon: AppIconography.info,
          ),
        );
      });
    }
  }
}
