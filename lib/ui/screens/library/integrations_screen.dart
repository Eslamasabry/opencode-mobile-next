part of '../library_screen.dart';

/// Which of the two integration domains this screen shows: LLM provider
/// connections, MCP servers and their resources, or the legacy combined
/// surface.
enum IntegrationsMode { providers, mcp, all }

/// The page's sections that can fail to load, in page order.
enum _Section { providers, servers, resources }

/// Providers and MCP servers of the current server, built from kit parts
/// (screen-library-1). MCP › Add opens the add sheet (P2.4): the MCP
/// catalogue (P2.5) or the manual form.
class IntegrationsScreen extends StatefulWidget {
  final ConnectionController controller;
  final Future<bool> Function(Uri destination)? authorizationLauncher;
  final IntegrationsMode mode;

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
  String? _serverError;
  String? _removalError;
  final Set<String> _removingMcp = {};
  int? _serversLocationRevision;
  ServerOperationsGateway? _serversRepository;
  Object? _serversSource;
  String? _resourceError;
  String? _integrationError;
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
    final connectID = widget.connectProviderID;
    if (connectID == null) {
      _load();
    } else {
      unawaited(_load().then((_) => _autoConnect(connectID)));
    }
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
    // Returning from a browser is not consent to poll or submit a code.
  }

  void _say(String message, {AppStatusTone tone = AppStatusTone.ok}) {
    if (!mounted) return;
    setState(() => _notice = _IntegrationsNotice(message, tone));
  }

  Future<void> _load() async {
    if (!_catalogAvailable) return;
    await widget.controller.prunePendingIntegrationAuth();
    if (!mounted) return;
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) {
      final message = _l10n.e7LibraryOpenCodeIsReconnectingTryAgainShortly;
      setState(() {
        _serverError = message;
        _resourceError = message;
        _integrationError = message;
      });
      return;
    }
    await Future.wait([
      if (_showMcp) _loadServers(repository),
      if (_showMcp) _loadResources(repository),
      if (_showProviders) _loadIntegrations(repository),
    ]);
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
    } catch (_) {
      if (currentScope() && generation == _serverLoadGeneration) {
        setState(() => _serverError = _l10n.mcpLoadFailed);
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
    } catch (_) {
      if (mounted &&
          source == _mcpSource &&
          generation == _resourceLoadGeneration) {
        setState(() => _resourceError = _l10n.mcpLoadFailed);
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
        setState(() => _integrationError = productErrorText(error));
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
  static KitTaskState _signInMark(IntegrationAuthState state) =>
      switch (state) {
        IntegrationAuthState.pending => KitTaskState.needsYou,
        IntegrationAuthState.complete => KitTaskState.done,
        IntegrationAuthState.failed ||
        IntegrationAuthState.expired => KitTaskState.failed,
      };

  /// Connected providers first, then alphabetical, so the ones a user can
  /// already use sit at the top of the list.
  static List<PresentedIntegration> _sortedIntegrations(
    List<IntegrationInfo> integrations,
  ) {
    final presented = presentIntegrations(integrations);
    presented.sort((a, b) {
      if (a.connected != b.connected) return a.connected ? -1 : 1;
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
