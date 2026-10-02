import 'dart:math' as math;
// Everything waiting to reach the agent, as one bubble at the end of the
// conversation (docs/ux-system/kit-api/KitQueuedMessage.md; STATE-17,
// STATE-10, STATE-5, DATA-7, DATA-11, KIT-28, A11Y-5, LOOK-26, LOOK-5).
import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../kit_buttons.dart';
import '../kit_divider.dart';
import '../kit_layout.dart';
import '../kit_menu.dart';
import '../kit_motion.dart';
import '../kit_receipt.dart';
import '../kit_since.dart';
import '../kit_technical_value.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import '../motion/kit_animated_rows.dart';

/// Where one waiting message stands. The host maps its queue entry or its
/// server inbox item; the bubble says it in words.
enum KitQueuedState {
  /// Offline: it sends when the app is back online.
  waiting,

  /// On the wire now.
  sending,

  /// It left, and no confirmation came back: Try again is offered (a
  /// resend).
  notConfirmed,

  /// The server accepted it but this phone could not record that: never
  /// resend.
  reachedServer,

  /// The send failed; [KitQueuedItem.reason] says why.
  failed,

  /// A receipt check is running now: it only looks, it never sends.
  checking,

  /// It left and the phone holds a receipt record, but the server has not
  /// confirmed it yet. The action only checks again; it never resends
  /// ([KitQueuedItem.checkedAt] says when it last looked).
  uncertain,

  /// This phone could not safely record the send, so it was not sent.
  storageFull,

  /// OpenCode 2: accepted, delivered when the running reply finishes.
  afterThisReply,

  /// OpenCode 2: accepted, delivered at the agent's next step.
  addToThisTurn,

  /// OpenCode 2: an update the server itself queued (not typed by the
  /// person).
  contextUpdate,
}

/// One message waiting to reach the agent.
@immutable
class KitQueuedItem {
  const KitQueuedItem({
    required this.id,
    required this.text,
    required this.state,
    this.attachmentCount = 0,
    this.reason,
    this.since,
    this.checkedAt,
    this.details = const <KitTechnicalValue>[],
    this.detailNotes = const <String>[],
    this.menu = const <KitMenuItem>[],
    this.key,
  });

  final Object id;

  /// The message; may be empty for [KitQueuedState.contextUpdate].
  final String text;
  final KitQueuedState state;

  /// "2 attachments".
  final int attachmentCount;

  /// failed / notConfirmed: plain words (agentErrorWords), never raw.
  final String? reason;

  /// sending: when it left, for the 8 s escalation.
  final DateTime? since;

  /// uncertain: when the last check ran ("Last checked 14:02").
  final DateTime? checkedAt;

  /// Technical truth (ids, sent time) and plain notes, folded under Details
  /// below the item. Never the message, a URL, a header or a raw error.
  final List<KitTechnicalValue> details;
  final List<String> detailNotes;

  /// Edit, Send now / Add to this turn / Send after, Try again, Remove.
  final List<KitMenuItem> menu;

  /// A test handle on the item (today's `ValueKey('queued-send-<i>')` /
  /// `ValueKey('pending-send-<id>')`).
  final Key? key;
}

/// Everything waiting to reach the agent, in one bubble at the end of the
/// conversation (STATE-17). Styled as the person's prompt bubble (surface2,
/// radii 20/20/6/20); lists each item oldest first; one action line inside.
///
/// The bubble never sends, resends, edits or deletes anything: every act is
/// the host's, through [action], [secondaryAction] and each item's menu.
///
/// States: waiting (offline), sending, not confirmed, reached server,
/// failed, queued behind the reply (OpenCode 2), steering (OpenCode 2),
/// mixed, empty (KIT-12).
class KitQueuedMessage extends StatelessWidget {
  const KitQueuedMessage({
    super.key,
    required this.items,
    this.action,
    this.secondaryAction,
    this.bubbleKey,
  });

  /// Oldest first; empty renders nothing.
  final List<KitQueuedItem> items;

  /// The bubble's one call to action: "Send now", "Try again".
  final KitAction? action;

  /// At most one more: "Edit".
  final KitAction? secondaryAction;

  final Key? bubbleKey;

  @override
  Widget build(BuildContext context) {
    final reduced = KitMotion.reduced(context);
    return AnimatedSwitcher(
      duration: reduced ? Duration.zero : KitMotion.quick,
      switchInCurve: KitMotion.enter,
      switchOutCurve: KitMotion.exit,
      layoutBuilder: (current, previous) => Stack(
        alignment: AlignmentDirectional.topEnd,
        children: [...previous, ?current],
      ),
      child: items.isEmpty
          ? const SizedBox.shrink(key: ValueKey('kit-queued-empty'))
          : _Bubble(
              key: const ValueKey('kit-queued-bubble'),
              items: items,
              action: action,
              secondaryAction: secondaryAction,
              bubbleKey: bubbleKey,
            ),
    );
  }
}

class _Bubble extends StatelessWidget {
  const _Bubble({
    super.key,
    required this.items,
    required this.action,
    required this.secondaryAction,
    required this.bubbleKey,
  });

  final List<KitQueuedItem> items;
  final KitAction? action;
  final KitAction? secondaryAction;
  final Key? bubbleKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final count = items.length;
    final allWaiting = items.every(
      (item) => item.state == KitQueuedState.waiting,
    );
    final head = [
      l10n.kitQueuedTitle(count),
      if (allWaiting) l10n.kitQueuedOffline,
    ].join(' · ');

    final actions = [
      if (action case final primary?)
        KitButton.fromAction(primary, role: KitButtonRole.tertiary),
      if (secondaryAction case final secondary?)
        KitButton.fromAction(secondary, role: KitButtonRole.tertiary),
    ];

    final rows = <Widget>[
      for (final (index, item) in items.indexed)
        KeyedSubtree(
          key: ValueKey<Object>(('kit-queued-item', item.id)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (index > 0) const KitDivider(),
              _Item(item: item, index: index + 1, count: count),
            ],
          ),
        ),
    ];

    final body = Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.space2,
        tokens.space3,
        tokens.space2,
        tokens.space2,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space2),
            child: ExcludeSemantics(
              child: KitText(head, role: KitTextRole.label),
            ),
          ),
          SizedBox(height: tokens.space2),
          KitAnimatedRows(children: rows),
          if (actions.isNotEmpty) ...[
            SizedBox(height: tokens.space2),
            Wrap(
              spacing: tokens.space2,
              runSpacing: tokens.space2,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: actions,
            ),
          ],
        ],
      ),
    );

    final bubble = DecoratedBox(
      key: bubbleKey,
      decoration: ShapeDecoration(
        color: roles.surface2,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadiusDirectional.only(
            topStart: Radius.circular(KitTokens.bubbleRadius),
            topEnd: Radius.circular(KitTokens.bubbleRadius),
            bottomStart: Radius.circular(KitTokens.bubbleRadius),
            bottomEnd: Radius.circular(KitTokens.bubbleTailRadius),
          ),
        ),
      ),
      child: body,
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : KitLayout.paneDetailMaxWidth;
        return Align(
          alignment: AlignmentDirectional.centerEnd,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: math.max(0.0, width - KitLayout.bubbleStartInset),
            ),
            child: Semantics(
              container: true,
              explicitChildNodes: true,
              label: head,
              child: bubble,
            ),
          ),
        );
      },
    );
  }
}

class _Item extends StatelessWidget {
  const _Item({required this.item, required this.index, required this.count});

  final KitQueuedItem item;
  final int index;
  final int count;

  @override
  Widget build(BuildContext context) {
    final sendingSince = item.state == KitQueuedState.sending
        ? item.since
        : null;
    return KitSince(
      since: sendingSince,
      builder: (context, status) {
        final shown = item.state == KitQueuedState.sending && status.isSlow
            ? KitQueuedState.notConfirmed
            : item.state;
        return _content(context, shown);
      },
    );
  }

  Widget _content(BuildContext context, KitQueuedState shown) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final stateSpan = _stateSpan(context, shown, item.reason, item.checkedAt);
    final stateWords = stateSpan.toPlainText();
    final text = item.text.trim();
    final label = l10n.kitQueuedItemLabel(
      index,
      count,
      text.isEmpty ? '' : KitBidi.auto(text),
      [
        stateWords,
        if (item.attachmentCount > 0)
          l10n.kitQueuedAttachments(item.attachmentCount),
      ].join(', '),
    );

    final content = Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: tokens.space2,
        vertical: tokens.space2,
      ),
      child: SizedBox(
        width: double.infinity,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (text.isNotEmpty)
              KitText(
                text,
                role: KitTextRole.body,
                tone: KitTextTone.primary,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.start,
              ),
            if (item.attachmentCount > 0)
              KitText(
                l10n.kitQueuedAttachments(item.attachmentCount),
                role: KitTextRole.secondary,
              ),
            KitText.rich(stateSpan, role: KitTextRole.secondary),
          ],
        ),
      ),
    );

    final Widget row;
    if (item.menu.isEmpty) {
      row = Semantics(
        container: true,
        label: label,
        excludeSemantics: true,
        child: KeyedSubtree(key: item.key, child: content),
      );
    } else {
      row = Builder(
        builder: (anchor) => KitTappable(
          tappableKey: item.key,
          label: label,
          menu: item.menu,
          surface: KitSurfaceLevel.surface2,
          shape: KitShape.button,
          onTap: () => showKitMenu(
            anchor,
            items: item.menu,
            semanticsLabel: l10n.kitQueuedActions,
          ),
          child: content,
        ),
      );
    }
    if (item.details.isEmpty && item.detailNotes.isEmpty) return row;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        row,
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space2),
          child: KitDetailsFold(
            foldKey: ValueKey<Object>(('kit-queued-details', item.id)),
            values: item.details,
            notes: item.detailNotes,
          ),
        ),
      ],
    );
  }
}

/// The state line's words. Receipt states use [KitReceipt.span], without
/// its trailing " · " (it is a prefix for a row's supporting line; here the
/// state stands alone).
InlineSpan _stateSpan(
  BuildContext context,
  KitQueuedState state,
  String? reason,
  DateTime? checkedAt,
) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  InlineSpan receipt(KitReceiptState receiptState, {String? why}) {
    final span = KitReceipt.span(context, receiptState, reason: why);
    if (span is TextSpan) {
      final words = span.text ?? '';
      return TextSpan(
        text: words.endsWith(' · ')
            ? words.substring(0, words.length - 3)
            : words,
        style: span.style,
      );
    }
    return span;
  }

  return switch (state) {
    KitQueuedState.sending => receipt(KitReceiptState.sending),
    KitQueuedState.notConfirmed => _withReason(
      context,
      receipt(KitReceiptState.notConfirmed),
      reason,
    ),
    KitQueuedState.failed => receipt(KitReceiptState.refused, why: reason),
    KitQueuedState.checking => TextSpan(text: l10n.kitQueuedChecking),
    KitQueuedState.uncertain => _uncertain(
      context,
      receipt(KitReceiptState.notConfirmed),
      checkedAt,
    ),
    KitQueuedState.storageFull => TextSpan(text: l10n.kitQueuedStorageFull),
    KitQueuedState.reachedServer => TextSpan(text: l10n.kitQueuedReachedServer),
    KitQueuedState.waiting => TextSpan(text: l10n.kitQueuedWaiting),
    KitQueuedState.afterThisReply => TextSpan(text: l10n.kitQueuedAfterReply),
    KitQueuedState.addToThisTurn => TextSpan(text: l10n.kitQueuedAddToTurn),
    KitQueuedState.contextUpdate => TextSpan(text: l10n.kitQueuedUpdate),
  };
}

/// [span]'s words followed by the plain [reason] ("Not confirmed yet: socket
/// closed"), in the same colour; [span] unchanged when there is no reason.
InlineSpan _withReason(BuildContext context, InlineSpan span, String? reason) {
  final why = reason?.trim();
  if (why == null || why.isEmpty || span is! TextSpan) return span;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  return TextSpan(
    text: l10n.kitReceiptActRefusedReason(span.text ?? '', KitBidi.auto(why)),
    style: span.style,
  );
}

/// "We couldn't confirm your message arrived." in the not-confirmed colour,
/// followed by "Last checked 14:02." once a check has run.
InlineSpan _uncertain(BuildContext context, InlineSpan span, DateTime? at) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final style = span is TextSpan ? span.style : null;
  final time = at == null
      ? null
      : MaterialLocalizations.of(
          context,
        ).formatTimeOfDay(TimeOfDay.fromDateTime(at));
  return TextSpan(
    text: time == null
        ? l10n.kitQueuedUncertain
        : '${l10n.kitQueuedUncertain} ${l10n.kitQueuedLastChecked(time)}',
    style: style,
  );
}
