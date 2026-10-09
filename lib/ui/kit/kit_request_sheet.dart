/// The one details sheet for a request (docs/ux-system/kit-api/
/// KitRequestSheet.md; kit-v2 §2.1, cut C15).
library;

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../widgets/request_routes.dart';
import 'kit_buttons.dart';
import 'kit_choice_list.dart';
import 'kit_code_block.dart';
import 'kit_diff_view.dart';
import 'chat/kit_markdown.dart';
import 'kit_field.dart';
import 'kit_key_value.dart';
import 'kit_layout.dart';
import 'kit_request_card.dart';
import 'kit_row_parts.dart';
import 'kit_sheet.dart';
import 'kit_since.dart';
import 'kit_technical_value.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';

/// How a request sheet closed. Returned by [showKitRequestSheet].
enum KitRequestSheetOutcome {
  /// The person answered here; the card now shows the receipt.
  answered,

  /// [RequestRoutes] closed it (answered on another device, or expired).
  answeredElsewhere,

  /// Back, swipe, Esc, Close, tap outside: the request still waits.
  dismissed,
}

/// "Always allow" as a risky switch (K2 §2.1, §2.6; KIT-30, SEC-9). Never
/// a primary. Turning it on first unfolds its scope in place; choosing a
/// duration answers the request with "always" and closes the sheet.
@immutable
class KitRequestAlwaysAllow {
  const KitRequestAlwaysAllow({
    required this.title,
    required this.scope,
    required this.onLabel,
    required this.onAllowAlways,
    this.until = const [KitUntil.conversation],
    this.switchKey,
  }) : assert(until.length >= 1 && until.length <= 3);

  /// "Always allow flutter test".
  final String title;

  /// "In this conversation, on laptop".
  final String scope;

  /// The status-line words while a saved rule is on.
  final String onLabel;

  /// Sends the "always" answer.
  final ValueChanged<KitUntil> onAllowAlways;

  /// What the server supports (1–3).
  final List<KitUntil> until;
  final Key? switchKey;
}

/// A note sent with the answer: a reason with Reject (permission), notes
/// with Send back (gate). Kept in a draft (DATA-1); the host reads its
/// controller when its reject callback runs, and clears it once the answer
/// is confirmed.
@immutable
class KitRequestMessage {
  const KitRequestMessage({
    required this.fieldLabel,
    required this.draft,
    this.helper,
    this.fieldKey,
  });

  /// "Tell the agent why (optional)".
  final String fieldLabel;

  /// Target `request.<requestId>.note`.
  final KitDraft draft;
  final String? helper;
  final Key? fieldKey;
}

/// Opens the details of [card] (a [KitRequestCard.ask]). The header is the
/// card's: its icon on the attention tile, its title, and "{who}[ on
/// {server}] · waiting 4 min" as the subtitle. The pinned answers come from
/// `card.answers`. Returns how it closed.
///
/// The variant is `card.kind`; each has fixed slots, top to bottom:
///
/// - permission: `ifIgnored`; [fullText] in a command block (mono, left to
///   right, copyable, never truncated); [change]; [alwaysAllow] as a risky
///   [KitSwitchRow]; [message]; [details]. Pinned: "Allow once" and
///   "Reject" ([KitRequestDecide]).
/// - question and choice: `ifIgnored`; [fullText] (the question); every
///   option ([KitChoiceList.single] that sends on a tap for
///   [KitRequestChoose], [KitChoiceList.multi] for [KitRequestChooseMany]);
///   [details]. Pinned: none for Choose; Send for ChooseMany, disabled with
///   "Choose at least one answer." until one is chosen.
/// - form: `ifIgnored`; [body]; [details]. Pinned: [submit].
/// - gate: `ifIgnored`; [fullText] (the plan); [change]; [message] (the
///   notes); [details]. Pinned: "Approve" and "Send back".
/// - reply: `ifIgnored`; [fullText] (the task's message); the reply field
///   (the card's [KitRequestReply.draft]). Pinned: Send, disabled with
///   "Type a reply first." while empty.
///
/// States: permission, permission-always (the risk scope unfolded),
/// permission-change (with the diff), question, question-many (Send
/// disabled with its reason), form (submit disabled with its reason),
/// form-discard (the question in place), gate, gate-confirm (the in-place
/// confirmation), reply, closed-elsewhere (a behaviour, with no picture).
/// No loading, empty or error of its own: a [change] still being fetched,
/// empty or failed is the [KitDiffView]'s own state; a failed answer is the
/// card's receipt, since the sheet has closed (STATE-10). Working: none,
/// answering closes the sheet at once and the card's `sending` phase is
/// the working state. Disabled: the pinned answers with their reason
/// (STATE-8).
///
/// Answering here calls the card's own callback exactly once, fires
/// [KitHaptics.send] once and closes with
/// [KitRequestSheetOutcome.answered]; every later tap or shortcut is
/// ignored. The receipt appears on the card only (one per request).
///
/// Closing by itself (AUTO-12): [routes] removes the route a frame after
/// the request stops pending, and the call completes with
/// [KitRequestSheetOutcome.answeredElsewhere]; nothing is sent. If [routes]
/// is already not pending, nothing opens.
///
/// Data safety (DATA-1, DATA-2, DATA-3): typed text lives in drafts (the
/// note, the reply, the form's [draft]), kept silently on back, swipe, Esc
/// and Close; the host clears them, never the sheet. With only [dirty],
/// back asks the discard question in place.
///
/// Keyboard (G14, LAY-10): Esc closes; on a fine pointer from
/// [KitWindow.expanded] up, while focus is on the sheet but not in a text
/// field, A allows, D rejects and 1–9 choose (physical keys). Enter never
/// answers a permission or a gate: it only activates the focused button.
///
/// A card that is no longer `waiting` (answered here and still sending)
/// opens without answers: there is nothing left to press.
/// How a request sheet draws its [showKitRequestSheet] `fullText`.
enum KitRequestText {
  /// A shell command: the mono block with its `$` prompt.
  command,

  /// A path, address or pattern list: the mono block, copyable, no prompt.
  block,

  /// Plain words (a mode change, a question).
  words,

  /// Markdown (a plan).
  markdown,
}

Future<KitRequestSheetOutcome> showKitRequestSheet(
  BuildContext context, {
  required KitRequestCard card,
  required RequestRoutes routes,
  String? fullText,
  KitRequestText? fullTextStyle,
  List<KitKeyValueRow> facts = const [],
  KitDiffView? change,
  KitRequestAlwaysAllow? alwaysAllow,
  KitRequestMessage? message,
  WidgetBuilder? body,
  KitAction? submit,
  KitDraft? draft,
  ValueListenable<bool>? dirty,
  List<KitTechnicalValue> details = const [],
  Key? sheetKey,
}) async {
  final kind = card.kind;
  assert(kind != null, 'showKitRequestSheet: the card is a KitRequestCard.ask');
  assert(
    alwaysAllow == null || kind == KitRequestKind.permission,
    'showKitRequestSheet: alwaysAllow is for a permission only (KIT-30)',
  );
  if (!routes.isPending) return KitRequestSheetOutcome.answeredElsewhere;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final answers = card.answers;
  final reply = answers is KitRequestReply ? answers : null;
  final session = _KitRequestSession(
    routes: routes,
    waiting: card.phase == KitRequestPhase.waiting,
    hostDirty: dirty,
    reply: reply?.draft.controller,
  );
  try {
    final (primary, secondary) = session.waiting
        ? _pinned(l10n, card, session, submit)
        : (null, null);
    // The kit's own live answers (a chosen set, a typed reply) rebuild the
    // frame's pinned block through its `dirty` listenable; its value stays
    // the host's own unsaved-input flag.
    final live = answers is KitRequestChooseMany || reply != null;
    await showKitSheet<void>(
      context,
      title: card.title,
      subtitle: _subtitle(context, l10n, card),
      icon: card.icon,
      tone: KitSheetTone.attention,
      height: change != null || body != null
          ? KitSheetHeight.full
          : KitSheetHeight.content,
      primary: primary,
      secondary: secondary,
      draft: draft ?? message?.draft ?? reply?.draft,
      dirty: live || dirty != null ? session : null,
      routes: routes,
      sheetKey: sheetKey,
      body: (sheetContext) => _KitRequestSheetBody(
        session: session,
        card: card,
        fullText: fullText,
        fullTextStyle: fullTextStyle,
        facts: facts,
        change: change,
        alwaysAllow: alwaysAllow,
        message: message,
        body: body,
        details: details,
      ),
    );
  } finally {
    session.dispose();
  }
  if (session.answered) return KitRequestSheetOutcome.answered;
  if (!routes.isPending) return KitRequestSheetOutcome.answeredElsewhere;
  return KitRequestSheetOutcome.dismissed;
}

/// "{who}[ on {server}] · waiting 4 min".
String _subtitle(
  BuildContext context,
  AppLocalizations l10n,
  KitRequestCard card,
) {
  final who = KitBidi.auto(card.who ?? '');
  final server = card.server;
  final whoOnServer = server == null || server.isEmpty
      ? who
      : l10n.kitNeedsYouWhoOnServer(who, KitBidi.auto(server));
  final since = card.since;
  if (since == null) return whoOnServer;
  final age = l10n.kitRequestAge(
    KitSince.ageLabel(context, clock.now().difference(since)),
  );
  return '$whoOnServer · $age';
}

/// The pinned answers by `card.answers`: (primary, secondary).
(KitAction?, KitAction?) _pinned(
  AppLocalizations l10n,
  KitRequestCard card,
  _KitRequestSession session,
  KitAction? submit,
) {
  final answers = card.answers;
  switch (answers) {
    case KitRequestDecide():
      final gate = card.kind == KitRequestKind.gate;
      final onAllow = answers.onAllow;
      final onReject = answers.onReject;
      final reason = answers.disabledReason;
      return (
        KitAction(
          label:
              answers.allowLabel ??
              (gate ? l10n.kitRequestApprove : l10n.kitRequestAllowOnce),
          shortcut: 'A',
          onPressed: onAllow == null ? null : () => session.answer(onAllow),
          disabledReason: onAllow == null ? reason : null,
        ),
        KitAction(
          label:
              answers.rejectLabel ??
              (gate ? l10n.kitRequestSendBack : l10n.kitRequestReject),
          shortcut: 'D',
          onPressed: onReject == null ? null : () => session.answer(onReject),
          // One reason line: under the first disabled answer only.
          disabledReason: onReject == null && onAllow != null ? reason : null,
        ),
      );
    case KitRequestChooseMany():
      return (
        _KitLiveAction(
          label: answers.sendLabel ?? l10n.kitRequestSend,
          icon: AppIconography.send,
          ready: () => session.chosen.isNotEmpty,
          reason: l10n.kitRequestChooseOneReason,
          run: () => session.sendMany(answers),
        ),
        null,
      );
    case KitRequestReply():
      return (
        _KitLiveAction(
          label: answers.sendLabel ?? l10n.kitRequestSend,
          icon: AppIconography.send,
          ready: () => answers.draft.controller.text.trim().isNotEmpty,
          reason: l10n.kitRequestReplyEmptyReason,
          run: () => session.sendReply(answers),
        ),
        null,
      );
    case KitRequestChoose() || KitRequestInSheet() || null:
      if (submit == null) return (null, null);
      final onPressed = submit.onPressed;
      return (
        KitAction(
          label: submit.label,
          icon: submit.icon,
          key: submit.key,
          working: submit.working,
          shortcut: submit.shortcut,
          disabledReason: submit.disabledReason,
          onPressed: onPressed == null ? null : () => session.answer(onPressed),
        ),
        null,
      );
  }
}

/// A pinned answer whose enabled state follows the kit's own input (a
/// chosen set, a typed reply). The frame rebuilds it through the session.
class _KitLiveAction extends KitAction {
  const _KitLiveAction({
    required super.label,
    required this.ready,
    required this.reason,
    required this.run,
    super.icon,
  }) : super(onPressed: null);

  final bool Function() ready;
  final String reason;
  final VoidCallback run;

  @override
  VoidCallback? get onPressed => ready() ? run : null;

  @override
  String? get disabledReason => ready() ? null : reason;
}

/// One open sheet: the one-answer guard, the route it closes, the kit's
/// live input, and the host's unsaved-input flag passed to the frame.
class _KitRequestSession extends ChangeNotifier
    implements ValueListenable<bool> {
  _KitRequestSession({
    required this.routes,
    required this.waiting,
    this.hostDirty,
    this.reply,
  }) {
    hostDirty?.addListener(notifyListeners);
    reply?.addListener(notifyListeners);
  }

  final RequestRoutes routes;

  /// The card waits for an answer; false opens without answers.
  final bool waiting;
  final ValueListenable<bool>? hostDirty;
  final TextEditingController? reply;

  ModalRoute<Object?>? route;
  FocusScopeNode? scope;
  BuildContext? context;

  /// False while an in-place confirmation hides the body.
  bool bodyShown = true;

  bool _answered = false;
  bool get answered => _answered;

  Set<Object?> _chosen = const {};
  Set<Object?> get chosen => _chosen;

  @override
  bool get value => hostDirty?.value ?? false;

  void choose(Set<Object?> chosen) {
    _chosen = chosen;
    notifyListeners();
  }

  /// Answers once: the guard, the send haptic, the card's own callback,
  /// then the sheet closes.
  void answer(VoidCallback act, {bool haptic = true}) {
    if (!waiting || _answered || !routes.isPending) return;
    _answered = true;
    final context = this.context;
    if (haptic && context != null && context.mounted) {
      KitHaptics.send(context);
    }
    act();
    _close();
  }

  void sendMany(KitRequestChooseMany<Object?> answers) {
    if (_chosen.isEmpty) return;
    final values = [
      for (final choice in answers.choices)
        if (_chosen.contains(choice.value)) choice.value,
    ];
    answer(() => _sendSet(answers, values));
  }

  /// [KitRequestChooseMany] reaches the kit through the sealed
  /// [KitRequestAnswers] without its type argument, so the set is built
  /// for the reified types hosts use (answer ids and indexes); any other
  /// `T` must be a supertype of Object?.
  static void _sendSet(
    KitRequestChooseMany<Object?> answers,
    List<Object?> values,
  ) => switch (answers) {
    KitRequestChooseMany<String>() => answers.onSend(Set<String>.from(values)),
    KitRequestChooseMany<int>() => answers.onSend(Set<int>.from(values)),
    _ => answers.onSend(values.toSet()),
  };

  void sendReply(KitRequestReply answers) {
    final text = answers.draft.controller.text.trim();
    if (text.isEmpty) return;
    answer(() => answers.onSend(text));
  }

  void _close() {
    final route = this.route;
    final navigator = route?.navigator;
    if (route == null || navigator == null || !route.isActive) return;
    if (route.isCurrent) {
      navigator.pop();
    } else {
      navigator.removeRoute(route);
    }
  }

  @override
  void dispose() {
    hostDirty?.removeListener(notifyListeners);
    reply?.removeListener(notifyListeners);
    super.dispose();
  }
}

/// The sheet's body: the variant's slots in order, and the card's
/// shortcuts while focus is on the sheet.
class _KitRequestSheetBody extends StatefulWidget {
  const _KitRequestSheetBody({
    required this.session,
    required this.card,
    required this.fullText,
    required this.fullTextStyle,
    required this.facts,
    required this.change,
    required this.alwaysAllow,
    required this.message,
    required this.body,
    required this.details,
  });

  final _KitRequestSession session;
  final KitRequestCard card;
  final String? fullText;
  final KitRequestText? fullTextStyle;
  final List<KitKeyValueRow> facts;
  final KitDiffView? change;
  final KitRequestAlwaysAllow? alwaysAllow;
  final KitRequestMessage? message;
  final WidgetBuilder? body;
  final List<KitTechnicalValue> details;

  @override
  State<_KitRequestSheetBody> createState() => _KitRequestSheetBodyState();
}

class _KitRequestSheetBodyState extends State<_KitRequestSheetBody> {
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

  _KitRequestSession get _session => widget.session;

  @override
  void initState() {
    super.initState();
    HardwareKeyboard.instance.addHandler(_onKey);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _session
      ..route = ModalRoute.of(context)
      ..scope = FocusScope.of(context)
      ..context = context
      // An in-place confirmation hides the body (and stops its tickers).
      ..bodyShown = TickerMode.valuesOf(context).enabled;
  }

  @override
  void dispose() {
    HardwareKeyboard.instance.removeHandler(_onKey);
    if (_session.context == context) _session.context = null;
    super.dispose();
  }

  /// The card's shortcuts (§8.2): physical keys, so any layout.
  bool _onKey(KeyEvent event) {
    if (event is! KeyDownEvent || !mounted) return false;
    final session = _session;
    final route = session.route;
    if (!session.waiting || session.answered || !session.bodyShown) {
      return false;
    }
    if (route == null || !route.isCurrent) return false;
    if (!KitLayout.finePointer(context) ||
        !KitLayout.windowOf(context).isWide) {
      return false;
    }
    final keyboard = HardwareKeyboard.instance;
    if (keyboard.isControlPressed ||
        keyboard.isMetaPressed ||
        keyboard.isAltPressed) {
      return false;
    }
    // Focus on this sheet, and not in a field: typing is words.
    final focus = FocusManager.instance.primaryFocus;
    final scope = session.scope;
    if (focus == null ||
        scope == null ||
        (focus != scope && !focus.ancestors.contains(scope))) {
      return false;
    }
    final focused = focus.context;
    if (focused != null &&
        (focused.widget is EditableText ||
            focused.findAncestorWidgetOfExactType<EditableText>() != null)) {
      return false;
    }
    final key = event.physicalKey;
    final answers = widget.card.answers;
    if (answers is KitRequestDecide) {
      final act = key == PhysicalKeyboardKey.keyA
          ? answers.onAllow
          : key == PhysicalKeyboardKey.keyD
          ? answers.onReject
          : null;
      if (act == null) return false;
      session.answer(act);
      return true;
    }
    if (answers is KitRequestChoose) {
      var index = _digits.indexOf(key);
      if (index < 0) index = _numpad.indexOf(key);
      return index >= 0 && _chooseAt(answers, index);
    }
    return false;
  }

  bool _chooseAt<T>(KitRequestChoose<T> answers, int index) {
    final choices = answers.choices;
    if (index >= choices.length || !choices[index].enabled) return false;
    final value = choices[index].value;
    _session.answer(() => answers.onChosen(value));
    return true;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final card = widget.card;
    final kind = card.kind ?? KitRequestKind.permission;
    final waiting = _session.waiting;
    final fullText = widget.fullText;
    final hasFullText = fullText != null && fullText.isNotEmpty;
    final change = widget.change;
    final message = widget.message;
    final alwaysAllow = widget.alwaysAllow;
    final ifIgnored = card.ifIgnored;
    final slots = <Widget>[
      if (ifIgnored != null && ifIgnored.isNotEmpty)
        KitText(
          ifIgnored,
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
      ...switch (kind) {
        KitRequestKind.permission => [
          if (hasFullText)
            _text(fullText, widget.fullTextStyle, change != null),
          if (widget.facts.isNotEmpty) KitKeyValue(rows: widget.facts),
          ?change,
          if (alwaysAllow != null && waiting) _always(alwaysAllow),
          if (message != null && waiting) _note(message),
        ],
        KitRequestKind.question || KitRequestKind.choice => [
          if (hasFullText)
            _text(
              fullText,
              widget.fullTextStyle ?? KitRequestText.words,
              false,
            ),
          ?_options(context),
        ],
        KitRequestKind.form => [
          if (widget.body case final body?) Builder(builder: body),
        ],
        KitRequestKind.gate => [
          if (hasFullText) _words(fullText),
          ?change,
          if (message != null && waiting) _note(message),
        ],
        KitRequestKind.reply => [
          if (hasFullText) _words(fullText),
          if (card.answers case final KitRequestReply reply when waiting)
            _reply(reply),
        ],
      },
    ];
    final details = widget.details;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final (i, slot) in slots.indexed) ...[
          if (i > 0) SizedBox(height: tokens.space4),
          slot,
        ],
        // Last, collapsed, in one fold (KIT-33).
        if (details.isNotEmpty && kind != KitRequestKind.reply) ...[
          SizedBox(height: tokens.sectionGap),
          KitDetailsFold(values: details),
        ],
      ],
    );
  }

  /// The whole command, path or pattern list: left to right, copyable,
  /// never truncated. It wraps (continuation lines hang past the `$`), so
  /// the tail of a command the person is asked to allow is never hidden
  /// behind a sideways scroll and its edge fade (KitRequestSheet.md: the
  /// full text wraps and never truncates).
  Widget _text(String text, KitRequestText? style, bool hasChange) =>
      switch (style ??
      (hasChange ? KitRequestText.block : KitRequestText.command)) {
        KitRequestText.words => _words(text),
        KitRequestText.markdown => KitMarkdown(text, selectable: true),
        final style => _command(text, path: style == KitRequestText.block),
      };

  Widget _command(String text, {required bool path}) => KitCodeBlock(
    text: text,
    kind: path ? KitCodeKind.output : KitCodeKind.command,
    maxLines: null,
    wrap: true,
    showWrapToggle: false,
    highlight: false,
  );

  /// The question, plan or message as sent (COPY-2), first-strong.
  Widget _words(String text) =>
      KitText(KitBidi.auto(text), role: KitTextRole.body);

  Widget _always(KitRequestAlwaysAllow always) => KitSwitchRow(
    title: always.title,
    value: false,
    switchKey: always.switchKey,
    // The duration's choice answers; the row's own "on" has nothing left
    // to do, since the sheet closes.
    onChanged: (_) {},
    risk: KitRisk(
      scope: always.scope,
      onLabel: always.onLabel,
      icon: AppIconography.permissions,
      until: always.until,
      onUntil: (until) => _session.answer(() => always.onAllowAlways(until)),
    ),
  );

  Widget _note(KitRequestMessage message) => KitField(
    label: message.fieldLabel,
    kind: KitFieldKind.multiline,
    draft: message.draft,
    helper: message.helper,
    fieldKey: message.fieldKey,
  );

  Widget _reply(KitRequestReply reply) => KitField(
    label: reply.fieldLabel,
    kind: KitFieldKind.multiline,
    draft: reply.draft,
    fieldKey: reply.fieldKey,
    onSubmitted: (_) => _session.sendReply(reply),
  );

  Widget? _options(BuildContext context) {
    final answers = widget.card.answers;
    if (!_session.waiting) return null;
    return switch (answers) {
      KitRequestChoose() => _single(answers),
      KitRequestChooseMany() => _many(answers),
      _ => null,
    };
  }

  Widget _single<T>(KitRequestChoose<T> answers) {
    final other = answers.other;
    return KitChoiceList<T>.single(
      choices: answers.choices,
      selected: null,
      sends: true,
      semanticsLabel: widget.card.title,
      // KitChoiceList sends the haptic itself and holds its own guard.
      onSelected: (value) =>
          _session.answer(() => answers.onChosen(value), haptic: false),
      other: other == null
          ? null
          : KitChoiceOther(
              label: other.label,
              fieldLabel: other.fieldLabel,
              draft: other.draft,
              fieldKey: other.fieldKey,
              onSubmitted: (text) =>
                  _session.answer(() => other.onSubmitted(text), haptic: false),
            ),
    );
  }

  Widget _many<T>(KitRequestChooseMany<T> answers) => ListenableBuilder(
    listenable: _session,
    builder: (context, _) => KitChoiceList<T>.multi(
      choices: answers.choices,
      selected: {
        for (final choice in answers.choices)
          if (_session.chosen.contains(choice.value)) choice.value,
      },
      onChanged: (chosen) => _session.choose(chosen),
      semanticsLabel: widget.card.title,
    ),
  );
}
