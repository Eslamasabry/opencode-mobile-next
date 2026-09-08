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
                    prefixIcon: Icon(AppIconography.search),
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
                              trailing: const Icon(AppIconography.play),
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
      final sessionID = await showRunCommandDialog(
        context,
        controller: widget.controller,
        command: command,
      );
      if (mounted && sessionID != null) {
        Navigator.of(context).pushNamed('/chat/$sessionID');
      }
    } finally {
      _openingCommand = false;
    }
  }
}
