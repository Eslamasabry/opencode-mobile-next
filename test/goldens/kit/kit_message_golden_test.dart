// Gallery (gate G4) for KitMessage (docs/ux-system/kit-api/KitMessage.md):
// every declared state at the phone size inside a short transcript, the
// default (a prompt followed by a reply, as on the approved Chat canvas) at
// the phone and wide sizes (the wide one in the 700 dp conversation pane),
// and the default at 2.0 text. Sizes follow the owner decision of
// 2026-09-27: 412x915 and 1280x800, dark and light; no Arabic.
//
// Deterministic (TEST-11): no working mark runs a loop in the gallery's
// still harness; no clock is read (the prompt time is a fixed DateTime).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_message_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer_chips.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import 'kit_gallery.dart';

void _noop() {}

const _menu = [
  KitMenuItem.copy(label: 'Copy message', text: _promptWords),
  KitMenuItem(label: 'Edit and resend', onSelected: _noop),
];

String _promptWords() => _prompt;

const _prompt =
    'Fix the flaky checkout test. It fails about one run in five on CI.';

const _reply =
    'The flakiness comes from `CheckoutBloc` racing the price refresh: '
    '`applyCoupon()` awaits the network before the total settles, so the '
    'assertion sometimes reads the old total. I made the test wait for the '
    'settled state.';

final _time = DateTime(2026, 9, 27, 14, 5);

KitMessage _promptMessage({List<KitAttachment> attachments = const []}) =>
    KitMessage.prompt(
      body: const KitMarkdown(_prompt, selectable: false),
      attachments: attachments,
      time: _time,
      menu: _menu,
    );

const _replyMessage = KitMessage.reply(
  body: KitMarkdown(_reply, selectable: false),
);

const _thoughtBody = KitMarkdown(
  'The test awaits `applyCoupon()` but the total is recomputed on the next '
  'price refresh. Waiting for `CheckoutSettled` removes the race.',
  role: KitTextRole.secondary,
  selectable: false,
);

/// The part among a few transcript neighbours, with a gutter.
Widget _scene(List<Widget> children) => Padding(
  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      for (var i = 0; i < children.length; i++) ...[
        if (i > 0) const SizedBox(height: 16),
        children[i],
      ],
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

final _default = _pane(_scene([_promptMessage(), _replyMessage]));

final _states = <String, Widget>{
  'prompt': _scene([
    _promptMessage(),
    const KitMessage.prompt(body: KitMarkdown('And run it ten times.')),
  ]),
  'prompt_attachments': _scene([
    _promptMessage(
      attachments: const [
        KitAttachment(
          id: 1,
          label: 'checkout_test.dart',
          kind: KitAttachmentKind.file,
        ),
        KitAttachment(
          id: 2,
          label: 'ci-failure.png',
          kind: KitAttachmentKind.image,
        ),
      ],
    ),
  ]),
  'prompt_long': _scene([
    const KitMessage.prompt(
      body: KitMarkdown(
        'Make the coupon test deterministic, run the whole suite twice '
        'before you commit, and tell me if anything else is flaky.',
        selectable: false,
      ),
    ),
    _replyMessage,
  ]),
  'reply': _scene([_replyMessage]),
  'reply_code': _scene([
    const KitMessage.reply(
      body: KitMarkdown(
        'Run this, then the longer one:\n\n'
        '```sh\nflutter test --concurrency=1 test/checkout_test.dart\n```\n\n'
        '```dart\nfinal total = await bloc.stream.firstWhere((s) => s.settled && s.total > 0);\nexpect(total, 42);\n```',
        selectable: false,
      ),
    ),
  ]),
  'thought_folded': _scene([
    const KitMessage.thought(body: _thoughtBody, working: true),
    const KitMessage.thought(body: _thoughtBody, took: Duration(seconds: 12)),
    const KitMessage.thought(
      body: _thoughtBody,
      heading: 'Reading the checkout bloc',
    ),
    _replyMessage,
  ]),
  'thought_open': _scene([
    const KitMessage.thought(
      body: _thoughtBody,
      took: Duration(seconds: 12),
      expanded: true,
    ),
    _replyMessage,
  ]),
  'notice': _scene([
    const KitMessage.notice(text: 'Context added'),
    const KitMessage.notice(
      text: 'Skill loaded',
      icon: AppIconography.sparkle,
      technical: 'flutter-testing',
    ),
    const KitMessage.notice(
      text: 'Conversation compacted',
      icon: AppIconography.archive,
      action: KitAction(label: 'Compact again', onPressed: _noop),
      detail: KitMarkdown(
        'Kept the last 12 messages and a summary of the rest.',
        role: KitTextRole.secondary,
        selectable: false,
      ),
      expanded: true,
    ),
  ]),
  'notice_failed': _scene([
    const KitMessage.notice(
      text: 'Compaction did not finish',
      failed: true,
      action: KitAction(label: 'Try again', onPressed: _noop),
    ),
    const KitMessage.notice(
      text: 'Instructions not loaded',
      failed: true,
      technical: 'packages/checkout/AGENTS.md',
    ),
  ]),
  'marker': _scene([
    _replyMessage,
    const KitMessage.marker(
      text: 'Switched to Sonnet 4',
      icon: AppIconography.model,
    ),
    const KitMessage.marker(text: 'Compacting the conversation', working: true),
    _promptMessage(),
  ]),
};

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final MapEntry(key: state, value: child) in _states.entries) {
      testWidgets('$state · 412x915 · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_message_$state', _phone, light: light),
          size: _phone,
          light: light,
          child: child,
        );
      });
    }

    for (final size in [_phone, _wide]) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_message_default', size, light: light),
          size: size,
          light: light,
          child: _default,
        );
      });

      testWidgets('default · 2.0 text · ${kitGallerySize(size)} · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_message_default',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _default,
        );
      });
    }
  }
}
