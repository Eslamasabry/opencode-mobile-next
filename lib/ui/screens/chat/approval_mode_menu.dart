part of '../chat_screen.dart';

/// How this conversation answers permission requests, least to most
/// permissive. Each is a mode the app really has: "ask" and "auto" are this
/// conversation's own saved setting; "everything" is the saved,
/// server-wide switch from the approvals sheet.
enum _ApprovalChoice { ask, auto, everything }

_ApprovalChoice _choiceOf(EffectiveAutoApproval effective) =>
    effective.serverWide
    ? _ApprovalChoice.everything
    : effective.automatic
    ? _ApprovalChoice.auto
    : _ApprovalChoice.ask;

extension _ChatApprovalModeMenu on _ChatScreenState {
  /// The modes offered. Asking always is. The two automatic modes are offered
  /// only while the person's automation policy lets this phone answer
  /// requests (supervision below High); there is no server-side preset to
  /// offer, so none is listed. The current mode is always listed, so the
  /// check never goes missing.
  List<_ApprovalChoice> _offeredApprovalChoices(_ApprovalChoice current) => [
    for (final choice in _ApprovalChoice.values)
      if (choice == _ApprovalChoice.ask ||
          choice == current ||
          _conn.automationPolicy.allowsAutoApproval)
        choice,
  ];

  /// Opens the mode menu anchored to the chip ([chipContext]).
  Future<void> _showApprovalModeMenu(BuildContext chipContext) async {
    final strings = _chatL10n(chipContext);
    final current = _choiceOf(_conn.autoApprovalFor(widget.sessionID));
    final offered = _offeredApprovalChoices(current);
    KitMenuItem item(
      _ApprovalChoice choice,
      String key,
      String title,
      String? detail,
    ) => KitMenuItem(
      key: Key(key),
      label: title,
      supporting: detail,
      checked: choice == current,
      group: 'modes',
      onSelected: () => unawaited(_chooseApprovalMode(choice)),
    );
    await showKitMenu(
      chipContext,
      semanticsLabel: strings.approvalModeMenuLabel,
      menuKey: const Key('approval-mode-menu'),
      items: [
        for (final choice in offered)
          switch (choice) {
            _ApprovalChoice.ask => item(
              choice,
              'approval-mode-ask',
              strings.approvalModeAskTitle,
              strings.approvalsUiAskDetail,
            ),
            _ApprovalChoice.auto => item(
              choice,
              'approval-mode-auto',
              strings.approvalModeAutoTitle,
              strings.approvalModeAutoDetail,
            ),
            _ApprovalChoice.everything => item(
              choice,
              'approval-mode-everything',
              strings.approvalModeEverythingTitle,
              strings.approvalModeEverythingDetail,
            ),
          },
        KitMenuItem(
          key: const Key('approval-mode-settings'),
          label: strings.approvalModeSettings,
          icon: AppIconography.permissions,
          group: 'settings',
          onSelected: () => unawaited(
            showSessionApprovalsSheet(
              context,
              controller: _conn,
              sessionID: widget.sessionID,
            ),
          ),
        ),
      ],
    );
  }

  /// Applies [choice] to this conversation through the same controller
  /// calls the approvals sheet uses. A stricter mode applies at once; a
  /// more permissive one first asks, with the same scope text as the
  /// sheet's risk step, and Cancel changes nothing.
  Future<void> _chooseApprovalMode(_ApprovalChoice choice) async {
    final strings = _chatL10n(context);
    final current = _choiceOf(_conn.autoApprovalFor(widget.sessionID));
    if (choice == current) return;
    Future<void> apply() async {
      switch (choice) {
        case _ApprovalChoice.ask:
          await _conn.setSessionAutoApproval(
            widget.sessionID,
            const SessionAutoApproval(mode: AutoApprovalMode.ask),
          );
        case _ApprovalChoice.auto:
          await _conn.setSessionAutoApproval(
            widget.sessionID,
            const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
          );
        case _ApprovalChoice.everything:
          await _conn.setApprovesEverything(true);
          // A conversation's own choice outranks the server-wide one; drop
          // it so this conversation follows "approve everything".
          if (_conn.autoApprovalFor(widget.sessionID).explicit) {
            await _conn.setSessionAutoApproval(widget.sessionID, null);
          }
      }
    }

    final note = switch (choice) {
      _ApprovalChoice.ask => strings.approvalModeNowAsk,
      _ApprovalChoice.auto => strings.approvalModeNowAuto,
      _ApprovalChoice.everything => strings.approvalModeNowEverything,
    };
    try {
      if (choice.index > current.index) {
        final confirmed = await showKitConfirm(
          context,
          title: choice == _ApprovalChoice.auto
              ? strings.approvalModeConfirmAutoTitle
              : strings.approvalModeConfirmEverythingTitle,
          body: choice == _ApprovalChoice.auto
              ? strings.approvalsUiAutoDetail
              : strings.approvalsUiEverythingConfirmBody,
          confirmLabel: choice == _ApprovalChoice.auto
              ? strings.approvalModeConfirmAutoAction
              : strings.approvalModeConfirmEverythingAction,
          icon: choice == _ApprovalChoice.auto
              ? AppIconography.shield
              : AppIconography.warning,
          sheetKey: const Key('approval-mode-confirm'),
          action: apply,
        );
        if (!confirmed || !mounted) return;
      } else {
        await apply();
        if (!mounted) return;
      }
      _showComposerNote(note, key: const Key('approval-mode-note'));
    } catch (error) {
      if (!mounted) return;
      _showComposerNote(
        _chatL10n(context).approvalsUiSaveFailed(productErrorText(error)),
        key: const Key('approval-mode-failed'),
      );
    }
  }
}
