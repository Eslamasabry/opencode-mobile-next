import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/agent_handoff_controller.dart';
import '../../state/connection.dart';
import '../../state/continue_with.dart';
import '../../state/continue_with_connection.dart';
import '../app_theme.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_field.dart';
import '../kit/kit_details_fold.dart';
import '../kit/kit_handoff_record.dart';
import '../kit/kit_key_value.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_progress.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_section_label.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/motion/kit_reveal.dart';
import '../screens/agents/agent_sheet.dart' show AgentSheetStep, showAgentSheet;

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// What "Continue with…" gave back: a conversation to open, or the agent whose
/// sign-in the person asked for (the sheet closes first; sheets never stack).
@immutable
class ContinueWithResult {
  const ContinueWithResult.open(String this.sessionId) : signInAgentId = null;
  const ContinueWithResult.signIn(String this.signInAgentId) : sessionId = null;

  final String? sessionId;
  final String? signInAgentId;
}

/// The conversation menu's "Continue with…" (and the quiet "Continue with
/// another agent" after a provider failure): opens the sheet for
/// [sessionId], walks the sign-in flow when asked, and opens the new
/// conversation. Returns the id opened, or null.
Future<String?> showContinueWith(
  BuildContext context, {
  required ConnectionController connection,
  required String sessionId,
}) async {
  while (context.mounted) {
    final l10n = _l10n(context);
    final host = ConnectionContinueWithHost(
      connection,
      sessionId: sessionId,
      untitled: l10n.chatUiOpenCodeSession,
    );
    final ContinueWithResult? result;
    try {
      result = await showContinueWithSheet(
        context,
        host: host,
        draftScope: connection.profile == null
            ? null
            : (profileId: connection.profile!.id, sessionId: sessionId),
      );
    } finally {
      host.release();
    }
    if (result == null || !context.mounted) return null;
    final signIn = result.signInAgentId;
    if (signIn != null) {
      await showAgentSheet(
        context,
        agentId: signIn,
        step: AgentSheetStep.signIn,
      );
      continue;
    }
    final id = result.sessionId;
    if (id != null && context.mounted) {
      await Navigator.of(context).pushNamed('/chat/$id');
      return id;
    }
    return null;
  }
  return null;
}

/// The sheet itself, over any [ContinueWithHost] (a fake in tests).
Future<ContinueWithResult?> showContinueWithSheet(
  BuildContext context, {
  required ContinueWithHost host,
  bool Function()? isActive,

  /// Whose first message is kept across dismissal (G10): the server profile
  /// and the conversation it continues. Null keeps nothing.
  ({String profileId, String sessionId})? draftScope,
}) {
  final l10n = _l10n(context);
  final flow = ContinueWithFlow(host, isActive: isActive);
  final message = TextEditingController();
  final kitDraft = draftScope == null
      ? null
      : KitDraft(
          target: 'continue-with.${draftScope.sessionId}',
          profileId: draftScope.profileId,
          controller: message,
        );
  final primary = ValueNotifier<KitAction?>(null);
  final secondary = ValueNotifier<KitAction?>(null);
  final loading = ValueNotifier<bool>(true);

  void sync() {
    final busy =
        flow.phase == ContinueWithPhase.loading ||
        flow.phase == ContinueWithPhase.preparing ||
        flow.phase == ContinueWithPhase.switching ||
        flow.phase == ContinueWithPhase.starting;
    loading.value = busy;
    primary.value = _primaryFor(context, flow, message, kitDraft);
    secondary.value = _secondaryFor(context, flow);
  }

  flow.addListener(sync);
  message.addListener(sync);
  var seeded = '';
  flow.addListener(() {
    // The first message starts as the draft the controller wrote, and is
    // masked again as the person edits it, so the review matches what is sent.
    final draft = flow.draft;
    if (draft != null && seeded != draft.recordId) {
      seeded = draft.recordId;
      // What the person wrote last time wins over the generated first message.
      unawaited(() async {
        await kitDraft?.restore();
        if (message.text.isEmpty) message.text = draft.prompt;
      }());
    }
  });
  unawaited(flow.load());
  sync();

  return showKitSheet<ContinueWithResult>(
    context,
    title: l10n.continueWithTitle,
    icon: AppIconography.forward,
    height: KitSheetHeight.content,
    primaryListenable: primary,
    secondaryListenable: secondary,
    secondaryDismisses: true,
    loading: loading,
    sheetKey: const ValueKey('continue-with-sheet'),
    body: (sheetContext) => _ContinueWithBody(
      flow: flow,
      message: message,
      kitDraft: kitDraft,
      owned: [primary, secondary, loading],
      detach: () {
        flow.removeListener(sync);
        message.removeListener(sync);
      },
      onOpen: (id) => Navigator.of(context).pop(ContinueWithResult.open(id)),
      onSignIn: (agentId) =>
          Navigator.of(context).pop(ContinueWithResult.signIn(agentId)),
    ),
  );
}

KitAction? _primaryFor(
  BuildContext context,
  ContinueWithFlow flow,
  TextEditingController message,
  KitDraft? kitDraft,
) {
  final l10n = _l10n(context);
  switch (flow.phase) {
    case ContinueWithPhase.native:
    case ContinueWithPhase.switching:
      return KitAction(
        key: const ValueKey('continue-with-use-model'),
        label: l10n.continueWithUseModel,
        working: flow.phase == ContinueWithPhase.switching,
        onPressed: () async {
          final done = await flow.useModel();
          if (done && context.mounted) Navigator.of(context).pop();
        },
      );
    case ContinueWithPhase.ready:
    case ContinueWithPhase.starting:
      final starting = flow.phase == ContinueWithPhase.starting;
      return KitAction(
        key: const ValueKey('continue-with-start'),
        label: l10n.continueWithStart,
        working: starting,
        onPressed: message.text.trim().isEmpty
            ? null
            : () async {
                final id = await flow.start(message.text);
                if (id != null) await kitDraft?.clear();
                if (id != null && context.mounted) {
                  Navigator.of(context).pop(ContinueWithResult.open(id));
                }
              },
        disabledReason: message.text.trim().isEmpty
            ? l10n.continueWithMessageInvalid
            : null,
      );
    case ContinueWithPhase.linkFailed:
      return KitAction(
        key: const ValueKey('continue-with-save-link'),
        label: l10n.continueWithSaveLinkAgain,
        onPressed: () async {
          final id = await flow.retryLink();
          if (id != null && context.mounted) {
            Navigator.of(context).pop(ContinueWithResult.open(id));
          }
        },
      );
    case ContinueWithPhase.loading:
    case ContinueWithPhase.loadFailed:
    case ContinueWithPhase.choosing:
    case ContinueWithPhase.preparing:
    case ContinueWithPhase.done:
      return null;
  }
}

KitAction _secondaryFor(BuildContext context, ContinueWithFlow flow) {
  final l10n = _l10n(context);
  if (flow.phase == ContinueWithPhase.linkFailed &&
      flow.openSessionId != null) {
    return KitAction(
      key: const ValueKey('continue-with-open'),
      label: l10n.continueWithOpenConversation,
      onPressed: () => Navigator.of(
        context,
      ).pop(ContinueWithResult.open(flow.openSessionId!)),
    );
  }
  return KitAction(
    key: const ValueKey('continue-with-cancel'),
    label: l10n.continueWithCancel,
    onPressed: () => Navigator.of(context).pop(),
  );
}

String _problemText(AppLocalizations l10n, ContinueWithProblem problem) =>
    switch (problem) {
      ContinueWithProblem.noWriter => l10n.continueWithErrNoWriter,
      ContinueWithProblem.project => l10n.continueWithErrProject,
      ContinueWithProblem.target => l10n.continueWithErrTarget,
      ContinueWithProblem.source => l10n.continueWithErrSource,
      ContinueWithProblem.scope => l10n.continueWithErrScope,
      ContinueWithProblem.read => l10n.continueWithErrRead,
      ContinueWithProblem.tooLarge => l10n.continueWithErrTooLarge,
      ContinueWithProblem.file => l10n.continueWithErrFile,
      ContinueWithProblem.unknown => l10n.continueWithErrUnknown,
      ContinueWithProblem.link => l10n.continueWithErrLink,
      ContinueWithProblem.message => l10n.continueWithMessageInvalid,
      ContinueWithProblem.model => l10n.continueWithModelFailed,
      ContinueWithProblem.generic => l10n.continueWithErrGeneric,
    };

/// Owns the sheet's state: it is disposed with the body, after the field and
/// the frame have let go of it (the closing animation still builds them).
class _ContinueWithBody extends StatefulWidget {
  const _ContinueWithBody({
    required this.flow,
    required this.message,
    required this.kitDraft,
    required this.owned,
    required this.detach,
    required this.onOpen,
    required this.onSignIn,
  });

  final ContinueWithFlow flow;
  final TextEditingController message;
  final KitDraft? kitDraft;
  final List<ChangeNotifier> owned;
  final VoidCallback detach;
  final ValueChanged<String> onOpen;
  final ValueChanged<String> onSignIn;

  @override
  State<_ContinueWithBody> createState() => _ContinueWithBodyState();
}

class _ContinueWithBodyState extends State<_ContinueWithBody> {
  ContinueWithFlow get flow => widget.flow;
  TextEditingController get message => widget.message;

  @override
  void dispose() {
    widget.detach();
    flow.close();
    flow.dispose();
    message.dispose();
    for (final notifier in widget.owned) {
      notifier.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final onSignIn = widget.onSignIn;
    return ListenableBuilder(
      listenable: flow,
      builder: (context, _) {
        final l10n = _l10n(context);
        final tokens = KitTokens.of(context);
        final plan = flow.plan;
        final selected = flow.selected;
        final phase = flow.phase;
        final gap = SizedBox(height: tokens.space3);
        return Column(
          key: const ValueKey('continue-with-body'),
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (plan != null)
              KitText(
                l10n.continueWithSubtitle(plan.sourceTitle, plan.project),
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            KitReveal(
              child: phase == ContinueWithPhase.loading
                  ? Padding(
                      padding: EdgeInsets.only(top: tokens.space3),
                      child: const KitSkeletonRows(count: 3),
                    )
                  : null,
            ),
            KitReveal(
              child: phase == ContinueWithPhase.loadFailed
                  ? Padding(
                      padding: EdgeInsets.only(top: tokens.space3),
                      child: KitNotice(
                        key: const ValueKey('continue-with-load-failed'),
                        tone: AppStatusTone.failure,
                        message: l10n.continueWithLoadFailed,
                        actions: [
                          KitAction(
                            key: const ValueKey('continue-with-reload'),
                            label: l10n.continueWithTryAgain,
                            icon: AppIconography.retry,
                            onPressed: () => flow.load(),
                          ),
                        ],
                      ),
                    )
                  : null,
            ),
            // The list: choose where to continue.
            KitReveal(
              child: plan != null && selected == null
                  ? Padding(
                      padding: EdgeInsets.only(top: tokens.space3),
                      child: _Choices(
                        flow: flow,
                        plan: plan,
                        onSignIn: onSignIn,
                      ),
                    )
                  : null,
            ),
            // The choice made, and what happens next.
            KitReveal(
              child: selected == null
                  ? null
                  : Padding(
                      padding: EdgeInsets.only(top: tokens.space3),
                      child: _Selection(
                        flow: flow,
                        plan: plan!,
                        choice: selected,
                        message: message,
                        kitDraft: widget.kitDraft,
                      ),
                    ),
            ),
            gap,
          ],
        );
      },
    );
  }
}

class _Choices extends StatelessWidget {
  const _Choices({
    required this.flow,
    required this.plan,
    required this.onSignIn,
  });

  final ContinueWithFlow flow;
  final ContinueWithPlan plan;
  final ValueChanged<String> onSignIn;

  Widget _row(BuildContext context, ContinueWithChoice choice) {
    final l10n = _l10n(context);
    final key = ValueKey('continue-with-choice-${choice.key}');
    if (!choice.usable) {
      final reason = switch (choice.block!) {
        ContinueWithBlock.needsSignIn => l10n.continueWithNeedsSignIn(
          choice.agentName,
        ),
        ContinueWithBlock.limitReached => l10n.continueWithLimitReached(
          choice.agentName,
        ),
        ContinueWithBlock.notReady => l10n.continueWithNotReady(
          choice.agentName,
        ),
        ContinueWithBlock.notSameProject => l10n.continueWithErrProject,
        ContinueWithBlock.noRecordWriter => l10n.continueWithErrNoWriter,
      };
      final signIn =
          choice.block == ContinueWithBlock.needsSignIn &&
          choice.agentId != null;
      // A helper Android or an update stopped can take longer than the
      // sheet's wait to start: reading again is the way forward.
      final retry = choice.block == ContinueWithBlock.notReady;
      return KitRow.unavailable(
        key: key,
        title: choice.agentName,
        reason: reason,
        leading: const KitRowIcon(AppIconography.agent),
        chip: signIn
            ? KitRowChip(
                key: ValueKey('continue-with-sign-in-${choice.agentId}'),
                label: l10n.continueWithSignIn,
                semanticsLabel:
                    '${l10n.continueWithSignIn} ${choice.agentName}',
                onPressed: () => onSignIn(choice.agentId!),
              )
            : retry
            ? KitRowChip(
                key: ValueKey('continue-with-retry-${choice.key}'),
                label: l10n.continueWithTryAgain,
                semanticsLabel:
                    '${l10n.continueWithTryAgain} ${choice.agentName}',
                onPressed: flow.load,
              )
            : null,
      );
    }
    final locked = flow.locked && !choice.native;
    return KitRow(
      key: key,
      title: choice.modelName,
      supporting: TextSpan(text: choice.agentName),
      leading: KitRowIcon(
        choice.native ? AppIconography.model : AppIconography.agent,
      ),
      enabled: !locked,
      disabledReason: locked ? l10n.continueWithErrUnknown : null,
      onTap: () => flow.select(choice),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    if (plan.all.isEmpty) {
      return KitNotice(
        key: const ValueKey('continue-with-empty'),
        icon: AppIconography.info,
        message: l10n.continueWithNothingReady,
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (flow.problem != null)
          Padding(
            padding: EdgeInsets.only(bottom: KitTokens.of(context).space3),
            child: KitNotice(
              key: const ValueKey('continue-with-problem'),
              tone: AppStatusTone.failure,
              message: _problemText(l10n, flow.problem!),
            ),
          ),
        if (plan.models.isNotEmpty)
          KitRowGroup(
            margin: EdgeInsets.zero,
            label: l10n.continueWithModelsHere,
            children: [for (final c in plan.models) _row(context, c)],
          ),
        if (plan.agents.isNotEmpty)
          KitRowGroup(
            margin: EdgeInsets.zero,
            label: l10n.continueWithAgents,
            children: [for (final c in plan.agents) _row(context, c)],
          ),
      ],
    );
  }
}

class _Selection extends StatelessWidget {
  const _Selection({
    required this.flow,
    required this.plan,
    required this.choice,
    required this.message,
    required this.kitDraft,
  });

  final ContinueWithFlow flow;
  final ContinueWithPlan plan;
  final ContinueWithChoice choice;
  final TextEditingController message;
  final KitDraft? kitDraft;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final phase = flow.phase;
    final draft = flow.draft;
    final problem = flow.problem;
    final busy =
        phase == ContinueWithPhase.switching ||
        phase == ContinueWithPhase.starting;
    final progress = switch (flow.attempt) {
      AgentHandoffPhase.writingFile => l10n.continueWithSaving,
      AgentHandoffPhase.creating ||
      AgentHandoffPhase.sending => l10n.continueWithStarting,
      AgentHandoffPhase.linking => l10n.continueWithLinking,
      _ => l10n.continueWithPreparing,
    };
    final gap = SizedBox(height: tokens.space3);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitRowGroup(
          margin: EdgeInsets.zero,
          children: [
            KitRow(
              key: const ValueKey('continue-with-selected'),
              title: choice.modelName,
              supporting: TextSpan(text: choice.agentName),
              leading: KitRowIcon(
                choice.native ? AppIconography.model : AppIconography.agent,
                current: true,
              ),
              action: busy
                  ? null
                  : KitAction(
                      key: const ValueKey('continue-with-change'),
                      label: l10n.continueWithChange,
                      onPressed: flow.backToChoices,
                    ),
            ),
          ],
        ),
        gap,
        KitText(
          choice.native
              ? l10n.continueWithNativeNote
              : l10n.continueWithNewNote,
          key: const ValueKey('continue-with-note'),
        ),
        if (!choice.native) ...[
          SizedBox(height: tokens.space2),
          KitText(
            l10n.continueWithSourceStays,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
          gap,
          KitKeyValue(
            rows: [
              KitKeyValueRow(
                label: l10n.continueWithFactAgent,
                value: choice.agentName,
              ),
              KitKeyValueRow(
                label: l10n.continueWithFactModel,
                value: choice.modelName,
              ),
              KitKeyValueRow(
                label: l10n.continueWithFactProject,
                value: plan.project,
              ),
            ],
          ),
        ],
        KitReveal(
          child: problem == null
              ? null
              : Padding(
                  padding: EdgeInsets.only(top: tokens.space3),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      KitNotice(
                        key: const ValueKey('continue-with-problem'),
                        tone: AppStatusTone.failure,
                        message: _problemText(l10n, problem),
                        actions: [
                          // Nothing was sent: a fresh preparation is safe.
                          if (problem == ContinueWithProblem.source ||
                              problem == ContinueWithProblem.read ||
                              problem == ContinueWithProblem.scope ||
                              problem == ContinueWithProblem.file)
                            KitAction(
                              key: const ValueKey(
                                'continue-with-prepare-again',
                              ),
                              label: l10n.continueWithPrepareAgain,
                              icon: AppIconography.retry,
                              onPressed: flow.prepareAgain,
                            ),
                        ],
                      ),
                      if (flow.problemDetail case final String detail)
                        KitDetailsFold(text: detail),
                    ],
                  ),
                ),
        ),
        KitReveal(
          child:
              phase == ContinueWithPhase.preparing ||
                  phase == ContinueWithPhase.starting
              ? Padding(
                  padding: EdgeInsets.only(top: tokens.space3),
                  child: KitNotice(
                    key: const ValueKey('continue-with-progress'),
                    tone: AppStatusTone.progress,
                    message: progress,
                  ),
                )
              : null,
        ),
        KitReveal(
          child: draft == null || choice.native
              ? null
              : Padding(
                  padding: EdgeInsets.only(top: tokens.space3),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      KitSectionLabel.inline(l10n.continueWithRecordTitle),
                      KitHandoffRecord(
                        sizeBytes: draft.record.byteLength,
                        omittedMessages: draft.record.omittedMessages,
                        redacted: draft.record.redacted,
                        markdown: draft.record.markdown,
                        path: draft.relativePath,
                      ),
                      gap,
                      _FirstMessage(
                        flow: flow,
                        draft: draft,
                        message: message,
                        kitDraft: kitDraft,
                        invalid: problem == ContinueWithProblem.message,
                      ),
                    ],
                  ),
                ),
        ),
      ],
    );
  }
}

class _FirstMessage extends StatelessWidget {
  const _FirstMessage({
    required this.flow,
    required this.draft,
    required this.message,
    required this.kitDraft,
    required this.invalid,
  });

  final ContinueWithFlow flow;
  final AgentHandoffDraft draft;
  final TextEditingController message;
  final KitDraft? kitDraft;
  final bool invalid;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final editable = flow.phase == ContinueWithPhase.ready;
    return KitField(
      fieldKey: const ValueKey('continue-with-message'),
      label: l10n.continueWithFirstMessage,
      controller: kitDraft == null ? message : null,
      draft: kitDraft,
      kind: KitFieldKind.multiline,
      minLines: 3,
      maxLines: 8,
      helper: l10n.continueWithFirstMessageHelp,
      error: invalid ? l10n.continueWithMessageInvalid : null,
      enabled: editable,
      disabledReason: l10n.continueWithStarting,
      onChanged: (text) {
        final clean = draft.sanitizePrompt(text);
        if (clean == text) return;
        final at = math.min(message.selection.baseOffset, clean.length);
        message.value = TextEditingValue(
          text: clean,
          selection: TextSelection.collapsed(
            offset: at < 0 ? clean.length : at,
          ),
        );
      },
    );
  }
}
