part of '../chat_screen.dart';

/// How this conversation answers permission requests, least to most
/// permissive. Each is a mode the app really has: "ask" and "auto" are this
/// conversation's own saved setting; "everything" is the saved,
/// server-wide switch.
enum _ApprovalChoice { ask, auto, everything }

_ApprovalChoice _choiceOf(EffectiveAutoApproval effective) =>
    effective.serverWide
    ? _ApprovalChoice.everything
    : effective.automatic
    ? _ApprovalChoice.auto
    : _ApprovalChoice.ask;

/// The modes offered: all three, always. The AI Team's supervision level
/// governs the team, not conversations; here the person's choice is the
/// consent ("Approve everything" is confirmed when chosen). There is no
/// server-side preset to offer, so none is listed.
List<_ApprovalChoice> _offeredApprovalChoices(
  ConnectionController conn,
  _ApprovalChoice current,
) => _ApprovalChoice.values;

/// Writes [choice] for [sessionID] through the controller. The one write
/// path: the chip menu and the approvals sheet both call it, so they save
/// exactly the same settings.
Future<void> _writeApprovalChoice(
  ConnectionController conn,
  String sessionID,
  _ApprovalChoice choice,
) async {
  // Leaving "Approve everything" turns the server-wide switch off: it is
  // the mode being left, and there is no other place to undo it.
  if (choice != _ApprovalChoice.everything &&
      _choiceOf(conn.autoApprovalFor(sessionID)) ==
          _ApprovalChoice.everything) {
    await conn.setApprovesEverything(false);
  }
  switch (choice) {
    case _ApprovalChoice.ask:
      await conn.setSessionAutoApproval(
        sessionID,
        const SessionAutoApproval(mode: AutoApprovalMode.ask),
      );
    case _ApprovalChoice.auto:
      await conn.setSessionAutoApproval(
        sessionID,
        const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
      );
    case _ApprovalChoice.everything:
      await conn.setApprovesEverything(true);
      // A conversation's own choice outranks the server-wide one; drop it
      // so this conversation follows "approve everything".
      if (conn.autoApprovalFor(sessionID).explicit) {
        await conn.setSessionAutoApproval(sessionID, null);
      }
  }
}

/// Switches [sessionID] to [choice]. Only the server-wide "Approve
/// everything" asks first (one sentence, then the act or Cancel); the
/// conversation's own modes apply at once. Returns whether the mode
/// changed. A failed save throws.
Future<bool> _requestApprovalChoice(
  BuildContext context,
  ConnectionController conn,
  String sessionID,
  _ApprovalChoice choice,
) async {
  final current = _choiceOf(conn.autoApprovalFor(sessionID));
  if (choice == current) return false;
  if (choice != _ApprovalChoice.everything) {
    await _writeApprovalChoice(conn, sessionID, choice);
    return true;
  }
  final strings = _chatL10n(context);
  return showKitConfirm(
    context,
    title: strings.approvalModeConfirmEverythingTitle,
    body: strings.approvalModeConfirmEverythingBody,
    confirmLabel: strings.approvalModeConfirmEverythingAction,
    sheetKey: const Key('approval-mode-confirm'),
    action: () => _writeApprovalChoice(conn, sessionID, choice),
  );
}

extension _ChatApprovalModeMenu on _ChatScreenState {
  /// Opens the mode menu anchored to the chip ([chipContext]).
  Future<void> _showApprovalModeMenu(BuildContext chipContext) async {
    final strings = _chatL10n(chipContext);
    final current = _choiceOf(_conn.autoApprovalFor(widget.sessionID));
    final offered = _offeredApprovalChoices(_conn, current);
    KitMenuItem item(
      _ApprovalChoice choice,
      String key,
      String title,
      String detail,
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
              strings.approvalModeAskDetail,
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
              _conn.isAgentBackend
                  ? strings.approvalModeEverythingAgentDetail(
                      KitBidi.auto(_conn.profile?.name ?? ''),
                    )
                  : strings.approvalModeEverythingDetail,
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

  /// Applies [choice] to this conversation (see [_requestApprovalChoice])
  /// and says so in a short composer note.
  Future<void> _chooseApprovalMode(_ApprovalChoice choice) async {
    final strings = _chatL10n(context);
    try {
      final changed = await _requestApprovalChoice(
        context,
        _conn,
        widget.sessionID,
        choice,
      );
      if (!changed || !mounted) return;
      _showComposerNote(switch (choice) {
        _ApprovalChoice.ask => strings.approvalModeNowAsk,
        _ApprovalChoice.auto => strings.approvalModeNowAuto,
        _ApprovalChoice.everything => strings.approvalModeNowEverything,
      }, key: const Key('approval-mode-note'));
    } catch (error) {
      if (!mounted) return;
      _showComposerNote(
        _chatL10n(context).approvalsUiSaveFailed(productErrorText(error)),
        key: const Key('approval-mode-failed'),
      );
    }
  }
}
