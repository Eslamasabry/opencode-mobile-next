part of '../settings_screen.dart';

/// The server-backed default shell, as one hub row that opens the shell
/// sheet. It owns its own load/save state so the hub does not spend a request
/// for a row a search has filtered out.
class DefaultShellRow extends StatefulWidget {
  final ConnectionController controller;

  /// On a server's own page rows carry no icon tile (R4).
  final bool icon;
  const DefaultShellRow({
    super.key,
    required this.controller,
    this.icon = true,
  });

  @override
  State<DefaultShellRow> createState() => _DefaultShellRowState();
}

class _DefaultShellRowState extends State<DefaultShellRow>
    with WidgetsBindingObserver {
  TerminalShellSettings? _shellSettings;
  String? _shellError;
  String? _saveError;
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
    // Runs from initState, so inherited lookups are not yet allowed.
    final copy = earlyAppLocalizations(context);
    // The hub only builds this row when the server has shell settings; the
    // guard keeps a capability change mid-life from spending a request that
    // can only come back as an "unavailable" error.
    if (!widget.controller.capabilities.shellSettings) return;
    final generation = ++_shellLoadGeneration;
    setState(() {
      _loadingShell = true;
      _shellError = null;
    });
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw ProductException(copy.e7SettingsUi19);
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
    final copy = _settingsCopy(context);
    final settings = _shellSettings;
    if (_savingShell) return;
    if (settings == null) {
      await _loadShellSettings();
      return;
    }
    final choices = _shellChoices(settings);
    final selected = await showKitChoiceSheet<String>(
      context,
      title: copy.settingsV3Shell,
      // The shell is the server's own setting, shared with every app that
      // uses it: the sheet says whose it is before anything is chosen.
      subtitle: widget.controller.profile == null
          ? copy.e7SettingsUi36
          : '${copy.e7SettingsUi36} ${savedOnNote(copy, serverDisplayName(widget.controller.profile, lookupAppLocalizations(Localizations.localeOf(context)), among: widget.controller.store.profiles))}',
      selected: settings.selected,
      choices: [
        for (final choice in choices)
          KitChoice(
            key: ValueKey('server-shell-${choice.id}'),
            value: choice.value,
            title: choice.label,
            supporting: choice.terminalOnly ? copy.e7SettingsUi37 : null,
          ),
      ],
    );
    if (selected == null || selected == settings.selected || !mounted) return;

    setState(() {
      _savingShell = true;
      _saveError = null;
    });
    final locationRevision = widget.controller.locationRevision;
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw ProductException(copy.e7SettingsUi19);
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
      // The row now names the new shell: that is the confirmation.
      await _loadShellSettings();
    } catch (error) {
      // A failed save says so in the row itself, and a tap tries again.
      if (mounted) setState(() => _saveError = productErrorText(error));
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
      _ShellChoice(
        id: 'automatic',
        value: '',
        label: _settingsCopy(context).e7SettingsUi39,
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
    if (settings.selected.isEmpty) {
      return _settingsCopy(context).settingsV3ShellAutomatic;
    }
    final choices = _shellChoices(settings);
    for (final choice in choices) {
      if (choice.value == settings.selected) return choice.label;
    }
    return settings.selected;
  }

  /// The server offers one shell and nothing else is chosen: there is
  /// nothing to pick, so the row says so instead of opening a sheet.
  String? _onlyShell(TerminalShellSettings settings) {
    if (settings.options.length != 1) return null;
    final only = settings.options.single;
    final selected = settings.selected;
    if (selected.isNotEmpty && selected != only.name && selected != only.path) {
      return null;
    }
    return only.name;
  }

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final busy = _loadingShell || _savingShell;
    final settings = _shellSettings;
    final only = settings == null || _shellError != null || _saveError != null
        ? null
        : _onlyShell(settings);
    if (only != null) {
      return KitRow(
        key: const ValueKey('default-shell-settings-entry'),
        leading: widget.icon
            ? KitRow.icon(context, AppIconography.terminal)
            : null,
        title: copy.settingsV3Shell,
        // Nothing to pick, so nothing opens: the row says why in a line.
        supporting: TextSpan(text: copy.defaultShellOnlyOne(only)),
        supportingMaxLines: 2,
      );
    }
    // Loading and saving read in the row's own words; no spinner (§4).
    return _CategoryRow(
      rowKey: 'default-shell-settings-entry',
      icon: widget.icon ? AppIconography.terminal : null,
      title: copy.settingsV3Shell,
      subtitle: _shellError != null
          ? copy.e7SettingsRetryError(_shellError!)
          : _saveError != null
          ? copy.defaultShellSaveFailed(_saveError!)
          : settings == null
          ? copy.e7SettingsUi41
          : null,
      value: settings == null || _shellError != null || _saveError != null
          ? null
          : _selectedShellLabel(settings),
      enabled: !busy,
      onTap: _shellError != null ? _loadShellSettings : _chooseShell,
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
