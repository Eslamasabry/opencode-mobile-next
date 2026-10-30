part of '../chat_screen.dart';

// "Send later or repeat": a long-press on Send turns the words in the box
// into a scheduled prompt for this conversation (the scheduling sheet of
// lib/ui/screens/scheduling/, opened with this conversation as its target).

extension _ChatScheduleSend on _ChatScreenState {
  /// Send's long-press menu, where this server and conversation can take a
  /// scheduled prompt.
  /// The Send tip ("Hold Send to send later or repeat") while it is this
  /// screen's to show.
  KitComposerTip? _sendTip() {
    const id = TeachingIds.sendLater;
    final tips = _conn.tips;
    if (!tips.isActive(id) || tips.activeIsReplay) return null;
    return KitComposerTip(
      message: _chatL10n(context).tipSendLater,
      onDismissed: () => tips.dismiss(id),
    );
  }

  /// The Send tip's moment: words wait in the box, Send has its menu, and
  /// no reply runs (Send is Stop then). Called as the words change.
  void _offerSendTip() {
    const id = TeachingIds.sendLater;
    final tips = _conn.tips;
    if (_conn.isIsolated ||
        _composer.text.trim().isEmpty ||
        _live != null ||
        _conn.busySessions.contains(widget.sessionID) ||
        tips.isActive(id) ||
        !tips.due(id, maxCount: teachingEntry(id).maxCount) ||
        _sendMenu().isEmpty) {
      return;
    }
    offerTip(
      tips,
      id,
      visit: this,
      // Send rides above the keyboard: the keyboard never covers it.
      moment: TipMoment(
        questionWaiting: _tipMoment().questionWaiting,
        onboarding: _tipMoment().onboarding,
      ),
    );
  }

  List<KitMenuItem> _sendMenu() {
    if (_conn.isIsolated || _watching || !canSchedulePrompts(_conn)) {
      return const [];
    }
    return [
      KitMenuItem(
        key: const Key('composer-send-later'),
        label: _chatL10n(context).chatSendLaterOrRepeat,
        icon: AppIconography.clock,
        onSelected: () => unawaited(_sendLaterOrRepeat()),
      ),
    ];
  }

  /// Opens the scheduling sheet seeded with the draft, for this
  /// conversation; once it is saved the draft leaves the box and the note
  /// says so.
  Future<void> _sendLaterOrRepeat() async {
    _conn.tips.markUsed(TeachingIds.sendLater);
    final profile = _conn.profile;
    final directory = _conn.directory;
    if (profile == null || directory == null || !canSchedulePrompts(_conn)) {
      return;
    }
    final controller = ScheduledPromptsController(profileId: profile.id);
    try {
      await controller.load();
      if (!mounted) return;
      final selection = scheduleSelectionSeed(
        sessionSelection: _conn.selectionForSession(widget.sessionID),
        defaultModel: _conn.selectedModel,
        defaultAgent: _conn.selectedAgent,
      );
      final now = controller.now();
      final exit = await showScheduleSheet(
        context,
        controller: controller,
        seed: ScheduledPrompt(
          id: now.microsecondsSinceEpoch.toString(),
          profileId: profile.id,
          profileFingerprint: scheduledProfileFingerprint(profile),
          prompt: _composer.text.trim(),
          projectPath: directory,
          workspace: _conn.workspace,
          sessionId: widget.sessionID,
          agent: selection.agent,
          modelProviderId: selection.model?.providerID,
          modelId: selection.model?.modelID,
          // "Later" first: an hour from now, on the hour.
          recurrence: ScheduleRecurrence.once(
            DateTime(now.year, now.month, now.day, now.hour + 1),
          ),
          createdAt: now,
        ),
        where: scheduleProjectName(directory),
      );
      if (!mounted || exit != ScheduleSheetExit.saved) return;
      _composer.clear();
      _persistDraft();
      _showComposerNote(_chatL10n(context).chatSendLaterSaved);
    } finally {
      controller.dispose();
    }
  }
}
