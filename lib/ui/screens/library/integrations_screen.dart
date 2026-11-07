part of '../library_screen.dart';

/// Which of the two integration domains this screen shows: LLM provider
/// connections, MCP servers and their resources, or the legacy combined
/// surface.
enum IntegrationsMode { providers, mcp, all }

/// How long a brief confirmation stays at the top of the page.
@visibleForTesting
Duration integrationBriefNoticeDuration = const Duration(seconds: 3);

/// The page's sections that can fail to load, in page order.
enum _Section { providers, servers, resources }

/// Providers and MCP servers of the current server, built from kit parts
/// (screen-library-1). MCP › Add opens the add sheet (P2.4): the MCP
/// catalogue (P2.5) or the manual form.
class IntegrationsScreen extends StatefulWidget {
  final ConnectionController controller;
  final Future<bool> Function(Uri destination)? authorizationLauncher;
  final IntegrationsMode mode;

  /// Body only, for a tab of Settings › Tools: no top bar of its own, and the
  /// MCP "Add" is a button at the top of the list instead of a top-bar action.
  final bool embedded;

  /// Opens the connect flow of this provider as soon as the list loads, and
  /// goes back to the caller (the model picker) once a key was saved or the
  /// person cancelled. A browser sign-in stays on this page: it has steps
  /// to finish here.
  final String? connectProviderID;

  const IntegrationsScreen({
    super.key,
    required this.controller,
    this.authorizationLauncher,
    this.mode = IntegrationsMode.all,
    this.embedded = false,
    this.connectProviderID,
  });

  @override
  State<IntegrationsScreen> createState() => _IntegrationsScreenState();
}

/// A message about one act that just finished or failed, shown at the top
/// of the list until dismissed (KIT-34: a toast is only done-with-undo).
class _IntegrationsNotice {
  final String message;
  final AppStatusTone tone;
  const _IntegrationsNotice(this.message, this.tone);
}

class _IntegrationsScreenState extends State<IntegrationsScreen>
    with WidgetsBindingObserver {
  List<McpServerInfo>? _servers;
  List<McpResourceInfo>? _resources;
  List<IntegrationInfo>? _integrations;
  Object? _integrationsSource;

  /// What stopped each list from loading (the thrown object, never shown
  /// itself: [_sectionLoadState] words it), or null.
  Object? _serverError;
  String? _removalError;
  final Set<String> _removingMcp = {};
  int? _serversLocationRevision;
  ServerOperationsGateway? _serversRepository;
  Object? _serversSource;
  Object? _resourceError;
  Object? _integrationError;

  /// The server's connection is not ready (starting, reconnecting, the app
  /// coming back from the background): the lists wait for it and load by
  /// themselves once it is, instead of saying they could not load.
  bool _waitingForConnection = false;
  bool _loading = false;
  final Set<String> _busy = {};
  _PendingMcpOAuth? _pendingMcpOAuth;
  bool _finishingMcpOAuth = false;
  _PendingIntegrationOAuth? _pendingOAuth;
  bool _checkingOAuth = false;
  int _serverLoadGeneration = 0;
  int _resourceLoadGeneration = 0;
  int _integrationLoadGeneration = 0;

  /// Sign-ins whose explicit act is running, and what the server last said
  /// about each persisted one (keyed by [PendingAuthAttempt.key]).
  final Set<Object> _signInBusy = {};
  final Map<Object, IntegrationAuthState> _signInStatus = {};

  /// What the person needs to finish an automatic sign-in, kept for this
  /// launch only (never persisted): the device code and the page to open.
  final Map<Object, String> _signInCodes = {};
  final Map<Object, Uri> _signInUrls = {};

  /// Automatic sign-ins this screen is waiting on, and those that ended.
  final Set<Object> _signInWaiting = {};
  final Set<Object> _signInEnded = {};
  final Map<Timer, Completer<bool>> _waitPauses = {};
  Object? _reloadedForSource;
  final TextEditingController _providerSearch = TextEditingController();
  String _providerQuery = '';
  _IntegrationsNotice? _notice;

  AppLocalizations get _l10n => _libraryCopy(context);

  // Used only for equality checks; never render or log this sign-in snapshot.
  Object get _mcpSource {
    return _integrationSourceFor(widget.controller);
  }

  /// This server shares its providers and MCP servers with the app. Codex
  /// and Paseo do not: the page explains that instead of failing to load.

  /// Extensions in the part files cannot call [setState] themselves.
  void _set(VoidCallback fn) => setState(fn);
  bool get _catalogAvailable => widget.controller.capabilities.serverCatalog;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_onControllerChanged);
    final connectID = widget.connectProviderID;
    if (connectID == null) {
      unawaited(_load().then((_) => _attachAutoSignIns()));
    } else {
      unawaited(
        _load().then((_) {
          _attachAutoSignIns();
          return _autoConnect(connectID);
        }),
      );
    }
  }

  /// The connection was replaced (the app came back from the background and
  /// reconnected, or another server or project was opened), or it is ready
  /// after the page waited for it: the lists are read again for it, and the
  /// waits on automatic sign-ins are picked up again, instead of the page
  /// sitting on "could not load" until someone taps Try again. In every
  /// mode: the MCP tab of Settings › Tools never loads providers, and once
  /// waited on a provider list that never came (issue #124).
  void _onControllerChanged() {
    if (!mounted || !_catalogAvailable || _loading) return;
    final source = _mcpSource;
    final controller = widget.controller;
    final bool reload;
    final Object key;
    if (_waitingForConnection) {
      reload = controller.repository != null && !controller.lifecycleSuspended;
      // Once per connection state, so a connection that is still not
      // ready does not reload on every change.
      key = (source, controller.status);
    } else {
      reload =
          (_showProviders &&
              _integrations != null &&
              _integrationsSource != source) ||
          (_showMcp && _serversSource != null && _serversSource != source);
      key = source;
    }
    if (!reload || _reloadedForSource == key) return;
    _reloadedForSource = key;
    unawaited(_load().then((_) => _attachAutoSignIns()));
  }

  Future<void> _autoConnect(String id) async {
    if (!mounted) return;
    final integration = _integrations
        ?.where((candidate) => candidate.id == id)
        .firstOrNull;
    if (integration == null) return;
    final done = await _connectIntegration(integration);
    if (done && mounted) await Navigator.of(context).maybePop();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Coming back from the browser re-attaches the wait of an automatic
    // sign-in the app lost while it was in the background.
    if (state == AppLifecycleState.resumed) _attachAutoSignIns();
  }

  Timer? _noticeTimer;

  /// [brief] notices (a confirmation such as "Code copied") clear
  /// themselves after a few seconds instead of sitting at the top.
  void _say(
    String message, {
    AppStatusTone tone = AppStatusTone.ok,
    bool brief = false,
  }) {
    if (!mounted) return;
    _noticeTimer?.cancel();
    final notice = _IntegrationsNotice(message, tone);
    setState(() => _notice = notice);
    if (brief) {
      _noticeTimer = Timer(integrationBriefNoticeDuration, () {
        if (mounted && identical(_notice, notice)) {
          setState(() => _notice = null);
        }
      });
    }
  }

  Future<void> _load() async {
    if (!_catalogAvailable) return;
    _loading = true;
    try {
      await widget.controller.prunePendingIntegrationAuth();
      if (!mounted) return;
      final ServerOperationsGateway? repository;
      try {
        repository = await widget.controller.prepareActionRepository();
      } catch (error) {
        // The connection refused the work (a history move still running):
        // every list says so with its reason, not a stuck skeleton.
        if (mounted) {
          setState(() {
            _waitingForConnection = false;
            _serverError = error;
            _resourceError = error;
            _integrationError = error;
          });
        }
        return;
      }
      if (!mounted) return;
      if (repository == null) {
        setState(() => _waitingForConnection = true);
        return;
      }
      setState(() => _waitingForConnection = false);
      await Future.wait([
        if (_showMcp) _loadServers(repository),
        if (_showMcp) _loadResources(repository),
        if (_showProviders) _loadIntegrations(repository),
      ]);
    } finally {
      _loading = false;
      // The connection may have changed while this load ran (its results
      // were then dropped): read the lists again for the current one.
      if (mounted) _onControllerChanged();
    }
  }

  Future<void> _loadServers(ServerOperationsGateway repository) async {
    final source = _mcpSource;
    final controller = widget.controller;
    final profile = controller.profile;
    final location = controller.locationRevision;
    bool currentScope() =>
        mounted &&
        source == _mcpSource &&
        identical(widget.controller, controller) &&
        identical(controller.profile, profile) &&
        controller.locationRevision == location &&
        identical(controller.repository, repository);
    final generation = ++_serverLoadGeneration;
    setState(() => _serverError = null);
    try {
      final servers = await repository.listMcpServers();
      if (currentScope() && generation == _serverLoadGeneration) {
        setState(() {
          _servers = servers;
          _serversLocationRevision = location;
          _serversRepository = repository;
          _serversSource = source;
        });
      }
    } catch (error) {
      if (currentScope() && generation == _serverLoadGeneration) {
        setState(() => _serverError = error);
      }
    }
  }

  Future<void> _loadResources(ServerOperationsGateway repository) async {
    final source = _mcpSource;
    final generation = ++_resourceLoadGeneration;
    setState(() => _resourceError = null);
    try {
      final resources = await repository.listMcpResources();
      if (mounted &&
          source == _mcpSource &&
          generation == _resourceLoadGeneration) {
        setState(() => _resources = resources);
      }
    } catch (error) {
      if (mounted &&
          source == _mcpSource &&
          generation == _resourceLoadGeneration) {
        setState(() => _resourceError = error);
      }
    }
  }

  Future<void> _loadIntegrations(ServerOperationsGateway repository) async {
    if (!identical(repository, widget.controller.repository)) return;
    final source = _mcpSource;
    final generation = ++_integrationLoadGeneration;
    setState(() => _integrationError = null);
    try {
      final integrations = await repository.listIntegrations();
      if (mounted &&
          source == _mcpSource &&
          generation == _integrationLoadGeneration) {
        setState(() {
          _integrations = integrations;
          _integrationsSource = source;
        });
      }
    } catch (error) {
      if (mounted &&
          source == _mcpSource &&
          generation == _integrationLoadGeneration) {
        setState(() => _integrationError = error);
      }
    }
  }

  bool get _showMcp => widget.mode != IntegrationsMode.providers;
  bool get _showProviders => widget.mode != IntegrationsMode.mcp;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([
      widget.controller,
      widget.controller.profileDataChanges,
    ]),
    builder: (context, _) => _buildScreen(context),
  );

  /// Amber only where the person must act: finishing in the browser.
  /// Never red: a sign-in that ended or failed is neutral (the provider's
  /// own logo, secondary words) and its way forward is on the row.
  static KitTaskState? _signInMark(IntegrationAuthState state) =>
      switch (state) {
        IntegrationAuthState.pending => KitTaskState.needsYou,
        IntegrationAuthState.complete => KitTaskState.done,
        IntegrationAuthState.failed || IntegrationAuthState.expired => null,
      };

  /// The providers most people sign in to, in this order, right after the
  /// connected ones; the long tail follows alphabetically.
  static const popularProviders = [
    'openai',
    'anthropic',
    'google',
    'zai-coding-plan',
    'minimax',
    'openrouter',
    'github-copilot',
    'deepseek',
    'xai',
    'mistral',
    'groq',
  ];

  /// Connected providers first, then the popular ones, then alphabetical, so
  /// the ones a user can use or is likely to want sit at the top.
  static List<PresentedIntegration> _sortedIntegrations(
    List<IntegrationInfo> integrations,
  ) {
    final presented = presentIntegrations(integrations);
    int rank(PresentedIntegration p) {
      final i = popularProviders.indexOf(p.integration.id);
      return i < 0 ? popularProviders.length : i;
    }

    presented.sort((a, b) {
      if (a.connected != b.connected) return a.connected ? -1 : 1;
      final byRank = rank(a).compareTo(rank(b));
      if (byRank != 0) return byRank;
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });
    return presented;
  }

  @override
  void dispose() {
    _serverLoadGeneration++;
    _resourceLoadGeneration++;
    _integrationLoadGeneration++;
    _providerSearch.dispose();
    _noticeTimer?.cancel();
    widget.controller.removeListener(_onControllerChanged);
    for (final entry in _waitPauses.entries.toList()) {
      entry.key.cancel();
      if (!entry.value.isCompleted) entry.value.complete(false);
    }
    _waitPauses.clear();
    if (_pendingMcpOAuth case final pending?) {
      unawaited(_disposePendingMcpAuthentication(pending));
    }
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Future<void> _disposePendingMcpAuthentication(
    _PendingMcpOAuth pending,
  ) async {
    await pending.listener?.close();
    // No network action on navigation, particularly not through a new source.
    // This legacy protocol has no persisted attempt-recovery contract.
  }
}

/// A provider the server configured itself (an `opencode.json` entry) shown
/// as an integration: connected through the server, nothing to connect from
/// here, and no credential mobile could remove.
IntegrationInfo configuredProviderIntegration(
  CatalogProvider provider,
  AppLocalizations l10n,
) => IntegrationInfo(
  id: provider.id,
  name: provider.name.isEmpty ? provider.id : provider.name,
  methods: const [],
  connections: [
    IntegrationConnectionInfo(
      type: 'config',
      label: l10n.e7LibraryConfiguredOnTheServer,
    ),
  ],
  connectionCount: 1,
);

/// Case-insensitive match of a provider row against a search query: the
/// presented name, the wire id and its consolidated aliases (so "zhipu" finds
/// the Z.AI routes), the logo domain minus its TLD (so "aws" finds Bedrock),
/// and the ids and names of the catalog [models] the provider serves. An
/// empty query matches everything.
bool _providerMatchesSearch(
  PresentedIntegration presented,
  String query, {
  Iterable<CatalogModel> models = const [],
}) {
  final normalized = query.trim().toLowerCase();
  if (normalized.isEmpty) return true;
  final integration = presented.integration;
  final terms = <String>{
    presented.name,
    integration.id,
    integration.name,
    ..._providerSearchAliases(integration.id),
    for (final model in models) ...[model.id, model.name],
  };
  return terms.any((term) => term.toLowerCase().contains(normalized));
}

/// Wire ids OpenCode consolidates into one product family; every id in the
/// same presentation group is a search alias for the others.
const _consolidatedProviderIDs = [
  'zai',
  'zhipuai',
  'zai-coding-plan',
  'zhipuai-coding-plan',
];

Set<String> _providerSearchAliases(String providerID) {
  final presentation = presentProvider(providerID);
  final domainLabels = providerLogoDomain(providerID).split('.');
  return {
    presentation.groupID,
    presentation.name,
    ?presentation.route,
    for (final alias in _consolidatedProviderIDs)
      if (presentProvider(alias).groupID == presentation.groupID) alias,
    // Drop the TLD so the "<id>.com" fallback never matches "com" for all.
    if (domainLabels.length > 1)
      domainLabels.sublist(0, domainLabels.length - 1).join('.'),
  };
}
