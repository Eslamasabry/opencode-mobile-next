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
                    _controller.isAgentBackend
                        ? strings.approvalModeEverythingAgentDetail(
                            KitBidi.auto(_controller.profile?.name ?? ''),
                          )
                        : strings.approvalModeEverythingDetail,
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

/// The approval chip in the strip above the composer, while the agent is
/// not working (owner 20 Oct 2026: decision 4 is about the working screen;
/// while it works the chip leaves and the line under the header says it). A
/// switcher, not only a gauge, in the model chip's one quiet look: a filled
/// shield reading "Auto-approve" while an automatic mode is on (the menu
/// says which mode), an outline shield reading "Asks first" while asking,
/// "Auto-approve paused" while disconnected. [onOpen] receives the chip's own context so the mode menu
/// anchors to it. No count; the sheet keeps the record. The full wording
/// (the latest approval, where the setting comes from) is its spoken label.
class _AutoApprovalIndicator extends StatelessWidget {
  const _AutoApprovalIndicator({
    super.key,
    required this.effective,
    required this.connected,
    required this.starting,
    required this.approved,
    required this.onOpen,
  });

  final EffectiveAutoApproval effective;
  final bool connected;
  final bool starting;
  final List<AutoApprovedPermission> approved;
  final void Function(BuildContext chipContext) onOpen;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final last = approved.lastOrNull;
    final automatic = effective.automatic;
    final paused = automatic && !connected && !starting;
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
      // The mode's own name, the one the menu ticks.
      text = strings.approvalModeAskTitle;
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
      text = strings.chatChipAutoApproveOn;
    }
    // Words, not the glyph alone, carry the state (STATE-9).
    return Semantics(
      button: true,
      // Copy may already end in a full stop; never read out "request..".
      label: [
        for (final part in [label, ?detail, strings.approvalModeChange])
          part.endsWith('.') ? part.substring(0, part.length - 1) : part,
      ].join('. '),
      excludeSemantics: true,
      onTap: () => onOpen(context),
      child: Builder(
        builder: (chipContext) => KitChip.action(
          key: const Key('auto-approval-indicator'),
          onPressed: () => onOpen(chipContext),
          // On reads at a glance: the words say "on" and the chip wears
          // the kit's mint check (its selected look); asking is the plain
          // shield. No fill on the pill: it stays as quiet as the model chip
          // beside it.
          icon: paused ? AppIconography.pause : AppIconography.shield,
          selected: automatic && !paused ? true : null,
          label: text,
        ),
      ),
    );
  }
}

/// The Auto-approve row of the composer's "+" sheet: on while an automatic
/// mode answers this conversation's requests, off while it asks first. One
/// tap flips it (the switch never leaves the sheet stale: it listens to the
/// connection). The whole word for the state under way, and the way back
/// out, is the one line under the header ([_autoApproveStatus]).
class _AutoApproveSwitchRow extends StatelessWidget {
  const _AutoApproveSwitchRow({
    required this.conn,
    required this.sessionID,
    required this.onChange,
  });

  final ConnectionController conn;
  final String sessionID;
  final ValueChanged<bool> onChange;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    return ListenableBuilder(
      listenable: conn,
      builder: (context, _) {
        final effective = conn.autoApprovalFor(sessionID);
        return KitSwitchRow(
          key: const Key('composer-setting-approval'),
          leading: const KitRowIcon(AppIconography.shield),
          title: strings.chatStripAutoApprove,
          supporting: effective.serverWide
              ? strings.approvalsSheetSetByServer
              : strings.composerSettingApprovalDetail,
          value: effective.automatic,
          onChanged: onChange,
        );
      },
    );
  }
}
