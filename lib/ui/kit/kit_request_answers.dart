part of 'kit_request_card.dart';

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
    this.secondary = false,
  });

  final List<KitChoice<T>> choices;
  final ValueChanged<Set<T>> onSend;

  /// Answer drawn as a secondary button: for a card among others in a list,
  /// where the screen keeps its own one primary (LAY-12).
  final bool secondary;

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
  const KitRequestInSheet({this.label, this.key, this.secondary = false});

  final String? label;
  final Key? key;

  /// Answer drawn as a secondary button (a card among others in a list).
  final bool secondary;
}
