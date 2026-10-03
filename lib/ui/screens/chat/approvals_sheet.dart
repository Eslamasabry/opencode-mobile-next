part of '../chat_screen.dart';

/// Opens the approval settings in the one sheet frame: one short list of
/// the ways this conversation can answer permission requests, the same
/// choices as the composer's approval chip.
Future<void> showSessionApprovalsSheet(
  BuildContext context, {
  required ConnectionController controller,
  required String sessionID,
}) => showKitSheet<void>(
  context,
  title: _chatL10n(context).approvalsUiMenu,
  icon: AppIconography.permissions,
  body: (_) =>
      SessionApprovalsSheet(controller: controller, sessionID: sessionID),
);

/// The approvals sheet body: Ask first, Auto-approve this conversation or
/// Approve everything as three choices (only the modes the connection
/// supports), a "Subagents follow this" switch while this conversation
/// auto-approves, one quiet line when the mode is inherited, and the
/// automatically approved history only when there is some. Every control
/// writes through [_requestApprovalChoice] / the controller, the same calls
/// the chip's menu makes. A save that fails says so at the top.
class SessionApprovalsSheet extends StatefulWidget {
  const SessionApprovalsSheet({
    super.key,
    required this.controller,
    required this.sessionID,
  });

  final ConnectionController controller;
  final String sessionID;

  @override
  State<SessionApprovalsSheet> createState() => _SessionApprovalsSheetState();
}

class _SessionApprovalsSheetState extends State<SessionApprovalsSheet> {
  String? _error;

  ConnectionController get _controller => widget.controller;

  Future<void> _save(Future<void> Function() write) async {
    setState(() => _error = null);
    try {
      await write();
    } catch (error) {
      if (!mounted) return;
      setState(
        () => _error = _chatL10n(
          context,
        ).approvalsUiSaveFailed(productErrorText(error)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: _controller,
      builder: (context, _) {
        final effective = _controller.autoApprovalFor(widget.sessionID);
        final current = _choiceOf(effective);
        final hasParent =
            _controller.sessionsById[widget.sessionID]?.parentID != null;
        final error = _error;
        final approved = _controller.autoApprovedFor(widget.sessionID);
        final offered = _offeredApprovalChoices(_controller, current);
        KitChoice<_ApprovalChoice> choice(
          _ApprovalChoice value,
          String key,
          String title,
          String detail,
        ) => KitChoice(
          value: value,
          key: Key(key),
          title: title,
          supporting: detail,
        );
        final setBy = effective.serverWide
            ? strings.approvalsSheetSetByServer
            : effective.inherited
            ? strings.approvalsSheetSetByParent(
                KitBidi.auto(
                  _controller.sessionsById[effective.inheritedFrom]?.title ??
                      strings.approvalsUiInheritedFrom,
                ),
              )
            : null;
        final sections = <Widget>[
          if (error != null)
            KitNotice.error(
              message: error,
              messageKey: const Key('approvals-save-failed'),
            ),
          KitChoiceList<_ApprovalChoice>.single(
            semanticsLabel: strings.approvalModeMenuLabel,
            selected: current,
            choices: [
              for (final value in offered)
                switch (value) {
                  _ApprovalChoice.ask => choice(
                    value,
                    'approvals-mode-ask',
                    strings.approvalModeAskTitle,
                    strings.approvalModeAskDetail,
                  ),
                  _ApprovalChoice.auto => choice(
                    value,
                    'approvals-mode-auto',
                    strings.approvalModeAutoTitle,
                    strings.approvalModeAutoDetail,
                  ),
                  _ApprovalChoice.everything => choice(
                    value,
                    'approvals-mode-everything',
                    strings.approvalModeEverythingTitle,
                    strings.approvalModeEverythingDetail,
                  ),
                },
            ],
            onSelected: (value) => unawaited(
              _save(
                () => _requestApprovalChoice(
                  context,
                  _controller,
                  widget.sessionID,
                  value,
                ),
              ),
            ),
          ),
          if (setBy != null)
            KitInset(
              child: KitText(
                setBy,
                key: const Key('approvals-set-by'),
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
            ),
          if (current == _ApprovalChoice.auto)
            KitRowGroup(
              margin: EdgeInsets.zero,
              leadingIcons: false,
              children: [
                KitSwitchRow(
                  switchKey: const Key('approvals-inherit-switch'),
                  title: strings.approvalsSheetSubagents,
                  value: effective.setting.inheritToChildren,
                  onChanged: (value) => unawaited(
                    _save(
                      () => _controller.setSessionAutoApproval(
                        widget.sessionID,
                        SessionAutoApproval(
                          mode: AutoApprovalMode.autoOnce,
                          inheritToChildren: value,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          if (effective.explicit && hasParent)
            KitInset(
              child: KitButton.tertiary(
                key: const Key('approvals-follow-parent'),
                icon: AppIconography.nested,
                label: strings.approvalsUiFollowParent,
                onPressed: () => unawaited(
                  _save(
                    () => _controller.setSessionAutoApproval(
                      widget.sessionID,
                      null,
                    ),
                  ),
                ),
              ),
            ),
          if (approved.isNotEmpty) _AutoApprovalRecord(approved: approved),
          if (effective.automatic)
            KitNotice(
              key: const Key('approvals-rules-note'),
              message: strings.approvalsSheetFootnote,
              liveRegion: false,
            ),
        ];
        return Column(
          key: const Key('session-approvals-sheet'),
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (final (index, section) in sections.indexed) ...[
              if (index > 0) SizedBox(height: tokens.space4),
              section,
            ],
          ],
        );
      },
    );
  }
}

/// What this phone approved automatically in the session since connecting,
/// as one collapsed row: the transparency half of a setting that removes
/// prompts. Opens to the latest five, newest first. Not drawn while empty.
class _AutoApprovalRecord extends StatelessWidget {
  const _AutoApprovalRecord({required this.approved});

  final List<AutoApprovedPermission> approved;
  static const _shown = 5;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final recent = approved.reversed.take(_shown).toList();
    return KitDetailsFold(
      key: const Key('approvals-record'),
      label: strings.approvalsSheetHistory,
      child: KitRowGroup(
        margin: EdgeInsets.zero,
        children: [
          for (final entry in recent)
            KitRow(
              leading: KitRowIcon(permissionActionIcon(entry.permission)),
              title: strings.approvalsUiAutoApproved(
                permissionRequestTitle(entry.permission, l10n: strings),
              ),
              supporting: entry.patterns.isEmpty
                  ? null
                  : TextSpan(text: KitBidi.ltr(entry.patterns.join(' · '))),
              supportingMaxLines: 2,
            ),
        ],
      ),
    );
  }
}

/// The approval chip in the strip above the composer: always there in a
/// conversation, and a switcher, not only a gauge. While an automatic mode
/// is on it is an accent-tinted chip with a filled shield reading
/// "Auto-approve" (the menu says which mode); while asking it is a quiet
/// neutral chip with an outline shield reading "Asks first"; while
/// automatic approval is paused (disconnected) it reads "Auto-approve
/// paused". [onOpen] receives the chip's own context so the mode menu
/// anchors to it. No count; the sheet keeps the record. The full wording
/// (the latest approval, where the setting comes from) is its spoken label.
class _AutoApprovalIndicator extends StatelessWidget {
  const _AutoApprovalIndicator({
    super.key,
    required this.effective,
    required this.connected,
    required this.approved,
    required this.onOpen,
  });

  final EffectiveAutoApproval effective;
  final bool connected;
  final List<AutoApprovedPermission> approved;
  final void Function(BuildContext chipContext) onOpen;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final last = approved.lastOrNull;
    final automatic = effective.automatic;
    final paused = automatic && !connected;
    final String label;
    final String? detail;
    final String text;
    if (paused) {
      label = strings.approvalsUiIndicatorPaused;
      detail = strings.approvalsUiPausedDetail;
      text = strings.chatStripAutoApprovePaused;
    } else if (!automatic) {
      label = strings.approvalModeAskTitle;
      detail = strings.approvalModeAskDetail;
      text = strings.chatStripApprovalAsk;
    } else {
      label = strings.approvalsUiIndicatorOn;
      detail = last != null
          ? strings.approvalsUiAutoApproved(
              permissionRequestTitle(last.permission, l10n: strings),
            )
          : effective.serverWide
          ? strings.approvalsSheetSetByServer
          : effective.inherited
          ? strings.approvalsUiInheritedFrom
          : null;
      text = strings.chatStripAutoApprove;
    }
    // Words, not the glyph alone, carry the state (STATE-9).
    return Semantics(
      button: true,
      label: [label, ?detail, strings.approvalModeChange].join('. '),
      excludeSemantics: true,
      onTap: () => onOpen(context),
      child: Builder(
        builder: (chipContext) => KitChip.action(
          key: const Key('auto-approval-indicator'),
          onPressed: () => onOpen(chipContext),
          tone: automatic && !paused ? KitChipTone.active : KitChipTone.neutral,
          icon: paused
              ? AppIconography.pause
              : automatic
              ? AppIconography.shieldFilled
              : AppIconography.shield,
          label: text,
        ),
      ),
    );
  }
}
