import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../domain/server_gateway.dart';
import '../../domain/staged_revert_message_count.dart';
import '../widgets/session_title.dart' show presentedSessionTitleText;
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_iconography.dart';
import '../kit/kit_bidi.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_page_route.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_screen.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_state_view.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_top_bar.dart';
import '../widgets/product_states.dart'
    show productErrorDetails, productErrorText;
import '../widgets/request_routes.dart';
import 'review_workspace.dart' show DiffPage;

AppLocalizations _strings(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

bool _revertBusy(ConnectionController controller, String sessionID) =>
    controller.sessionRevertSaving(sessionID) ||
    controller.busySessions.contains(sessionID);

/// "Undo from this prompt?" (map stage-revert-sheet): the one "Undo from
/// here" flow on every server. It quotes the prompt the undo starts from,
/// says what happens to the conversation, and covers the files.
///
/// Two shapes, by what the server allows (never by server name):
///
/// * Staged ([stage], or neither callback): the prompt and everything after
///   it are hidden while the person reviews, with a "Put files back too"
///   choice. Resolves to that files choice after "Undo and review"; the
///   review page then lists the files and the exact message count. With
///   [stage] the sheet stages itself: the primary shows the work, and a
///   failure keeps the sheet open with a notice, so the choice is never lost
///   to an error. Without it the caller stages after the sheet closes.
/// * One step ([undo]): the server undoes at once, so the sheet is the
///   honest confirm. It says how many messages go ([messagesAfter], null when
///   unknown), that files go back, and lists the files the agent reported
///   editing after the prompt ([editedFiles]). "Undo now" runs [undo] inside
///   the sheet; a failure keeps it open. Resolves to true once it ran.
///
/// Resolves to null when the person closes the sheet. The primary turns
/// off, and says why, while the conversation is busy or its undo state
/// changed on the server (stale).
Future<bool?> showStageRevertSheet(
  BuildContext context, {
  required ConnectionController controller,
  required SessionRevertReview review,
  required String prompt,
  Future<void> Function(bool applyFiles)? stage,
  Future<void> Function()? undo,
  List<String> editedFiles = const [],
  int? messagesAfter,
}) async {
  assert(stage == null || undo == null, 'one shape at a time');
  final l10n = _strings(context);
  final navigator = Navigator.of(context);
  final oneStep = undo != null;
  final applyFiles = ValueNotifier<bool>(true);
  final working = ValueNotifier<bool>(false);
  final failure = ValueNotifier<Object?>(null);
  final primary = ValueNotifier<KitAction?>(null);
  var open = true;

  Future<void> submit() async {
    final value = oneStep || applyFiles.value;
    final Future<void> Function()? run =
        undo ?? (stage == null ? null : () => stage(value));
    if (run == null) {
      if (open) navigator.pop(value);
      return;
    }
    working.value = true;
    failure.value = null;
    try {
      await run();
    } catch (error) {
      working.value = false;
      failure.value = error;
      return;
    }
    working.value = false;
    // Never pop another route: the sheet may have been closed meanwhile.
    if (open) navigator.pop(value);
  }

  final label = oneStep
      ? l10n.undoFromHereNowAction
      : l10n.reviewRevertSheetAction;
  void sync() {
    if (working.value) {
      // A working action is never shown disabled (STATE-7); a second tap
      // does nothing.
      primary.value = KitAction(
        key: const ValueKey('stage-revert-confirm'),
        label: label,
        icon: AppIconography.undo,
        destructive: oneStep,
        working: true,
        onPressed: () {},
      );
      return;
    }
    final reason = !controller.isRevertReviewCurrent(review)
        ? l10n.revertReviewChanged
        : _revertBusy(controller, review.sessionID)
        ? l10n.revertBusy
        : null;
    primary.value = KitAction(
      key: const ValueKey('stage-revert-confirm'),
      label: label,
      icon: AppIconography.undo,
      // One step cannot be reviewed first, so it carries the error tone.
      destructive: oneStep,
      disabledReason: reason,
      onPressed: reason == null ? () => unawaited(submit()) : null,
    );
  }

  sync();
  controller.addListener(sync);
  working.addListener(sync);
  try {
    return await showKitSheet<bool>(
      context,
      sheetKey: const ValueKey('stage-revert-sheet'),
      title: l10n.reviewRevertSheetTitle,
      icon: AppIconography.undo,
      primaryListenable: primary,
      body: (context) => ListenableBuilder(
        listenable: Listenable.merge([
          controller,
          applyFiles,
          working,
          failure,
        ]),
        builder: (context, _) {
          final l10n = _strings(context);
          final tokens = KitTokens.of(context);
          final current = controller.isRevertReviewCurrent(review);
          final busy = _revertBusy(controller, review.sessionID);
          final error = failure.value;
          final text = prompt.trim();
          final count = messagesAfter;
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              KitText(
                key: const ValueKey('stage-revert-body'),
                !oneStep
                    ? l10n.reviewRevertSheetBody
                    : count == null
                    ? l10n.undoFromHereBodyUnknown
                    : l10n.undoFromHereBody(count),
                tone: KitTextTone.secondary,
              ),
              SizedBox(height: tokens.space4),
              KitRowGroup(
                label: l10n.reviewRevertPromptLabel,
                margin: EdgeInsets.zero,
                children: [
                  KitRow(
                    key: const ValueKey('stage-revert-prompt'),
                    leading: const KitRowIcon(AppIconography.chat),
                    title: text.isEmpty ? l10n.revertAttachmentPrompt : text,
                    titleMaxLines: 4,
                  ),
                ],
              ),
              SizedBox(height: tokens.space4),
              if (oneStep)
                KitRowGroup(
                  label: l10n.undoFromHereFilesLabel,
                  margin: EdgeInsets.zero,
                  children: [
                    if (editedFiles.isEmpty)
                      KitRow(
                        key: const ValueKey('stage-revert-no-edits'),
                        leading: const KitRowIcon(AppIconography.file),
                        title: l10n.undoFromHereNoEdits,
                        titleMaxLines: 3,
                      )
                    else
                      for (final path in editedFiles)
                        _editedFileRow(l10n, path),
                  ],
                )
              else
                KitRowGroup(
                  margin: EdgeInsets.zero,
                  children: [
                    KitSwitchRow(
                      switchKey: const ValueKey('stage-revert-files'),
                      leading: const KitRowIcon(AppIconography.file),
                      title: l10n.reviewRevertFilesToggle,
                      supporting: l10n.reviewRevertFilesToggleHint,
                      value: applyFiles.value,
                      onChanged: current && !busy && !working.value
                          ? (value) => applyFiles.value = value
                          : null,
                    ),
                  ],
                ),
              if (error != null) ...[
                SizedBox(height: tokens.space4),
                KitNotice.error(
                  key: const ValueKey('stage-revert-failed'),
                  message: oneStep
                      ? l10n.reviewRevertFailed
                      : l10n.reviewRevertStageFailed,
                  error: error,
                  details: productErrorDetails(error),
                ),
              ],
            ],
          );
        },
      ),
    );
  } finally {
    open = false;
    controller.removeListener(sync);
    working.removeListener(sync);
  }
}

/// A file the agent reported editing after the prompt: its name, with the
/// folder beneath. Read-only: one-step servers give no preview to open.
Widget _editedFileRow(AppLocalizations l10n, String path) {
  final cut = path.lastIndexOf('/');
  final name = cut < 0 ? path : path.substring(cut + 1);
  final folder = cut <= 0 ? null : path.substring(0, cut);
  return KitRow(
    key: ValueKey('stage-revert-edited-$path'),
    leading: const KitRowIcon(AppIconography.file),
    title: name,
    titleIsFileName: true,
    supporting: folder == null ? null : TextSpan(text: KitBidi.ltr(folder)),
  );
}

/// What the person chose on the review page, once it went through.
enum _Outcome { kept, restored }

/// Review the undo (map staged-revert): the prompt it starts from, the
/// files the server lists for it (each opens its diff), and the decision
/// pinned at the bottom: "Put everything back" (the primary) and the
/// destructive "Delete the hidden messages". Each asks first
/// (staged-revert-confirm-sheet) and runs inside the question, so a failure
/// keeps the question open.
///
/// The file list is the server's staged preview, never a new working-tree
/// diff. A remote replacement requires an explicit review before enabling
/// actions: an open question closes itself when the undo changes on the
/// server ("stale while open").
///
/// States (STATE-20): staged, busy, failed (a notice with the reason behind
/// Details), stale ("Review latest state"), nothing staged, no files, no
/// preview, and done (kept or restored, with the way back).
class StagedRevertScreen extends StatefulWidget {
  final ConnectionController controller;
  final String sessionID;
  const StagedRevertScreen({
    super.key,
    required this.controller,
    required this.sessionID,
  });

  @override
  State<StagedRevertScreen> createState() => _StagedRevertScreenState();
}

class _StagedRevertScreenState extends State<StagedRevertScreen> {
  /// The conversation as the person knows it, to name in the questions.
  String _conversationTitle(AppLocalizations l10n) => presentedSessionTitleText(
    widget.controller.sessionsById[widget.sessionID]?.title,
    l10n: l10n,
  );

  late SessionRevertReview _review;
  String? _error;
  String? _prompt;
  bool _promptUnavailable = false;
  _Outcome? _outcome;

  /// How many messages follow the hidden prompt, read from the server for
  /// exactly [_review]. Null while loading, or when it could not be counted:
  /// the page then says "everything after it", never zero.
  int? _count;

  /// True while a confirmed act runs inside its question: the question
  /// stays open through the controller changes the act itself causes.
  bool _acting = false;

  @override
  void initState() {
    super.initState();
    _review = widget.controller.reviewSessionRevert(widget.sessionID);
    unawaited(_loadPrompt());
    unawaited(_loadCount());
  }

  /// A point-in-time count for the reviewed boundary (SV1). It is dropped
  /// as soon as the review is replaced or goes stale, and never shown for a
  /// review other than the one it was read for.
  Future<void> _loadCount() async {
    final review = _review;
    final controller = widget.controller;
    final api = controller.api;
    final revert = review.revert;
    final operations = controller.repository;
    if (api == null ||
        revert == null ||
        !api.canCountStagedRevertMessages(operations)) {
      return;
    }
    bool current() =>
        mounted &&
        identical(_review, review) &&
        controller.isRevertReviewCurrent(review);
    try {
      final result = await api.countStagedRevertMessages(
        widget.sessionID,
        expected: revert,
        operations: operations,
        isCurrent: current,
      );
      if (!current()) return;
      setState(() => _count = result.messagesAfterPrompt);
    } on StagedRevertCountException {
      // Unknown stays unknown: the page keeps its "everything after it" copy.
    }
  }

  Future<void> _loadPrompt() async {
    final review = _review;
    final repository = widget.controller.repository;
    final messageID = review.revert?.messageID;
    if (messageID == null || repository is! StagedRevertGateway) return;
    try {
      final prompt = await (repository as StagedRevertGateway)
          .sessionRevertPrompt(widget.sessionID, messageID);
      if (!mounted || !identical(_review, review)) return;
      setState(() {
        _prompt = prompt;
        _promptUnavailable = prompt == null;
      });
    } catch (_) {
      if (mounted && identical(_review, review)) {
        setState(() => _promptUnavailable = true);
      }
    }
  }

  Future<void> _reviewLatest() async {
    final controller = widget.controller;
    await controller.ensureSession(widget.sessionID);
    if (!mounted ||
        _review.scope !=
            controller.reviewSessionRevert(widget.sessionID).scope) {
      return;
    }
    setState(() {
      _review = controller.reviewSessionRevert(widget.sessionID);
      _error = controller.sessionDetailsErrors[widget.sessionID];
      _prompt = null;
      _promptUnavailable = false;
      _count = null;
    });
    unawaited(_loadPrompt());
    unawaited(_loadCount());
  }

  Future<void> _apply({required bool commit}) async {
    final controller = widget.controller;
    final reviewed = _review;
    final l10n = _strings(context);
    final files = reviewed.revert?.files;
    final count = _count;
    final routes = RequestRoutes(
      changes: controller,
      isPending: () => _acting || controller.isRevertReviewCurrent(reviewed),
    );
    setState(() => _error = null);
    Future<void> act() async {
      _acting = true;
      try {
        if (commit) {
          await controller.commitSessionRevert(reviewed);
        } else {
          await controller.clearSessionRevert(reviewed);
        }
      } catch (error) {
        _acting = false;
        // Shown only as the notice's details (redacted), never as words.
        if (mounted) {
          setState(
            () =>
                _error = productErrorDetails(error) ?? productErrorText(error),
          );
        }
        rethrow;
      }
    }

    final bool confirmed;
    if (commit) {
      confirmed = await showKitConfirm(
        context,
        title: l10n.reviewRevertKeepConfirmTitle(_conversationTitle(l10n)),
        body: l10n.reviewRevertKeepConfirmBody,
        confirmLabel: l10n.reviewRevertKeepConfirmAction,
        icon: AppIconography.delete,
        kind: KitConfirmKind.destructive,
        consequenceItems: [
          KitConsequence(
            count == null
                ? l10n.reviewRevertKeepConsequenceMessages
                : l10n.reviewRevertKeepConsequenceCount(count),
            mark: KitConsequenceMark.lost,
          ),
          KitConsequence(
            l10n.reviewRevertKeepConsequenceFiles,
            mark: KitConsequenceMark.kept,
          ),
        ],
        action: act,
        routes: routes,
        sheetKey: const ValueKey('staged-revert-confirm-sheet'),
        confirmKey: const ValueKey('confirm-staged-revert'),
      );
    } else {
      // Files go back to the saved snapshot: any edit made to them since
      // the undo was set up is replaced, so that is the error tone.
      final overwrites = files == null || files.isNotEmpty;
      confirmed = await showKitConfirm(
        context,
        title: l10n.reviewRevertRestoreConfirmTitle(_conversationTitle(l10n)),
        body: l10n.reviewRevertRestoreConfirmBody,
        confirmLabel: l10n.reviewRevertRestoreTitle,
        icon: AppIconography.restore,
        kind: overwrites ? KitConfirmKind.destructive : KitConfirmKind.neutral,
        consequenceItems: [
          KitConsequence(
            l10n.reviewRevertRestoreConsequenceMessages,
            mark: KitConsequenceMark.kept,
          ),
          if (files != null && files.isNotEmpty)
            KitConsequence(
              l10n.reviewRevertRestoreConsequenceFiles(files.length),
              mark: KitConsequenceMark.lost,
            )
          else if (files == null)
            KitConsequence(
              l10n.reviewRevertRestoreConsequenceUnknownFiles,
              mark: KitConsequenceMark.lost,
            ),
        ],
        action: act,
        routes: routes,
        sheetKey: const ValueKey('staged-revert-confirm-sheet'),
        confirmKey: const ValueKey('confirm-staged-revert'),
      );
    }
    routes.close();
    _acting = false;
    if (!mounted || !confirmed) return;
    setState(() => _outcome = commit ? _Outcome.kept : _Outcome.restored);
  }

  void _back() => unawaited(Navigator.of(context).maybePop());

  Widget _done(AppLocalizations l10n, _Outcome outcome) => KitStateView(
    key: const ValueKey('staged-revert-done'),
    icon: outcome == _Outcome.kept
        ? AppIconography.checkCircle
        : AppIconography.restore,
    title: outcome == _Outcome.kept
        ? l10n.reviewRevertKeptTitle
        : l10n.reviewRevertRestoredTitle,
    body: outcome == _Outcome.kept
        ? l10n.reviewRevertKeptBody
        : l10n.reviewRevertRestoredBody,
    primary: KitAction(
      key: const ValueKey('staged-revert-back'),
      label: l10n.reviewRevertBackAction,
      onPressed: _back,
    ),
  );

  Widget _unavailable(
    AppLocalizations l10n, {
    required bool stale,
    required bool sameScope,
    required bool busy,
    String? error,
  }) {
    // A failed act that left the server state moved still says it did not
    // finish, with the reason behind Details (STATE-3).
    final failed = error == null
        ? null
        : KitNotice.error(
            key: const ValueKey('staged-revert-failed'),
            message: l10n.reviewRevertFailed,
            details: error,
          );
    if (!stale) {
      return KitStateView(
        key: const ValueKey('staged-revert-none'),
        icon: AppIconography.history,
        title: l10n.reviewRevertNoneTitle,
        body: l10n.reviewRevertNoneBody,
        content: failed,
        primary: KitAction(
          key: const ValueKey('staged-revert-back'),
          label: l10n.reviewRevertBackAction,
          onPressed: _back,
        ),
      );
    }
    return KitStateView(
      key: const ValueKey('staged-revert-stale'),
      icon: AppIconography.sync,
      title: l10n.reviewRevertStaleTitle,
      body: l10n.revertReviewChanged,
      content: failed,
      primary: sameScope
          ? KitAction(
              key: const ValueKey('staged-revert-latest'),
              label: l10n.revertReviewLatest,
              disabledReason: busy ? l10n.revertBusy : null,
              onPressed: busy ? null : () => unawaited(_reviewLatest()),
            )
          : null,
      secondary: KitAction(
        label: l10n.reviewRevertBackAction,
        onPressed: _back,
      ),
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final l10n = _strings(context);
      final tokens = KitTokens.of(context);
      final controller = widget.controller;
      final current = controller.isRevertReviewCurrent(_review);
      final sameScope =
          _review.scope ==
          controller.reviewSessionRevert(widget.sessionID).scope;
      final revert = _review.revert;
      final busy = _revertBusy(controller, widget.sessionID);
      final error = _error ?? controller.sessionRevertErrors[widget.sessionID];
      final outcome = _outcome;
      final Widget body;
      if (outcome != null) {
        body = _done(l10n, outcome);
      } else if (!current || revert == null) {
        body = _unavailable(
          l10n,
          stale: !current,
          sameScope: sameScope,
          busy: busy,
          error: error,
        );
      } else {
        final files = revert.files;
        final prompt = _promptUnavailable
            ? l10n.revertPromptUnavailable
            : _prompt == null
            ? l10n.revertPromptLoading
            : _prompt!.isEmpty
            ? l10n.revertAttachmentPrompt
            : _prompt!;
        final gap = SizedBox(height: tokens.sectionGap);
        Widget onRails(Widget child) => Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: child,
        );
        body = ListView(
          key: const ValueKey('staged-revert-list'),
          padding: EdgeInsetsDirectional.only(
            top: tokens.space3,
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            onRails(
              KitText(
                key: const ValueKey('staged-revert-intro'),
                _count == null
                    ? l10n.reviewRevertScreenIntro
                    : l10n.reviewRevertScreenIntroCount(_count!),
                tone: KitTextTone.secondary,
              ),
            ),
            if (error != null) ...[
              SizedBox(height: tokens.space3),
              onRails(
                KitNotice.error(
                  key: const ValueKey('staged-revert-failed'),
                  message: l10n.reviewRevertFailed,
                  details: error,
                ),
              ),
            ],
            gap,
            KitRowGroup(
              label: l10n.reviewRevertPromptLabel,
              children: [
                KitRow(
                  key: const ValueKey('staged-revert-prompt'),
                  leading: const KitRowIcon(AppIconography.chat),
                  title: prompt,
                  titleMaxLines: 4,
                ),
              ],
            ),
            gap,
            KitRowGroup(
              label: l10n.reviewRevertFilesLabel,
              children: [
                if (files == null)
                  KitRow(
                    key: const ValueKey('staged-revert-no-preview'),
                    leading: const KitRowIcon(AppIconography.question),
                    title: l10n.revertPreviewUnavailable,
                    titleMaxLines: 4,
                  )
                else if (files.isEmpty)
                  KitRow(
                    key: const ValueKey('staged-revert-no-files'),
                    leading: const KitRowIcon(AppIconography.file),
                    title: l10n.reviewRevertNoFiles,
                    titleMaxLines: 3,
                  )
                else
                  for (final file in files) _fileRow(context, l10n, file),
              ],
            ),
          ],
        );
      }
      // The page's one decision, pinned at the bottom: putting everything
      // back is the primary; deleting the hidden messages is the
      // destructive tertiary, named for what it deletes.
      final decide = outcome == null && current && revert != null;
      final reason = busy ? l10n.revertBusy : null;
      return KitScreen(
        topBar: KitTopBar(title: l10n.reviewRevertScreenTitle),
        // A page to read and decide on: a readable width on a wide window.
        width: KitScreenWidth.reading,
        loading: busy && outcome == null,
        loadingLabel: l10n.revertBusy,
        body: body,
        bottom: decide
            ? KitActionBlock(
                primary: KitAction(
                  key: const ValueKey('clear-staged-revert'),
                  label: l10n.reviewRevertRestoreTitle,
                  icon: AppIconography.restore,
                  disabledReason: reason,
                  onPressed: busy
                      ? null
                      : () => unawaited(_apply(commit: false)),
                ),
                tertiary: [
                  KitAction(
                    key: const ValueKey('commit-staged-revert'),
                    label: l10n.reviewRevertKeepTitle,
                    icon: AppIconography.delete,
                    destructive: true,
                    disabledReason: reason,
                    onPressed: busy
                        ? null
                        : () => unawaited(_apply(commit: true)),
                  ),
                ],
              )
            : null,
      );
    },
  );

  Widget _fileRow(BuildContext context, AppLocalizations l10n, FileDiff file) {
    final cut = file.file.lastIndexOf('/');
    final name = cut < 0 ? file.file : file.file.substring(cut + 1);
    final folder = cut <= 0 ? '' : file.file.substring(0, cut);
    final lines = l10n.reviewRevertFileLines(
      file.counts.added,
      file.counts.removed,
    );
    return KitRow(
      key: ValueKey('staged-revert-file-${file.file}'),
      leading: const KitRowIcon(AppIconography.file),
      title: name,
      titleIsFileName: true,
      supporting: TextSpan(
        text: folder.isEmpty
            ? lines
            : l10n.reviewRevertFileSupporting(KitBidi.ltr(folder), lines),
      ),
      trailing: const KitChevron(),
      onTap: () =>
          unawaited(pushKitPage<void>(context, (_) => DiffPage.single(file))),
    );
  }
}
