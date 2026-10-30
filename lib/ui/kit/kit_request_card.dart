import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'chat/kit_markdown.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_choice_list.dart';
import 'kit_code_surface.dart';
import 'kit_field.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_needs_you.dart';
import 'kit_receipt.dart';
import 'kit_sheet.dart';
import 'kit_since.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tappable.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';
import 'motion/kit_reveal.dart';
import 'style/kit_style.dart' show KitStyleGlow, kitStyleSpec;
import 'style/kit_signal.dart' show KitSignalAspect, KitTrack, kitSignalHead;
import 'style/kit_signal_slip.dart';

part 'kit_request_answers.dart';
part 'kit_request_question.dart';
part 'kit_request_row.dart';
part 'kit_request_shape.dart';

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
    this.detailMarkdown = false,
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
    this.inList = false,
    this.queued = 0,
    this.targets = const [],
    this.look = KitRequestLook.standard,
  }) : _icon = icon,
       tone = null,
       body = null,
       primary = null,
       secondary = null,
       _ask = true,
       _place = null,
       _onOpen = null,
       _openLabel = null,
       _openKey = null;

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
       detailMarkdown = false,
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
       inList = false,
       queued = 0,
       targets = const [],
       look = KitRequestLook.standard,
       _ask = false,
       _place = null,
       _onOpen = null,
       _openLabel = null,
       _openKey = null;

  /// The Inbox's row (docs/qa/vision-inbox-2026-10-20/): a list of its own,
  /// with no row above to name the place, so the request starts with the
  /// amber needs-you dot and [place] ("Laptop · shopfront · Fix the build"),
  /// then the question, the command in one mono box and the answers at the
  /// row's end. The place line and the question are one target that opens
  /// the conversation ([openLabel] says so). A [KitRequestDecide] answers in
  /// place; a request with no answers (a question or form owner decision 1
  /// sends to the chat) has one "Open" ([openKey]). The answer, receipt,
  /// Undo and once-only guard are the card's own. Any other answer kind
  /// keeps the card's shape.
  KitRequestCard asRow({
    required String place,
    required VoidCallback onOpen,
    String? openLabel,
    Key? openKey,
  }) => _ask && (answers is KitRequestDecide || answers == null)
      ? KitRequestCard._row(
          this,
          place: place,
          onOpen: onOpen,
          openLabel: openLabel,
          openKey: openKey,
        )
      : this;

  KitRequestCard._row(
    KitRequestCard card, {
    required String place,
    required VoidCallback onOpen,
    String? openLabel,
    Key? openKey,
  }) : kind = card.kind,
       title = card.title,
       who = card.who,
       reason = card.reason,
       ifIgnored = card.ifIgnored,
       announcement = card.announcement,
       phase = card.phase,
       server = card.server,
       detail = card.detail,
       detailMarkdown = card.detailMarkdown,
       summary = card.summary,
       answers = card.answers,
       answer = card.answer,
       receipt = card.receipt,
       onDetails = card.onDetails,
       since = card.since,
       tertiary = card.tertiary,
       _icon = card._icon,
       titleKey = card.titleKey,
       detailsKey = card.detailsKey,
       inList = true,
       queued = card.queued,
       targets = card.targets,
       look = card.look,
       tone = null,
       body = null,
       primary = null,
       secondary = null,
       _ask = true,
       _place = place,
       _onOpen = onOpen,
       _openLabel = openLabel,
       _openKey = openKey,
       super(key: card.key);

  /// The Inbox row's place line, open target, its spoken name and the Open
  /// button's key ([asRow]); null for every other card.
  final String? _place;
  final VoidCallback? _onOpen;
  final String? _openLabel;
  final Key? _openKey;

  /// More choices than this are not listed in place: the first
  /// (maxChoicesInPlace − 1) show, then "{n} more answers" opens Details.
  static const int maxChoicesInPlace = 5;

  /// At most this many [targets] show in place; then "+{n} more", and
  /// Details lists them all.
  static const int maxTargetsInPlace = 3;

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

  /// [detail] is Markdown (a plan): drawn as formatted text in a bounded
  /// height, with Show more to read the rest in place.
  final bool detailMarkdown;

  /// A command, path or pattern: mono, left to right, two lines.
  final String? summary;

  /// What the request will run or touch, one mono line each (B's list):
  /// every path an edit names, the size of each change when the request
  /// carries it. In place of [summary] when given; the first
  /// [maxTargetsInPlace] show, then "+{n} more".
  final List<KitRequestTarget> targets;

  /// [KitRequestLook.question] in a conversation; every other place keeps
  /// the standard card.
  final KitRequestLook look;

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

  /// Under a list row: no reading-width centring, page gutter or entrance
  /// of its own (the list places it), as [KitAgentCard.inList]. The caller
  /// passes secondary answers, since the screen keeps its one primary.
  final bool inList;

  /// How many other requests of this conversation wait behind this one; the
  /// label says so ("Needs you · 1 more request is waiting").
  final int queued;

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
    KitRequestGo() => kind == KitRequestKind.gate,
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
    if (answers is KitRequestGo) {
      final go = answers.onGo;
      if (key != PhysicalKeyboardKey.keyA || go == null) {
        return KeyEventResult.ignored;
      }
      if (_mayAnswer()) go();
      return KeyEventResult.handled;
    }
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
          child: widget._place != null
              ? (collapsed ? _rowAnswered(context) : _rowAsk(context))
              : collapsed
              ? _collapsed(context)
              : widget.look == KitRequestLook.question
              ? _questionCard(context)
              : _card(context),
        ),
      );
    }
    return kitRequestPlacement(context, inList: widget.inList, child: content);
  }

  // ── The frame ──────────────────────────────────────────────────────────

  /// The card's frame ([kitRequestFrame]) with the focus border on it
  /// while the card itself has keyboard focus.
  Widget _frame(
    BuildContext context, {
    required bool attention,
    required Widget child,
    Key? key,
    bool edge = false,
    bool endsWithTap = false,
  }) {
    final focused =
        _focus.hasPrimaryFocus &&
        FocusManager.instance.highlightMode == FocusHighlightMode.traditional;
    return kitRequestFrame(
      context,
      key: key,
      attention: attention,
      focused: focused,
      edge: edge,
      endsWithTap: endsWithTap,
      // The v2 card is one Tab stop; the pre-v2 card keeps today's traversal.
      wrapCard: (card) => Focus(
        focusNode: _focus,
        canRequestFocus: widget._ask,
        skipTraversal: !widget._ask,
        onKeyEvent: _onKey,
        onFocusChange: (_) => setState(() {}),
        child: card,
      ),
      child: child,
    );
  }

  Widget _tile(BuildContext context, {required bool attention}) =>
      kitRequestTile(context, icon: widget.icon, attention: attention);

  Widget _summary(BuildContext context, String summary, {bool first = false}) {
    final tokens = KitTokens.of(context);
    if (KitTrack.on(context)) {
      // Signal: the command is printed on the slip itself, in firm mono,
      // a shell command after its prompt ("\$ git push origin main").
      final shell = widget.icon == AppIconography.terminal;
      return Padding(
        padding: first
            ? EdgeInsets.zero
            : EdgeInsetsDirectional.only(top: tokens.space3),
        child: Semantics(
          container: true,
          label: summary,
          textDirection: TextDirection.ltr,
          child: ExcludeSemantics(
            child: Text(
              shell ? '\$ $summary' : summary,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              textDirection: TextDirection.ltr,
              style: KitText.styleOf(
                context,
                KitTextRole.mono,
                tone: KitTextTone.primary,
              ).copyWith(fontWeight: FontWeight.w600),
            ),
          ),
        ),
      );
    }
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space3),
      // The command is code: under Paper and Soft the ground it is filled
      // with is the card's own face too, and the style's code edge (the
      // rule, the well) is what keeps it visible.
      child: kitCodeSurface(
        context,
        fill: tokens.roles.ground,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(tokens.codeRadius),
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
    return large
        ? Flexible(flex: 2, child: SingleChildScrollView(child: child))
        : child;
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
    final tertiary = _quietLine(context);
    final large = MediaQuery.textScalerOf(context).scale(10) >= 20;

    return KitSince(
      since: widget.since,
      ticks: KitSinceTicks.minutes,
      builder: (context, status) {
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

        // A question reads as its own words: the question is the headline
        // and the short header is left to Details. Everything else keeps the
        // title and the one line of why under it.
        final promote =
            (widget.kind == KitRequestKind.question ||
                widget.kind == KitRequestKind.choice) &&
            !widget.detailMarkdown &&
            detail != null &&
            detail.isNotEmpty;
        // Signal, under its row on a list: the row already says "Needs you"
        // and carries the conversation, so the slip holds what runs and the
        // keys (concept CD); a slip with nothing to print keeps its words.
        final underRow = widget.inList && KitTrack.on(context);
        final bare =
            underRow && !promote && summary != null && summary.isNotEmpty;
        final header = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!underRow || widget.queued > 0) ...[
              kitRequestNeedsYouTag(
                context,
                text: widget.queued > 0
                    ? '${l10n.kitRequestNeedsYouLabel} · '
                          '${l10n.chatRequestMoreWaiting(widget.queued).replaceFirst(RegExp(r'[.。]$'), '')}'
                    : l10n.kitRequestNeedsYouLabel,
                waiting: waiting,
              ),
              if (!bare) SizedBox(height: tokens.space1),
            ],
            if (!bare)
              KitText(
                promote ? detail : widget.title,
                key: widget.titleKey,
                role: KitTextRole.headline,
                tone: KitTextTone.primary,
              ),
            if (!bare && !promote && detail != null && detail.isNotEmpty)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space1),
                child: widget.detailMarkdown
                    ? _BoundedMarkdown(detail)
                    : KitText(
                        detail,
                        role: KitTextRole.secondary,
                        tone: KitTextTone.secondary,
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
              _summary(context, summary, first: bare && widget.queued == 0),
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
            // At large text the quiet line scrolls with the words, so the
            // answers stay in sight; otherwise it sits under them.
            if (large && tertiary != null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space2),
                child: tertiary,
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
            endsWithTap: !large && tertiary != null,
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
                if (!large && tertiary != null)
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
      // At 2.0 text the way to look scrolls with the words, so the
      // question stays readable and the act stays in sight under them.
      KitRequestGo() when MediaQuery.textScalerOf(context).scale(10) >= 20 => (
        _goLook(context, answers),
        _go(context, answers, withLook: false),
      ),
      KitRequestGo() => (null, _go(context, answers)),
      KitRequestChoose() => (
        answers._build(context, this, semanticsLabel: widget.title),
        null,
      ),
      KitRequestChooseMany() => (
        null,
        answers.secondary
            ? KitButton.secondary(
                label: l10n.kitRequestAnswer,
                onPressed: widget.onDetails,
              )
            : KitButton.primary(
                label: l10n.kitRequestAnswer,
                onPressed: widget.onDetails,
              ),
      ),
      KitRequestInSheet() => (
        null,
        answers.secondary
            ? KitButton.secondary(
                key: answers.key,
                label: answers.label ?? l10n.kitRequestAnswer,
                onPressed: widget.onDetails,
              )
            : KitButton.primary(
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
    // Signal: Allow is the slip's green key wherever the slip is; on a
    // list it is still the screen's only colour (New is an ink key).
    final allow = KitButton(
      role: answers.secondary && !KitTrack.on(context)
          ? KitButtonRole.secondary
          : KitButtonRole.primary,
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
        // Stacked, the question look keeps the main answer at the bottom,
        // under the thumb, as it keeps it at the end side by side.
        final thumb = widget.look == KitRequestLook.question;
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            thumb ? reject : allow,
            SizedBox(height: tokens.space2),
            thumb ? allow : reject,
          ],
        );
      },
    );
    final buttons = pair;
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

  /// A gate's one act: [KitRequestGo.look] full width above, the named
  /// primary under the thumb, each on its own line so both read whole; the
  /// reason under them when the act is off.
  Widget? _goLook(BuildContext context, KitRequestGo answers) {
    final look = answers.look;
    if (look == null) return null;
    return KitButton.secondary(
      key: look.key,
      label: look.label,
      icon: look.icon,
      onPressed: look.onPressed,
    );
  }

  Widget _go(
    BuildContext context,
    KitRequestGo answers, {
    bool withLook = true,
  }) {
    final tokens = KitTokens.of(context);
    final onGo = answers.onGo;
    final look = withLook ? _goLook(context, answers) : null;
    final reason = answers.disabledReason;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (look != null) ...[look, SizedBox(height: tokens.space2)],
        KitButton(
          role: KitButtonRole.primary,
          key: answers.goKey,
          label: answers.label,
          icon: answers.icon,
          shortcut: 'A',
          working: answers.working,
          onPressed: onGo == null
              ? null
              : () {
                  if (_mayAnswer()) onGo();
                },
        ),
        if (onGo == null && reason != null)
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

  /// The quiet line under the answers (LAY-13): the standing grant and the
  /// one more act on the left, "Details" on the right, in small plain
  /// buttons. It wraps rather than clips at large text.
  Widget? _quietLine(
    BuildContext context, {
    bool centred = false,
    bool withDetails = true,
  }) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final answers = widget.answers;
    // The primary already opens the sheet: no second way to the same place.
    final sheetPrimary =
        widget.phase == KitRequestPhase.waiting &&
        (answers is KitRequestInSheet || answers is KitRequestChooseMany);
    final details = widget.onDetails;
    final always =
        widget.phase == KitRequestPhase.waiting && answers is KitRequestDecide
        ? answers.alwaysAllow
        : null;
    final decide = answers is KitRequestDecide ? answers : null;
    final disabled = decide?.disabledReason;
    Widget quiet(
      String label,
      VoidCallback? onTap, {
      Key? key,
      String? reason,
    }) => KitTappable(
      key: key,
      onTap: onTap,
      label: label,
      disabledReason: onTap == null ? (reason ?? label) : null,
      child: Padding(
        padding: EdgeInsets.symmetric(vertical: tokens.space3),
        child: centred
            // The question look underlines its quiet acts, as links.
            ? Text(
                label,
                textAlign: TextAlign.center,
                style:
                    KitText.styleOf(
                      context,
                      KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                    ).copyWith(
                      decoration: TextDecoration.underline,
                      decorationColor: tokens.roles.text3,
                    ),
              )
            : KitText(
                label,
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
      ),
    );
    final start = <Widget>[
      if (always != null)
        quiet(
          always.label ?? l10n.kitRequestAlwaysAllowLike,
          decide?.onAllow == null
              ? null
              : () => unawaited(_confirmAlways(always, l10n)),
          key: always.buttonKey,
          reason: disabled,
        ),
      for (final action in widget.tertiary.take(1))
        quiet(action.label, action.onPressed, key: action.key),
    ];
    final end = details != null && !sheetPrimary && withDetails
        ? quiet(l10n.kitDetails, details, key: widget.detailsKey)
        : null;
    if (start.isEmpty && end == null) return null;
    if (centred) {
      return Wrap(
        alignment: WrapAlignment.center,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: tokens.space6,
        children: [...start, ?end],
      );
    }
    // Alone, "Details" sits at the far end like it does beside a grant.
    if (start.isEmpty) {
      return Align(alignment: AlignmentDirectional.centerEnd, child: end);
    }
    // A Wrap, not a Row: at large text "Details" drops under the grant
    // instead of squeezing it into a sliver.
    return Wrap(
      alignment: WrapAlignment.spaceBetween,
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: tokens.space4,
      children: [...start, ?end],
    );
  }

  // ── The collapsed row ──────────────────────────────────────────────────

  /// Answered, answered elsewhere or expired: it no longer needs you
  /// (LOOK-4), so no card, border, ring or attention colour.
  Widget _collapsed(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final expired = widget.phase == KitRequestPhase.expired;
    return kitAnsweredRow(
      context,
      key: const ValueKey('kit-request-row'),
      icon: widget.icon,
      title: widget.title,
      titleKey: widget.titleKey,
      outcome: expired
          ? Semantics(
              container: true,
              liveRegion: true,
              child: KitText(
                l10n.kitRequestExpired,
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
            )
          : widget.receipt!,
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
          if (!actions.isEmpty) ...[
            SizedBox(height: tokens.space3),
            _actionsWhenLarge(context, actions),
          ],
        ],
      ),
    );
  }

  /// The buttons under the words: at 2.0 text, inside the card's height cap,
  /// they scroll too when they outgrow their third, the decide button first
  /// and so always in sight.
  Widget _actionsWhenLarge(BuildContext context, Widget actions) {
    final large = MediaQuery.textScalerOf(context).scale(10) >= 20;
    return large
        ? Flexible(child: SingleChildScrollView(child: actions))
        : actions;
  }
}

/// A plan or other long Markdown in a request card: formatted, a bounded
/// height first, and "Show more" to read the rest in place.
class _BoundedMarkdown extends StatefulWidget {
  const _BoundedMarkdown(this.data);

  final String data;

  @override
  State<_BoundedMarkdown> createState() => _BoundedMarkdownState();
}

class _BoundedMarkdownState extends State<_BoundedMarkdown> {
  static const _height = 168.0;
  bool _open = false;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // A plan this short needs no fold.
    final long =
        widget.data.length > 320 || '\n'.allMatches(widget.data).length > 7;
    final text = KitMarkdown(
      widget.data,
      role: KitTextRole.secondary,
      selectable: false,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (!long || _open)
          text
        else
          ConstrainedBox(
            constraints: const BoxConstraints(maxHeight: _height),
            child: ClipRect(
              child: SingleChildScrollView(
                physics: const NeverScrollableScrollPhysics(),
                child: text,
              ),
            ),
          ),
        if (long)
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: KitButton.tertiary(
              key: const Key('request-detail-toggle'),
              label: _open ? l10n.kitRequestShowLess : l10n.kitRequestShowMore,
              onPressed: () => setState(() => _open = !_open),
            ),
          ),
      ],
    );
  }
}
