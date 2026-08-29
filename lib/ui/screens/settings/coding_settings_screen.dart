part of '../settings_screen.dart';

/// Coding defaults category: the server-backed default shell, the selected
/// model and agent, and experimental notes.
class CodingSettingsScreen extends StatefulWidget {
  final ConnectionController controller;
  const CodingSettingsScreen({super.key, required this.controller});

  @override
  State<CodingSettingsScreen> createState() => _CodingSettingsScreenState();
}

class _CodingSettingsScreenState extends State<CodingSettingsScreen>
    with WidgetsBindingObserver {
  TerminalShellSettings? _shellSettings;
  String? _shellError;
  bool _loadingShell = false;
  bool _savingShell = false;
  int _shellLoadGeneration = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_connectionChanged);
    _loadShellSettings();
  }

  void _connectionChanged() {
    if (mounted) setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_loadShellSettings());
    }
  }

  Future<void> _loadShellSettings() async {
    final generation = ++_shellLoadGeneration;
    setState(() {
      _loadingShell = true;
      _shellError = null;
    });
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final settings = await repository.loadTerminalShellSettings();
      if (mounted && generation == _shellLoadGeneration) {
        setState(() => _shellSettings = settings);
      }
    } catch (error) {
      if (mounted && generation == _shellLoadGeneration) {
        setState(() => _shellError = productErrorText(error));
      }
    } finally {
      if (mounted && generation == _shellLoadGeneration) {
        setState(() => _loadingShell = false);
      }
    }
  }

  Future<void> _chooseShell() async {
    final settings = _shellSettings;
    if (_savingShell) return;
    if (settings == null) {
      await _loadShellSettings();
      return;
    }
    final choices = _shellChoices(settings);
    final selected = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * 0.8,
          ),
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(
                leading: Icon(Icons.terminal_rounded),
                title: Text('Default shell'),
                subtitle: Text(
                  'Used by new terminals and compatible shell commands on this OpenCode server.',
                ),
              ),
              for (final choice in choices)
                ListTile(
                  key: ValueKey('server-shell-${choice.id}'),
                  leading: Icon(
                    choice.value == settings.selected
                        ? Icons.radio_button_checked_rounded
                        : Icons.radio_button_off_rounded,
                  ),
                  title: Text(choice.label),
                  subtitle: choice.terminalOnly
                      ? const Text(
                          'Terminal only; OpenCode uses a compatible fallback for shell tools.',
                        )
                      : null,
                  onTap: () => Navigator.pop(context, choice.value),
                ),
            ],
          ),
        ),
      ),
    );
    if (selected == null || selected == settings.selected || !mounted) return;

    setState(() => _savingShell = true);
    final locationRevision = widget.controller.locationRevision;
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      await repository.selectTerminalShell(selected);
      if (!mounted) return;
      if (locationRevision == widget.controller.locationRevision) {
        setState(
          () => _shellSettings = TerminalShellSettings(
            selected: selected,
            options: settings.options,
          ),
        );
      }
      await _loadShellSettings();
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Default shell updated')));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _savingShell = false);
    }
  }

  List<_ShellChoice> _shellChoices(TerminalShellSettings settings) {
    final nameCounts = <String, int>{};
    for (final option in settings.options) {
      nameCounts.update(option.name, (count) => count + 1, ifAbsent: () => 1);
    }
    final choices = <_ShellChoice>[
      const _ShellChoice(
        id: 'automatic',
        value: '',
        label: 'Automatic (server default)',
        terminalOnly: false,
      ),
    ];
    final values = <String>{''};
    for (final option in settings.options) {
      final ambiguous = nameCounts[option.name] != 1;
      final value = ambiguous ? option.path : option.name;
      if (!values.add(value)) continue;
      choices.add(
        _ShellChoice(
          id: option.path,
          value: value,
          label: ambiguous ? option.path : option.name,
          terminalOnly: !option.acceptable,
        ),
      );
    }
    if (settings.selected.isNotEmpty && values.add(settings.selected)) {
      choices.add(
        _ShellChoice(
          id: settings.selected,
          value: settings.selected,
          label: settings.selected,
          terminalOnly: false,
        ),
      );
    }
    return choices;
  }

  String _selectedShellLabel(TerminalShellSettings settings) {
    if (settings.selected.isEmpty) return 'Automatic (server default)';
    final choices = _shellChoices(settings);
    for (final choice in choices) {
      if (choice.value == settings.selected) return choice.label;
    }
    return settings.selected;
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    return Scaffold(
      appBar: AppBar(title: const Text('Coding defaults')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          ListTile(
            key: const ValueKey('default-shell-settings-entry'),
            leading: const Icon(Icons.terminal_rounded),
            title: const Text('Default shell'),
            subtitle: Text(
              _shellError != null
                  ? '${_shellError!} Tap to retry.'
                  : _shellSettings == null
                  ? 'Loading shells from OpenCode…'
                  : _selectedShellLabel(_shellSettings!),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: _loadingShell || _savingShell
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.chevron_right_rounded),
            onTap: _loadingShell || _savingShell ? null : _chooseShell,
          ),
          ListTile(
            leading: const Icon(Icons.model_training_outlined),
            title: const Text('Selected model'),
            subtitle: Text(
              controller.selectedModel == null
                  ? 'Server default'
                  : [
                      '${presentedProviderName(controller.selectedModel!.providerID, controller.catalog?.providers ?? const [])} · ${controller.selectedModel!.modelID}',
                      if (controller.selectedVariant.isNotEmpty)
                        controller.selectedVariant,
                    ].join(' · '),
              style: const TextStyle(fontFamily: 'AppMono', fontSize: 12),
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => showModelPicker(context),
          ),
          ListTile(
            leading: const Icon(Icons.support_agent_outlined),
            title: const Text('Selected agent'),
            subtitle: Text(
              controller.selectedAgent.isEmpty
                  ? 'Server default'
                  : controller.selectedAgent,
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => showModelPicker(context),
          ),
          const SectionLabel('Experimental'),
          const ListTile(
            leading: Icon(Icons.science_outlined),
            title: Text('Workspaces'),
            subtitle: Text(
              'Workspace and worktree switching is available from the Workspace tab. '
              'Availability depends on the connected server.',
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _shellLoadGeneration++;
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_connectionChanged);
    super.dispose();
  }
}
