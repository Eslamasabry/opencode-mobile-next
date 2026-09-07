import 'package:flutter/material.dart';

import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import 'product_states.dart';
import 'session_inventory_footer.dart';

/// Opens review only. Execution requires the user's Run action.
Future<String?> showRunCommandDialog(
  BuildContext context, {
  required ConnectionController controller,
  required CommandInfo command,
  Future<bool> Function()? validateCommand,
}) => showDialog<String>(
  context: context,
  barrierDismissible: false,
  builder: (_) => _RunCommandDialog(
    controller: controller,
    command: command,
    validateCommand: validateCommand,
  ),
);

class _RunCommandDialog extends StatefulWidget {
  const _RunCommandDialog({
    required this.controller,
    required this.command,
    this.validateCommand,
  });

  final ConnectionController controller;
  final CommandInfo command;
  final Future<bool> Function()? validateCommand;

  @override
  State<_RunCommandDialog> createState() => _RunCommandDialogState();
}

class _RunCommandDialogState extends State<_RunCommandDialog> {
  final _arguments = TextEditingController();
  late List<Session> _sessions;
  late final int _locationRevision;
  late final String? _profileID;
  late final String? _directory;
  late final String? _workspace;
  late String _destination;
  String? _createdSessionID;
  String? _error;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    final controller = widget.controller;
    _sessions = controller.sortedSessions();
    _locationRevision = controller.locationRevision;
    _profileID = controller.profile?.id;
    _directory = controller.directory;
    _workspace = controller.workspace;
    _destination = _sessions.isEmpty ? '' : _sessions.first.id;
    controller.addListener(_sessionsChanged);
  }

  void _sessionsChanged() {
    if (!_sameLocation) return;
    final current = widget.controller.sortedSessions();
    // Keep a selected destination visible while a refreshed page is partial.
    final selected = _sessions.where((session) => session.id == _destination);
    setState(
      () => _sessions = [
        ...current,
        if (!current.any((session) => session.id == _destination)) ...selected,
      ],
    );
  }

  bool get _sameLocation =>
      mounted &&
      widget.controller.locationRevision == _locationRevision &&
      widget.controller.profile?.id == _profileID &&
      widget.controller.directory == _directory &&
      widget.controller.workspace == _workspace;

  AppLocalizations get _l10n =>
      Localizations.of<AppLocalizations>(context, AppLocalizations) ??
      lookupAppLocalizations(Localizations.localeOf(context));

  Future<void> _submit() async {
    if (_sending) return;
    final locationError = _l10n.commandLocationChanged;
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      if (!_sameLocation) throw ProductException(locationError);
      final api = await widget.controller.prepareActionTransport();
      if (!_sameLocation) throw ProductException(locationError);
      if (api == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      if (widget.validateCommand case final validate?) {
        if (!await validate() || !_sameLocation) {
          throw ProductException(_l10n.pluginMappingUnavailable);
        }
      }
      var sessionID = _destination;
      if (sessionID.isEmpty) {
        _createdSessionID ??= (await widget.controller.createSession()).id;
        if (!_sameLocation) throw ProductException(locationError);
        sessionID = _createdSessionID!;
      }
      await widget.controller.waitForSessionSelection(
        sessionID,
        expectedApi: api,
      );
      if (!_sameLocation) throw ProductException(locationError);
      final variant = widget.controller.variantForSession(sessionID);
      if (widget.validateCommand case final validate?) {
        if (!await validate() || !_sameLocation) {
          throw ProductException(_l10n.pluginMappingUnavailable);
        }
      }
      await api.slashCommand(
        sessionID,
        widget.command.name,
        _arguments.text.trim(),
        model: widget.controller.modelForSession(sessionID),
        variant: variant.isEmpty ? null : variant,
      );
      if (!_sameLocation) throw ProductException(locationError);
      if (mounted) Navigator.of(context).pop(sessionID);
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_sessionsChanged);
    _arguments.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    return PopScope(
      canPop: !_sending,
      child: AlertDialog(
        title: Text(l10n.commandRunTitle(widget.command.name)),
        scrollable: true,
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            DropdownButtonFormField<String>(
              key: const ValueKey('command-destination'),
              initialValue: _destination,
              isExpanded: true,
              decoration: InputDecoration(labelText: l10n.commandDestination),
              items: [
                DropdownMenuItem(value: '', child: Text(l10n.commandNewChat)),
                for (final session in _sessions)
                  DropdownMenuItem(
                    value: session.id,
                    child: Text(
                      session.title?.isNotEmpty == true
                          ? session.title!
                          : l10n.commandUntitledChat,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              onChanged: _sending
                  ? null
                  : (value) {
                      if (value != null) setState(() => _destination = value);
                    },
            ),
            const SizedBox(height: 14),
            if (_sameLocation)
              SessionInventoryFooter(controller: widget.controller),
            TextField(
              key: const ValueKey('command-arguments'),
              controller: _arguments,
              enabled: !_sending,
              minLines: 1,
              maxLines: 5,
              decoration: InputDecoration(
                labelText: l10n.commandArguments,
                border: const OutlineInputBorder(),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 14),
              Text(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: _sending ? null : () => Navigator.of(context).pop(),
            child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
          ),
          FilledButton(
            key: const ValueKey('command-submit'),
            onPressed: _sending ? null : _submit,
            child: Text(_sending ? l10n.commandRunning : l10n.commandRun),
          ),
        ],
      ),
    );
  }
}
