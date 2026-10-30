// Gallery (gate G4) for KitStatusLine v2
// (docs/ux-system/kit-api/KitStatusLine.md): every declared state under a
// stub top bar — connection (reconnecting, with an action), slow (escalated
// after 8 s), the team's Now line, a risky switch with Turn off, update
// ready, an error with Try again, and a long message that stacks — at the
// phone size, plus connection at the wide size and connection and the Now
// line at 2.0 text. Owner decision 2026-09-27: phone 412x915 and 1280x800
// only, light and dark; no Arabic.
//
// The slow shot's `since` is offset from the fake clock by a fixed
// Duration, so the image never depends on the wall clock (TEST-11).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_status_line_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_since.dart';
import 'package:opencode_mobile/ui/kit/kit_status_line.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

/// Now on the clock KitSince reads (faked by testWidgets).
DateTime _now() {
  final epoch = DateTime.utc(2000);
  return epoch.add(KitSince.statusOf(epoch).elapsed);
}

KitAction _act(String label) => KitAction(label: label, onPressed: () {});

/// A stub top bar (KitTopBar is another unit's part): the screen's name
/// where the real bar puts it, so the line is seen directly under a bar.
class _StubTopBar extends StatelessWidget {
  const _StubTopBar();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return SizedBox(
      height: tokens.rowHeight,
      child: Padding(
        padding: EdgeInsetsDirectional.only(start: tokens.gutter),
        child: const Align(
          alignment: AlignmentDirectional.centerStart,
          child: KitText('Laptop', role: KitTextRole.headline),
        ),
      ),
    );
  }
}

Map<String, KitStatusLine Function()> _states() => {
  'connection': () => KitStatusLine.of(
    KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:laptop',
      icon: AppIconography.cloudOff,
      message: 'Reconnecting to laptop…',
      tone: AppStatusTone.progress,
      supporting: '2 drafts will send when connected',
      action: _act('Servers'),
    ),
  ),
  'slow': () => KitStatusLine.of(
    KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:laptop',
      icon: AppIconography.cloudOff,
      message: 'Reconnecting to laptop…',
      tone: AppStatusTone.progress,
      since: _now().subtract(KitMotion.escalateAfter * 2),
      onSlow: [_act('Retry'), _act('Leave it running')],
    ),
  ),
  'now': () => KitStatusLine.of(
    KitStatus(
      kind: KitStatusKind.work,
      id: 'team:login-fix',
      icon: AppIconography.agent,
      message: 'Working on the login fix',
      tone: AppStatusTone.progress,
      next: 'A reviewer checks it next · about 6 min',
      action: _act('Open'),
    ),
  ),
  'risky': () => KitStatusLine.of(
    KitStatus(
      kind: KitStatusKind.riskySwitch,
      icon: AppIconography.shield,
      message: 'Auto-approve is on for this chat',
      action: _act('Turn off'),
    ),
  ),
  'update': () => KitStatusLine.of(
    KitStatus(
      kind: KitStatusKind.update,
      icon: AppIconography.download,
      message: 'Update ready',
      supporting: 'Restart the app to use it',
      action: _act('Restart'),
      onDismiss: () {},
    ),
  ),
  'error': () => KitStatusLine.of(
    KitStatus(
      kind: KitStatusKind.info,
      icon: AppIconography.error,
      message: 'Couldn’t share this conversation',
      tone: AppStatusTone.failure,
      action: _act('Try again'),
      more: [_act('Details')],
    ),
  ),
  // The link's own retry timer (vision 2026-10-20): muted, the countdown
  // as the second line, Try now as the action.
  'countdown': () => KitStatusLine.of(
    KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:laptop',
      icon: AppIconography.cloudOff,
      message: 'Laptop isn’t answering',
      countdownTo: _now().add(const Duration(milliseconds: 12400)),
      countdownLine: (left) =>
          'Trying again in ${(left.inMilliseconds / 1000).ceil()} s',
      action: _act('Try now'),
      more: [_act('Details')],
    ),
  ),
  'stacked': () => KitStatusLine(
    icon: AppIconography.cloudOff,
    message:
        'Can’t reach the laptop over Tailscale; it may be asleep or offline',
    tone: AppStatusTone.attention,
    action: _act('Wake it'),
    more: [_act('Servers')],
  ),
};

Widget _screen(Widget line) => Column(
  crossAxisAlignment: CrossAxisAlignment.stretch,
  children: [const _StubTopBar(), line],
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final MapEntry(key: state, value: build) in _states().entries) {
      testWidgets('$state · $mode', (tester) async {
        await tester.pumpWidget(const SizedBox.shrink());
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_status_line_$state', _phone, light: light),
          size: _phone,
          light: light,
          child: _screen(build()),
        );
      });
    }

    testWidgets('connection · ${kitGallerySize(_wide)} · $mode', (
      tester,
    ) async {
      await tester.pumpWidget(const SizedBox.shrink());
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_status_line_connection', _wide, light: light),
        size: _wide,
        light: light,
        child: _screen(_states()['connection']!()),
      );
    });

    for (final state in ['connection', 'now', 'countdown']) {
      for (final size in [_phone, _wide]) {
        testWidgets('$state · text 2.0 · ${kitGallerySize(size)} · $mode', (
          tester,
        ) async {
          await tester.pumpWidget(const SizedBox.shrink());
          await kitGalleryPart(
            tester,
            name: kitGalleryName(
              'kit_status_line_$state',
              size,
              light: light,
              text2: true,
            ),
            size: size,
            light: light,
            textScale: 2,
            child: _screen(_states()[state]!()),
          );
        });
      }
    }
  }
}
