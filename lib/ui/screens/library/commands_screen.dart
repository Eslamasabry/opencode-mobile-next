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

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      final commands = await repository.listCommands();
      if (mounted) setState(() => _commands = commands);
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
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
                            icon: Icons.electric_bolt_outlined,
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
                            separatorBuilder: (_, _) =>
                                const Divider(height: 1),
                            itemBuilder: (context, index) {
                              final command = commands[index];
                              return ListTile(
                                leading: const Icon(
                                  Icons.electric_bolt_outlined,
                                ),
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
    if (widget.embedded) return body;
    return Scaffold(
      appBar: AppBar(title: const Text('Server commands')),
      body: body,
    );
  }

  Future<void> _run(CommandInfo command) async {
    final sessions = widget.controller.sortedSessions();
    if (sessions.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Create a session before running a command.'),
        ),
      );
      return;
    }
    var sessionID = sessions.first.id;
    final arguments = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text('Run /${command.name}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                initialValue: sessionID,
                decoration: const InputDecoration(labelText: 'Session'),
                items: [
                  for (final session in sessions)
                    DropdownMenuItem(
                      value: session.id,
                      child: Text(
                        session.title?.isNotEmpty == true
                            ? session.title!
                            : 'Untitled session',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
                onChanged: (value) {
                  if (value != null) setDialogState(() => sessionID = value);
                },
              ),
              const SizedBox(height: 14),
              TextField(
                controller: arguments,
                decoration: const InputDecoration(
                  labelText: 'Arguments',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Run'),
            ),
          ],
        ),
      ),
    );
    final argumentText = arguments.text.trim();
    arguments.dispose();
    if (confirmed != true) return;
    try {
      final api = await widget.controller.prepareActionTransport();
      if (api == null) throw const ProductException('OpenCode is reconnecting.');
      await api.slashCommand(
        sessionID,
        command.name,
        argumentText,
        model: widget.controller.selectedModel,
      );
      if (mounted) Navigator.of(context).pushNamed('/chat/$sessionID');
    } catch (error) {
      if (mounted) showProductError(context, error);
    }
  }
}

