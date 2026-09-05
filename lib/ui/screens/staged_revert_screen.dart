import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../domain/server_gateway.dart';
import '../widgets/diff_view.dart';

AppLocalizations _strings(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

Future<bool?> showStageRevertSheet(
  BuildContext context, {
  required ConnectionController controller,
  required SessionRevertReview review,
  required String prompt,
}) {
  var applyFiles = true;
  return showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    builder: (context) => StatefulBuilder(
      builder: (context, setState) => ListenableBuilder(
        listenable: controller,
        builder: (context, _) {
          final l10n = _strings(context);
          final current = controller.isRevertReviewCurrent(review);
          final busy =
              controller.sessionRevertSaving(review.sessionID) ||
              controller.busySessions.contains(review.sessionID);
          return SafeArea(
            child: SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(
                20,
                12,
                20,
                20 + MediaQuery.viewInsetsOf(context).bottom,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    l10n.revertStageTitle,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 12),
                  Text(prompt, maxLines: 4, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 12),
                  Text(l10n.revertStageDescription),
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(l10n.revertApplyFiles),
                    subtitle: Text(l10n.revertApplyFilesHint),
                    value: applyFiles,
                    onChanged: current && !busy
                        ? (value) => setState(() => applyFiles = value ?? false)
                        : null,
                  ),
                  if (!current) Text(l10n.revertReviewChanged),
                  if (busy) Text(l10n.revertBusy),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: current && !busy
                        ? () => Navigator.pop(context, applyFiles)
                        : null,
                    child: Text(l10n.revertStageAction),
                  ),
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text(l10n.revertCancel),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    ),
  );
}

/// The file list is the server's staged preview, never a new working-tree diff.
/// A remote replacement requires an explicit review before enabling actions.
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
  late SessionRevertReview _review;
  String? _error;
  String? _prompt;
  bool _promptUnavailable = false;

  @override
  void initState() {
    super.initState();
    _review = widget.controller.reviewSessionRevert(widget.sessionID);
    unawaited(_loadPrompt());
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

  Future<void> _apply({required bool commit}) async {
    final controller = widget.controller;
    final reviewed = _review;
    final confirmed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (context) => ListenableBuilder(
        listenable: controller,
        builder: (context, _) {
          final l10n = _strings(context);
          final current = controller.isRevertReviewCurrent(reviewed);
          final busy =
              controller.sessionRevertSaving(widget.sessionID) ||
              controller.busySessions.contains(widget.sessionID);
          return SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    commit ? l10n.revertCommitTitle : l10n.revertClearTitle,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    commit
                        ? l10n.revertCommitDescription
                        : l10n.revertClearDescription,
                  ),
                  if (!current) ...[
                    const SizedBox(height: 12),
                    Text(l10n.revertReviewChanged),
                  ],
                  if (busy) Text(l10n.revertBusy),
                  const SizedBox(height: 16),
                  FilledButton(
                    key: const ValueKey('confirm-staged-revert'),
                    style: commit
                        ? FilledButton.styleFrom(
                            backgroundColor: Theme.of(
                              context,
                            ).colorScheme.error,
                            foregroundColor: Theme.of(
                              context,
                            ).colorScheme.onError,
                          )
                        : null,
                    onPressed: current && !busy
                        ? () => Navigator.pop(context, true)
                        : null,
                    child: Text(
                      commit ? l10n.revertCommitAction : l10n.revertClearAction,
                    ),
                  ),
                  TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: Text(l10n.revertCancel),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
    if (!mounted || confirmed != true) return;
    setState(() => _error = null);
    try {
      if (commit) {
        await controller.commitSessionRevert(reviewed);
      } else {
        await controller.clearSessionRevert(reviewed);
      }
      if (!mounted) return;
      // Never pop another route opened while the request was in flight.
      final route = ModalRoute.of(context);
      if (route?.isCurrent == true) Navigator.pop(context);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final l10n = _strings(context);
      final controller = widget.controller;
      final current = controller.isRevertReviewCurrent(_review);
      final sameScope =
          _review.scope ==
          controller.reviewSessionRevert(widget.sessionID).scope;
      final revert = _review.revert;
      final busy =
          controller.sessionRevertSaving(widget.sessionID) ||
          controller.busySessions.contains(widget.sessionID);
      final files = revert?.files;
      final error = _error ?? controller.sessionRevertErrors[widget.sessionID];
      return Scaffold(
        appBar: AppBar(title: Text(l10n.revertReviewTitle)),
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              if (!current || revert == null) ...[
                Text(!current ? l10n.revertReviewChanged : l10n.revertNoStage),
                if (sameScope)
                  TextButton(
                    onPressed: busy
                        ? null
                        : () async {
                            await controller.ensureSession(widget.sessionID);
                            if (!mounted ||
                                _review.scope !=
                                    controller
                                        .reviewSessionRevert(widget.sessionID)
                                        .scope) {
                              return;
                            }
                            setState(() {
                              _review = controller.reviewSessionRevert(
                                widget.sessionID,
                              );
                              _error = controller
                                  .sessionDetailsErrors[widget.sessionID];
                              _prompt = null;
                              _promptUnavailable = false;
                            });
                            unawaited(_loadPrompt());
                          },
                    child: Text(l10n.revertReviewLatest),
                  ),
                const SizedBox(height: 16),
              ],
              if (revert != null) ...[
                Text(
                  l10n.revertBoundaryLabel,
                  style: Theme.of(context).textTheme.labelLarge,
                ),
                const SizedBox(height: 8),
                Text(
                  _promptUnavailable
                      ? l10n.revertPromptUnavailable
                      : _prompt == null
                      ? l10n.revertPromptLoading
                      : _prompt!.isEmpty
                      ? l10n.revertAttachmentPrompt
                      : _prompt!,
                ),
                const SizedBox(height: 4),
                Text(
                  revert.messageID,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                Text(l10n.revertPreviewDescription),
                const SizedBox(height: 12),
                if (files == null)
                  Text(l10n.revertPreviewUnavailable)
                else if (files.isEmpty)
                  Text(l10n.revertPreviewEmpty)
                else
                  for (final file in files)
                    _FileRow(file: file, enabled: current),
                const SizedBox(height: 20),
                if (busy) ...[
                  const LinearProgressIndicator(),
                  const SizedBox(height: 8),
                  Text(l10n.revertBusy),
                ],
                if (error != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    error,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                FilledButton(
                  key: const ValueKey('commit-staged-revert'),
                  onPressed: current && !busy
                      ? () => _apply(commit: true)
                      : null,
                  child: Text(l10n.revertCommitAction),
                ),
                const SizedBox(height: 8),
                OutlinedButton(
                  key: const ValueKey('clear-staged-revert'),
                  onPressed: current && !busy
                      ? () => _apply(commit: false)
                      : null,
                  child: Text(l10n.revertClearAction),
                ),
              ],
            ],
          ),
        ),
      );
    },
  );
}

class _FileRow extends StatelessWidget {
  final FileDiff file;
  final bool enabled;
  const _FileRow({required this.file, required this.enabled});
  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(file.file),
    subtitle: Text('+${file.counts.added}  −${file.counts.removed}'),
    trailing: const Icon(Icons.chevron_right_rounded),
    enabled: enabled,
    onTap: enabled
        ? () => Navigator.of(
            context,
          ).push(MaterialPageRoute<void>(builder: (_) => DiffView.single(file)))
        : null,
  );
}
