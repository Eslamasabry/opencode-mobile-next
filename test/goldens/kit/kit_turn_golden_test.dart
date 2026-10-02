// Gallery (gate G4) for KitTurn (docs/ux-system/kit-api/KitTurn.md): every
// declared state at the phone size as a real composition (a KitMessage
// prompt, a KitWorkLine, a reply, and where relevant a KitRequestCard or a
// KitNotice), the default (finished, latest) on a wide window inside the
// 700 dp conversation pane, and the default at 2.0 text.
//
// Owner decision 2026-09-27 narrows the sizes to the phone (412x915) and one
// wide size (1280x800), in light and dark, and drops Arabic (the frozen
// spec's larger grid does not apply; see docs/qa/revamp-kit-KitTurn-2026-09-27).
//
// Deterministic (TEST-11): the harness turns animations off, so the running
// marks are still; waits are measured from the test's fake clock.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_turn_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_turn.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_work_line.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_needs_you.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_request_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

void _noop() {}

const _promptWords =
    'Fix the flaky checkout test. It fails about one run in five on CI.';

const _replyWords =
    'The flakiness comes from `CheckoutBloc` racing the price refresh: '
    '`applyCoupon()` awaits the network before the total settles, so the '
    'assertion sometimes reads the old total. I made the test wait for the '
    'settled state.';

const _prompt = KitMessage.prompt(
  body: KitMarkdown(_promptWords, selectable: false),
  menu: [KitMenuItem(label: 'Edit and resend', onSelected: _noop)],
);

const _reply = KitMessage.reply(
  body: KitMarkdown(_replyWords, selectable: false),
);

const _firstWords = KitMessage.reply(
  body: KitMarkdown(
    'I will read the checkout test and the bloc it drives.',
    selectable: false,
  ),
);

const _counts = KitWorkCounts(read: 3, edited: 1);

KitWorkLine _line(KitWorkState state, {String? now}) => KitWorkLine(
  counts: _counts,
  state: state,
  now: now,
  steps: const [
    KitToolRow(
      kind: KitToolKind.read,
      title: 'Read',
      path: 'test/checkout_test.dart',
      status: KitToolStatus.done,
    ),
    KitToolRow(
      kind: KitToolKind.edit,
      title: 'Edited',
      path: 'test/checkout_test.dart',
      added: 6,
      removed: 2,
      status: KitToolStatus.done,
    ),
  ],
);

Widget _permission() => KitRequestCard.ask(
  kind: KitRequestKind.permission,
  title: 'Run this command?',
  who: 'fox',
  server: 'shopfront',
  reason: KitNeedsYouReason.decision,
  ifIgnored: 'The agent waits; nothing is lost.',
  announcement: 'Permission needed: Run this command',
  detail: 'To check the fix',
  summary: 'flutter test test/checkout_test.dart',
  since: clock.now().subtract(const Duration(minutes: 1)),
  onDetails: _noop,
  answers: const KitRequestDecide(
    allowLabel: 'Run once',
    rejectLabel: "Don't run",
    onAllow: _noop,
    onReject: _noop,
  ),
);

String _copy() => _replyWords;

const _footer = KitTurnFooter(
  copyText: _copy,
  meta: 'Sonnet 4.5 · 12k tokens · 10:42',
  menu: [
    KitMenuItem(label: 'Fork from here', onSelected: _noop),
    KitMenuItem(label: 'Revert', onSelected: _noop),
  ],
);

/// The turn with the host's gutter, followed by the next turn's prompt so
/// the turn's own trailing gap shows.
Widget _scene(KitTurn turn) => Padding(
  padding: const EdgeInsets.symmetric(horizontal: 16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      turn,
      const KitMessage.prompt(
        body: KitMarkdown('And run it ten times.', selectable: false),
      ),
    ],
  ),
);

/// The conversation pane: capped at 700 dp on a wide window (LAY-5).
Widget _pane(Widget child) => Align(
  alignment: Alignment.topCenter,
  child: ConstrainedBox(
    constraints: const BoxConstraints(maxWidth: KitLayout.paneDetailMaxWidth),
    child: child,
  ),
);

Widget _default() => _pane(
  _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [_line(KitWorkState.done), _reply],
      phase: KitTurnPhase.finished,
      footer: _footer,
      latest: true,
    ),
  ),
);

Map<String, Widget Function()> _states() => {
  'starting': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: const [],
      phase: KitTurnPhase.starting,
      since: clock.now(),
      footer: _footer,
    ),
  ),
  'starting_slow': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: const [],
      phase: KitTurnPhase.starting,
      since: clock.now().subtract(const Duration(seconds: 9)),
      footer: _footer,
    ),
  ),
  'running': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [
        _firstWords,
        _line(KitWorkState.running, now: 'Editing test/checkout_test.dart'),
      ],
      phase: KitTurnPhase.running,
      footer: _footer,
      latest: true,
    ),
  ),
  // 01B and 12A: the status is the turn's live line under the reply, with
  // one dot and no Stop (Send in the composer is the Stop).
  'running_live': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [
        _firstWords,
        _line(KitWorkState.running, now: 'Editing test/checkout_test.dart'),
      ],
      phase: KitTurnPhase.running,
      live: KitTurnLive(
        activity: KitTurnActivity.writing,
        since: clock.now().subtract(const Duration(seconds: 6)),
      ),
      latest: true,
    ),
  ),
  'waiting_for_you': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [_firstWords, _line(KitWorkState.waitingForYou), _permission()],
      phase: KitTurnPhase.waitingForYou,
      footer: _footer,
      latest: true,
    ),
  ),
  'finished': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [_line(KitWorkState.done), _reply],
      phase: KitTurnPhase.finished,
      footer: _footer,
    ),
  ),
  'finished_latest': _default,
  'stopped': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [_firstWords, _line(KitWorkState.stopped)],
      phase: KitTurnPhase.stopped,
      footer: _footer,
      latest: true,
    ),
  ),
  'interrupted': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [_firstWords, _line(KitWorkState.endedFailed)],
      phase: KitTurnPhase.interrupted,
      footer: _footer,
      latest: true,
    ),
  ),
  'failed': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [
        _firstWords,
        _line(KitWorkState.endedFailed),
        const KitNotice.error(
          message: 'The model provider stopped answering.',
          retry: KitAction(label: 'Try again', onPressed: _noop),
        ),
      ],
      phase: KitTurnPhase.failed,
      footer: _footer,
      latest: true,
    ),
  ),
  'highlighted': () => _scene(
    KitTurn(
      prompt: _prompt,
      blocks: [_line(KitWorkState.done), _reply],
      phase: KitTurnPhase.finished,
      footer: _footer,
      highlighted: true,
    ),
  ),
};

void main() {
  setUpAll(loadKitGalleryFonts);
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final MapEntry(key: state, value: build) in _states().entries) {
      testWidgets('$state · 412x915 · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_turn_$state', _phone, light: light),
          size: _phone,
          light: light,
          child: build(),
        );
      });
    }

    testWidgets('default · 1280x800 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_turn_default', _wide, light: light),
        size: _wide,
        light: light,
        child: _default(),
      );
    });

    for (final size in [_phone, _wide]) {
      testWidgets('default · 2.0 text · ${kitGallerySize(size)} · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_turn_default',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _default(),
        );
      });
    }
  }
}
