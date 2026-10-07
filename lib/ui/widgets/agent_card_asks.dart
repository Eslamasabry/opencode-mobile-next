import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/widgets.dart';

import '../../domain/genui/gen_ui.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import 'agent_card_form.dart';
import 'agent_card_photos.dart';
import 'product_states.dart' show productErrorText;

/// Sends one answer; completes when the controller accepted it.
typedef AgentCardAnswer =
    Future<void> Function(
      GenUiAnswer answer, {
      List<PromptAttachment> attachments,
    });

/// What a card asks, as the kit's controls: the choice list, the form, the
/// confirm pair or the photo ask. Each ends in one Send (the card's one
/// primary; a secondary in a list, where the screen keeps its own).
///
/// [blockedReason] is why nothing can be sent now (the agent is still
/// working, an earlier answer is on its way): the controls stay readable and
/// Send says why it is off. [sending] shows Send working.
class AgentCardAsk extends StatelessWidget {
  const AgentCardAsk({
    super.key,
    required this.ask,
    required this.onAnswer,
    this.blockedReason,
    this.sending = false,
    this.inList = false,
    this.photos,
  });

  final GenUiAsk ask;
  final AgentCardAnswer onAnswer;
  final String? blockedReason;
  final bool sending;
  final bool inList;
  final AgentCardPhotos? photos;

  @override
  Widget build(BuildContext context) => switch (ask) {
    GenUiChoiceAsk() => _ChoiceAsk(
      ask: ask as GenUiChoiceAsk,
      onAnswer: onAnswer,
      blockedReason: blockedReason,
      sending: sending,
      inList: inList,
    ),
    GenUiFormAsk() => AgentCardForm(
      ask: ask as GenUiFormAsk,
      onAnswer: onAnswer,
      blockedReason: blockedReason,
      sending: sending,
      inList: inList,
    ),
    GenUiConfirmAsk() => _ConfirmAsk(
      ask: ask as GenUiConfirmAsk,
      onAnswer: onAnswer,
      blockedReason: blockedReason,
      sending: sending,
      inList: inList,
    ),
    GenUiPhotoAsk() => _PhotoAsk(
      ask: ask as GenUiPhotoAsk,
      onAnswer: onAnswer,
      blockedReason: blockedReason,
      sending: sending,
      inList: inList,
      photos: photos,
    ),
  };
}

/// The Send action in the card's one hierarchy: primary in a conversation,
/// secondary in a list.
Widget agentCardSendBlock(
  BuildContext context, {
  required KitAction send,
  required bool inList,
  KitAction? cancel,
}) => KitActionBlock(
  primary: inList ? null : send,
  secondary: inList ? send : cancel,
  tertiary: inList && cancel != null ? [cancel] : const [],
);

// ── Choice ────────────────────────────────────────────────────────────────

class _ChoiceAsk extends StatefulWidget {
  const _ChoiceAsk({
    required this.ask,
    required this.onAnswer,
    required this.blockedReason,
    required this.sending,
    required this.inList,
  });

  final GenUiChoiceAsk ask;
  final AgentCardAnswer onAnswer;
  final String? blockedReason;
  final bool sending;
  final bool inList;

  @override
  State<_ChoiceAsk> createState() => _ChoiceAskState();
}

class _ChoiceAskState extends State<_ChoiceAsk> {
  final Set<String> _picked = {};

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final ask = widget.ask;
    final choices = [
      for (final option in ask.options)
        KitChoice<String>(
          value: option.id,
          title: KitBidi.auto(option.label),
          supporting: option.detail == null
              ? null
              : KitBidi.auto(option.detail!),
        ),
    ];
    final String? reason =
        widget.blockedReason ??
        (_picked.isEmpty
            ? (ask.multi
                  ? l10n.agentCardChooseOneOrMore
                  : l10n.agentCardChoosePrompt)
            : null);
    final list = ask.multi
        ? KitChoiceList<String>.multi(
            choices: choices,
            selected: _picked,
            onChanged: (value) => setState(() {
              _picked
                ..clear()
                ..addAll(value);
            }),
          )
        : KitChoiceList<String>.single(
            choices: choices,
            selected: _picked.isEmpty ? null : _picked.first,
            actsOnTap: false,
            onSelected: (value) => setState(() {
              _picked
                ..clear()
                ..add(value);
            }),
          );
    final send = KitAction(
      label: l10n.agentCardSend,
      key: const Key('agent-card-send'),
      working: widget.sending,
      onPressed: reason == null && !widget.sending
          ? () => unawaited(
              widget.onAnswer(
                // The agent's own option order, not the order of the taps.
                GenUiChoiceAnswer([
                  for (final option in ask.options)
                    if (_picked.contains(option.id)) option.id,
                ]),
              ),
            )
          : null,
      disabledReason: reason == null || widget.sending ? null : reason,
    );
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        list,
        SizedBox(height: tokens.space3),
        agentCardSendBlock(context, send: send, inList: widget.inList),
      ],
    );
  }
}

// ── Confirm ───────────────────────────────────────────────────────────────

class _ConfirmAsk extends StatelessWidget {
  const _ConfirmAsk({
    required this.ask,
    required this.onAnswer,
    required this.blockedReason,
    required this.sending,
    required this.inList,
  });

  final GenUiConfirmAsk ask;
  final AgentCardAnswer onAnswer;
  final String? blockedReason;
  final bool sending;
  final bool inList;

  Future<void> _confirm(BuildContext context, String label) async {
    if (ask.tone == GenUiConfirmTone.danger) {
      final l10n = AppLocalizations.of(context);
      final yes = await showKitConfirm(
        context,
        title: l10n.agentCardDangerTitle(label),
        body: l10n.agentCardDangerBody,
        confirmLabel: label,
        cancelLabel: ask.cancelLabel,
        kind: KitConfirmKind.destructive,
        confirmKey: const Key('agent-card-danger-confirm'),
      );
      if (!yes) return;
    }
    await onAnswer(const GenUiConfirmAnswer(true));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final confirmLabel = ask.confirmLabel ?? l10n.agentCardConfirmDefault;
    final cancelLabel = ask.cancelLabel ?? l10n.agentCardCancelDefault;
    final blocked = blockedReason;
    final off = blocked != null || sending;
    final send = KitAction(
      label: confirmLabel,
      key: const Key('agent-card-confirm'),
      working: sending,
      onPressed: off ? null : () => unawaited(_confirm(context, confirmLabel)),
      disabledReason: blocked != null && !sending ? blocked : null,
    );
    final cancel = KitAction(
      label: cancelLabel,
      key: const Key('agent-card-cancel'),
      onPressed: off
          ? null
          : () => unawaited(onAnswer(const GenUiConfirmAnswer(false))),
    );
    return agentCardSendBlock(
      context,
      send: send,
      cancel: cancel,
      inList: inList,
    );
  }
}

// ── Photo ─────────────────────────────────────────────────────────────────

class _PickedPhoto {
  _PickedPhoto(this.attachment, this.bytes);
  final PromptAttachment attachment;
  final Uint8List? bytes;
}

class _PhotoAsk extends StatefulWidget {
  const _PhotoAsk({
    required this.ask,
    required this.onAnswer,
    required this.blockedReason,
    required this.sending,
    required this.inList,
    required this.photos,
  });

  final GenUiPhotoAsk ask;
  final AgentCardAnswer onAnswer;
  final String? blockedReason;
  final bool sending;
  final bool inList;
  final AgentCardPhotos? photos;

  @override
  State<_PhotoAsk> createState() => _PhotoAskState();
}

class _PhotoAskState extends State<_PhotoAsk> {
  final List<_PickedPhoto> _items = [];
  bool _picking = false;
  String? _error;

  static Uint8List? _preview(PromptAttachment attachment) {
    final comma = attachment.url.indexOf(',');
    if (comma < 0) return null;
    try {
      return base64Decode(attachment.url.substring(comma + 1));
    } on FormatException {
      return null;
    }
  }

  int get _total => _items.fold<int>(
    0,
    (sum, item) => sum + agentCardAttachmentBytes(item.attachment),
  );

  Future<void> _add(Future<List<PromptAttachment>> Function() pick) async {
    if (_picking) return;
    final l10n = AppLocalizations.of(context);
    setState(() {
      _picking = true;
      _error = null;
    });
    try {
      final picked = await pick();
      if (!mounted) return;
      var tooLarge = false;
      final room = widget.ask.max - _items.length;
      var total = _total;
      final added = <_PickedPhoto>[];
      for (final attachment in picked.take(room)) {
        final size = agentCardAttachmentBytes(attachment);
        if (size > agentCardPhotoMaxBytes ||
            total + size > agentCardPhotoMaxTotalBytes) {
          tooLarge = true;
          continue;
        }
        total += size;
        added.add(_PickedPhoto(attachment, _preview(attachment)));
      }
      setState(() {
        _items.addAll(added);
        _error = tooLarge ? l10n.agentCardPhotoTooLarge : null;
      });
    } catch (error) {
      // The picker's own failure, in plain words; never its text.
      if (mounted) {
        setState(() => _error = agentCardPhotoError(l10n, error));
      }
    } finally {
      if (mounted) setState(() => _picking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final ask = widget.ask;
    final photos = widget.photos;
    final purpose = KitBidi.auto(ask.purpose);
    if (photos == null) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitText(purpose, role: KitTextRole.body, tone: KitTextTone.primary),
          SizedBox(height: tokens.space2),
          KitText(
            l10n.agentCardPhotoUnavailable,
            key: const Key('agent-card-photo-unavailable'),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ],
      );
    }
    final busy = widget.sending || _picking;
    final reason =
        widget.blockedReason ??
        (_items.isEmpty ? l10n.agentCardPhotoNone : null);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitSenseAsk(
          kind: KitSenseKind.photo,
          purpose: purpose,
          max: ask.max,
          sending: widget.sending,
          secondary: widget.inList,
          sendLabel: l10n.agentCardSend,
          sendKey: const Key('agent-card-send'),
          sendDisabledReason: reason,
          countLabel: l10n.agentCardPhotoCount(_items.length, ask.max),
          maxReachedLabel: l10n.agentCardPhotoMax,
          actions: [
            if (photos.canTake)
              KitSenseAction(
                label: l10n.agentCardPhotoTake,
                icon: AppIconography.camera,
                key: const Key('agent-card-photo-take'),
                onPressed: busy
                    ? () {}
                    : () => unawaited(
                        _add(() async {
                          final one = await photos.take();
                          return [?one];
                        }),
                      ),
              ),
            KitSenseAction(
              label: l10n.agentCardPhotoChoose,
              icon: AppIconography.image,
              key: const Key('agent-card-photo-choose'),
              onPressed: busy
                  ? () {}
                  : () => unawaited(
                      _add(() => photos.choose(ask.max - _items.length)),
                    ),
            ),
          ],
          items: [
            for (final (index, item) in _items.indexed)
              KitSenseItem(
                id: '$index',
                name: l10n.agentCardPhotoName(index + 1),
                removeLabel: l10n.agentCardPhotoRemove(index + 1),
                thumbnail: item.bytes,
              ),
          ],
          onRemove: (id) => setState(() => _items.removeAt(int.parse(id))),
          onSend: widget.blockedReason != null || _items.isEmpty
              ? null
              : () => unawaited(
                  widget.onAnswer(
                    GenUiPhotoAnswer(_items.length),
                    attachments: [for (final item in _items) item.attachment],
                  ),
                ),
        ),
        if (_error != null) ...[
          SizedBox(height: tokens.space2),
          KitNotice(
            message: _error!,
            tone: AppStatusTone.failure,
            messageKey: const Key('agent-card-photo-error'),
          ),
        ],
      ],
    );
  }
}

/// The words for a failed answer: a [ProductException]'s own plain words, or
/// the card's generic line. Never the exception's text.
String agentCardFailureWords(AppLocalizations l10n, Object error) =>
    productErrorText(error, l10n: l10n);
