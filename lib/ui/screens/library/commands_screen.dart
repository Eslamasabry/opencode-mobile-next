part of '../library_screen.dart';

class CommandsScreen extends StatefulWidget {
  final ConnectionController controller;

  /// Embedded mode renders the body only, for the Commands & tools tabs.
  final bool embedded;
  const CommandsScreen({
    super.key,
    required this.controller,
    this.embedded = false,
  });

  @override
  State<CommandsScreen> createState() => _CommandsScreenState();
}

class _CommandsScreenState extends State<CommandsScreen> {
  List<CommandInfo>? _commands;
  String? _error;
  String _query = '';
  bool _openingCommand = false;
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    if (_commands == null) setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (!mounted || generation != _loadGeneration) return;
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      final commands = await repository.listCommands();
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _commands = commands;
        _error = null;
      });
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _error = productErrorText(error));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final commands = (_commands ?? const <CommandInfo>[]).where((command) {
      final query = _query.toLowerCase();
      return query.isEmpty ||
          command.name.toLowerCase().contains(query) ||
          (command.description ?? '').toLowerCase().contains(query);
    }).toList();
    final body = _commands == null && _error == null
        ? const LoadingList()
        : _error != null && _commands == null
        ? ProductErrorState(message: _error!, onRetry: _load)
        : Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(12),
                child: TextField(
                  decoration: const InputDecoration(
                    hintText: 'Search server commands',
                    prefixIcon: Icon(Icons.search_rounded),
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  onChanged: (value) => setState(() => _query = value),
                ),
              ),
              Expanded(
                child: commands.isEmpty
                    ? RefreshIndicator(
                        onRefresh: _load,
                        child: const ProductEmptyState(
                          icon: AppIcons.run,
                          title: 'No server commands found',
                          message:
                              'Commands from your project and skills appear here.',
                        ),
                      )
                    : RefreshIndicator(
                        onRefresh: _load,
                        child: ListView.separated(
                          physics: const AlwaysScrollableScrollPhysics(),
                          itemCount: commands.length,
                          separatorBuilder: (_, _) => const Divider(height: 1),
                          itemBuilder: (context, index) {
                            final command = commands[index];
                            return ListTile(
                              leading: const Icon(AppIcons.run),
                              title: Text('/${command.name}'),
                              subtitle: Text(
                                command.description ?? 'No description',
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                              trailing: const Icon(Icons.play_arrow_rounded),
                              onTap: () => _run(command),
                            );
                          },
                        ),
                      ),
              ),
            ],
          );
    final content = ProductRefreshBody(
      message: _commands == null ? null : _error,
      onRetry: _load,
      child: body,
    );
    if (widget.embedded) return content;
    return Scaffold(
      appBar: AppBar(title: const Text('Server commands')),
      body: content,
    );
  }

  Future<void> _run(CommandInfo command) async {
    if (_openingCommand) return;
    _openingCommand = true;
    try {
      final sessionID = await showDialog<String>(
        context: context,
        barrierDismissible: false,
        builder: (_) =>
            _RunCommandDialog(controller: widget.controller, command: command),
      );
      if (mounted && sessionID != null) {
        Navigator.of(context).pushNamed('/chat/$sessionID');
      }
    } finally {
      _openingCommand = false;
    }
  }
}

class _RunCommandDialog extends StatefulWidget {
  const _RunCommandDialog({required this.controller, required this.command});

  final ConnectionController controller;
  final CommandInfo command;

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
      var sessionID = _destination;
      if (sessionID.isEmpty) {
        _createdSessionID ??= (await api.createSession()).id;
        if (!_sameLocation) throw ProductException(locationError);
        sessionID = _createdSessionID!;
      }
      final variant = widget.controller.variantForSession(sessionID);
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
