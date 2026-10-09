part of '../library_screen.dart';

// The rows, notices and questions of the Integrations screen, built from
// kit parts only (screen-library-1). Every action names what it acts on
// ("Disconnect Anthropic", "Sign in to github"); a row's rarer actions sit
// in its KitRowMenu (long-press, right-click, or a tap when the row has no
// act of its own); a gate this server lacks is a disabled menu item that
// says why instead of vanishing.

AppLocalizations _libraryCopy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The state word leads the supporting line (STATE-9). A state that needs
/// the person reads at label weight in the primary tone beside the row's
/// KitNeedsYou mark, which alone draws the needs-you colour (LOOK-4,
/// LOOK-24); a failure reads in the danger tone at label weight; an
/// ordinary state keeps the line's muted tone, so the list scans by weight
/// and colour.
TextSpan _stateWord(
  BuildContext context,
  String word, {
  bool last = false,
  KitTextTone tone = KitTextTone.secondary,
}) => TextSpan(
  text: last ? word : '$word · ',
  style: tone == KitTextTone.secondary
      ? null
      : KitText.styleOf(context, KitTextRole.label, tone: tone),
);

/// How urgent an MCP server's state is: what needs the person first, then
/// what failed, then what runs, then the rest (owner rule 2026-09-27: one
/// list ordered by urgency, no state sections).
int _mcpUrgency(String status) => switch (status) {
  'needs_auth' || 'needs_client_registration' => 0,
  'failed' => 1,
  'connected' => 2,
  _ => 3,
};

class _PendingMcpOAuth {
  final Object source;
  final McpServerInfo server;
  final McpAuthLaunch launch;
  final McpOAuthLoopbackListener? listener;

  const _PendingMcpOAuth({
    required this.source,
    required this.server,
    required this.launch,
    required this.listener,
  });
}

/// One MCP server: its worded state, the one act its state calls for in
/// words under the text ("Sign in to github"), and Disconnect and Remove in
/// the row menu. Without MCP sign-in on this server, a server waiting on it
/// says where the sign-in has to happen instead of offering a dead button.
class _McpServerRow extends StatelessWidget {
  final McpServerInfo server;
  final String statusLabel;
  final bool busy;
  final bool authorizing;
  final bool authGated;
  final bool actionsAllowed;

  /// Remove is offered: the server supports runtime removal and the list
  /// is current. Otherwise the menu leaves it out (no dead item).
  final bool canRemove;
  final VoidCallback onAct;
  final VoidCallback onRemove;

  const _McpServerRow({
    required this.server,
    required this.statusLabel,
    required this.busy,
    required this.authorizing,
    required this.authGated,
    required this.actionsAllowed,
    required this.canRemove,
    required this.onAct,
    required this.onRemove,
  });

  IconData get _icon => switch (server.status) {
    'connected' => AppIconography.network,
    'failed' => AppIconography.error,
    'needs_auth' || 'needs_client_registration' => AppIconography.login,
    _ => AppIconography.unlink,
  };

  bool get _needsYou =>
      server.status == 'needs_auth' ||
      server.status == 'needs_client_registration';

  bool get _failed => server.status == 'failed';

  /// The yellow needs-you mark for a server waiting on sign-in, a
  /// failure-toned icon for one that failed, the plain icon otherwise.
  Widget _leading(BuildContext context) {
    if (_needsYou) return KitNeedsYou.mark();
    if (_failed) {
      return KitRow.icon(
        context,
        _icon,
        color: KitTokens.toneColor(
          KitTokens.of(context).roles,
          AppStatusTone.failure,
        ),
      );
    }
    return KitRow.icon(context, _icon);
  }

  /// The act the state calls for, or null when there is none to offer
  /// here (connected: Disconnect lives in the menu).
  String? _actLabel(AppLocalizations l10n) {
    if (authorizing || authGated) return null;
    return switch (server.status) {
      'connected' => null,
      'needs_auth' ||
      'needs_client_registration' => l10n.integrationsMcpSignIn(server.name),
      'failed' => l10n.integrationsMcpReconnect(server.name),
      _ => l10n.e7LibraryConnect2(server.name),
    };
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _libraryCopy(context);
    final act = _actLabel(l10n);
    final connected = server.status == 'connected';
    final canAct = actionsAllowed && !busy;
    return KitRow(
      key: ValueKey('mcp-server-${server.name}'),
      leading: busy
          ? SizedBox.square(
              dimension: KitTokens.of(context).iconTileSize,
              child: const Center(
                child: KitTaskMark(state: KitTaskState.working),
              ),
            )
          : _leading(context),
      title: server.name,
      supportingKey: authGated ? const ValueKey('gated-mcp-oauth') : null,
      supporting: TextSpan(
        children: [
          _stateWord(
            context,
            authorizing ? l10n.e7LibraryAuthorizing : statusLabel,
            last: !authGated,
            tone: authorizing
                ? KitTextTone.secondary
                : _needsYou
                ? KitTextTone.primary
                : _failed
                ? KitTextTone.danger
                : KitTextTone.secondary,
          ),
          if (authGated) TextSpan(text: l10n.integrationsMcpSignInOnServer),
        ],
      ),
      supportingMaxLines: 3,
      below: (act == null && (server.error?.trim().isEmpty ?? true))
          ? null
          : KitActionBlock(
              tertiary: [
                if (act != null)
                  KitAction(
                    key: ValueKey('mcp-action-${server.name}'),
                    label: act,
                    onPressed: canAct ? onAct : null,
                  ),
                // What the server said, in a sheet: plain words lead on the
                // row, the technical text waits here.
                if (server.error?.trim().isNotEmpty ?? false)
                  KitAction(
                    key: ValueKey('mcp-error-${server.name}'),
                    label: l10n.integrationsMcpDetails(server.name),
                    onPressed: () => unawaited(
                      showKitTechnicalDetails(
                        context,
                        title: l10n.integrationsMcpDetails(server.name),
                        text: server.error!.trim(),
                      ),
                    ),
                  ),
              ],
            ),
      menuLabel: l10n.integrationsMcpActions(server.name),
      menu: busy
          ? const []
          : [
              if (connected)
                KitMenuItem(
                  key: ValueKey('mcp-disconnect-${server.name}'),
                  label: l10n.integrationsDisconnectNamed(server.name),
                  icon: AppIconography.unlink,
                  enabled: actionsAllowed,
                  disabledReason: actionsAllowed ? null : l10n.mcpScopeChanged,
                  onSelected: onAct,
                ),
              if (canRemove && !authorizing)
                KitMenuItem(
                  key: ValueKey('mcp-remove-${server.name}'),
                  label: l10n.integrationsMcpRemoveUntilRestart(server.name),
                  icon: AppIconography.delete,
                  onSelected: onRemove,
                ),
            ],
    );
  }
}

/// A browser sign-in for an MCP server in flight: what the phone is doing,
/// and the manual way in (paste the code) or out (cancel).
class _PendingMcpOAuthNotice extends StatelessWidget {
  final _PendingMcpOAuth pending;
  final bool busy;
  final VoidCallback onEnterCode;
  final VoidCallback onCancel;

  const _PendingMcpOAuthNotice({
    required this.pending,
    required this.busy,
    required this.onEnterCode,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = _libraryCopy(context);
    return KitNotice(
      key: const ValueKey('pending-mcp-oauth'),
      tone: AppStatusTone.progress,
      icon: AppIconography.login,
      title: l10n.integrationsMcpSigningIn(pending.server.name),
      message: pending.listener == null
          ? l10n.e7LibraryAutomaticCallbackCaptureIsUnavailablePasteThe
          : l10n.e7LibraryThePhoneIsSecurelyListeningForThis,
      actions: [
        KitAction(
          key: const ValueKey('enter-mcp-oauth-code'),
          label: l10n.pendingAuthEnterCode,
          working: busy,
          onPressed: busy ? null : onEnterCode,
        ),
        KitAction(
          key: const ValueKey('cancel-mcp-oauth'),
          label: l10n.integrationsCancelSignIn,
          onPressed: busy ? null : onCancel,
        ),
      ],
    );
  }
}

/// One model provider: logo, name, the worded state with its model count
/// and how it connects. An unconnected provider opens its connect flow on
/// tap; a connected one opens its menu (accounts, server sign-in,
/// Disconnect); a provider the server manages says so and has no act.
class _ProviderRow extends StatelessWidget {
  final PresentedIntegration presented;
  final String subtitle;

  /// Models the catalog lists for this provider, or null before the
  /// catalog loads.
  final int? modelCount;
  final bool busy;
  final bool commandAuthSupported;
  final bool credentialsSupported;

  /// Signed in, but the server has not loaded the provider (or loaded and
  /// still cannot use it): its models are not usable, so it is not
  /// "Connected" and shows no model count.
  final bool notLoaded;

  /// A reload already ran and the provider stayed unloaded.
  final bool notUsable;
  final VoidCallback onAddKey;
  final VoidCallback onConnect;
  final VoidCallback onDisconnect;
  final VoidCallback onManageAccounts;
  final VoidCallback onServerSignIn;

  const _ProviderRow({
    required this.presented,
    required this.subtitle,
    required this.busy,
    required this.onConnect,
    required this.onDisconnect,
    required this.onManageAccounts,
    required this.onServerSignIn,
    required this.onAddKey,
    this.modelCount,
    this.notLoaded = false,
    this.notUsable = false,
    this.commandAuthSupported = false,
    this.credentialsSupported = false,
  });

  IntegrationInfo get _integration => presented.integration;

  bool get _hasKeyMethod =>
      _integration.methods.any((method) => method.type == 'key');

  bool get _hasCommand => _integration.methods.any(
    (method) => method.type == 'command' && method.id != null,
  );

  bool get _canConnect =>
      !presented.connected &&
      _integration.methods.any(
        (method) =>
            method.type == 'key' ||
            method.type == 'oauth' ||
            (commandAuthSupported &&
                method.type == 'command' &&
                method.id != null),
      );

  /// A stored credential (or a legacy OAuth connection) mobile can remove.
  bool get _canDisconnect =>
      _integration.credentialIDs.isNotEmpty ||
      (presented.connected &&
          _integration.methods.any((method) => method.type == 'oauth'));

  /// The server environment variables this provider's key is read from:
  /// technical words, shown only on the Details sheet (emulator QA B10).
  List<String> get _environmentNames => <String>{
    for (final method in _integration.methods)
      if (method.type == 'env') ...method.environmentNames,
    for (final connection in _integration.connections)
      if (connection.type == 'env') connection.label,
  }.where((name) => name.trim().isNotEmpty).toList();

  Future<void> _showDetails(BuildContext context, String name) {
    final l10n = _libraryCopy(context);
    return showKitTechnicalDetails(
      context,
      title: l10n.integrationsProviderDetails(name),
      text: '',
      sheetKey: ValueKey('provider-details-sheet-${_integration.id}'),
      values: [
        for (final variable in _environmentNames)
          KitTechnicalValue(l10n.integrationsEnvironmentVariable, variable),
      ],
      notes: [if (!presented.connected) l10n.integrationsEnvironmentNote(name)],
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _libraryCopy(context);
    final name = presented.name;
    final count = notLoaded ? null : modelCount;
    final serverManaged = presented.connected && !_canDisconnect;
    final word = busy
        ? l10n.e7LibraryUpdating
        : presented.connected && notLoaded
        ? l10n.integrationsSignedInUnusable
        : presented.connected
        ? l10n.e7LibraryConnected
        : l10n.e7LibraryNotConnected;
    final detail = notLoaded && _hasKeyMethod
        ? l10n.integrationsConnectWithKey
        : serverManaged
        ? (_integration.hasEnvironmentConnection
              ? l10n.e7LibraryServerEnvironment
              : l10n.e7LibraryServerManaged)
        : subtitle.trim();
    final menu = <KitMenuItem>[
      if (_canConnect)
        KitMenuItem(
          label: l10n.e7LibraryConnect2(name),
          icon: AppIconography.login,
          onSelected: onConnect,
        ),
      if (notLoaded && _hasKeyMethod)
        KitMenuItem(
          key: ValueKey('add-key-${_integration.id}'),
          label: l10n.integrationsConnectWithKey,
          icon: AppIconography.login,
          onSelected: onAddKey,
        ),
      if (_hasCommand)
        KitMenuItem(
          key: ValueKey('server-sign-in-${_integration.id}'),
          label: l10n.integrationsServerSignIn(name),
          icon: AppIconography.terminal,
          enabled: commandAuthSupported,
          disabledReason: commandAuthSupported
              ? null
              : l10n.integrationsServerSignInUnavailable,
          onSelected: onServerSignIn,
        ),
      if (credentialsSupported)
        KitMenuItem(
          key: ValueKey('manage-accounts-${_integration.id}'),
          label: l10n.integrationsManageAccounts(name),
          icon: AppIconography.manageAccount,
          onSelected: onManageAccounts,
        ),
      if (_environmentNames.isNotEmpty)
        KitMenuItem(
          key: ValueKey('provider-details-${_integration.id}'),
          label: l10n.kitDetails,
          icon: AppIconography.info,
          onSelected: () => unawaited(_showDetails(context, name)),
        ),
      if (_canDisconnect)
        KitMenuItem(
          key: ValueKey('disconnect-provider-${_integration.id}'),
          label: l10n.integrationsDisconnectNamed(name),
          icon: AppIconography.unlink,
          destructive: true,
          onSelected: onDisconnect,
        ),
    ];
    return KitRow(
      key: _canConnect
          ? ValueKey('connect-provider-${_integration.id}')
          : ValueKey('provider-${_integration.id}'),
      leading: ProviderLogo(_integration.id),
      title: name,
      supporting: TextSpan(
        children: [
          _stateWord(
            context,
            word,
            last: (count == null || count == 0) && detail.isEmpty,
          ),
          if (count != null && count > 0)
            TextSpan(text: l10n.integrationsModelCount(count)),
          if (count != null && count > 0 && detail.isNotEmpty)
            const TextSpan(text: ' · '),
          if (detail.isNotEmpty) TextSpan(text: detail),
        ],
      ),
      supportingMaxLines: 2,
      trailing: busy
          ? const KitTaskMark(state: KitTaskState.working)
          : _canConnect || (notLoaded && _hasKeyMethod)
          ? const KitChevron()
          : null,
      onTap: busy
          ? null
          : notLoaded && _hasKeyMethod
          ? onAddKey
          : !_canConnect
          ? null
          : onConnect,
      menuLabel: l10n.integrationsProviderActions(name),
      menu: busy ? const [] : menu,
    );
  }
}

class _PendingIntegrationOAuth {
  final Object source;
  final String integrationID;
  final String integrationName;
  final IntegrationAuthLaunch launch;
  final IntegrationAuthStatus? status;

  const _PendingIntegrationOAuth({
    required this.source,
    required this.integrationID,
    required this.integrationName,
    required this.launch,
    this.status,
  });

  _PendingIntegrationOAuth copyWith({required IntegrationAuthStatus status}) =>
      _PendingIntegrationOAuth(
        source: source,
        integrationID: integrationID,
        integrationName: integrationName,
        launch: launch,
        status: status,
      );
}

/// A provider sign-in folded into the provider list (owner rule
/// 2026-09-27: no state sections): its mark, the provider's name and its
/// worded state. Only a sign-in the person must finish in the browser
/// ("Sign-in waiting") takes the needs-you mark (visual language: amber is
/// "needs you" only); a failed or expired one takes the failed mark, a
/// finished one the done mark, and a start the server never confirmed
/// ("Sign-in may not have started") stays neutral — the provider's logo —
/// with its way forward after the word ([next]). A tap opens
/// [_showSignInSheet]; the same acts are the row's menu.
class _SignInRow extends StatelessWidget {
  final String integrationID;
  final String name;
  final String word;

  /// The row's mark: [KitTaskState.needsYou] only while the person must
  /// finish in the browser; null is neutral (the provider's logo).
  final KitTaskState? mark;

  /// The way forward after the word, for a neutral row.
  final String? next;
  final bool busy;
  final VoidCallback onOpen;
  final List<KitMenuItem> menu;
  final Key? rowKey;

  const _SignInRow({
    required this.integrationID,
    required this.name,
    required this.word,
    required this.mark,
    this.next,
    required this.busy,
    required this.onOpen,
    required this.menu,
    this.rowKey,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = _libraryCopy(context);
    return KitRow(
      key: rowKey ?? ValueKey('pending-auth-$integrationID'),
      leading: switch (mark) {
        KitTaskState.needsYou => KitNeedsYou.mark(),
        final KitTaskState state => KitTaskMark(state: state),
        null => ProviderLogo(integrationID),
      },
      title: name,
      supporting: TextSpan(
        children: [
          _stateWord(
            context,
            word,
            last: next == null,
            tone: switch (mark) {
              KitTaskState.needsYou => KitTextTone.primary,
              KitTaskState.failed => KitTextTone.danger,
              _ => KitTextTone.secondary,
            },
          ),
          if (next case final next?) TextSpan(text: next),
        ],
      ),
      supportingMaxLines: next == null ? 1 : 2,
      trailing: busy
          ? const KitTaskMark(state: KitTaskState.working)
          : const KitChevron(),
      onTap: busy ? null : onOpen,
      menuLabel: l10n.integrationsSignInActions(name),
      menu: busy ? const [] : menu,
    );
  }
}

/// What the person chose in a sign-in's sheet or menu.
enum _SignInChoice { finish, enterCode, cancel, forget }

/// One sign-in's sheet: the provider as the title, its state under it, one
/// line on what to do, and its acts: one primary ("Finish signing in to
/// Cloud") and the rest as tertiary actions named for what they act on.
/// Returns the choice, or null when closed.
Future<_SignInChoice?> _showSignInSheet(
  BuildContext context, {
  required String name,
  required String word,
  required String message,
  List<String> notes = const [],
  required List<(_SignInChoice, KitAction)> actions,
  _SignInChoice? primary,
}) {
  final navigator = Navigator.of(context);
  KitAction bind(_SignInChoice choice, KitAction action) => KitAction(
    key: action.key,
    label: action.label,
    destructive: action.destructive,
    onPressed: () => navigator.pop(choice),
  );
  final primaryAction = [
    for (final (choice, action) in actions)
      if (choice == primary) bind(choice, action),
  ].firstOrNull;
  return showKitSheet<_SignInChoice>(
    context,
    title: name,
    subtitle: word,
    icon: AppIconography.login,
    sheetKey: const ValueKey('sign-in-sheet'),
    primary: primaryAction,
    tertiary: [
      for (final (choice, action) in actions)
        if (choice != primary) bind(choice, action),
    ],
    body: (context) {
      final tokens = KitTokens.of(context);
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitText(message, tone: KitTextTone.secondary),
          for (final note in notes) ...[
            SizedBox(height: tokens.space2),
            KitText(note, role: KitTextRole.secondary),
          ],
        ],
      );
    },
  );
}

/// The one "Finish signing in" dialog for providers and MCP servers
/// (P3.11: the two code dialogs became this one). The code is a secret:
/// obscured, with reveal and paste, never prefilled, never kept in a draft
/// and never echoed. [parse] turns the pasted text into the code or throws
/// a [ProductException] whose words appear under the field, keeping the
/// dialog open. Returns the parsed code, or null on cancel.
Future<String?> _showFinishSignInDialog(
  BuildContext context, {
  required String label,
  required String helper,
  required String Function(String raw) parse,
  Key? fieldKey,
  Key? confirmKey,
}) async {
  final l10n = _libraryCopy(context);
  String? code;
  final raw = await showKitInputDialog(
    context,
    title: l10n.integrationsFinishSignInTitle,
    label: label,
    helper: helper,
    kind: KitFieldKind.secret,
    confirmLabel: l10n.integrationsFinishSignInAction,
    cancelLabel: l10n.workCancel,
    fieldKey: fieldKey,
    confirmKey: confirmKey,
    validate: (value) =>
        value.trim().isEmpty ? l10n.integrationsFinishSignInEmpty : null,
    onSubmit: (value) async {
      try {
        code = parse(value);
        return null;
      } catch (error) {
        return productErrorText(error, l10n: l10n);
      }
    },
  );
  return raw == null ? null : code;
}

bool _isPromptVisible(Map<String, dynamic> prompt, Map<String, String> values) {
  final when = prompt['when'];
  if (when is! Map) return true;
  final key = when['key']?.toString();
  final expected = when['value']?.toString();
  final operation = when['op']?.toString();
  if (key == null || expected == null) return true;
  return switch (operation) {
    'eq' => values[key] == expected,
    'neq' => values[key] != expected,
    _ => true,
  };
}

bool _isPromptRequired(Map<String, dynamic> prompt) =>
    prompt['required'] != false;

/// The server-defined questions a provider's browser sign-in needs first,
/// as a sheet: a labelled [KitField] per text prompt and a
/// [KitChoiceList] per choice, answered in place. The primary names its
/// result ("Open Groq sign-in"). Returns the visible answers, or null.
Future<Map<String, String>?> _showOAuthInputsSheet(
  BuildContext context, {
  required IntegrationMethodInfo method,
  required String providerName,
}) {
  final l10n = _libraryCopy(context);
  final form = GlobalKey<_OAuthInputsFormState>();
  return showKitSheet<Map<String, String>>(
    context,
    title: method.label,
    icon: AppIconography.login,
    sheetKey: const ValueKey('oauth-inputs-sheet'),
    body: (_) => _OAuthInputsForm(key: form, method: method),
    primary: KitAction(
      key: const ValueKey('oauth-inputs-continue'),
      label: l10n.integrationsOAuthInputsContinue(providerName),
      onPressed: () => form.currentState?.submit(),
    ),
  );
}

class _OAuthInputsForm extends StatefulWidget {
  final IntegrationMethodInfo method;
  const _OAuthInputsForm({super.key, required this.method});

  @override
  State<_OAuthInputsForm> createState() => _OAuthInputsFormState();
}

class _OAuthInputsFormState extends State<_OAuthInputsForm> {
  final _textControllers = <String, TextEditingController>{};
  final _values = <String, String>{};
  final _errors = <String, String>{};

  @override
  void initState() {
    super.initState();
    for (final prompt in widget.method.prompts) {
      if (prompt['type'] == 'text') {
        _textControllers[prompt['key'].toString()] = TextEditingController();
      }
    }
  }

  /// Checks the visible required answers; on success pops the sheet with
  /// them, otherwise shows each reason under its question.
  void submit() {
    final l10n = _libraryCopy(context);
    final errors = <String, String>{};
    final visibleValues = <String, String>{};
    for (final prompt in widget.method.prompts) {
      if (!_isPromptVisible(prompt, _values)) continue;
      final key = prompt['key'].toString();
      if (prompt['type'] == 'text') {
        final value = _textControllers[key]?.text ?? '';
        if (_isPromptRequired(prompt) && value.trim().isEmpty) {
          errors[key] = l10n.e7LibraryEnterAValue;
        }
        visibleValues[key] = value;
      } else if (_values[key] case final value?) {
        visibleValues[key] = value;
      } else if (_isPromptRequired(prompt)) {
        errors[key] = l10n.e7LibrarySelectAnOption;
      }
    }
    if (errors.isNotEmpty) {
      setState(() {
        _errors
          ..clear()
          ..addAll(errors);
      });
      return;
    }
    Navigator.of(context).pop(visibleValues);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final prompts = [
      for (final prompt in widget.method.prompts)
        if (_isPromptVisible(prompt, _values)) prompt,
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final (index, prompt) in prompts.indexed) ...[
          if (index > 0) SizedBox(height: tokens.space4),
          _prompt(context, prompt),
        ],
      ],
    );
  }

  Widget _prompt(BuildContext context, Map<String, dynamic> prompt) {
    final key = prompt['key'].toString();
    final label = prompt['message']?.toString() ?? key;
    final error = _errors[key];
    if (prompt['type'] == 'select') {
      final options = [
        for (final option in (prompt['options'] as List? ?? const []))
          if (option is Map && option.containsKey('value')) option,
      ];
      return Column(
        key: ValueKey('oauth-prompt-$key'),
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitText(label, role: KitTextRole.label),
          SizedBox(height: KitTokens.of(context).space2),
          KitChoiceList<String>.single(
            semanticsLabel: label,
            actsOnTap: false,
            selected: _values[key],
            choices: [
              for (final option in options)
                KitChoice<String>(
                  key: ValueKey('oauth-prompt-$key-${option['value']}'),
                  value: option['value'].toString(),
                  title: option['label']?.toString() ?? '',
                ),
            ],
            onSelected: (value) => setState(() {
              _values[key] = value;
              _errors.remove(key);
            }),
          ),
          if (error != null)
            Semantics(
              liveRegion: true,
              child: KitText(
                error,
                role: KitTextRole.secondary,
                tone: KitTextTone.primary,
              ),
            ),
        ],
      );
    }
    return KitField(
      label: label,
      hint: prompt['placeholder']?.toString(),
      controller: _textControllers[key],
      error: error,
      fieldKey: ValueKey('oauth-prompt-$key'),
      onChanged: (value) => setState(() {
        _values[key] = value;
        _errors.remove(key);
      }),
    );
  }

  @override
  void dispose() {
    for (final controller in _textControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }
}

/// Parses a server-provided authorization URL using the app's external-launch
/// policy. Authentication is allowed only on HTTPS origins without embedded
/// credentials.
Uri parseAuthorizationUrl(String value, {AppLocalizations? l10n}) {
  final uri = Uri.tryParse(value.trim());
  if (uri == null ||
      uri.scheme.toLowerCase() != 'https' ||
      uri.host.trim().isEmpty ||
      uri.userInfo.isNotEmpty) {
    throw ProductException(
      (l10n ?? lookupAppLocalizations(const Locale('en')))
          .e7LibraryTheServerReturnedAnUnsafeAuthorizationLink,
    );
  }
  return uri;
}

/// Accept either the short code shown by a provider or the callback URL a
/// headless browser leaves in its address bar.
String providerOAuthCompletionCode(String value) {
  final trimmed = value.trim();
  final fromUrl = Uri.tryParse(trimmed)?.queryParameters['code']?.trim();
  return fromUrl?.isNotEmpty == true ? fromUrl! : trimmed;
}
