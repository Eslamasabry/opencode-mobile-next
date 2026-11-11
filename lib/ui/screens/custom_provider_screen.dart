import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/builtin_server.dart'
    show BuiltinServerStarter, builtinServerStarterProvider;
import '../../domain/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import '../widgets/phone_server_restart.dart';
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/work_status_line.dart' show confirmPhoneServerRestart;

AppLocalizations _l10nOf(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Declaring an endpoint OpenCode's own catalog does not have.
///
/// OpenCode 2 has no endpoint that adds a provider (providers are declared in
/// the server's `opencode.json`), so this page is offered only where the app
/// owns that file: the engine running on this phone. The key is never typed
/// here; it is connected afterwards on the provider row like any other
/// provider's, so it never reaches a config file.
///
/// States: form; saving; save failed (the reason on the status line, the
/// draft kept); saved but the server has not restarted (the one thing left
/// to do); restart failed (the provider stays declared, not loaded); not
/// available on this server (explained, P7.4); discard question when leaving
/// with unsaved input.
class CustomProviderScreen extends StatefulWidget {
  final ConnectionController controller;

  const CustomProviderScreen({super.key, required this.controller});

  @override
  State<CustomProviderScreen> createState() => _CustomProviderScreenState();
}

class _CustomProviderScreenState extends State<CustomProviderScreen> {
  final _id = TextEditingController();
  final _name = TextEditingController();
  final _url = TextEditingController();
  final _models = TextEditingController();

  late final bool _supported;
  bool _saving = false;
  bool _saved = false;
  bool _restarted = false;
  bool _submitted = false;
  bool _leaving = false;
  String? _saveError;
  String? _restartError;
  String _startSignature = '';

  bool get _editable => !_saved;

  /// A server that keeps its own config cannot take a provider from the app.
  bool get _hasInput => !_saved && _inputSignature() != _startSignature;

  String _inputSignature() => [
    _id.text,
    _name.text,
    _url.text,
    _models.text,
  ].map((text) => text.trim()).where((text) => text.isNotEmpty).join('\u0000');

  @override
  void initState() {
    super.initState();
    _supported = widget.controller.capabilities.customProviders;
    _startSignature = _inputSignature();
  }

  @override
  void dispose() {
    _id.dispose();
    _name.dispose();
    _url.dispose();
    _models.dispose();
    super.dispose();
  }

  // --- validation -----------------------------------------------------------

  /// The ID rule lives in the draft ([CustomProviderDraft.isValidID](#)),
  /// which also re-checks it before writing.
  String? get _idError {
    final value = _id.text.trim().toLowerCase();
    if (value.isEmpty) return _l10nOf(context).providerSetupIDRequired;
    return CustomProviderDraft.isValidID(value)
        ? null
        : _l10nOf(context).providerSetupIDInvalid;
  }

  String? get _nameError => _name.text.trim().isEmpty
      ? _l10nOf(context).providerSetupNameRequired
      : null;

  String? get _urlError {
    final uri = Uri.tryParse(_url.text.trim());
    if (uri == null ||
        !uri.hasAuthority ||
        (uri.scheme != 'https' && uri.scheme != 'http') ||
        uri.userInfo.isNotEmpty) {
      return _l10nOf(context).e7LibraryEnterAValidHTTPOrHTTPSURL;
    }
    return null;
  }

  String? get _modelsError => _models.text.trim().isEmpty
      ? _l10nOf(context).providerSetupModelsRequired
      : null;

  bool get _formValid =>
      _idError == null &&
      _nameError == null &&
      _urlError == null &&
      _modelsError == null;

  String? _shown(String? error) => _submitted ? error : null;

  void _edited([String _ = '']) => setState(() {});

  CustomProviderDraft _draft() => CustomProviderDraft(
    id: _id.text,
    name: _name.text,
    baseURL: _url.text,
    models: _models.text.split('\n'),
  );

  // --- save -----------------------------------------------------------------

  Future<void> _save() async {
    if (!_editable || _saving) return;
    if (!_formValid) {
      setState(() => _submitted = true);
      return;
    }
    final route = ModalRoute.of(context);
    final l10n = _l10nOf(context);
    setState(() {
      _submitted = true;
      _saving = true;
      _saveError = null;
    });
    try {
      final draft = _draft();
      draft.toConfigJson();
      await widget.controller.addCustomProvider(draft);
      if (!mounted || !_routeIsActive(route)) return;
      setState(() {
        _saved = true;
        _saving = false;
      });
      await _restart(l10n);
    } catch (error) {
      if (mounted && _routeIsCurrent(route)) {
        setState(() => _saveError = productErrorText(error));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// Restarting is what loads the declaration: the server read its config at
  /// startup. A failure leaves the provider declared and unloaded, so say
  /// exactly that instead of claiming success.
  Future<void> _restart(AppLocalizations l10n) async {
    if (_restarted || _saving) return;
    final starter = _starter();
    if (starter == null) {
      setState(() => _restartError = l10n.providerSetupRestartNeeded);
      return;
    }
    if (!await confirmPhoneServerRestart(context) || !mounted) return;
    setState(() {
      _saving = true;
      _restartError = null;
    });
    try {
      final phone = phoneServerRestartFor(
        connection: widget.controller,
        builtin: starter,
        context: context,
      );
      final restart = phone.restart;
      if (!phone.onThisPhone || restart == null) {
        setState(() => _restartError = l10n.providerSetupRestartNeeded);
        return;
      }
      await restart();
      if (!mounted) return;
      if (widget.controller.connectionError != null) {
        setState(() => _restartError = l10n.providerSetupNotLoaded);
        return;
      }
      setState(() => _restarted = true);
    } catch (error) {
      if (mounted) setState(() => _restartError = productErrorText(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// The app's server owner; a bare widget test has none, and the restart
  /// then reports that instead of failing on the channel.
  BuiltinServerStarter? _starter() {
    try {
      return ProviderScope.containerOf(
        context,
        listen: false,
      ).read(builtinServerStarterProvider);
    } on StateError {
      return null;
    }
  }

  bool _routeIsCurrent(ModalRoute<dynamic>? route) =>
      _routeIsActive(route) && route!.isCurrent;

  bool _routeIsActive(ModalRoute<dynamic>? route) =>
      mounted && route != null && identical(ModalRoute.of(context), route);

  // --- leave ----------------------------------------------------------------

  Future<void> _confirmLeave() async {
    if (_leaving) return;
    final l10n = _l10nOf(context);
    final navigator = Navigator.of(context);
    final discard = await showKitConfirm(
      context,
      kind: KitConfirmKind.discard,
      icon: AppIconography.delete,
      title: l10n.providerSetupDiscardTitle,
      body: l10n.providerSetupDiscardBody,
      confirmLabel: l10n.providerSetupDiscardConfirm,
    );
    if (!discard || !mounted) return;
    setState(() => _leaving = true);
    navigator.pop();
  }

  // --- build ----------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final l10n = _l10nOf(context);
    if (!_supported) {
      return KitScreen(
        width: KitScreenWidth.list,
        topBar: KitTopBar(
          title: l10n.providerSetupTitle,
          subtitle: widget.controller.profile?.name,
        ),
        body: KitStateView.missing(
          key: const ValueKey('provider-setup-unavailable'),
          capability: 'flag:serverCatalog',
          icon: AppIconography.network,
          title: l10n.providerSetupUnavailableTitle,
          why: l10n.providerSetupUnavailableBody,
          size: KitStateSize.page,
        ),
      );
    }
    if (_saved) return _savedScreen(context, l10n);
    return PopScope<Object?>(
      canPop: _leaving || !_hasInput,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_confirmLeave());
      },
      child: KitScreen(
        width: KitScreenWidth.list,
        topBar: KitTopBar(
          title: l10n.providerSetupTitle,
          subtitle: widget.controller.profile?.name,
        ),
        status: _status(l10n),
        bottom: KitActionBlock(primary: _primary(l10n)),
        body: ListView(
          key: const ValueKey('provider-setup-form'),
          padding: KitScreen.padding(context),
          children: [
            KitText(
              l10n.providerSetupIntro,
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
            ),
            _gap(context),
            KitField(
              label: l10n.providerSetupID,
              controller: _id,
              kind: KitFieldKind.mono,
              hint: 'my-endpoint',
              helper: l10n.providerSetupIDHelper,
              error: _shown(_idError),
              enabled: _editable,
              disabledReason: _disabledReason(l10n),
              textInputAction: TextInputAction.next,
              onChanged: _edited,
              fieldKey: const ValueKey('provider-id'),
            ),
            _gap(context),
            KitField(
              label: l10n.e7LibraryServerName,
              controller: _name,
              hint: l10n.providerSetupNameHint,
              error: _shown(_nameError),
              enabled: _editable,
              disabledReason: _disabledReason(l10n),
              textInputAction: TextInputAction.next,
              onChanged: _edited,
              fieldKey: const ValueKey('provider-name'),
            ),
            _gap(context),
            KitField(
              label: l10n.providerSetupBaseURL,
              controller: _url,
              kind: KitFieldKind.url,
              hint: 'https://api.example.com/v1',
              helper: l10n.providerSetupBaseURLHelper,
              error: _shown(_urlError),
              enabled: _editable,
              disabledReason: _disabledReason(l10n),
              textInputAction: TextInputAction.next,
              onChanged: _edited,
              fieldKey: const ValueKey('provider-url'),
            ),
            _gap(context),
            KitField(
              label: l10n.providerSetupModels,
              controller: _models,
              kind: KitFieldKind.mono,
              maxLines: 5,
              hint: l10n.providerSetupModelsHint,
              helper: l10n.providerSetupModelsHelper,
              error: _shown(_modelsError),
              enabled: _editable,
              disabledReason: _disabledReason(l10n),
              onChanged: _edited,
              fieldKey: const ValueKey('provider-models'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _gap(BuildContext context) =>
      SizedBox(height: KitTokens.of(context).space5);

  String? _disabledReason(AppLocalizations l10n) => _editable
      ? null
      : _saved
      ? l10n.mcpSavedStatus
      : l10n.e7LibrarySavingConfiguration;

  /// Declared, and the server has to restart before it is listed. Once it
  /// restarted, the page says what to do next rather than looking unfinished.
  Widget _savedScreen(BuildContext context, AppLocalizations l10n) {
    final restartError = _restartError;
    final name = _name.text.trim();
    final id = _id.text.trim().toLowerCase();
    return KitScreen(
      width: KitScreenWidth.list,
      topBar: KitTopBar(
        title: l10n.providerSetupTitle,
        subtitle: widget.controller.profile?.name,
      ),
      bottom: KitActionBlock(
        primary: KitAction(
          key: const ValueKey('provider-setup-close'),
          label: l10n.isolatedTaskClose,
          icon: AppIconography.check,
          onPressed: _saving ? null : () => Navigator.pop(context, true),
          disabledReason: _saving ? l10n.providerSetupRestarting : null,
        ),
        secondary: _restarted
            ? null
            : KitAction(
                key: const ValueKey('provider-setup-restart'),
                label: l10n.workServerRestart,
                icon: AppIconography.retry,
                working: _saving,
                onPressed: _saving ? null : () => _restart(l10n),
              ),
      ),
      body: ListView(
        padding: KitScreen.padding(context),
        children: [
          KitStateView(
            key: const ValueKey('provider-setup-saved'),
            icon: _restarted
                ? AppIconography.checkCircle
                : restartError == null
                ? AppIconography.clock
                : AppIconography.warning,
            tone: _restarted
                ? AppStatusTone.ok
                : restartError == null
                ? AppStatusTone.neutral
                : AppStatusTone.failure,
            title: _restarted
                ? l10n.providerSetupSavedLoaded(name)
                : l10n.providerSetupSaved(name),
            body: _restarted
                ? l10n.providerSetupNextStep
                : restartError ?? l10n.providerSetupRestartBody,
            size: KitStateSize.page,
          ),
          if (_restarted) ...[
            _gap(context),
            KitText(
              l10n.providerSetupConnectHint(id),
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
            ),
          ],
        ],
      ),
    );
  }

  /// The one condition on the form, on the screen's status line.
  KitStatus? _status(AppLocalizations l10n) {
    final error = _saveError;
    if (error == null) return null;
    return KitStatus(
      id: 'provider-setup:save-failed',
      kind: KitStatusKind.info,
      icon: AppIconography.warning,
      tone: AppStatusTone.failure,
      message: l10n.providerSetupSaveFailed,
      supporting: error,
    );
  }

  KitAction _primary(AppLocalizations l10n) {
    final name = _name.text.trim();
    return KitAction(
      key: const ValueKey('provider-setup-save'),
      label: _saving
          ? l10n.e7LibrarySavingConfiguration
          : name.isEmpty
          ? l10n.providerSetupAdd
          : l10n.mcpSetupAddNamed(name),
      icon: AppIconography.add,
      working: _saving,
      onPressed: _save,
    );
  }
}
