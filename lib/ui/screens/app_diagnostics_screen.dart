import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/product_repository.dart';
import '../../diagnostics/app_diagnostics.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import '../app_theme.dart';

class AppDiagnosticsScreen extends StatefulWidget {
  const AppDiagnosticsScreen({super.key, required this.controller});

  final ConnectionController controller;

  @override
  State<AppDiagnosticsScreen> createState() => _AppDiagnosticsScreenState();
}

class _AppDiagnosticsScreenState extends State<AppDiagnosticsScreen> {
  bool _sending = false;

  AppDiagnosticsController get _diagnostics => widget.controller.diagnostics;

  Future<void> _copy() async {
    await Clipboard.setData(ClipboardData(text: _diagnostics.reportText()));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Diagnostics copied')));
  }

  Future<void> _send() async {
    if (_sending || _diagnostics.isEmpty) return;
    setState(() => _sending = true);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final count = _diagnostics.count;
      await repository.writeClientLog(
        message: 'OpenCode Mobile diagnostics ($count handled errors)',
        extra: _diagnostics.reportJson(),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Diagnostics sent to OpenCode')),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Could not send diagnostics: '
            '${_diagnostics.sanitize(error.toString(), limit: 300)}',
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _clear() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear diagnostics?'),
        content: const Text(
          'This removes every captured error from process memory.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Clear'),
          ),
        ],
      ),
    );
    if (confirmed == true) _diagnostics.clear();
  }

  String _time(DateTime value) {
    final local = value.toLocal();
    String two(int part) => part.toString().padLeft(2, '0');
    return '${local.year}-${two(local.month)}-${two(local.day)} '
        '${two(local.hour)}:${two(local.minute)}:${two(local.second)}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('App diagnostics')),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: _diagnostics,
          builder: (context, _) {
            final entries = _diagnostics.entries.reversed.toList();
            return CustomScrollView(
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Text(
                          'Private until you send it',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Handled app errors are redacted and kept only in memory. '
                          'Chat messages and file contents are not collected. Nothing '
                          'is sent automatically.',
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(
                                color: Theme.of(
                                  context,
                                ).colorScheme.onSurfaceVariant,
                              ),
                        ),
                        const SizedBox(height: 14),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            FilledButton.icon(
                              key: const ValueKey('send-app-diagnostics'),
                              // §7 row 24: the control stays, visibly dead,
                              // with the explainer directly beneath it.
                              onPressed:
                                  entries.isEmpty ||
                                      _sending ||
                                      !widget
                                          .controller
                                          .capabilities
                                          .clientDiagnostics
                                  ? null
                                  : _send,
                              icon: _sending
                                  ? const SizedBox.square(
                                      dimension: 16,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                      ),
                                    )
                                  : const Icon(Icons.send_outlined),
                              label: Text(_sending ? 'Sending…' : 'Send'),
                            ),
                            OutlinedButton.icon(
                              key: const ValueKey('copy-app-diagnostics'),
                              onPressed: entries.isEmpty ? null : _copy,
                              icon: const Icon(AppIcons.copy),
                              label: const Text('Copy'),
                            ),
                            TextButton.icon(
                              key: const ValueKey('clear-app-diagnostics'),
                              onPressed: entries.isEmpty ? null : _clear,
                              icon: const Icon(Icons.delete_outline_rounded),
                              label: const Text('Clear'),
                            ),
                          ],
                        ),
                        if (!widget.controller.capabilities.clientDiagnostics)
                          Padding(
                            key: const ValueKey('gated-client-diagnostics'),
                            padding: const EdgeInsets.only(top: 10),
                            child: Text(
                              "This server doesn't accept client logs",
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(
                                    color: Theme.of(
                                      context,
                                    ).colorScheme.onSurfaceVariant,
                                  ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
                if (entries.isEmpty)
                  const SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: Padding(
                        padding: EdgeInsets.all(32),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.health_and_safety_outlined, size: 38),
                            SizedBox(height: 14),
                            Text('No captured app errors'),
                            SizedBox(height: 6),
                            Text(
                              'Handled Flutter, platform, and startup errors will appear here for this app run.',
                              textAlign: TextAlign.center,
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                else ...[
                  SliverToBoxAdapter(
                    child: SectionLabel(
                      '${entries.length} handled error${entries.length == 1 ? '' : 's'}',
                    ),
                  ),
                  SliverList.separated(
                    itemCount: entries.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final entry = entries[index];
                      return ExpansionTile(
                        key: ValueKey('diagnostic-entry-${entry.id}'),
                        leading: const Icon(Icons.error_outline_rounded),
                        title: Text(
                          entry.message,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Text(
                          '${entry.source} · ${_time(entry.timestamp)}'
                          '${entry.occurrences > 1 ? ' · ${entry.occurrences} occurrences' : ''}',
                        ),
                        childrenPadding: const EdgeInsets.fromLTRB(
                          16,
                          0,
                          16,
                          16,
                        ),
                        expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          SelectionArea(
                            child: Text(
                              [
                                entry.message,
                                if (entry.stack.isNotEmpty) entry.stack,
                              ].join('\n\n'),
                              style: const TextStyle(
                                fontFamily: AppTheme.monoFamily,
                                fontSize: AppTheme.codeFontSize,
                              ),
                            ),
                          ),
                        ],
                      );
                    },
                  ),
                  const SliverToBoxAdapter(child: SizedBox(height: 24)),
                ],
              ],
            );
          },
        ),
      ),
    );
  }
}
