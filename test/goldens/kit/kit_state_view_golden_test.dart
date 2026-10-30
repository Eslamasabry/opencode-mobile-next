// Gallery (gate G4) for KitStateView v2 (docs/ux-system/kit-api/
// KitStateView.md "Galleries required"), trimmed by the owner decision of
// 2026-09-27 (phone 412x915 and one wide size, 1280x800; light and dark; no
// Arabic): the declared page states, two inline states, 2.0 text on the
// error, and the default (empty) page at the wide size.
//
// Every `since` is offset from `clock.now()` by a fixed Duration, so the
// words never depend on the wall clock (TEST-11).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_state_view_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_progress.dart';
import 'package:opencode_mobile/ui/kit/kit_state_view.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

KitAction _act(String label) => KitAction(label: label, onPressed: () {});

const _details =
    'GET /api/permission failed: 500\n'
    'FormatException: Unexpected character (at character 1)';

Widget _empty() => KitStateView(
  icon: AppIconography.branch,
  title: 'No worktrees yet',
  body: 'A worktree lets an agent work on a branch without touching yours.',
  primary: _act('Create worktree'),
);

Widget _errorOther({KitStateSize size = KitStateSize.page}) =>
    KitStateView.error(
      title: 'Couldn’t load saved permissions',
      body: 'The server answered with something the app can’t read.',
      error: const FormatException('x'),
      details: _details,
      retry: _act('Try again'),
      reportSource: 'saved-permissions',
      size: size,
    );

Widget _missingEnable({KitStateSize size = KitStateSize.inline}) =>
    KitStateView.missing(
      capability: 'voice.model',
      title: 'Voice needs a model',
      why: 'Voice runs on this phone once a model is downloaded.',
      enable: _act('Download voice model'),
      cost: const ['About 208 MB', 'about 4 min', 'uses Wi-Fi'],
      size: size,
    );

/// The calm form (vision 2026-10-20): the big line, one next step, the main
/// action at the bottom and one quiet text action.
Widget _calm({KitStateSize size = KitStateSize.page}) => KitStateView.calm(
  icon: AppIconography.chat,
  title: 'Nothing running',
  body: 'Start a conversation, or open one to pick up where you left off.',
  primary: size == KitStateSize.page ? _act('New conversation') : null,
  tertiary: [_act('Show all conversations')],
  size: size,
);

/// The page states, keyed by their shot stem.
Map<String, Widget Function()> _pageStates() => {
  'loading': () => const KitStateView(
    icon: AppIconography.sync,
    title: 'Loading conversations…',
    tone: AppStatusTone.progress,
    progress: KitProgress.waiting(),
  ),
  'working': () => const KitStateView(
    icon: AppIconography.download,
    title: 'Installing OpenCode…',
    tone: AppStatusTone.progress,
    progress: KitProgress.staged(step: 3, of: 5, label: 'Installing packages'),
  ),
  'empty': _empty,
  'error_network': () => KitStateView.error(
    title: 'Couldn’t reach laptop',
    body: 'Check that the server is running and this phone is online.',
    error: TimeoutException('t'),
    details: 'GET http://192.168.1.20:4096/session timed out after 10 s',
    retry: _act('Try again'),
    switchServer: _act('Switch server'),
  ),
  'error_other': _errorOther,
  'calm': _calm,
  'missing_explains': () => const KitStateView.missing(
    capability: 'terminal.local',
    title: 'Terminal needs a server on this phone',
    why:
        'The terminal runs where the server runs. Laptop servers offer it '
        'from the Servers page.',
    size: KitStateSize.page,
  ),
  'missing_enable': () => _missingEnable(size: KitStateSize.page),
  'slow': () => KitStateView(
    icon: AppIconography.sync,
    title: 'Starting OpenCode…',
    body: 'This takes a few seconds.',
    tone: AppStatusTone.progress,
    progress: const KitProgress.waiting(),
    since: clock.now().subtract(KitMotion.escalateAfter * 2),
    onSlow: [_act('Try again'), _act('Restart')],
  ),
};

/// A page state fills the window's height, as it does in a real body.
Widget _page(Size size, Widget child) =>
    SizedBox(height: size.height - 64, child: child);

void main() {
  setUpAll(loadKitGalleryFonts);
  tearDown(() => KitReportHook.handler = null);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final MapEntry(key: state, value: build) in _pageStates().entries) {
      testWidgets('$state · page · $mode', (tester) async {
        KitReportHook.handler = (_, _) async {};
        await tester.pumpWidget(const SizedBox.shrink());
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_state_view_$state', _phone, light: light),
          size: _phone,
          light: light,
          child: _page(_phone, build()),
        );
      });
    }

    testWidgets('error other · inline · $mode', (tester) async {
      KitReportHook.handler = (_, _) async {};
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_state_view_error_other_inline',
          _phone,
          light: light,
        ),
        size: _phone,
        light: light,
        child: _errorOther(size: KitStateSize.inline),
      );
    });

    testWidgets('calm · inline · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_state_view_calm_inline',
          _phone,
          light: light,
        ),
        size: _phone,
        light: light,
        child: _calm(size: KitStateSize.inline),
      );
    });

    testWidgets('calm · 2.0 text · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_state_view_calm',
          _phone,
          light: light,
          text2: true,
        ),
        size: _phone,
        light: light,
        textScale: 2,
        child: _page(_phone, _calm()),
      );
    });

    testWidgets('missing enable · inline · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_state_view_missing_enable_inline',
          _phone,
          light: light,
        ),
        size: _phone,
        light: light,
        child: _missingEnable(),
      );
    });

    testWidgets('error other · 2.0 text · $mode', (tester) async {
      KitReportHook.handler = (_, _) async {};
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_state_view_error_other',
          _phone,
          light: light,
          text2: true,
        ),
        size: _phone,
        light: light,
        textScale: 2,
        child: _page(_phone, _errorOther()),
      );
    });

    testWidgets('empty · page · 1280x800 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_state_view_empty', _wide, light: light),
        size: _wide,
        light: light,
        child: _page(_wide, _empty()),
      );
    });
  }
}
