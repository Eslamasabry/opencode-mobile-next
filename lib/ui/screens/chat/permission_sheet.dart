import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../api/models.dart';
import '../../permission_presentation.dart';
import '../../app_theme.dart';

/// Presents the OpenCode 2 permission prompt as a modal bottom sheet
/// (design doc §3). One component serves three entry points: the chat
/// auto-present, the Requests tile, and notification taps.
///
/// [onReply] receives `once` / `always` / `reject` plus the optional reject
/// [message] (v2 steering-by-rejection; ignored by v1 transports). Throwing
/// keeps the sheet open with the error inline. [supportsRejectMessage]
/// controls the reject expansion: when false (v1 servers, whose reply shape
/// has no message field) Reject submits directly. [onShowSource], when
/// given for a request carrying a tool source, renders the "From tool call"
/// chip; it runs after the sheet dismisses itself.
Future<void> showPermissionSheet(
  BuildContext context, {
  required PermissionRequest permission,
  required Future<void> Function(String reply, {String? message}) onReply,
  required bool supportsRejectMessage,
  String contextLabel = 'in this chat',
  VoidCallback? onShowSource,
}) {
  return showModalBottomSheet<void>(
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
        onReply: onReply,
        supportsRejectMessage: supportsRejectMessage,
        contextLabel: contextLabel,
        onShowSource: onShowSource,
      ),
    ),
  );
}

class PermissionSheet extends StatefulWidget {
  const PermissionSheet({
    super.key,
    required this.permission,
    required this.onReply,
    required this.supportsRejectMessage,
    this.contextLabel = 'in this chat',
    this.onShowSource,
  });

  final PermissionRequest permission;
  final Future<void> Function(String reply, {String? message}) onReply;
  final bool supportsRejectMessage;
  final String contextLabel;
  final VoidCallback? onShowSource;

  @override
  State<PermissionSheet> createState() => _PermissionSheetState();
}

class _PermissionSheetState extends State<PermissionSheet> {
  final _rejectMessage = TextEditingController();
  bool _replying = false;
  bool _rejecting = false;
  Object? _error;

  @override
  void dispose() {
    _rejectMessage.dispose();
    super.dispose();
  }

  Future<void> _reply(String reply, {String? message}) async {
    if (_replying) return;
    if (reply == 'always' && !await _confirmAlways()) return;
    setState(() {
      _replying = true;
      _error = null;
    });
    try {
      await widget.onReply(reply, message: message);
      if (mounted) Navigator.of(context).pop();
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _replying = false;
        _error = error;
      });
    }
  }

  void _startReject() {
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
          builder: (context) => AlertDialog(
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
          ),
        ) ??
        false;
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
                const SizedBox(height: 12),
                _resourcesCard(theme),
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
                      final onShowSource = widget.onShowSource!;
                      Navigator.of(context).pop();
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

  Widget _resourcesCard(ThemeData theme) {
    final resources = widget.permission.patterns;
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
          child: _replying
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
