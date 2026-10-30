/// The task's merge gate (TEAM-205, 02-ux-flows-and-screens §8a; vision
/// 2026-10-20, docs/qa/vision-team-task-2026-10-20/README.md): present only
/// once every work item of the run is done or review-ready and the host has
/// merge roles ([OrchestrationCapabilities.mergeReadiness] with an adapter
/// that implements [OrchestrationMergeGateway], the host front). A team on
/// this phone merges by itself; there is no gate there.
///
/// Ready, it is the conversation's one question card ([KitRequestCard.ask],
/// the question look, the same as the chat's permission card):
///
/// ```
/// ● Needs you · 6 min
/// Merge 6 changes into main?
/// 6 files · +312 / −87 · all 5 checks passed
/// [        Review changes        ]
/// [ ⑂      Merge into main        ]
/// ```
///
/// The question names the act, the count and the branch from the host's
/// readiness document, so one tap on **Merge into main** merges (the card is
/// the confirmation; there is no second sheet saying the same). While the
/// merge request waits for an approval the same card answers **Approve the
/// merge** first (reversible on the host); then Merge. A boundary that
/// blocks ("Never merge without approval") or a team that isn't answering
/// turns Merge off with its reason. A refused merge stays on the card with
/// the host's reason and Try again. **Review changes** opens the checks,
/// the changed files with their counts and the task's steps; there is no
/// diff to open (the readiness carries no patch).
///
/// Not ready (a check failing or still running, readiness unavailable), the
/// section is a calm panel that says why Merge is off: nobody can act yet,
/// so it is never amber. Merged, it says only where it landed, "Merged into
/// main · 4f9c2a1", with Review changes.
///
/// A task's own cost is not reported by any host (slice-P5.2), so the card
/// shows none. Force-merge, branch reset and worktree deletion do not exist
/// here, on the host front, or anywhere on the phone.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/orchestration_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/orchestration.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/product_states.dart' show productErrorText;
import '../../widgets/team_vocabulary.dart';
import 'work_sheet.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Readiness line keys the section knows a label for, in the §8a order.
const teamMergeKnownLines = [
  'work',
  'tests',
  'build',
  'review',
  'conflicts',
  'acceptance',
];

/// The localized label of a readiness line; an unknown (configured) key
/// is shown as the host named it.
String teamMergeLineLabel(AppLocalizations l10n, String key) => switch (key) {
  'work' => l10n.teamUiMergeLineWork,
  'tests' => l10n.teamUiMergeLineTests,
  'build' => l10n.teamUiMergeLineBuild,
  'review' => l10n.teamUiMergeLineReview,
  'conflicts' => l10n.teamUiMergeLineConflicts,
  'acceptance' => l10n.teamUiMergeLineAcceptance,
  _ => key,
};

/// True when every work item of [run] is done or review-ready (cancelled
/// items have nothing to merge and do not hold the section back); without
/// tracked items the run's own step counts or completed state decide.
bool teamMergeWorkDone(OrchestrationRun run, List<WorkItem> work) {
  var any = false;
  for (final item in work) {
    if (item.runId != run.id) continue;
    any = true;
    switch (item.state) {
      case WorkState.completed:
      case WorkState.review:
      case WorkState.cancelled:
        break;
      case WorkState.queued:
      case WorkState.ready:
      case WorkState.working:
      case WorkState.waiting:
      case WorkState.blocked:
      case WorkState.needsInput:
      case WorkState.failed:
      case WorkState.unknown:
        return false;
    }
  }
  if (any) return true;
  if (run.state == RunState.completed) return true;
  final total = run.stepCount ?? 0;
  return total > 0 && (run.completedSteps ?? 0) >= total;
}

/// The section belongs in the Overview: the capability is on, the adapter
/// has merge roles and the run's work is done.
bool teamMergeEligible(
  OrchestrationController controller,
  OrchestrationRun run,
) {
  if (!teamMergeGateOnHost(controller)) return false;
  return teamMergeWorkDone(run, controller.snapshot.work);
}

/// This host asks the person to merge (the host front's merge roles); a
/// team without them merges by itself. The header says "Next: merge" only
/// here.
bool teamMergeGateOnHost(OrchestrationController controller) =>
    controller.capabilities.mergeReadiness &&
    controller.gateway is OrchestrationMergeGateway;

/// The merge gate waits on the person: eligible, the host says ready, and
/// it is not merged yet. The page's header counts it as one thing that
/// needs you (the gate card is the question).
bool teamMergeWaitsOnYou(
  OrchestrationController controller,
  OrchestrationRun run,
) {
  if (!teamMergeEligible(controller, run)) return false;
  final readiness = controller.mergeReadinessFor(run.id);
  if (readiness == null || !readiness.ready || readiness.alreadyMerged) {
    return false;
  }
  final merge = controller.latestMutation(
    kind: MutationKind.merge,
    targetId: run.id,
  );
  return merge?.status != MutationStatus.confirmed;
}

/// The merge gate (or its calm panel), at the end of the task's
/// conversation.
class TeamMergeSection extends StatefulWidget {
  const TeamMergeSection({
    super.key,
    required this.controller,
    required this.run,
    this.now,
  });

  final OrchestrationController controller;
  final OrchestrationRun run;

  /// Clock for the Work sheet's ages; tests pin it.
  final DateTime Function()? now;

  @override
  State<TeamMergeSection> createState() => _TeamMergeSectionState();
}

class _TeamMergeSectionState extends State<TeamMergeSection> {
  bool _requested = false;

  OrchestrationController get _controller => widget.controller;
  OrchestrationRun get _run => widget.run;

  @override
  void didUpdateWidget(TeamMergeSection old) {
    super.didUpdateWidget(old);
    if (old.run.id != widget.run.id || old.controller != widget.controller) {
      _requested = false;
    }
  }

  /// Asks the controller for the readiness once the frame is built (the
  /// controller notifies, and a notify during build is an error). Called
  /// from build only while the section is eligible, so a run with open
  /// work never costs the host a readiness computation.
  void _request() {
    if (_requested) return;
    _requested = true;
    final controller = _controller;
    final runId = _run.id;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      unawaited(controller.mergeReadiness(runId));
    });
  }

  // revamp: remove (slice-P6.4) for team-merge-approve-sheet: approving is
  // reversible on the host, so it goes at once with its receipt.
  Future<void> _approve(MergeRequestInfo request) =>
      _controller.approveMergeRequest(request.id, runId: _run.id);

  /// Merges: the gate card asked the question ("Merge 6 changes into
  /// main?"), so the tap is the answer.
  Future<void> _merge() => _controller.mergeRun(_run.id);

  void _openChanges(MergeReadiness? readiness) {
    final l10n = _copy(context);
    final controller = _controller;
    final runId = _run.id;
    final now = widget.now;
    final parent = context;
    unawaited(
      showKitSheet<void>(
        context,
        title: l10n.teamUiMergeChangesTitle,
        subtitle: readiness == null
            ? null
            : l10n.teamUiMergeFiles(
                readiness.files,
                readiness.additions,
                readiness.deletions,
              ),
        icon: AppIconography.file,
        height: KitSheetHeight.half,
        sheetKey: const ValueKey('team-merge-changes'),
        body: (sheetContext) => _Changes(
          controller: controller,
          runId: runId,
          readiness: readiness,
          onOpenWork: (id) {
            Navigator.of(sheetContext).pop();
            unawaited(showWorkSheet(parent, controller, id, now: now));
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _controller,
    builder: (context, _) {
      if (!teamMergeEligible(_controller, _run)) {
        return const SizedBox.shrink();
      }
      _request();
      final l10n = _copy(context);
      final tokens = KitTokens.of(context);
      final readiness = _controller.mergeReadinessFor(_run.id);
      final error = _controller.mergeReadinessError(_run.id);
      final loading = _controller.mergeReadinessLoading(_run.id);
      final mergeRecord = _controller.latestMutation(
        kind: MutationKind.merge,
        targetId: _run.id,
      );
      final request = readiness?.mergeRequest;
      final approveRecord = request == null
          ? null
          : _controller.latestMutation(
              kind: MutationKind.approveMerge,
              targetId: request.id,
            );
      final merged =
          readiness?.alreadyMerged == true ||
          mergeRecord?.status == MutationStatus.confirmed;
      final tone = merged || readiness?.ready == true
          ? AppStatusTone.ok
          : AppStatusTone.neutral;
      final stale = _controller.isStale;
      final mergeBusy = mergeRecord != null && mergeRecord.isSent;
      final approveBusy = approveRecord != null && approveRecord.isSent;
      final approved =
          request?.isApproved == true ||
          approveRecord?.status == MutationStatus.confirmed;
      final canMerge =
          readiness != null &&
          readiness.canMerge &&
          !merged &&
          !mergeBusy &&
          !stale;
      final canApprove =
          request != null && !approved && !approveBusy && !stale && !merged;
      // The panel is only for a task not ready yet (or readiness unknown):
      // ready is the gate card, merged its one line.
      final title = l10n.teamUiMergeTitleNotReady;
      final requestLabel = request == null
          ? null
          : l10n.teamUiMergeRequest(request.id);

      final merge = KitAction(
        key: const ValueKey('team-merge-merge'),
        label: l10n.teamUiMergeMerge,
        icon: AppIconography.branch,
        working: mergeBusy,
        onPressed: canMerge ? () => unawaited(_merge()) : null,
      );
      final approve = request == null
          ? null
          : KitAction(
              key: const ValueKey('team-merge-approve'),
              label: l10n.teamUiMergeApprove,
              icon: AppIconography.check,
              working: approveBusy,
              onPressed: canApprove ? () => unawaited(_approve(request)) : null,
            );
      // The next step is the one primary: Approve while the request waits
      // for it, then Merge.
      final approveFirst = approve != null && !approved && !merged;
      final review = KitAction(
        key: const ValueKey('team-merge-review'),
        label: l10n.teamUiMergeReviewChanges,
        onPressed: () => _openChanges(readiness),
      );
      if (merged) {
        // Merged: one quiet line in the transcript, where it landed, that
        // opens what was merged. The checks, the request and a Merge that
        // can no longer do anything are behind it now; the lead's reply
        // already says it is merged, so no second heading says it again.
        return Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space2),
          child: KeyedSubtree(
            key: const ValueKey('team-merge-section'),
            child: KitEntrance(
              child: KitRow(
                key: const ValueKey('team-merge-review'),
                padding: EdgeInsetsDirectional.symmetric(
                  vertical: tokens.space2,
                ),
                leading: const KitIcon(
                  AppIconography.branch,
                  tone: KitTextTone.success,
                ),
                title: _mergedLine(l10n, readiness, mergeRecord),
                titleKey: const ValueKey('team-merge-receipt'),
                supporting: TextSpan(text: l10n.teamUiMergeReviewChanges),
                trailing: const KitChevron(),
                onTap: () => _openChanges(readiness),
              ),
            ),
          ),
        );
      }

      // Ready: the one question card. The host decides "ready"; a boundary
      // or a stale view only turns Merge off, with its reason.
      if (readiness != null && readiness.ready) {
        return Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space2),
          child: _gate(
            context,
            readiness: readiness,
            approveFirst: approveFirst,
            canApprove: canApprove,
            canMerge: canMerge,
            stale: stale,
            mergeRecord: mergeRecord,
            approveRecord: approveRecord,
            review: review,
          ),
        );
      }

      return Padding(
        padding: EdgeInsetsDirectional.only(top: tokens.space5),
        child: KitPanel(
          key: const ValueKey('team-merge-section'),
          tone: tone,
          icon: AppIconography.branch,
          title: title,
          titleKey: const ValueKey('team-merge-title'),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (requestLabel != null)
                KitText(
                  requestLabel,
                  key: const ValueKey('team-merge-request'),
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                ),
              SizedBox(height: tokens.space2),
              if (readiness != null) ...[
                _Lines(readiness: readiness),
                SizedBox(height: tokens.space2),
                KitText(
                  l10n.teamUiMergeFiles(
                    readiness.files,
                    readiness.additions,
                    readiness.deletions,
                  ),
                  key: const ValueKey('team-merge-files'),
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                ),
              ] else if (loading)
                KitText(
                  l10n.teamUiMergeLoading,
                  key: const ValueKey('team-merge-loading'),
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                )
              else
                KitText(
                  error == null
                      ? l10n.teamUiMergeNoRoles
                      : l10n.teamUiMergeUnavailable(
                          productErrorText(error, l10n: l10n),
                        ),
                  key: const ValueKey('team-merge-unavailable'),
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                ),
              SizedBox(height: tokens.space4),
              // One block (§2): the next step is the primary, the other
              // the secondary, Review changes the tertiary. A button that
              // is off has its reason in the notes under it.
              KitActionBlock(
                primary: approveFirst ? approve : merge,
                secondary: approveFirst ? merge : null,
                tertiary: [review],
              ),
              ..._notes(
                l10n,
                tokens,
                readiness: readiness,
                request: request,
                mergeRecord: mergeRecord,
                approveRecord: approveRecord,
              ),
            ],
          ),
        ),
      );
    },
  );

  /// The merge gate as the conversation's question card: "Merge 6 changes
  /// into main?", the files and the checks in one line, Review changes,
  /// and the next step as the primary (Approve the merge while the request
  /// waits for it, then Merge into main).
  Widget _gate(
    BuildContext context, {
    required MergeReadiness readiness,
    required bool approveFirst,
    required bool canApprove,
    required bool canMerge,
    required bool stale,
    required MutationRecord? mergeRecord,
    required MutationRecord? approveRecord,
    required KitAction review,
  }) {
    final l10n = _copy(context);
    final branch = readiness.targetBranch ?? 'main';
    final question = l10n.teamMergeGateQuestion(readiness.files, branch);
    final checks = readiness.lines.length;
    final detail = [
      if (readiness.files > 0)
        l10n.teamUiMergeFiles(
          readiness.files,
          readiness.additions,
          readiness.deletions,
        ),
      if (checks > 0) l10n.teamMergeGateChecksPassed(checks),
    ].join(' · ');
    // The answer in flight, refused or unconfirmed: the newest of the
    // step this card is on.
    final record = approveFirst ? approveRecord : mergeRecord;
    final answer = approveFirst
        ? l10n.teamMergeGateApprove
        : l10n.teamUiMergeConfirmAction(branch);
    final boundary = record == null ? null : _boundaryOf(record);
    final (phase, receipt) = switch (record?.status) {
      null || MutationStatus.confirmed => (KitRequestPhase.waiting, null),
      MutationStatus.sent => (
        KitRequestPhase.sending,
        KitReceipt(
          key: const ValueKey('team-merge-receipt'),
          state: KitReceiptState.sending,
          since: record!.createdAt,
        ),
      ),
      MutationStatus.unconfirmed => (
        KitRequestPhase.sending,
        KitReceipt(
          key: const ValueKey('team-merge-receipt'),
          state: KitReceiptState.notConfirmed,
          since: record!.createdAt,
          reason: l10n.teamUiReceiptUnconfirmed,
        ),
      ),
      MutationStatus.rejected => (
        KitRequestPhase.waiting,
        KitReceipt(
          key: const ValueKey('team-merge-receipt'),
          state: KitReceiptState.refused,
          reason: boundary != null
              ? l10n.teamUiMergeBoundary(boundary)
              : l10n.teamUiMergeRefused,
        ),
      ),
    };
    final blocking = readiness.firstBlocking;
    // Why the act is off (STATE-8). While it is sent the card shows the
    // answer line instead, so a busy button never needs words of its own.
    final String? offReason;
    if (approveFirst ? canApprove : canMerge) {
      offReason = null;
    } else if (!approveFirst && !stale && blocking != null) {
      offReason = l10n.teamUiMergeBoundary(blocking.text);
    } else {
      offReason = l10n.teamMergeGateStale;
    }
    final request = readiness.mergeRequest;
    final go = approveFirst
        ? KitRequestGo(
            goKey: const ValueKey('team-merge-approve'),
            label: l10n.teamMergeGateApprove,
            icon: AppIconography.check,
            look: review,
            onGo: canApprove && request != null
                ? () => unawaited(_approve(request))
                : null,
            disabledReason: offReason,
          )
        : KitRequestGo(
            goKey: const ValueKey('team-merge-merge'),
            label: l10n.teamUiMergeConfirmAction(branch),
            icon: AppIconography.branch,
            look: review,
            onGo: canMerge ? () => unawaited(_merge()) : null,
            disabledReason: offReason,
          );
    // The host's own words are technical: a Details fold under the card,
    // never the card's copy (a boundary is already words).
    final hostWords =
        record?.status == MutationStatus.rejected && boundary == null
        ? record?.receipt?.message?.trim() ?? ''
        : '';
    // Placed by the transcript's gutter; it rises in like the chat's.
    final card = KitEntrance(
      child: KitRequestCard.ask(
        key: const ValueKey('team-merge-section'),
        inList: true,
        look: KitRequestLook.question,
        kind: KitRequestKind.gate,
        icon: AppIconography.branch,
        title: question,
        titleKey: const ValueKey('team-merge-title'),
        who: l10n.teamChatLeadName,
        reason: KitNeedsYouReason.decision,
        ifIgnored: l10n.teamMergeGateIfIgnored,
        announcement: l10n.teamMergeGateAnnouncement(question),
        detail: detail.isEmpty ? null : detail,
        since: _readySince(),
        phase: phase,
        receipt: receipt,
        answer: phase == KitRequestPhase.sending ? answer : null,
        answers: go,
      ),
    );
    if (hostWords.isEmpty) return card;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        card,
        KitDetailsFold(
          label: l10n.teamUiTechnicalDetails,
          foldKey: const ValueKey('team-merge-receipt-details'),
          notes: [l10n.teamDispatchHostWords],
          text: hostWords,
        ),
      ],
    );
  }

  /// When the task's last step finished (host time): how long the merge
  /// has waited for the person. Null when no step dates it.
  DateTime? _readySince() {
    DateTime? latest;
    for (final item in _controller.snapshot.work) {
      if (item.runId != _run.id) continue;
      final at = item.updatedAt;
      if (at != null && (latest == null || at.isAfter(latest))) latest = at;
    }
    return latest;
  }

  /// Where a merged task landed: "Merged into main · 4f9c2a1", from the
  /// merge's own answer first, else the host's readiness; "Already on
  /// main" when no commit is known.
  String _mergedLine(
    AppLocalizations l10n,
    MergeReadiness? readiness,
    MutationRecord? mergeRecord,
  ) {
    final confirmed = mergeRecord?.status == MutationStatus.confirmed
        ? mergeRecord
        : null;
    final branch =
        (confirmed == null ? null : _branchOf(confirmed)) ??
        readiness?.targetBranch ??
        '';
    final commit =
        (confirmed == null ? null : _commitOf(confirmed)) ??
        readiness?.mergeCommit;
    if (commit == null) return l10n.teamUiMergeAlready(branch);
    return l10n.teamUiMergeMerged(branch, _shortCommit(commit));
  }

  /// The helper lines under the buttons: why Merge is off, the boundary
  /// that blocks, the approval, and the receipts; a refused merge is a
  /// notice with its next step.
  List<Widget> _notes(
    AppLocalizations l10n,
    KitTokens tokens, {
    required MergeReadiness? readiness,
    required MergeRequestInfo? request,
    required MutationRecord? mergeRecord,
    required MutationRecord? approveRecord,
  }) {
    final notes = <Widget>[];
    void note(String text, {Key? key, KitTextTone? tone}) {
      notes.add(
        Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space2),
          child: KitText(
            text,
            key: key,
            role: KitTextRole.secondary,
            tone: tone ?? KitTextTone.secondary,
          ),
        ),
      );
    }

    if (readiness != null) {
      final missing = readiness.firstMissing;
      final blocking = readiness.firstBlocking;
      if (missing != null) {
        note(
          l10n.teamUiMergeDisabledReason(
            teamMergeLineLabel(l10n, missing.key),
            missing.pending ? l10n.teamUiMergePending : (missing.detail ?? ''),
          ),
          key: const ValueKey('team-merge-disabled-reason'),
          tone: missing.pending ? KitTextTone.secondary : KitTextTone.primary,
        );
      } else if (blocking != null) {
        note(
          l10n.teamUiMergeBoundary(blocking.text),
          key: const ValueKey('team-merge-boundary'),
          tone: KitTextTone.primary,
        );
      }
    }
    final approvedBy = request?.approvedBy;
    if (approvedBy != null && approvedBy.isNotEmpty) {
      note(
        l10n.teamUiMergeApprovedBy(approvedBy),
        key: const ValueKey('team-merge-approved-by'),
        tone: KitTextTone.success,
      );
    }
    if (approveRecord != null) {
      final text = switch (approveRecord.status) {
        MutationStatus.sent => l10n.teamUiMergeSent,
        MutationStatus.confirmed => l10n.teamUiMergeApproveConfirmed,
        MutationStatus.rejected => l10n.teamUiMergeRefused,
        MutationStatus.unconfirmed => l10n.teamUiReceiptUnconfirmed,
      };
      note(
        text,
        key: const ValueKey('team-merge-approve-receipt'),
        tone: switch (approveRecord.status) {
          MutationStatus.rejected => KitTextTone.primary,
          MutationStatus.confirmed => KitTextTone.success,
          _ => KitTextTone.secondary,
        },
      );
    }
    if (mergeRecord != null) {
      if (mergeRecord.status == MutationStatus.rejected) {
        // Refused: why, and what to do next (the section stays; Merge is
        // on again once the host allows it).
        final boundary = _boundaryOf(mergeRecord);
        notes.add(
          Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space3),
            child: KitNotice(
              key: const ValueKey('team-merge-receipt'),
              tone: AppStatusTone.failure,
              icon: AppIconography.error,
              title: boundary != null
                  ? l10n.teamUiMergeBoundary(boundary)
                  : l10n.teamUiMergeRefused,
              message: l10n.teamMergeFailedNext,
              actions: [
                if (readiness != null && readiness.canMerge)
                  KitAction(
                    key: const ValueKey('team-merge-retry'),
                    label: l10n.teamUiCardRetry,
                    icon: AppIcons.retry,
                    onPressed: () => unawaited(_merge()),
                  ),
              ],
            ),
          ),
        );
        // The host's own words (not a boundary, which is already words):
        // technical, under Details.
        final hostWords = mergeRecord.receipt?.message?.trim() ?? '';
        if (boundary == null && hostWords.isNotEmpty) {
          notes.add(
            KitDetailsFold(
              label: l10n.teamUiTechnicalDetails,
              foldKey: const ValueKey('team-merge-receipt-details'),
              notes: [l10n.teamDispatchHostWords],
              text: hostWords,
            ),
          );
        }
        return notes;
      }
      final text = switch (mergeRecord.status) {
        MutationStatus.confirmed => l10n.teamUiMergeMerged(
          _branchOf(mergeRecord) ?? readiness?.targetBranch ?? '',
          _shortCommit(_commitOf(mergeRecord) ?? readiness?.mergeCommit),
        ),
        MutationStatus.unconfirmed => l10n.teamUiReceiptUnconfirmed,
        _ => l10n.teamUiMergeSent,
      };
      note(
        text,
        key: const ValueKey('team-merge-receipt'),
        tone: mergeRecord.status == MutationStatus.confirmed
            ? KitTextTone.success
            : KitTextTone.secondary,
      );
    }
    return notes;
  }
}

/// The receipt body the front stored for a merge or approval: the
/// `body` inside a front receipt, else the raw answer itself.
Map<String, Object?> _bodyOf(MutationRecord record) {
  final raw = record.receipt?.raw ?? const {};
  final body = raw['body'];
  if (body is Map) {
    return {for (final entry in body.entries) '${entry.key}': entry.value};
  }
  return raw;
}

String? _string(Object? value) {
  if (value is! String) return null;
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

/// The boundary text when the host refused on a boundary (the front's
/// `code: boundary` problem), else null.
String? _boundaryOf(MutationRecord record) {
  final body = _bodyOf(record);
  if (_string(body['code']) != 'boundary' &&
      _string(body['boundary']) == null) {
    return null;
  }
  return _string(body['detail']) ??
      _string(body['boundary']) ??
      record.receipt?.message;
}

String? _commitOf(MutationRecord record) =>
    _string(_bodyOf(record)['mergeCommit']);

String? _branchOf(MutationRecord record) => _string(_bodyOf(record)['branch']);

String _shortCommit(String? sha) {
  if (sha == null) return '';
  return sha.length > 7 ? sha.substring(0, 7) : sha;
}

/// The readiness lines as one checklist: done, still running on the host,
/// or failed with its detail.
class _Lines extends StatelessWidget {
  const _Lines({required this.readiness});

  final MergeReadiness readiness;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    return KitChecklist(
      checklistKey: const ValueKey('team-merge-lines'),
      steps: [
        for (final line in readiness.lines)
          KitStep(
            key: ValueKey('team-merge-line-${line.key}'),
            title: teamMergeLineLabel(l10n, line.key),
            state: line.ok
                ? KitMarkState.done
                : line.pending
                ? KitMarkState.working
                : KitMarkState.failed,
            supporting: switch (line.pending
                ? l10n.teamUiMergePending
                : line.detail) {
              final detail?
                  when detail.isNotEmpty && (!line.ok || line.key == 'work') =>
                detail,
              _ => null,
            },
          ),
      ],
    );
  }
}

/// Review changes: the changed files with their +/− counts and the run's
/// work items, each opening its sheet. There is no diff to open here yet:
/// the merge readiness carries each file's path and counts only, no patch
/// (slice-P3.7a; once it does, a file opens showKitDiff like everywhere else).
class _Changes extends StatelessWidget {
  const _Changes({
    required this.controller,
    required this.runId,
    required this.readiness,
    required this.onOpenWork,
  });

  final OrchestrationController controller;
  final String runId;
  final MergeReadiness? readiness;
  final ValueChanged<String> onOpenWork;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final changes = readiness?.changes ?? const <MergeChange>[];
    final work = [
      for (final item in controller.snapshot.work)
        if (item.runId == runId) item,
    ]..sort((a, b) => teamWorkStateRank(a.state) - teamWorkStateRank(b.state));
    final added = KitText.styleOf(
      context,
      KitTextRole.secondary,
      tone: KitTextTone.success,
    );
    final removed = KitText.styleOf(
      context,
      KitTextRole.secondary,
      tone: KitTextTone.primary,
    );
    final lines = readiness?.lines ?? const <MergeReadinessLine>[];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // The checks the host ran, first: what "all checks passed" means.
        if (lines.isNotEmpty) ...[
          _Lines(readiness: readiness!),
          SizedBox(height: tokens.sectionGap),
        ],
        if (changes.isEmpty)
          KitText(
            l10n.teamUiMergeChangesEmpty,
            key: const ValueKey('team-merge-changes-empty'),
            tone: KitTextTone.secondary,
          )
        else
          for (final change in changes)
            Padding(
              padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const KitIcon(
                    AppIconography.file,
                    size: KitIconSize.small,
                    tone: KitTextTone.secondary,
                  ),
                  SizedBox(width: tokens.space2),
                  Expanded(child: KitText.mono(change.path)),
                  SizedBox(width: tokens.space2),
                  KitText.rich(
                    TextSpan(
                      children: [
                        TextSpan(text: '+${change.additions}', style: added),
                        const TextSpan(text: ' / '),
                        TextSpan(text: '−${change.deletions}', style: removed),
                      ],
                    ),
                    role: KitTextRole.secondary,
                    tabular: true,
                  ),
                ],
              ),
            ),
        if (work.isNotEmpty) ...[
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            label: l10n.teamUiMergeChangesWork,
            margin: EdgeInsetsDirectional.zero,
            children: [
              for (final item in work)
                KitRow(
                  key: ValueKey('team-merge-work-${item.id}'),
                  leading: teamWorkMark(item.state).leading(context),
                  title: item.title,
                  supporting: TextSpan(
                    text: teamWorkStateWord(l10n, item.state),
                  ),
                  trailing: const KitChevron(),
                  onTap: () => onOpenWork(item.id),
                ),
            ],
          ),
        ],
      ],
    );
  }
}
