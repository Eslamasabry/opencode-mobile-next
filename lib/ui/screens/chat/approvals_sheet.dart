part of '../chat_screen.dart';

/// Opens the per-session approval settings in the one sheet frame: ask each
/// time (the default) or let this phone answer permission requests with
/// "Allow once" while connected, whether subagent conversations inherit
/// that, and the server-wide switch. Both automatic switches are risky
/// switches (KIT-30): turning one on first states what it covers.
Future<void> showSessionApprovalsSheet(
  BuildContext context, {
  required ConnectionController controller,
  required String sessionID,
}) => showKitSheet<void>(
  context,
  title: _chatL10n(context).approvalsUiTitle,
  icon: AppIconography.permissions,
  body: (_) =>
      SessionApprovalsSheet(controller: controller, sessionID: sessionID),
);

/// The approvals sheet body. Every control writes through the controller and
/// re-reads the effective setting, so an inheriting child session shows its
/// parent's choice until it takes one of its own. A save that fails says so
/// at the top, in place of a snackbar.
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

  void _write(SessionAutoApproval? setting) => unawaited(
    _save(() => _controller.setSessionAutoApproval(widget.sessionID, setting)),
  );

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: _controller,
      builder: (context, _) {
        final effective = _controller.autoApprovalFor(widget.sessionID);
        final setting = effective.setting;
        final hasParent =
            _controller.sessionsById[widget.sessionID]?.parentID != null;
        final error = _error;
        final sections = <Widget>[
          if (error != null)
            KitNotice.error(
              message: error,
              messageKey: const Key('approvals-save-failed'),
            ),
          if (setting.automatic && !_controller.isConnected)
            KitNotice(
              key: const Key('approvals-paused'),
              title: strings.approvalsUiIndicatorPaused,
              message: strings.approvalsUiPausedDetail,
              icon: AppIconography.pause,
            ),
          if (effective.inherited)
            KitNotice(
              key: const Key('approvals-inherited-note'),
              title: strings.approvalsUiInheritedFrom,
              message: strings.approvalsUiInheritedDetail,
              icon: AppIconography.nested,
              actions: [
                KitAction(
                  key: const Key('approvals-override'),
                  label: strings.approvalsUiOverride,
                  onPressed: () => _write(setting),
                ),
              ],
            ),
          if (effective.serverWide)
            KitNotice(
              key: const Key('approvals-everything-active'),
              message: strings.approvalsUiEverythingActive,
              icon: AppIconography.shield,
            ),
          KitRowGroup(
            margin: EdgeInsets.zero,
            leadingIcons: false,
            children: [
              KitSwitchRow(
                switchKey: const Key('approvals-mode-auto'),
                title: strings.approvalsUiAutoTitle,
                supporting: setting.automatic
                    ? null
                    : strings.approvalsUiAskDetail,
                value: setting.automatic,
                onChanged: (on) => _write(
                  on
                      ? const SessionAutoApproval(
                          mode: AutoApprovalMode.autoOnce,
                        )
                      // Inheritance is meaningless while asking; drop it so
                      // switching back on starts from the safe default.
                      : const SessionAutoApproval(mode: AutoApprovalMode.ask),
                ),
                risk: KitRisk(
                  scope: strings.approvalsUiAutoDetail,
                  onLabel: strings.approvalsUiIndicatorOn,
                  icon: AppIconography.shield,
                  onUntil: (_) {},
                ),
              ),
              KitSwitchRow(
                switchKey: const Key('approvals-inherit-switch'),
                title: strings.approvalsUiInheritTitle,
                supporting: strings.approvalsUiInheritDetail,
                value: setting.automatic && setting.inheritToChildren,
                onChanged: setting.automatic
                    ? (value) => _write(
                        SessionAutoApproval(
                          mode: AutoApprovalMode.autoOnce,
                          inheritToChildren: value,
                        ),
                      )
                    : null,
                disabledReason: strings.approvalsUiInheritUnavailable,
              ),
            ],
          ),
          if (effective.explicit && hasParent)
            KitInset(
              child: KitButton.tertiary(
                key: const Key('approvals-follow-parent'),
                icon: AppIconography.nested,
                label: strings.approvalsUiFollowParent,
                onPressed: () => _write(null),
              ),
            ),
          // The saved, server-wide choice: the one setting here that
          // reaches conversations you are not looking at.
          KitRowGroup(
            margin: EdgeInsets.zero,
            leadingIcons: false,
            children: [
              KitSwitchRow(
                switchKey: const Key('approvals-everything-switch'),
                title: strings.approvalsUiEverythingTitle,
                supporting: strings.approvalsUiEverythingDetail,
                value: _controller.approvesEverything,
                onChanged: (value) => unawaited(
                  _save(() => _controller.setApprovesEverything(value)),
                ),
                risk: KitRisk(
                  scope: strings.approvalsUiEverythingConfirmBody,
                  onLabel: strings.approvalsUiEverythingActive,
                  icon: AppIconography.warning,
                  onUntil: (_) {},
                ),
              ),
            ],
          ),
          if (setting.automatic)
            _AutoApprovalRecord(
              approved: _controller.autoApprovedFor(widget.sessionID),
            ),
          // What holds whatever is chosen above. What new conversations do
          // is said once, by the "Approve everything" switch.
          KitNotice(
            key: const Key('approvals-rules-note'),
            message: strings.approvalsUiServerRulesNoteEverything,
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

/// What this phone approved automatically in the session since connecting:
/// the transparency half of a setting that removes prompts. The latest five,
/// newest first.
class _AutoApprovalRecord extends StatelessWidget {
  const _AutoApprovalRecord({required this.approved});

  final List<AutoApprovedPermission> approved;
  static const _shown = 5;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final recent = approved.reversed.take(_shown).toList();
    final title = strings.approvalsUiRecordTitle(approved.length);
    if (recent.isEmpty) {
      return KitNotice(
        key: const Key('approvals-record'),
        message: title,
        liveRegion: false,
      );
    }
    return KitRowGroup(
      key: const Key('approvals-record'),
      margin: EdgeInsets.zero,
      label: title,
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
    );
  }
}

/// The approval chip in the strip above the composer: always there in a
/// conversation, naming how this conversation answers permission requests
/// ("Asks first", "Auto-approve", "Approves everything"), and saying when
/// automatic approval is paused because the app is disconnected. It is a
/// switcher, not only a gauge: [onOpen] receives the chip's own context so
/// the mode menu anchors to it. No count; the sheet keeps the record. The
/// full wording (the latest approval, where the setting comes from) is its
/// spoken label.
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
      detail = strings.approvalsUiAskDetail;
      text = strings.chatStripApprovalAsk;
    } else {
      label = strings.approvalsUiIndicatorOn;
      detail = last != null
          ? strings.approvalsUiAutoApproved(
              permissionRequestTitle(last.permission, l10n: strings),
            )
          : effective.inherited
          ? strings.approvalsUiInheritedFrom
          : null;
      text = effective.serverWide
          ? strings.chatStripApprovalEverything
          : strings.chatStripAutoApprove;
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
          icon: paused
              ? AppIconography.pause
              : !automatic
              ? AppIconography.permissions
              : effective.serverWide
              ? AppIconography.warning
              : effective.inherited
              ? AppIconography.nested
              : AppIconography.shield,
          label: text,
        ),
      ),
    );
  }
}
