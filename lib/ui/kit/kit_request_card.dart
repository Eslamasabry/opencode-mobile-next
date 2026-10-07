import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_choice_list.dart';
import 'kit_field.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_needs_you.dart';
import 'kit_receipt.dart';
import 'kit_sheet.dart';
import 'kit_since.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';
import 'motion/kit_reveal.dart';

/// What the agent asks for (docs/ux-system/kit-api/KitRequestCard.md). No
/// `change` variant (P2 deferred, C50).
enum KitRequestKind { permission, question, form, choice, gate, reply }

/// Where the request is.
enum KitRequestPhase {
  /// Needs an answer: the full card, answers in place.
  waiting,

  /// Answered here, not yet confirmed: the answer line and its receipt.
  sending,

  /// The server echoed the answer: the collapsed row.
  answered,

  /// Answered on another device: the collapsed row.
  answeredElsewhere,

  /// The agent stopped waiting: the collapsed row, nothing to press.
  expired,
}

/// The common answer shown in place. Sealed, so the kit owns the labels, the
/// order, the shortcuts, the send haptic and double-send protection.
sealed class KitRequestAnswers {
  const KitRequestAnswers();
}

/// permission and gate: two acts. Shortcuts A and D on a PC.
final class KitRequestDecide extends KitRequestAnswers {
  const KitRequestDecide({
    required this.onAllow,
    required this.onReject,
    this.allowLabel,
    this.rejectLabel,
    this.disabledReason,
    this.allowKey,
    this.rejectKey,
    this.alwaysAllow,
    this.secondary = false,
  }) : assert(
         (onAllow != null && onReject != null) || disabledReason != null,
         'KitRequestDecide: a missing callback needs its disabledReason '
         '(STATE-8)',
       );

  final VoidCallback? onAllow;
  final VoidCallback? onReject;

  /// Allow drawn as a secondary button: for a card among others in a list,
  /// where the screen keeps its own one primary (LAY-12).
  final bool secondary;

  /// A verb (COPY-8): "Run once". Default by kind: permission "Allow once",
  /// gate "Approve".
  final String? allowLabel;

  /// "Don't run". Default by kind: permission "Reject", gate "Send back".
  final String? rejectLabel;

  /// Shown under the buttons when a callback is null (STATE-8).
  final String? disabledReason;
  final Key? allowKey;
  final Key? rejectKey;

  /// "Always allow" next to the two answers (permission only, 13B). Null
  /// where the server keeps no standing grants: the button is not drawn.
  final KitRequestAlwaysAllowStep? alwaysAllow;
}

/// The card's "Always allow": a quiet third act that asks one plain
/// confirm first ("Always allow `ls` in this project?" with Always allow
/// and Cancel) and only then calls [onConfirmed]. Cancel grants nothing.
@immutable
class KitRequestAlwaysAllowStep {
  const KitRequestAlwaysAllowStep({
    required this.what,
    required this.covers,
    required this.onConfirmed,
    this.buttonKey,
    this.confirmKey,
  });

  /// The exact command, file or tool the confirm names ("Always allow
  /// `git status` in this project?").
  final String what;

  /// What the grant covers (the server's patterns, or the command itself);
  /// null says all matching requests. The confirm adds where to take it back.
  final String? covers;

  /// Sends the one "always" answer, after the person confirmed.
  final VoidCallback onConfirmed;
  final Key? buttonKey;
  final Key? confirmKey;
}

/// question and choice with one answer: a tap sends
/// ([KitChoiceList.single] with `sends: true`). Shortcuts 1–9 on a PC.
final class KitRequestChoose<T> extends KitRequestAnswers {
  const KitRequestChoose({
    required this.choices,
    required this.onChosen,
    this.chosen,
    this.other,
  });

  /// At most [KitRequestCard.maxChoicesInPlace] are listed in place.
  final List<KitChoice<T>> choices;

  /// Called once per answer.
  final ValueChanged<T> onChosen;

  /// The value sent (sending): its row carries the receipt.
  final T? chosen;

  /// "Something else": a KitField with a KitDraft.
  final KitChoiceOther? other;

  /// The choices listed in the card.
  List<KitChoice<T>> get _inPlace =>
      choices.length > KitRequestCard.maxChoicesInPlace
      ? choices.take(KitRequestCard.maxChoicesInPlace - 1).toList()
      : choices;

  /// How many choices only the details sheet lists.
  int get _hidden => choices.length - _inPlace.length;

  /// The digit shortcut: sends the [index]th choice in place. False when
  /// there is no such choice (a digit beyond the list does nothing).
  bool _sendAt(int index, _KitRequestCardState card) {
    final shown = _inPlace;
    if (index < 0 || index >= shown.length || !shown[index].enabled) {
      return false;
    }
    if (card._mayAnswer()) onChosen(shown[index].value);
    return true;
  }

  Widget _build(
    BuildContext context,
    _KitRequestCardState card, {
    required String semanticsLabel,
  }) {
    final widget = card.widget;
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    if (widget.phase == KitRequestPhase.sending) {
      // The chosen row carries the receipt; the other rows go.
      final picked = [
        for (final choice in choices)
          if (chosen != null && choice.value == chosen) choice,
      ];
      if (picked.isEmpty) return card._answerLine(context);
      return KitChoiceList<T>.single(
        choices: picked.take(1).toList(),
        selected: chosen,
        onSelected: (_) {},
        sends: true,
        receipt: widget.receipt,
        semanticsLabel: semanticsLabel,
      );
    }
    final other = this.other;
    final list = IgnorePointer(
      ignoring: card._answered,
      child: KitChoiceList<T>.single(
        choices: _inPlace,
        selected: null,
        // KitChoiceList already sent the haptic and holds its own guard.
        onSelected: (value) {
          if (card._mayAnswer(haptic: false)) onChosen(value);
        },
        sends: true,
        semanticsLabel: semanticsLabel,
        other: other == null
            ? null
            : KitChoiceOther(
                label: other.label,
                fieldLabel: other.fieldLabel,
                draft: other.draft,
                fieldKey: other.fieldKey,
                onSubmitted: (text) {
                  if (card._mayAnswer(haptic: false)) other.onSubmitted(text);
                },
              ),
      ),
    );
    final hidden = _hidden;
    if (hidden <= 0) return list;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        list,
        Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space1),
          child: KitInset(
            child: KitButton.tertiary(
              label: l10n.kitRequestMoreAnswers(hidden),
              onPressed: widget.onDetails,
            ),
          ),
        ),
      ],
    );
  }
}

/// A question with several answers: the card shows "Answer" (opens the
/// sheet); the sheet shows KitChoiceList.multi and a pinned Send.
final class KitRequestChooseMany<T> extends KitRequestAnswers {
  const KitRequestChooseMany({
    required this.choices,
    required this.onSend,
    this.sendLabel,
  });

  final List<KitChoice<T>> choices;
  final ValueChanged<Set<T>> onSend;

  /// The sheet's Send; default "Send".
  final String? sendLabel;
}

/// reply (an external task asks for words): a multiline KitField with a
/// draft and Send in place.
final class KitRequestReply extends KitRequestAnswers {
  const KitRequestReply({
    required this.fieldLabel,
    required this.draft,
    required this.onSend,
    this.sendLabel,
    this.fieldKey,
    this.sendKey,
  });

  /// "Your reply".
  final String fieldLabel;

  /// DATA-1: survives back, kill, restart. The host clears it once the
  /// answer is confirmed; the card never does.
  final KitDraft draft;
  final ValueChanged<String> onSend;

  /// Default "Send".
  final String? sendLabel;
  final Key? fieldKey;
  final Key? sendKey;
}

/// form, or anything too long to answer in place: one primary that opens
/// the sheet ([KitRequestCard.ask]'s `onDetails`). Default label "Answer".
final class KitRequestInSheet extends KitRequestAnswers {
  const KitRequestInSheet({this.label, this.key});

  final String? label;
  final Key? key;
}

/// The one answer card, in the conversation, for everything an agent asks
/// the person: OC1 permissions and questions, OC2 forms, ```choices```
/// blocks, AI Team gates and external-task replies
/// (docs/ux-system/kit-api/KitRequestCard.md; kit-v2 §2.1).
///
/// It says who asks about what and why, holds the common answer in place,
/// opens one details sheet, and after an answer collapses to a one-line row
/// carrying its [KitReceipt]. With [KitNeedsYou] it is the only part that
/// draws the needs-you look (LOOK-24): `surface1` under `attentionSurface`,
/// an `attentionLine` hairline, 22 dp corners and a 4 dp attention ring at
/// 6 % with no blur.
///
/// States: `waiting` (per kind), `waiting-refused` (a refused receipt above
/// the answers), `disabled` (the decide answers with their reason),
/// `sending`, `sending-escalated` (not confirmed yet, Try again),
/// `answered` (collapsed, Undo inside the window), `answered-elsewhere`,
/// `expired`. Working = sending; answered = answered or answeredElsewhere;
/// there is no loading or empty: the card exists only once the request
/// does.
///
/// One answer, once (G9): after the first answer (a tap, a shortcut or
/// Send) every answer control ignores input until the host rebuilds with a
/// new [phase] or [receipt]. [KitHaptics.send] fires once per answer.
///
/// On a fine pointer from [KitWindow.expanded], once the card has focus: A
/// allows, D rejects, 1–9 send the nth choice in place (physical keys, so
/// any keyboard layout). Ignored while focus is in the card's field.
class KitRequestCard extends StatefulWidget {
  /// The v2 card. Every new request uses this constructor.
  const KitRequestCard.ask({
    super.key,
    required KitRequestKind this.kind,
    required this.title,
    required String this.who,
    required KitNeedsYouReason this.reason,
    required String this.ifIgnored,
    required this.announcement,
    this.phase = KitRequestPhase.waiting,
    this.server,
    this.detail,
    this.summary,
    this.answers,
    this.answer,
    this.receipt,
    this.onDetails,
    this.since,
    this.tertiary = const [],
    IconData? icon,
    this.titleKey,
    this.detailsKey,
  }) : _icon = icon,
       tone = null,
       body = null,
       primary = null,
       secondary = null,
       _ask = true;

  /// Retired by kit-KitRequestCard-v2: use [KitRequestCard.ask].
  /// The pre-v2 constructor, kept working (KIT-43, never @Deprecated); its
  /// calls take the v2 frame. chat-5, shared-shell-1 and screen-team-1 bring
  /// its calls to zero.
  const KitRequestCard({
    super.key,
    required IconData icon,
    required this.title,
    required this.announcement,
    this.tone,
    this.summary,
    this.detail,
    this.body,
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.titleKey,
  }) : _icon = icon,
       kind = null,
       who = null,
       reason = null,
       ifIgnored = null,
       phase = KitRequestPhase.waiting,
       server = null,
       answers = null,
       answer = null,
       receipt = null,
       onDetails = null,
       since = null,
       detailsKey = null,
       _ask = false;

  /// More choices than this are not listed in place: the first
  /// (maxChoicesInPlace − 1) show, then "{n} more answers" opens Details.
  static const int maxChoicesInPlace = 5;

  /// permission: permissions · question: question · form: editNote ·
  /// choice: checks · gate: policy · reply: reply.
  static IconData iconFor(KitRequestKind kind) => switch (kind) {
    KitRequestKind.permission => AppIconography.permissions,
    KitRequestKind.question => AppIconography.question,
    KitRequestKind.form => AppIconography.editNote,
    KitRequestKind.choice => AppIconography.checks,
    KitRequestKind.gate => AppIconography.policy,
    KitRequestKind.reply => AppIconography.reply,
  };

  final bool _ask;
  final IconData? _icon;

  /// The tile's glyph: the given icon, or [iconFor] the kind.
  IconData get icon => _icon ?? iconFor(kind!);

  /// Null on the pre-v2 constructor.
  final KitRequestKind? kind;

  /// The ask in one line; permissions per COPY-15.
  final String title;

  /// Who asks: "fox", "Reviewer". Null on the pre-v2 constructor.
  final String? who;

  /// G37; words from [KitNeedsYou.reasonWord].
  final KitNeedsYouReason? reason;

  /// G37, AUTO-10: "The agent waits; nothing is lost."
  final String? ifIgnored;

  /// Read by a screen reader once when the card appears, for example
  /// "Permission needed: Run a shell command".
  final String announcement;
  final KitRequestPhase phase;

  /// Only when the request comes from another server: "laptop".
  final String? server;

  /// Why it asks, one line: "To check the fix".
  final String? detail;

  /// A command, path or pattern: mono, left to right, two lines.
  final String? summary;

  /// Null: nothing in place (Details only).
  final KitRequestAnswers? answers;

  /// The answer in words once sent: "Run once".
  final String? answer;

  /// Required from `sending` on; in `waiting`, a refused or not-confirmed
  /// earlier attempt shown above the answers.
  final KitReceipt? receipt;

  /// Opens the request's details sheet (showKitRequestSheet).
  final VoidCallback? onDetails;

  /// When it was asked: the age words.
  final DateTime? since;

  /// At most one more act on `.ask` ("See the change"); never "Always
  /// allow" (KIT-30).
  final List<KitAction> tertiary;
  final Key? titleKey;
  final Key? detailsKey;

  /// Pre-v2: [AppStatusTone.attention] paints the needs-you look; every
  /// other tone (and null) a plain `surface1` card.
  final AppStatusTone? tone;

  /// Pre-v2: the answer controls when the card holds them.
  final Widget? body;

  /// Pre-v2 actions in the one hierarchy.
  final KitAction? primary;
  final KitAction? secondary;

  @override
  State<KitRequestCard> createState() => _KitRequestCardState();
}

/// G37's debug checks on [KitRequestCard.ask]; each fires an
/// [AssertionError] on its bad configuration.
bool _debugCheckAsk(KitRequestCard card) {
  if (!card._ask) return true;
  final phase = card.phase;
  final receipt = card.receipt;
  final answers = card.answers;
  final kind = card.kind!;
  assert(
    card.ifIgnored!.trim().isNotEmpty,
    'KitRequestCard.ask: ifIgnored says what happens if nobody answers '
    '(G37, AUTO-10)',
  );
  switch (phase) {
    case KitRequestPhase.waiting:
      break;
    case KitRequestPhase.sending:
      assert(
        receipt != null && receipt.since != null,
        'KitRequestCard.ask: sending needs a receipt with since, so the 8 s '
        'escalation runs (STATE-5)',
      );
      assert(
        (card.answer?.trim() ?? '').isNotEmpty,
        'KitRequestCard.ask: sending needs the answer in words',
      );
    case KitRequestPhase.answered:
      assert(
        receipt?.state == KitReceiptState.confirmed,
        'KitRequestCard.ask: answered needs a confirmed receipt: never '
        '"answered" before the echo (STATE-10)',
      );
    case KitRequestPhase.answeredElsewhere:
      assert(
        receipt?.state == KitReceiptState.answeredElsewhere,
        'KitRequestCard.ask: answeredElsewhere needs an answeredElsewhere '
        'receipt',
      );
    case KitRequestPhase.expired:
      assert(
        receipt == null,
        'KitRequestCard.ask: expired offers nothing, so it has no receipt',
      );
  }
  assert(
    !(answers is KitRequestInSheet || answers is KitRequestChooseMany) ||
        card.onDetails != null,
    'KitRequestCard.ask: an answer in the sheet needs onDetails',
  );
  assert(
    answers is! KitRequestChoose ||
        answers.choices.length <= KitRequestCard.maxChoicesInPlace ||
        card.onDetails != null,
    'KitRequestCard.ask: more choices than fit in place need onDetails',
  );
  assert(
    card.tertiary.length <= 1,
    'KitRequestCard.ask: at most one tertiary act; Details takes the other '
    'slot (LAY-13)',
  );
  final fits = switch (answers) {
    null || KitRequestInSheet() => true,
    KitRequestDecide() =>
      kind == KitRequestKind.permission || kind == KitRequestKind.gate,
    KitRequestChoose() || KitRequestChooseMany() =>
      kind == KitRequestKind.question || kind == KitRequestKind.choice,
    KitRequestReply() => kind == KitRequestKind.reply,
  };
  assert(fits, 'KitRequestCard.ask: ${answers.runtimeType} does not fit $kind');
  return true;
}

class _KitRequestCardState extends State<KitRequestCard> {
  final FocusNode _focus = FocusNode(debugLabel: 'kit-request-card');

  /// The tapped-once guard (G9).
  bool _answered = false;

  @override
  void initState() {
    super.initState();
    assert(_debugCheckAsk(widget));
  }

  @override
  void didUpdateWidget(covariant KitRequestCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    assert(_debugCheckAsk(widget));
    final before = oldWidget.receipt;
    final now = widget.receipt;
    if (oldWidget.phase != widget.phase ||
        before?.state != now?.state ||
        before?.since != now?.since ||
        before?.at != now?.at) {
      _answered = false;
    }
  }

  @override
  void dispose() {
    _focus.dispose();
    super.dispose();
  }

  /// True once per answer: the first answer locks every control until the
  /// host passes a new phase or receipt.
  bool _mayAnswer({bool haptic = true}) {
    if (_answered || widget.phase != KitRequestPhase.waiting) return false;
    if (haptic) KitHaptics.send(context);
    setState(() => _answered = true);
    return true;
  }

  bool _shortcutsOn(BuildContext context) =>
      KitLayout.finePointer(context) && KitLayout.windowOf(context).isWide;

  static const _digits = [
    PhysicalKeyboardKey.digit1,
    PhysicalKeyboardKey.digit2,
    PhysicalKeyboardKey.digit3,
    PhysicalKeyboardKey.digit4,
    PhysicalKeyboardKey.digit5,
    PhysicalKeyboardKey.digit6,
    PhysicalKeyboardKey.digit7,
    PhysicalKeyboardKey.digit8,
    PhysicalKeyboardKey.digit9,
  ];

  static const _numpad = [
    PhysicalKeyboardKey.numpad1,
    PhysicalKeyboardKey.numpad2,
    PhysicalKeyboardKey.numpad3,
    PhysicalKeyboardKey.numpad4,
    PhysicalKeyboardKey.numpad5,
    PhysicalKeyboardKey.numpad6,
    PhysicalKeyboardKey.numpad7,
    PhysicalKeyboardKey.numpad8,
    PhysicalKeyboardKey.numpad9,
  ];

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    if (!_shortcutsOn(context) || widget.phase != KitRequestPhase.waiting) {
      return KeyEventResult.ignored;
    }
    if (HardwareKeyboard.instance.isControlPressed ||
        HardwareKeyboard.instance.isMetaPressed ||
        HardwareKeyboard.instance.isAltPressed) {
      return KeyEventResult.ignored;
    }
    // Typing in Something else or a reply is words, not answers.
    final focused = FocusManager.instance.primaryFocus?.context;
    if (focused != null &&
        (focused.widget is EditableText ||
            focused.findAncestorWidgetOfExactType<EditableText>() != null)) {
      return KeyEventResult.ignored;
    }
    final key = event.physicalKey;
    final answers = widget.answers;
    if (answers is KitRequestDecide) {
      final act = key == PhysicalKeyboardKey.keyA
          ? answers.onAllow
          : key == PhysicalKeyboardKey.keyD
          ? answers.onReject
          : null;
      if (act == null) return KeyEventResult.ignored;
      if (_mayAnswer()) act();
      return KeyEventResult.handled;
    }
    if (answers is KitRequestChoose) {
      var index = _digits.indexOf(key);
      if (index < 0) index = _numpad.indexOf(key);
      if (index < 0) return KeyEventResult.ignored;
      return answers._sendAt(index, this)
          ? KeyEventResult.handled
          : KeyEventResult.ignored;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final Widget content;
    if (!widget._ask) {
      content = _legacy(context);
    } else {
      final collapsed = switch (widget.phase) {
        KitRequestPhase.answered ||
        KitRequestPhase.answeredElsewhere ||
        KitRequestPhase.expired => true,
        _ => false,
      };
      // MOT-5: a cross-fade that keeps the scroll anchor, never a fold.
      content = AnimatedSwitcher(
        duration: KitMotion.reduced(context) ? Duration.zero : KitMotion.quick,
        switchInCurve: KitMotion.enter,
        switchOutCurve: KitMotion.exit,
        child: KeyedSubtree(
          key: ValueKey(collapsed),
          child: collapsed ? _collapsed(context) : _card(context),
        ),
      );
    }
    final tokens = KitTokens.of(context);
    return KitEntrance(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: KitLayout.readingWidth),
          child: Padding(
            padding: EdgeInsetsDirectional.symmetric(
              // The card's border sits on the page gutter (the ring adds
              // its own width around it), like every other block.
              horizontal: tokens.gutter - KitTokens.needsYouRingWidth,
              vertical: tokens.space1,
            ),
            child: content,
          ),
        ),
      ),
    );
  }

  // ── The frame ──────────────────────────────────────────────────────────

  /// The card's frame: the needs-you look when [attention] (with the 4 dp
  /// ring), else a plain `surface1` card with a hairline; the focus ring
  /// on its border while the card itself has keyboard focus.
  Widget _frame(
    BuildContext context, {
    required bool attention,
    required Widget child,
    Key? key,
  }) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final focused =
        _focus.hasPrimaryFocus &&
        FocusManager.instance.highlightMode == FocusHighlightMode.traditional;
    final side = focused
        ? BorderSide(
            color: roles.accent,
            width: KitTokens.focusRingWidth(context),
          )
        : BorderSide(
            color: attention ? roles.attentionLine : roles.hairline,
            width: KitTokens.hairlineWidth(context),
          );
    final large = MediaQuery.textScalerOf(context).scale(10) >= 20;
    Widget card = DecoratedBox(
      key: key,
      decoration: ShapeDecoration(
        color: attention
            ? Color.alphaBlend(roles.attentionSurface, roles.surface1)
            : roles.surface1,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(tokens.cardRadius),
          side: side,
        ),
      ),
      child: Padding(padding: EdgeInsets.all(tokens.space4), child: child),
    );
    // At 2.0 text never more than 45 % of the window: the words scroll
    // inside and the answers stay in sight.
    if (large) {
      final largest = MediaQuery.textScalerOf(context).scale(10) >= 25;
      card = ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight:
              MediaQuery.sizeOf(context).height *
              (largest
                  ? KitTokens.requestMaxHeightShareLarge
                  : KitTokens.requestMaxHeightShare),
        ),
        child: card,
      );
    }
    // The v2 card is one Tab stop; the pre-v2 card keeps today's traversal.
    card = Focus(
      focusNode: _focus,
      canRequestFocus: widget._ask,
      skipTraversal: !widget._ask,
      onKeyEvent: _onKey,
      onFocusChange: (_) => setState(() {}),
      child: card,
    );
    if (!attention) {
      return Padding(
        padding: const EdgeInsetsDirectional.symmetric(
          horizontal: KitTokens.needsYouRingWidth,
        ),
        child: card,
      );
    }
    // LOOK-20: the one ring the needs-you look allows, outside the border.
    return DecoratedBox(
      decoration: ShapeDecoration(
        color: roles.attention.withValues(alpha: KitTokens.needsYouRingAlpha),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(
            tokens.cardRadius + KitTokens.needsYouRingWidth,
          ),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(KitTokens.needsYouRingWidth),
        child: card,
      ),
    );
  }

  Widget _tile(BuildContext context, {required bool attention}) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final tint = attention
        ? roles.attention.withValues(alpha: tokens.markTintAlpha)
        : roles.surface3;
    return ExcludeSemantics(
      child: SizedBox.square(
        dimension: KitTokens.requestTileSize,
        child: DecoratedBox(
          decoration: ShapeDecoration(
            color: tint,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(KitTokens.requestTileRadius),
            ),
          ),
          child: Center(
            child: Icon(
              widget.icon,
              size: tokens.smallIconSize,
              color: attention ? roles.attention : roles.text1,
            ),
          ),
        ),
      ),
    );
  }

  Widget _summary(BuildContext context, String summary) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space3),
      child: DecoratedBox(
        decoration: ShapeDecoration(
          color: tokens.roles.ground,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(tokens.codeRadius),
          ),
        ),
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: tokens.space3,
            vertical: tokens.space2,
          ),
          // A command or a path reads left to right inside any language; the
          // full value is the node's label (A11Y-8).
          child: Semantics(
            container: true,
            label: summary,
            textDirection: TextDirection.ltr,
            child: ExcludeSemantics(
              child: SizedBox(
                width: double.infinity,
                child: Text(
                  summary,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  textDirection: TextDirection.ltr,
                  textAlign: TextAlign.start,
                  style: KitText.styleOf(
                    context,
                    KitTextRole.mono,
                    tone: KitTextTone.primary,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// The words above the answers: scroll together at 2.0 text.
  Widget _scrollWhenLarge(BuildContext context, Widget child) {
    final large = MediaQuery.textScalerOf(context).scale(10) >= 20;
    return large ? Flexible(child: SingleChildScrollView(child: child)) : child;
  }

  // ── The v2 card ────────────────────────────────────────────────────────

  Widget _card(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final reasonWord = KitNeedsYou.reasonWord(context, widget.reason!);
    final who = KitBidi.auto(widget.who!);
    final server = widget.server;
    final whoOnServer = server == null || server.isEmpty
        ? who
        : l10n.kitNeedsYouWhoOnServer(who, KitBidi.auto(server));
    final detail = widget.detail;
    final summary = widget.summary;
    final waiting = widget.phase == KitRequestPhase.waiting;

    final (inScroll, fixed) = _answers(context);
    final tertiary = _tertiaryLine(context);

    return KitSince(
      since: widget.since,
      ticks: KitSinceTicks.minutes,
      builder: (context, status) {
        final age = widget.since == null
            ? null
            : l10n.kitRequestAge(KitSince.ageLabel(context, status.elapsed));
        final ageSpoken = widget.since == null
            ? null
            : l10n.kitNeedsYouWaitingSpoken(status.elapsed.inMinutes);
        final label = [
          widget.title,
          reasonWord,
          whoOnServer,
          ?ageSpoken,
          if (detail != null && detail.isNotEmpty) detail,
          widget.ifIgnored!,
        ].join(', ');

        final header = Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _tile(context, attention: true),
            SizedBox(width: tokens.space3),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  KitText(
                    [reasonWord, whoOnServer, ?age].join(' · '),
                    role: KitTextRole.caption,
                    tone: waiting
                        ? KitTextTone.attention
                        : KitTextTone.secondary,
                  ),
                  SizedBox(height: tokens.space1),
                  KitText(
                    widget.title,
                    key: widget.titleKey,
                    role: KitTextRole.headline,
                    tone: KitTextTone.primary,
                  ),
                  if (detail != null && detail.isNotEmpty)
                    Padding(
                      padding: EdgeInsetsDirectional.only(top: tokens.space1),
                      child: KitText(
                        detail,
                        role: KitTextRole.secondary,
                        tone: KitTextTone.secondary,
                      ),
                    ),
                  Padding(
                    padding: EdgeInsetsDirectional.only(top: tokens.space1),
                    child: KitText(
                      widget.ifIgnored!,
                      role: KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                    ),
                  ),
                ],
              ),
            ),
          ],
        );

        final words = Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Read once when the card appears (A11Y-3): the same words on
            // every rebuild, so a live region never repeats them.
            Semantics(
              container: true,
              liveRegion: true,
              label: widget.announcement,
              child: ExcludeSemantics(child: header),
            ),
            if (summary != null && summary.isNotEmpty)
              _summary(context, summary),
            if (waiting && widget.receipt != null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space3),
                child: widget.receipt!,
              ),
            if (inScroll != null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space3),
                child: inScroll,
              ),
          ],
        );

        // The needs-you look holds until the answer lands (the escalated
        // "Not confirmed yet · Try again" still asks for the person); the
        // collapsed row drops it (LOOK-4).
        return Semantics(
          container: true,
          explicitChildNodes: true,
          label: label,
          child: _frame(
            context,
            attention: true,
            key: const ValueKey('kit-request-card'),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _scrollWhenLarge(context, words),
                if (fixed != null)
                  Padding(
                    padding: EdgeInsetsDirectional.only(top: tokens.space3),
                    child: fixed,
                  ),
                if (tertiary != null)
                  Padding(
                    padding: EdgeInsetsDirectional.only(top: tokens.space2),
                    child: tertiary,
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  /// The answers: (what scrolls with the words at 2.0 text, what stays in
  /// sight under them).
  (Widget?, Widget?) _answers(BuildContext context) {
    final answers = widget.answers;
    if (widget.phase == KitRequestPhase.sending) {
      if (answers is KitRequestChoose) {
        return (
          null,
          answers._build(context, this, semanticsLabel: widget.title),
        );
      }
      return (null, _answerLine(context));
    }
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return switch (answers) {
      null => (null, null),
      KitRequestDecide() => (null, _decide(context, answers)),
      KitRequestChoose() => (
        answers._build(context, this, semanticsLabel: widget.title),
        null,
      ),
      KitRequestChooseMany() => (
        null,
        KitButton.primary(
          label: l10n.kitRequestAnswer,
          onPressed: widget.onDetails,
        ),
      ),
      KitRequestInSheet() => (
        null,
        KitButton.primary(
          key: answers.key,
          label: answers.label ?? l10n.kitRequestAnswer,
          onPressed: widget.onDetails,
        ),
      ),
      KitRequestReply() => _reply(context, answers),
    };
  }

  /// "{answer} · {receipt}" ("Run once · Sending…").
  Widget _answerLine(BuildContext context) {
    final tokens = KitTokens.of(context);
    final receipt = widget.receipt;
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: tokens.space2,
      children: [
        KitText(
          '${widget.answer ?? ''} ·',
          role: KitTextRole.secondary,
          tone: KitTextTone.primary,
        ),
        ?receipt,
      ],
    );
  }

  Widget _decide(BuildContext context, KitRequestDecide answers) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final gate = widget.kind == KitRequestKind.gate;
    final allowLabel =
        answers.allowLabel ??
        (gate ? l10n.kitRequestApprove : l10n.kitRequestAllowOnce);
    final rejectLabel =
        answers.rejectLabel ??
        (gate ? l10n.kitRequestSendBack : l10n.kitRequestReject);
    final onAllow = answers.onAllow;
    final onReject = answers.onReject;
    final allow = KitButton(
      role: answers.secondary ? KitButtonRole.secondary : KitButtonRole.primary,
      key: answers.allowKey,
      label: allowLabel,
      shortcut: 'A',
      onPressed: onAllow == null
          ? null
          : () {
              if (_mayAnswer()) onAllow();
            },
    );
    final reject = KitButton.secondary(
      key: answers.rejectKey,
      label: rejectLabel,
      shortcut: 'D',
      onPressed: onReject == null
          ? null
          : () {
              if (_mayAnswer()) onReject();
            },
    );
    final reason = answers.disabledReason;
    final showReason = reason != null && (onAllow == null || onReject == null);
    final always = answers.alwaysAllow;
    final pair = LayoutBuilder(
      builder: (context, constraints) {
        final half = (constraints.maxWidth - tokens.space2) / 2;
        final fits =
            _buttonWidth(context, allowLabel) <= half &&
            _buttonWidth(context, rejectLabel) <= half;
        if (fits) {
          return Row(
            children: [
              Expanded(child: reject),
              SizedBox(width: tokens.space2),
              Expanded(child: allow),
            ],
          );
        }
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            allow,
            SizedBox(height: tokens.space2),
            reject,
          ],
        );
      },
    );
    final buttons = always == null
        ? pair
        : Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              pair,
              SizedBox(height: tokens.space2),
              KitButton.tertiary(
                key: always.buttonKey,
                label: l10n.chatUiAlwaysAllow,
                onPressed: onAllow == null
                    ? null
                    : () => unawaited(_confirmAlways(always, l10n)),
              ),
            ],
          );
    if (!showReason) return buttons;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        buttons,
        Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space2),
          child: KitText(
            reason,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ),
      ],
    );
  }

  /// The one plain confirm before a standing grant; Cancel, a dismissal or
  /// an answer that landed meanwhile send nothing.
  Future<void> _confirmAlways(
    KitRequestAlwaysAllowStep always,
    AppLocalizations l10n,
  ) async {
    if (_answered || widget.phase != KitRequestPhase.waiting) return;
    final confirmed = await showKitConfirm(
      context,
      title: l10n.chatRequestAlwaysConfirm(KitBidi.ltr(always.what)),
      body: l10n.chatRequestAlwaysScope(
        always.covers == null
            ? l10n.chatUiAllMatchingRequests
            : KitBidi.ltr(always.covers!),
        l10n.chatRequestAlwaysInProject,
      ),
      confirmLabel: l10n.chatUiAlwaysAllow,
      icon: AppIconography.permissions,
      sheetKey: const ValueKey('request-always-confirm'),
      confirmKey: always.confirmKey,
    );
    if (!confirmed || !mounted) return;
    if (_mayAnswer()) always.onConfirmed();
  }

  /// A button's width for [label] on one line: the words at the button
  /// role and the text scale, plus the button's side padding.
  double _buttonWidth(BuildContext context, String label) {
    final tokens = KitTokens.of(context);
    final painter = TextPainter(
      text: TextSpan(
        text: label,
        style: KitText.styleOf(context, KitTextRole.button),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width + tokens.space6 * 2;
  }

  (Widget?, Widget?) _reply(BuildContext context, KitRequestReply answers) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final controller = answers.draft.controller;
    void send() {
      final text = controller.text.trim();
      if (text.isEmpty) return;
      if (_mayAnswer()) answers.onSend(text);
    }

    final field = KitField(
      label: answers.fieldLabel,
      kind: KitFieldKind.multiline,
      draft: answers.draft,
      fieldKey: answers.fieldKey,
      onSubmitted: (_) => send(),
    );
    final action = ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final empty = controller.text.trim().isEmpty;
        return KitActionBlock(
          primary: KitAction(
            key: answers.sendKey,
            label: answers.sendLabel ?? l10n.kitRequestSend,
            icon: AppIconography.send,
            onPressed: empty ? null : send,
            disabledReason: empty ? l10n.kitRequestReplyEmptyReason : null,
          ),
        );
      },
    );
    return (field, action);
  }

  /// "Details" and at most one more act, start-aligned (LAY-13).
  Widget? _tertiaryLine(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final answers = widget.answers;
    // The primary already opens the sheet: no second way to the same place.
    final sheetPrimary =
        widget.phase == KitRequestPhase.waiting &&
        (answers is KitRequestInSheet || answers is KitRequestChooseMany);
    final details = widget.onDetails;
    final actions = [
      if (details != null && !sheetPrimary)
        KitAction(
          key: widget.detailsKey,
          label: l10n.kitDetails,
          onPressed: details,
        ),
      ...widget.tertiary.take(1),
    ];
    if (actions.isEmpty) return null;
    return KitInset(
      child: Wrap(
        spacing: tokens.space2,
        runSpacing: tokens.space1,
        children: [
          for (final action in actions)
            KitButton.fromAction(action, role: KitButtonRole.tertiary),
        ],
      ),
    );
  }

  // ── The collapsed row ──────────────────────────────────────────────────

  /// Answered, answered elsewhere or expired: it no longer needs you
  /// (LOOK-4), so no card, border, ring or attention colour.
  Widget _collapsed(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final twoLines = MediaQuery.textScalerOf(context).scale(10) >= 13;
    final expired = widget.phase == KitRequestPhase.expired;
    final Widget outcome = expired
        ? Semantics(
            container: true,
            liveRegion: true,
            child: KitText(
              l10n.kitRequestExpired,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          )
        : widget.receipt!;
    return ConstrainedBox(
      key: const ValueKey('kit-request-row'),
      constraints: BoxConstraints(minHeight: tokens.rowHeight),
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space4),
        child: Row(
          children: [
            ExcludeSemantics(
              child: Icon(
                widget.icon,
                size: tokens.smallIconSize,
                color: roles.text2,
              ),
            ),
            SizedBox(width: tokens.space3),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  KitText(
                    widget.title,
                    key: widget.titleKey,
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                    maxLines: twoLines ? 2 : 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  outcome,
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── The pre-v2 card (KIT-43) ───────────────────────────────────────────

  Widget _legacy(BuildContext context) {
    final tokens = KitTokens.of(context);
    final attention = widget.tone == AppStatusTone.attention;
    final actions = KitActionBlock(
      primary: widget.primary,
      secondary: widget.secondary,
      tertiary: widget.tertiary,
    );
    final summary = widget.summary;
    final detail = widget.detail;
    final body = widget.body;
    final words = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _tile(context, attention: attention),
            SizedBox(width: tokens.space3),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Semantics(
                    container: true,
                    liveRegion: true,
                    label: widget.announcement,
                    excludeSemantics: true,
                    child: KitText(
                      widget.title,
                      key: widget.titleKey,
                      role: KitTextRole.headline,
                      tone: KitTextTone.primary,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (detail != null && detail.isNotEmpty)
                    Padding(
                      padding: EdgeInsetsDirectional.only(top: tokens.space1),
                      child: KitText(
                        detail,
                        role: KitTextRole.secondary,
                        tone: KitTextTone.secondary,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
        if (summary != null && summary.isNotEmpty) _summary(context, summary),
        if (body != null)
          Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space2),
            child: body,
          ),
      ],
    );
    return _frame(
      context,
      attention: attention,
      key: const ValueKey('kit-request-card'),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _scrollWhenLarge(context, words),
          if (!actions.isEmpty) ...[SizedBox(height: tokens.space3), actions],
        ],
      ),
    );
  }
}
