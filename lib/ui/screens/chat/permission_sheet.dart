import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../api/models.dart';
import '../../../state/connection.dart';
import '../../permission_presentation.dart';
import '../../app_theme.dart';
import '../../widgets/code_highlight.dart';
import '../../widgets/diff_view.dart';
import '../../widgets/request_routes.dart';

/// Presents the OpenCode 2 permission prompt as a modal bottom sheet
/// (design doc §3). One component serves three entry points: the chat
/// review card, the Requests tile, and notification taps.
///
/// Replies retain the request's scope through transport recovery. Failures
/// keep the sheet open with the error inline while that request is pending.
/// v2 supports a rejection message; v1 Reject submits directly. [onShowSource], when
/// given for a request carrying a tool source, renders the "From tool call"
/// chip; it runs after the sheet dismisses itself.
Future<void> showPermissionSheet(
  BuildContext context, {
  required PermissionRequest permission,
  required ConnectionController controller,
  String contextLabel = 'in this chat',
  VoidCallback? onShowSource,
}) async {
  final request = controller.permissionIdentity(permission);
  if (!controller.isRequestPending(request)) return;
  final routes = RequestRoutes(
    changes: controller,
    isPending: () => controller.isRequestPending(request),
  );
  try {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      clipBehavior: Clip.antiAlias,
      constraints: const BoxConstraints(maxWidth: 720),
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
        ),
        child: PermissionSheet(
          permission: permission,
          routes: routes,
          onReply: (reply, {message}) => controller.answerPermission(
            permission.id,
            reply,
            message: message,
            expectedRequest: request,
          ),
          supportsRejectMessage: controller.permissionSupportsRejectMessage(
            permission.id,
          ),
          contextLabel: contextLabel,
          onShowSource: onShowSource,
        ),
      ),
    );
  } finally {
    routes.close();
  }
}

class PermissionSheet extends StatefulWidget {
  const PermissionSheet({
    super.key,
    required this.permission,
    required this.onReply,
    required this.supportsRejectMessage,
    this.contextLabel = 'in this chat',
    this.onShowSource,
    this.routes,
  });

  final PermissionRequest permission;
  final Future<void> Function(String reply, {String? message}) onReply;
  final bool supportsRejectMessage;
  final String contextLabel;
  final VoidCallback? onShowSource;
  final RequestRoutes? routes;

  @override
  State<PermissionSheet> createState() => _PermissionSheetState();
}

class _PermissionSheetState extends State<PermissionSheet> {
  late final _routes = widget.routes ?? RequestRoutes();
  final _rejectMessage = TextEditingController();
  bool _replying = false;
  bool _confirming = false;
  bool _rejecting = false;
  Object? _error;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _routes.own(ModalRoute.of(context));
  }

  @override
  void dispose() {
    _routes.close();
    _rejectMessage.dispose();
    super.dispose();
  }

  Future<void> _reply(String reply, {String? message}) async {
    if (_replying || !_routes.isPending) return;
    setState(() {
      _replying = true;
      _confirming = reply == 'always';
      _error = null;
    });
    try {
      if (reply == 'always' && !await _confirmAlways()) return;
      if (!mounted || !_routes.isPending) return;
      setState(() => _confirming = false);
      await widget.onReply(reply, message: message);
      _routes.close();
    } catch (error) {
      if (!mounted || !_routes.isPending) return;
      setState(() {
        _replying = false;
        _error = error;
      });
    } finally {
      if (mounted) {
        setState(() {
          _replying = false;
          _confirming = false;
        });
      }
    }
  }

  void _startReject() {
    if (_replying || !_routes.isPending) return;
    if (!widget.supportsRejectMessage) {
      // v1 reply shape has no message field: Reject submits directly.
      _reply('reject');
      return;
    }
    setState(() => _rejecting = true);
  }

  void _sendRejection() {
    final text = _rejectMessage.text.trim();
    // An empty reason is valid: it submits a plain rejection.
    _reply('reject', message: text.isEmpty ? null : text);
  }

  /// The existing two-step broader-access confirmation, copy unchanged,
  /// plus the pointer to Settings → Saved permissions.
  Future<bool> _confirmAlways() async {
    final permission = widget.permission;
    final broader = permission.always.isNotEmpty
        ? permission.always
        : permission.patterns;
    return await showDialog<bool>(
          context: context,
          builder: (context) {
            _routes.own(ModalRoute.of(context));
            return AlertDialog(
              scrollable: true,
              icon: const Icon(Icons.warning_amber_rounded),
              title: const Text('Confirm broader access'),
              content: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Context: ${permission.permission} ${widget.contextLabel}',
                  ),
                  const SizedBox(height: 12),
                  const Text('Always allow patterns:'),
                  const SizedBox(height: 4),
                  SelectableText(
                    broader.isEmpty
                        ? '(all matching requests)'
                        : broader.join('\n'),
                    style: const TextStyle(fontFamily: AppTheme.monoFamily),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Consequence: future matching actions can run without asking again for the lifetime of this OpenCode server. Allow once is safer.',
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Manage saved grants in Settings → Saved permissions.',
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context, false),
                  child: const Text('Keep asking'),
                ),
                FilledButton(
                  onPressed: () => Navigator.pop(context, true),
                  child: const Text('Confirm always allow'),
                ),
              ],
            );
          },
        ) ??
        false;
  }

  static const _diffPermissions = {'edit', 'write', 'multiedit', 'patch'};
  static const _diffMetadataKeys = ['diff', 'patch', 'preview'];

  /// A unified diff the server attached to an edit/write ask, from the
  /// common metadata spellings; null for other tools or when absent.
  String? get _diffPreview {
    final permission = widget.permission;
    if (!_diffPermissions.contains(permission.permission.toLowerCase())) {
      return null;
    }
    for (final key in _diffMetadataKeys) {
      final value = permission.metadata[key];
      if (value is String && value.trim().isNotEmpty) return value;
    }
    return null;
  }

  FileDiff _pendingDiff(String diff) => FileDiff(
    file: widget.permission.filePath ?? 'Pending change',
    patch: diff,
  );

  void _openFullDiff(String diff) {
    if (!_routes.isPending) return;
    final route = MaterialPageRoute<void>(
      fullscreenDialog: true,
      builder: (_) => DiffView.single(_pendingDiff(diff)),
    );
    _routes.own(route);
    Navigator.of(context).push(route);
  }

  IconData get _resourceIcon => switch (widget.permission.permission) {
    'bash' => Icons.terminal_rounded,
    'read' || 'edit' => Icons.description_outlined,
    'webfetch' => Icons.public_rounded,
    'external_directory' => Icons.folder_open_rounded,
    _ => Icons.key_rounded,
  };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final permission = widget.permission;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    final message = permission.message;
    final contextLine = message == null || message.isEmpty
        ? 'The agent wants to use ${permission.permission.isEmpty ? 'a permission' : permission.permission}.'
        : message;
    final showSourceChip =
        widget.onShowSource != null && permission.tool != null;
    return Column(
      key: const Key('permission-sheet'),
      mainAxisSize: MainAxisSize.min,
      children: [
        Flexible(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.admin_panel_settings_outlined,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        permissionRequestTitle(permission.permission),
                        style: theme.textTheme.titleMedium,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(contextLine, style: theme.textTheme.bodyMedium),
                if (permission.commandPreview case final command?) ...[
                  const SizedBox(height: 12),
                  _CommandPreview(command: command),
                ],
                if (permission.filePath case final path?) ...[
                  const SizedBox(height: 12),
                  _FilePathRow(path: path),
                ],
                if (_diffPreview case final diff?) ...[
                  const SizedBox(height: 12),
                  _DiffPreviewBox(
                    diff: diff,
                    onSeeFull: () => _openFullDiff(diff),
                  ),
                ],
                if (_resourcesCard(theme) case final resources?) ...[
                  const SizedBox(height: 12),
                  resources,
                ],
                if (permission.always.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  Text(
                    'Always allow would also cover',
                    style: theme.textTheme.labelLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    permission.always.join('\n'),
                    style: const TextStyle(
                      fontFamily: AppTheme.monoFamily,
                      fontSize: AppTheme.codeFontSize,
                    ),
                  ),
                ],
                if (showSourceChip) ...[
                  const SizedBox(height: 12),
                  ActionChip(
                    key: const Key('permission-source-chip'),
                    avatar: const Icon(Icons.build_circle_outlined, size: 18),
                    label: const Text('From tool call'),
                    onPressed: () {
                      if (!_routes.isPending) return;
                      final onShowSource = widget.onShowSource!;
                      _routes.close();
                      onShowSource();
                    },
                  ),
                ],
                if (_error != null) ...[
                  const SizedBox(height: 10),
                  Text(
                    'Reply failed: $_error',
                    style: TextStyle(color: theme.colorScheme.error),
                  ),
                ],
              ],
            ),
          ),
        ),
        _applyBar(theme, reduceMotion),
      ],
    );
  }

  /// The requested patterns, minus any already shown as the command or file
  /// preview above (those blocks carry their own Copy). Null when the
  /// previews covered everything, so the same string never appears twice.
  Widget? _resourcesCard(ThemeData theme) {
    final permission = widget.permission;
    final shown = {?permission.commandPreview, ?permission.filePath};
    final resources = permission.patterns
        .where((pattern) => !shown.contains(pattern))
        .toList();
    if (resources.isEmpty && shown.isNotEmpty) return null;
    final rows = resources.isEmpty
        ? const ['(all matching requests)']
        : resources;
    final card = Material(
      key: const Key('permission-resources'),
      color: theme.colorScheme.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final resource in rows)
              // Plain text plus a real Copy button: selection-by-long-press
              // was both an invisible gesture and a 16dp-tall tap target.
              ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Icon(
                      _resourceIcon,
                      size: 16,
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        resource,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontFamily: AppTheme.monoFamily,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: 'Copy $resource',
                      constraints: const BoxConstraints.tightFor(
                        width: 48,
                        height: 48,
                      ),
                      onPressed: () => unawaited(
                        Clipboard.setData(ClipboardData(text: resource)),
                      ),
                      icon: const Icon(AppIcons.copy, size: 18),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
    if (rows.length <= 6) return card;
    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 200),
      child: SingleChildScrollView(child: card),
    );
  }

  Widget _applyBar(ThemeData theme, bool reduceMotion) {
    return Material(
      key: const Key('permission-apply-bar'),
      color: theme.colorScheme.surfaceContainerHigh,
      elevation: 6,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          child: AnimatedSize(
            duration: reduceMotion
                ? Duration.zero
                : const Duration(milliseconds: 240),
            curve: Curves.easeOutCubic,
            alignment: Alignment.topCenter,
            child: _rejecting ? _rejectPane(theme) : _triad(theme),
          ),
        ),
      ),
    );
  }

  Widget _triad(ThemeData theme) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        FilledButton(
          key: const Key('permission-allow-once'),
          onPressed: _replying ? null : () => _reply('once'),
          child: _replying && !_confirming
              ? const SizedBox.square(
                  dimension: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('Allow once'),
        ),
        const SizedBox(height: 8),
        OutlinedButton(
          key: const Key('permission-allow-always'),
          onPressed: _replying ? null : () => _reply('always'),
          child: const Text('Always allow'),
        ),
        const SizedBox(height: 4),
        TextButton(
          key: const Key('permission-reject'),
          onPressed: _replying ? null : _startReject,
          child: Text(widget.supportsRejectMessage ? 'Reject…' : 'Reject'),
        ),
      ],
    );
  }

  Widget _rejectPane(ThemeData theme) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          key: const Key('permission-reject-message'),
          controller: _rejectMessage,
          autofocus: true,
          minLines: 1,
          maxLines: 3,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: 'Tell the agent why, or what to do instead (optional)',
          ),
        ),
        const SizedBox(height: 10),
        FilledButton.tonal(
          key: const Key('permission-reject-send'),
          style: FilledButton.styleFrom(
            backgroundColor: theme.colorScheme.errorContainer,
            foregroundColor: theme.colorScheme.onErrorContainer,
          ),
          onPressed: _replying ? null : _sendRejection,
          child: _replying
              ? const SizedBox.square(
                  dimension: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('Send rejection'),
        ),
        const SizedBox(height: 4),
        TextButton(
          onPressed: _replying
              ? null
              : () => setState(() => _rejecting = false),
          child: const Text('Back'),
        ),
      ],
    );
  }
}

/// The shell command awaiting approval, highlighted as bash in a mono block
/// so quoting and pipes read at a glance before the user allows it.
class _CommandPreview extends StatelessWidget {
  const _CommandPreview({required this.command});

  final String command;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      key: const Key('permission-command-preview'),
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.hairline(theme)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Text(
              '\$',
              style: theme.textTheme.bodySmall?.copyWith(
                fontFamily: AppTheme.monoFamily,
                color: AppTheme.mutedOf(theme),
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text.rich(
              highlightedCode(command, 'bash', CodeHighlightTheme.of(context)),
              style: theme.textTheme.bodySmall?.copyWith(
                fontFamily: AppTheme.monoFamily,
                fontSize: AppTheme.codeFontSize,
                height: 1.4,
              ),
            ),
          ),
          _CopyButton(text: command, label: 'Copy command'),
        ],
      ),
    );
  }
}

class _CopyButton extends StatelessWidget {
  const _CopyButton({required this.text, required this.label});

  final String text;
  final String label;

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: label,
    constraints: const BoxConstraints.tightFor(width: 48, height: 48),
    padding: EdgeInsets.zero,
    onPressed: () => unawaited(Clipboard.setData(ClipboardData(text: text))),
    icon: const Icon(AppIcons.copy, size: 18),
  );
}

/// The file an edit/write/read ask concerns: folder glyph plus the mono path.
class _FilePathRow extends StatelessWidget {
  const _FilePathRow({required this.path});

  final String path;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      key: const Key('permission-file-path'),
      children: [
        Icon(
          Icons.folder_outlined,
          size: 16,
          color: theme.colorScheme.onSurfaceVariant,
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            path,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodySmall?.copyWith(
              fontFamily: AppTheme.monoFamily,
            ),
          ),
        ),
        _CopyButton(text: path, label: 'Copy $path'),
      ],
    );
  }
}

/// The pending change as a diff-highlighted mono block, capped at 240 dp
/// with its own scroll, plus "See full diff" into the full-screen
/// [DiffView]. (DiffView itself is a Scaffold with a close button, so it is
/// not embedded inline where its close would dismiss the sheet.)
class _DiffPreviewBox extends StatelessWidget {
  const _DiffPreviewBox({required this.diff, required this.onSeeFull});

  final String diff;
  final VoidCallback onSeeFull;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 240),
          child: Container(
            key: const Key('permission-diff-preview'),
            width: double.infinity,
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppTheme.hairline(theme)),
            ),
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Text.rich(
                  highlightedCode(diff, 'diff', CodeHighlightTheme.of(context)),
                  softWrap: false,
                  style: theme.textTheme.bodySmall?.copyWith(
                    fontFamily: AppTheme.monoFamily,
                    fontSize: AppTheme.codeFontSize,
                    height: 1.4,
                  ),
                ),
              ),
            ),
          ),
        ),
        Align(
          alignment: AlignmentDirectional.centerEnd,
          child: TextButton.icon(
            key: const Key('permission-see-full-diff'),
            onPressed: onSeeFull,
            icon: const Icon(Icons.difference_outlined, size: 18),
            label: const Text('See full diff'),
          ),
        ),
      ],
    );
  }
}
