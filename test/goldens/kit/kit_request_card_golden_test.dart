// Gallery (gate G4) for KitRequestCard v2
// (docs/ux-system/kit-api/KitRequestCard.md): every declared state in a
// transcript-like column — the six kinds waiting, waiting-refused,
// disabled, sending, sending-escalated, answered (with Undo),
// answered-elsewhere and expired — plus the permission card on a wide
// window.
//
// Owner decision 2026-09-27 drops Arabic and RTL review for this wave and
// narrows the sizes to the phone (412x915) and one wide size (1280x800), in
// light and dark, with no Arabic or 2.0-text variant (the frozen spec's
// larger grid does not apply; see docs/qa/revamp-kit-KitRequestCard-v2).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_request_card_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_choice_list.dart';
import 'package:opencode_mobile/ui/kit/kit_needs_you.dart';
import 'package:opencode_mobile/ui/kit/kit_receipt.dart';
import 'package:opencode_mobile/ui/kit/kit_request_card.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

const _ifIgnored = 'The agent waits; nothing is lost.';

/// The states whose lone short `text2` words ("Sending…", a collapsed
/// title) G5's textContrast samples at 1x and fails by luck of their
/// anti-aliased letters, although `text2` measures 5:1 and more on every
/// pack: the gallery reads them as one merged node, as kit_receipt's
/// gallery does (docs/qa/revamp-kit-KitStatusMark-v2-2026-09-27).
const _merged = {'sending', 'answered', 'answered_elsewhere', 'expired'};

/// The card between two transcript lines, as the conversation shows it.
Widget _transcript(Widget card) => Column(
  crossAxisAlignment: CrossAxisAlignment.stretch,
  children: [
    const Padding(
      padding: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: KitText(
        'I found the failing test. Running it once more to check the fix.',
      ),
    ),
    card,
    const Padding(
      padding: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: KitText(
        'The next step waits for your answer.',
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
    ),
  ],
);

DateTime _asked() => clock.now().subtract(const Duration(minutes: 4));

KitRequestCard _permission({
  KitRequestPhase phase = KitRequestPhase.waiting,
  KitReceipt? receipt,
  String? answer,
  bool disabled = false,
}) => KitRequestCard.ask(
  kind: KitRequestKind.permission,
  title: 'Run a shell command',
  who: 'fox',
  server: 'laptop',
  reason: KitNeedsYouReason.decision,
  ifIgnored: _ifIgnored,
  announcement: 'Permission needed: Run a shell command',
  detail: 'To check the fix',
  summary: 'flutter test --concurrency=1 test/offline_queue_test.dart',
  since: _asked(),
  phase: phase,
  receipt: receipt,
  answer: answer,
  onDetails: () {},
  answers: KitRequestDecide(
    allowLabel: 'Run once',
    rejectLabel: "Don't run",
    onAllow: disabled ? null : () {},
    onReject: disabled ? null : () {},
    disabledReason: disabled ? 'Reconnect to the laptop to answer.' : null,
  ),
);

const _choices = [
  KitChoice(value: 'keep', title: 'Keep the old name'),
  KitChoice(value: 'rename', title: 'Rename it everywhere'),
  KitChoice(value: 'ask', title: 'Ask me per file'),
];

KitDraft _draft(String id) => KitDraft(
  target: 'request.$id',
  profileId: 'gallery',
  controller: TextEditingController(),
);

Map<String, Widget Function()> _states() => {
  'permission_waiting': () => _permission(),
  'question_waiting': () => KitRequestCard.ask(
    kind: KitRequestKind.question,
    title: 'Rename the storage key?',
    who: 'fox',
    reason: KitNeedsYouReason.decision,
    ifIgnored: _ifIgnored,
    announcement: 'Question: Rename the storage key?',
    detail: 'Three files read the old name.',
    since: _asked(),
    onDetails: () {},
    answers: KitRequestChoose<String>(
      choices: _choices,
      onChosen: (_) {},
      other: KitChoiceOther(
        label: 'Something else',
        fieldLabel: 'Your answer',
        onSubmitted: (_) {},
        draft: _draft('q1'),
      ),
    ),
  ),
  'form_waiting': () => KitRequestCard.ask(
    kind: KitRequestKind.form,
    title: 'Fill in the release notes',
    who: 'Reviewer',
    reason: KitNeedsYouReason.decision,
    ifIgnored: 'The release waits; nothing is lost.',
    announcement: 'Form: Fill in the release notes',
    detail: 'Four fields, two required.',
    since: _asked(),
    onDetails: () {},
    answers: const KitRequestInSheet(),
  ),
  'choice_waiting': () => KitRequestCard.ask(
    kind: KitRequestKind.choice,
    title: 'Which branch should the fix land on?',
    who: 'fox',
    reason: KitNeedsYouReason.decision,
    ifIgnored: _ifIgnored,
    announcement: 'Choice: Which branch should the fix land on?',
    since: _asked(),
    onDetails: () {},
    answers: KitRequestChoose<String>(
      choices: const [
        KitChoice(value: 'dev', title: 'dev'),
        KitChoice(value: 'main', title: 'main', supporting: 'Needs review'),
      ],
      onChosen: (_) {},
    ),
  ),
  'gate_waiting': () => KitRequestCard.ask(
    kind: KitRequestKind.gate,
    title: 'Merge the storage migration',
    who: 'Reviewer',
    server: 'laptop',
    reason: KitNeedsYouReason.decision,
    ifIgnored: 'The team waits; nothing is lost.',
    announcement: 'Gate: Merge the storage migration',
    detail: 'All 412 tests pass.',
    since: _asked(),
    onDetails: () {},
    answers: KitRequestDecide(onAllow: () {}, onReject: () {}),
  ),
  'reply_waiting': () => KitRequestCard.ask(
    kind: KitRequestKind.reply,
    title: 'What should the changelog say?',
    who: 'Release task',
    reason: KitNeedsYouReason.decision,
    ifIgnored: 'The task waits; nothing is lost.',
    announcement: 'Reply needed: What should the changelog say?',
    since: _asked(),
    answers: KitRequestReply(
      fieldLabel: 'Your reply',
      // Typed, so Send is live (the empty field's disabled Send and its
      // reason are covered by the behaviour test).
      draft: _draft('r1')..controller.text = 'Ship it on Friday.',
      onSend: (_) {},
    ),
  ),
  'waiting_refused': () => _permission(
    receipt: const KitReceipt(
      state: KitReceiptState.refused,
      reason: 'the session was busy',
    ),
  ),
  'disabled': () => _permission(disabled: true),
  'sending': () => _permission(
    phase: KitRequestPhase.sending,
    answer: 'Run once',
    receipt: KitReceipt(state: KitReceiptState.sending, since: clock.now()),
  ),
  'sending_escalated': () => _permission(
    phase: KitRequestPhase.sending,
    answer: 'Run once',
    receipt: KitReceipt(
      state: KitReceiptState.sending,
      since: clock.now().subtract(const Duration(seconds: 9)),
      onRetry: () {},
    ),
  ),
  'answered': () => _permission(
    phase: KitRequestPhase.answered,
    receipt: KitReceipt(
      state: KitReceiptState.confirmed,
      label: 'Allowed once',
      onUndo: () {},
    ),
  ),
  'answered_elsewhere': () => _permission(
    phase: KitRequestPhase.answeredElsewhere,
    receipt: const KitReceipt(
      state: KitReceiptState.answeredElsewhere,
      where: 'the laptop',
    ),
  ),
  'expired': () => _permission(phase: KitRequestPhase.expired),
  // The Inbox's row (asRow with place and onOpen): a permission answered in
  // place, then a question that opens its conversation.
  'inbox_row': () => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _permission().asRow(
          place: 'laptop · shopfront · Fix the flaky test',
          onOpen: () {},
          openLabel: 'Open Fix the flaky test',
        ),
        const SizedBox(height: 24),
        KitRequestCard.ask(
          kind: KitRequestKind.question,
          title: 'Which branch should I use?',
          who: 'fox',
          reason: KitNeedsYouReason.decision,
          ifIgnored: _ifIgnored,
          announcement: 'Which branch should I use?',
        ).asRow(
          place: 'laptop · shopfront · Add dark mode',
          onOpen: () {},
          openLabel: 'Open Add dark mode',
        ),
      ],
    ),
  ),
};

void main() {
  setUpAll(loadKitGalleryFonts);
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final MapEntry(key: state, value: build) in _states().entries) {
      testWidgets('$state · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_request_card_$state', _phone, light: light),
          size: _phone,
          light: light,
          child: _merged.contains(state)
              ? MergeSemantics(child: _transcript(build()))
              : _transcript(build()),
        );
      });
    }
    testWidgets('permission_waiting · 1280x800 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_request_card_permission_waiting',
          _wide,
          light: light,
        ),
        size: _wide,
        light: light,
        child: _transcript(_permission()),
      );
    });
  }
}
