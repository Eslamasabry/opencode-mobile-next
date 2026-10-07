import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/genui/gen_ui.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import 'agent_card_asks.dart';
import 'agent_card_nodes.dart';
import 'agent_card_photos.dart';
import 'product_states.dart' show productErrorText;

/// The card an agent described, drawn from the kit with its state: waiting
/// (the ask, answerable once the agent is idle), held (collapsed with Undo),
/// sending, delivery unknown, failed, answered (a receipt that opens
/// read-only), passed over ("Not answered"), report (no controls) and
/// unreadable (one line, the safe reason under Details).
///
/// Answers go through [controller] only; this widget never builds a prompt
/// or touches a transport. It rebuilds when the controller changes.
class AgentCardView extends StatefulWidget {
  const AgentCardView({
    super.key,
    required this.controller,
    required this.parse,
    required this.agentLabel,
    this.busy = false,
    this.inList = false,
    this.photos,
  });

  final GenUiController controller;
  final GenUiParse parse;

  /// The conversation's agent, as its other cards say it ("Claude Code").
  final String agentLabel;

  /// The conversation is running: controls wait for it to be idle.
  final bool busy;

  /// Under a row of the Conversations list: no page gutter, secondary
  /// buttons (the list keeps its one primary).
  final bool inList;

  /// Where a photo ask gets its pictures; null where none can be added.
  final AgentCardPhotos? photos;

  @override
  State<AgentCardView> createState() => _AgentCardViewState();
}

class _AgentCardViewState extends State<AgentCardView> {
  bool _submitting = false;

  /// What the person entered, by card identity: an answer taken back with
  /// Undo returns as it was. Dropped once the card is answered or passed over.
  final Map<String, AgentCardDraft> _drafts = {};
  String? _error;

  Future<void> _answer(
    GenUiCard card,
    GenUiAnswer answer, {
    List<PromptAttachment> attachments = const [],
  }) async {
    if (_submitting) return;
    final l10n = AppLocalizations.of(context);
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.controller.answerGenUi(
        card,
        answer,
        attachments: attachments,
      );
    } catch (error) {
      // Plain words only; the controller's failure text never reaches here.
      if (mounted) setState(() => _error = productErrorText(error, l10n: l10n));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    if (controller is Listenable) {
      return ListenableBuilder(
        listenable: controller as Listenable,
        builder: (context, _) => _content(context),
      );
    }
    return _content(context);
  }

  Widget _content(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final parse = widget.parse;
    if (parse is GenUiUnreadable) {
      return KitAgentCard(
        key: const Key('agent-card-unreadable'),
        eyebrow: l10n.agentCardAsks(KitBidi.auto(widget.agentLabel)),
        title: l10n.agentCardUnreadable,
        mode: KitAgentCardMode.unreadable,
        inList: widget.inList,
        unreadableLabel: l10n.agentCardUnreadable,
        detailsLabel: l10n.agentCardDetails,
        // Only the stable problem name; never the agent's payload.
        details: parse.reason.name,
      );
    }
    final card = (parse as GenUiParsed).card;
    final gen = widget.controller;
    final state = gen.genUiStateForCard(card);
    final delivery = gen.genUiDeliveryFor(card);
    final summary = gen.genUiAnswerSummary(card);
    final asks = card.ask != null;
    final agent = KitBidi.auto(widget.agentLabel);
    final eyebrow = asks
        ? l10n.agentCardAsks(agent)
        : l10n.agentCardReports(agent);
    final title = KitBidi.auto(card.title);
    final cardKey = ValueKey('agent-card-${card.callID}');
    final body = agentCardNodes(context, card.body);
    final closed =
        (state == GenUiCardState.answered ||
            state == GenUiCardState.passedOver) &&
        delivery != GenUiDeliveryState.held;
    if (closed) _drafts.remove(card.identity);

    if (delivery == GenUiDeliveryState.held) {
      return KitAgentCard(
        key: cardKey,
        eyebrow: eyebrow,
        title: title,
        body: body,
        mode: KitAgentCardMode.receipt,
        inList: widget.inList,
        receiptLabel: summary ?? l10n.agentCardSent,
        onUndo: () => gen.undoGenUiAnswer(card),
        expandLabel: l10n.agentCardShow,
      );
    }
    if (state == GenUiCardState.answered) {
      return KitAgentCard(
        key: cardKey,
        eyebrow: eyebrow,
        title: title,
        body: body,
        mode: KitAgentCardMode.receipt,
        inList: widget.inList,
        receiptLabel: summary ?? l10n.agentCardSent,
        expandLabel: l10n.agentCardShow,
      );
    }
    if (state == GenUiCardState.passedOver) {
      return KitAgentCard(
        key: cardKey,
        eyebrow: eyebrow,
        title: title,
        body: body,
        mode: KitAgentCardMode.passedOver,
        inList: widget.inList,
        passedOverLabel: l10n.agentCardNotAnswered,
        expandLabel: l10n.agentCardShow,
      );
    }

    final waiting = state == GenUiCardState.waiting && asks;
    final sending = delivery == GenUiDeliveryState.sending || _submitting;
    final blocked = delivery == GenUiDeliveryState.deliveryUnknown
        ? l10n.agentCardDeliveryUnknown
        : widget.busy
        ? l10n.agentCardBusy
        : null;
    final notice = _notice(l10n, delivery);
    final Widget? ask = waiting
        ? Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              AgentCardAsk(
                ask: card.ask!,
                inList: widget.inList,
                photos: widget.photos,
                draft: _drafts.putIfAbsent(card.identity, AgentCardDraft.new),
                blockedReason: blocked,
                sending: sending,
                onAnswer: (answer, {attachments = const []}) =>
                    _answer(card, answer, attachments: attachments),
              ),
              ?notice,
            ],
          )
        : null;
    return KitAgentCard(
      key: cardKey,
      eyebrow: eyebrow,
      title: title,
      body: [
        ...body,
        if (state == GenUiCardState.unknown && asks)
          KitText(
            l10n.agentCardUnknown,
            key: const Key('agent-card-unknown'),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
      ],
      ask: ask,
      inList: widget.inList,
      announcement: waiting
          ? l10n.agentCardAsksAnnouncement(agent, title)
          : null,
    );
  }

  /// Under the controls: what happened to the last try, in plain words.
  Widget? _notice(AppLocalizations l10n, GenUiDeliveryState delivery) {
    final tokens = KitTokens.of(context);
    final String? message;
    final AppStatusTone tone;
    final Key key;
    if (delivery == GenUiDeliveryState.sending || _submitting) {
      message = l10n.agentCardSending;
      tone = AppStatusTone.progress;
      key = const Key('agent-card-sending');
    } else if (delivery == GenUiDeliveryState.deliveryUnknown) {
      message = l10n.agentCardDeliveryUnknown;
      tone = AppStatusTone.neutral;
      key = const Key('agent-card-delivery-unknown');
    } else if (delivery == GenUiDeliveryState.failed) {
      message = _error ?? l10n.agentCardFailed;
      tone = AppStatusTone.failure;
      key = const Key('agent-card-failed');
    } else if (_error != null) {
      message = _error;
      tone = AppStatusTone.failure;
      key = const Key('agent-card-error');
    } else {
      return null;
    }
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space3),
      child: KitNotice(message: message!, tone: tone, messageKey: key),
    );
  }
}
