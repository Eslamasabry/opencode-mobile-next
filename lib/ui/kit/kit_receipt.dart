import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../theme_roles.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_ink_layer.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_since.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_time.dart';
import 'kit_tokens.dart';

/// What happened to a write the phone sent (docs/ux-system/kit-api/
/// KitReceipt.md; K2 §1.16, §4.8, §4.9; STATE-5, STATE-10, AUTO-4,
/// DATA-11, A11Y-3).
enum KitReceiptState {
  /// In flight.
  sending,

  /// Left the phone; no echo yet. Never reads as "Done" (STATE-10).
  sent,

  /// The server echoed it.
  confirmed,

  /// No echo in time, or the echo was lost: Try again.
  notConfirmed,

  /// The server said no: the reason, in words.
  refused,

  /// Someone answered it on another device first.
  answeredElsewhere,
}

/// A write's receipt, shown in place as a mark and a word: "Sending…" (or
/// the act's own in-flight words, [sendingLabel]), "Sent", "Done" (or the act, [label]), "Not confirmed yet" with Try
/// again, "Not accepted: {reason}", "Answered on {where}".
///
/// With [automatic] it is the automation vertical's `KitAutoLine` (AUTO-4):
/// "Restarted the phone's server at 10:42 · Undo". The [label] names the
/// act in every state and the state's own mark stays beside it, so an
/// automatic act that was only sent keeps the sent check, never the success
/// check (STATE-10).
///
/// A waiting receipt (sending or sent, with [since]) turns into "Not
/// confirmed yet" with Try again after [KitMotion.escalateAfter], on
/// [KitSince]'s timer. Undo shows only when [onUndo] is given, only on a
/// confirmed receipt, and only until [at] + [KitMotion.undoWindow] (with no
/// [at], for as long as the caller passes [onUndo]); the caller passes it
/// only where the server exposes an inverse (DATA-11).
///
/// One live region carries the words, so each change of words is announced
/// once and a rebuild with the same words is not (A11Y-3). The mark is
/// decoration: the word already says it. No haptics: the send tick fired
/// when the person sent (MOT-11).
///
/// States: sending, sent, confirmed, not confirmed, refused, answered
/// elsewhere, automatic (working = sending; answered = confirmed /
/// answeredElsewhere).
class KitReceipt extends StatelessWidget {
  const KitReceipt({
    super.key,
    required this.state,
    this.label,
    this.sendingLabel,
    this.reason,
    this.where,
    this.at,
    this.since,
    this.onRetry,
    this.onUndo,
    this.onTap,
    this.automatic = false,
    this.retryKey,
    this.undoKey,
  });

  final KitReceiptState state;

  /// The words for the act, replacing the confirmed word: "Allowed once".
  /// On an ordinary receipt it is used only once the write is confirmed: a
  /// label never makes a sent write read as done (STATE-10). On an
  /// [automatic] line it replaces the state word in every state
  /// ("Restarted the phone's server"), beside the state's own mark.
  final String? label;

  /// The in-flight words for the act, replacing "Sending…" while the state
  /// is [KitReceiptState.sending]: "Moving to Review…". Only the sending
  /// word changes: a write that stays unconfirmed still turns into "Not
  /// confirmed yet" with Try again, and a sent write still says "Sent".
  /// Ignored on an [automatic] line, where [label] names the act.
  final String? sendingLabel;

  /// [KitReceiptState.refused]: the server's reason in plain words.
  final String? reason;

  /// [KitReceiptState.answeredElsewhere]: the other device, "the laptop".
  final String? where;

  /// The time of the latest transition, shown as "at 10:42"; also where the
  /// Undo window starts.
  final DateTime? at;

  /// When the write was sent. A sending or sent receipt escalates to "Not
  /// confirmed yet" after [KitMotion.escalateAfter].
  final DateTime? since;

  /// "Try again" on a not-confirmed (or escalated) receipt.
  final VoidCallback? onRetry;

  /// "Undo" on a confirmed receipt, inside the window.
  final VoidCallback? onUndo;

  /// The receipt opens where the write lives (the gate sheet).
  final VoidCallback? onTap;

  /// The automatic-action line (KitAutoLine, AUTO-4): "{label} at {time}",
  /// with " · " before its action.
  final bool automatic;

  final Key? retryKey;
  final Key? undoKey;

  /// A row's supporting line (like `kitCurrentSpan`): "Sent · ",
  /// "Not confirmed yet · ", "Not accepted: {reason} · ",
  /// "Answered on {where} · ". No actions: a row's actions live in its
  /// KitRowMenu or its sheet.
  ///
  /// With [mark] the state's glyph leads the word (a row's receipt as a
  /// word and icon in its supporting line, so the row's trailing slot keeps
  /// its chevron); a sending receipt shows the still dot rather than a
  /// spinner inside text. It is then only the glyph and the word: the
  /// caller places it among the line's parts and their separators.
  static InlineSpan span(
    BuildContext context,
    KitReceiptState state, {
    String? reason,
    String? label,
    String? where,
    String? sendingLabel,
    bool mark = false,
  }) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final word = _visibleWord(
      context,
      state,
      reason: reason,
      label: label,
      where: where,
      sendingLabel: sendingLabel,
    );
    final style = TextStyle(color: _wordColor(roles, state));
    if (!mark) {
      return TextSpan(text: '$word · ', style: style);
    }
    final size = _glyphSize(context, tokens);
    // The glyph is decoration: the word beside it says the state.
    final glyph = WidgetSpan(
      alignment: PlaceholderAlignment.middle,
      child: ExcludeSemantics(
        child: Padding(
          padding: EdgeInsetsDirectional.only(end: tokens.space1),
          child: SizedBox.square(
            dimension: size,
            child: _mark(context, state, roles, size, reduced: true),
          ),
        ),
      ),
    );
    return TextSpan(
      children: [
        glyph,
        TextSpan(text: word, style: style),
      ],
    );
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final waits =
        state == KitReceiptState.sending || state == KitReceiptState.sent;
    return KitSince(
      since: waits ? since : null,
      builder: (context, status) {
        final shown = status.isSlow ? KitReceiptState.notConfirmed : state;
        final undo = onUndo;
        if (shown != KitReceiptState.confirmed || undo == null) {
          return _body(context, shown, undoOpen: false);
        }
        // The Undo window rides the kit's one wait timer: a wait turns slow
        // [KitMotion.escalateAfter] after its start, so a wait started at
        // [at] + (undoWindow - escalateAfter) turns slow exactly at
        // [at] + [KitMotion.undoWindow], whatever the two constants are.
        // With no [at], it stays open while the caller offers [onUndo].
        final opened = at;
        return KitSince(
          since: opened?.add(KitMotion.undoWindow - KitMotion.escalateAfter),
          builder: (context, window) =>
              _body(context, shown, undoOpen: !window.isSlow),
        );
      },
    );
  }

  Widget _body(
    BuildContext context,
    KitReceiptState shown, {
    required bool undoOpen,
  }) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final reduced = KitMotion.reduced(context);
    final fade = reduced ? Duration.zero : KitMotion.quick;

    final word = _visibleWord(
      context,
      shown,
      reason: reason,
      label: label,
      where: where,
      sendingLabel: sendingLabel,
      automatic: automatic,
    );
    final time = at == null ? null : _time(context, at!);
    final atWords = time == null ? null : l10n.kitReceiptAt(time);
    final trimmedReason = reason?.trim();
    final semanticsLabel = [
      _plainWord(
        context,
        shown,
        label: label,
        where: where,
        sendingLabel: sendingLabel,
        automatic: automatic,
      ),
      if (shown == KitReceiptState.refused &&
          trimmedReason != null &&
          trimmedReason.isNotEmpty)
        KitBidi.auto(trimmedReason),
      ?atWords,
    ].join(', ');

    final glyphSize = _glyphSize(context, tokens);
    final mark = SizedBox.square(
      dimension: glyphSize,
      child: AnimatedSwitcher(
        duration: fade,
        switchInCurve: KitMotion.enter,
        switchOutCurve: KitMotion.exit,
        child: KeyedSubtree(
          key: ValueKey(shown),
          child: _mark(context, shown, roles, glyphSize, reduced: reduced),
        ),
      ),
    );

    final retry = onRetry;
    final actions = <Widget>[
      if (shown == KitReceiptState.notConfirmed && retry != null)
        KitButton.tertiary(
          key: retryKey,
          label: l10n.kitTryAgain,
          onPressed: retry,
        ),
      if (undoOpen)
        KitButton.tertiary(
          key: undoKey,
          label: l10n.kitUndoAction,
          onPressed: onUndo,
        ),
    ];

    final words = KitText.rich(
      TextSpan(
        children: [
          TextSpan(
            text: word,
            style: TextStyle(color: _wordColor(roles, shown)),
          ),
          if (atWords != null)
            TextSpan(
              text: ' $atWords',
              style: TextStyle(color: roles.text2),
            ),
          // KitAutoLine: "{label} at {time} · Undo". The line breaks after
          // the separator, so the dot ends the words and the action follows
          // on the same line or starts the next. The live region's label
          // does not carry it, so it never re-announces.
          if (automatic && actions.isNotEmpty)
            TextSpan(
              text: ' ·',
              style: TextStyle(color: roles.text2),
            ),
        ],
      ),
      role: KitTextRole.secondary,
    );

    Widget line = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        mark,
        SizedBox(width: tokens.space2),
        Flexible(child: words),
      ],
    );
    final tap = onTap;
    if (tap != null) line = _TapTarget(onTap: tap, child: line);
    // The one live region (A11Y-3): its label changes once per transition,
    // and a rebuild with the same words sends nothing new to announce.
    line = Semantics(
      container: true,
      liveRegion: true,
      button: tap != null,
      onTap: tap,
      label: semanticsLabel,
      child: ExcludeSemantics(child: line),
    );

    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: tokens.space2,
      children: [
        line,
        AnimatedSwitcher(
          duration: fade,
          switchInCurve: KitMotion.enter,
          switchOutCurve: KitMotion.exit,
          child: actions.isEmpty
              ? const SizedBox.shrink(key: ValueKey('none'))
              : Wrap(
                  key: ValueKey((shown, undoOpen)),
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: tokens.space2,
                  children: actions,
                ),
        ),
      ],
    );
  }
}

/// The word a receipt shows: the state word, or for confirmed (and on an
/// automatic line, for every state) the act.
String _visibleWord(
  BuildContext context,
  KitReceiptState state, {
  String? reason,
  String? label,
  String? where,
  String? sendingLabel,
  bool automatic = false,
}) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final trimmed = reason?.trim();
  if (state == KitReceiptState.refused &&
      trimmed != null &&
      trimmed.isNotEmpty) {
    final act = _automaticAct(label, automatic);
    return act == null
        ? l10n.kitReceiptRefusedReason(KitBidi.auto(trimmed))
        : l10n.kitReceiptActRefusedReason(
            KitBidi.auto(act),
            KitBidi.auto(trimmed),
          );
  }
  return _plainWord(
    context,
    state,
    label: label,
    where: where,
    sendingLabel: sendingLabel,
    automatic: automatic,
  );
}

/// The act an automatic line names in place of the state word, if any.
String? _automaticAct(String? label, bool automatic) {
  if (!automatic) return null;
  final act = label?.trim();
  return act == null || act.isEmpty ? null : act;
}

/// The word without the refusal's reason (semantics lists that apart).
String _plainWord(
  BuildContext context,
  KitReceiptState state, {
  String? label,
  String? where,
  String? sendingLabel,
  bool automatic = false,
}) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  // KitAutoLine (KitReceipt.md States, "automatic"): the act replaces the
  // state word in every state; the state's own mark says how far it got.
  final automaticAct = _automaticAct(label, automatic);
  if (automaticAct != null) return KitBidi.auto(automaticAct);
  final act = label?.trim();
  final place = where?.trim();
  final sending = sendingLabel?.trim();
  return switch (state) {
    KitReceiptState.sending =>
      sending != null && sending.isNotEmpty
          ? KitBidi.auto(sending)
          : l10n.kitReceiptSending,
    KitReceiptState.sent => l10n.kitReceiptSent,
    KitReceiptState.confirmed =>
      act != null && act.isNotEmpty
          ? KitBidi.auto(act)
          : l10n.kitReceiptConfirmed,
    KitReceiptState.notConfirmed => l10n.kitReceiptNotConfirmed,
    KitReceiptState.refused => l10n.kitReceiptRefused,
    KitReceiptState.answeredElsewhere =>
      place != null && place.isNotEmpty
          ? l10n.kitReceiptAnsweredElsewhere(KitBidi.auto(place))
          : l10n.kitReceiptAnsweredElsewhereUnknown,
  };
}

/// Words in `text2`, and in `text1` where the person may need to act
/// (LOOK-4/5: no amber, no red).
Color _wordColor(ThemeRoles roles, KitReceiptState state) => switch (state) {
  KitReceiptState.notConfirmed || KitReceiptState.refused => roles.text1,
  _ => roles.text2,
};

Widget _mark(
  BuildContext context,
  KitReceiptState state,
  ThemeRoles roles,
  double size, {
  required bool reduced,
}) => switch (state) {
  KitReceiptState.sending =>
    reduced
        ? Center(
            child: Icon(
              AppIconography.statusDot,
              size: KitTokens.markDotSize,
              color: roles.accent,
            ),
          )
        : Padding(
            padding: EdgeInsets.all(KitTokens.focusRingWidth(context)),
            child: CircularProgressIndicator(
              strokeWidth: KitTokens.focusRingWidth(context),
              color: roles.accent,
            ),
          ),
  KitReceiptState.sent || KitReceiptState.answeredElsewhere => Icon(
    AppIconography.check,
    size: size,
    color: roles.text2,
  ),
  KitReceiptState.confirmed => Icon(
    AppIconography.check,
    size: size,
    color: roles.success,
  ),
  KitReceiptState.notConfirmed => Icon(
    AppIconography.warning,
    size: size,
    color: roles.text1,
  ),
  KitReceiptState.refused => Icon(
    AppIconography.error,
    size: size,
    color: roles.text1,
  ),
};

/// [KitTokens.smallIconSize] grown with the person's text up to the kit's
/// cap, on whole physical pixels (LOOK-33).
double _glyphSize(BuildContext context, KitTokens tokens) {
  final scaled = tokens.iconSize(context, tokens.smallIconSize);
  final dpr = MediaQuery.devicePixelRatioOf(context);
  return dpr > 0 ? (scaled * dpr).roundToDouble() / dpr : scaled;
}

/// The clock time of [at], in the person's 12/24-hour setting.
String _time(BuildContext context, DateTime at) => KitTime.clock(context, at);

/// The receipt as one button (KitReceipt.md Adaptive: "`onTap` makes the
/// receipt one button (focus ring, Enter)"): a 48 dp target (LAY-9) whose
/// words stay flush with the text above; keyboard focus draws the kit's
/// focus ring ([KitTokens.focusRingWidth] in `accent`, as [KitButton]
/// does, LOOK-21), and hover only highlights.
class _TapTarget extends StatefulWidget {
  const _TapTarget({required this.onTap, required this.child});

  final VoidCallback onTap;
  final Widget child;

  @override
  State<_TapTarget> createState() => _TapTargetState();
}

class _TapTargetState extends State<_TapTarget> {
  final WidgetStatesController _states = WidgetStatesController();
  // The pressed fill answers a touch on the next frame (KitPressTracker);
  // the InkWell's own highlight waits for the tap-or-scroll timeout.
  late final _press = KitPressTracker(() {
    if (mounted) setState(() {});
  });

  @override
  void dispose() {
    _states.dispose();
    _press.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final radius = BorderRadius.circular(tokens.space2);
    return ListenableBuilder(
      listenable: _states,
      builder: (context, child) => DecoratedBox(
        position: DecorationPosition.foreground,
        decoration: BoxDecoration(
          borderRadius: radius,
          border: _states.value.contains(WidgetState.focused)
              ? Border.all(
                  color: roles.accent,
                  width: KitTokens.focusRingWidth(context),
                )
              : null,
        ),
        child: child,
      ),
      child: _press.listen(
        context: context,
        enabled: true,
        child: InkWell(
          onTap: () {
            _press.confirm();
            widget.onTap();
          },
          onTapCancel: _press.cancel,
          statesController: _states,
          borderRadius: radius,
          hoverColor: roles.surface2,
          focusColor: Colors.transparent,
          highlightColor: Colors.transparent,
          splashFactory: NoSplash.splashFactory,
          // On Android a press draws the native ripple (KitInk); the flat
          // pressed fill is for the other platforms and reduced motion.
          child: KitInkLayer(
            key: _press.inkKey,
            shape: RoundedRectangleBorder(borderRadius: radius),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: _press.shown && !KitInk.on(context)
                    ? roles.surface3
                    : null,
                borderRadius: radius,
              ),
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: tokens.minTarget),
                child: widget.child,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
