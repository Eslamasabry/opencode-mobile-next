import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../domain/mcp_catalog.dart';
import '../../domain/server_gateway.dart';
import '../../domain/setup_registry.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/mcp_catalog_host.dart';
import '../../state/profiles.dart';
import '../../state/setup_registry_store.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import 'mcp_setup_screen.dart';
import 'this_phone_screen.dart' show openThisPhone;

/// The ways to add an MCP server (map `mcp-add-sheet`, P2.4).
enum McpAddPath { catalog, manual }

/// Whether this server can take a new MCP server from the app at all: a
/// saved configuration write or an add for the current location. Both the
/// catalogue and the manual form need one; gated on capabilities, never on
/// the server's flavour.
bool mcpAddSupported(ServerCapabilities capabilities) =>
    capabilities.serverCatalog &&
    (capabilities.mcpConfigWrites || capabilities.mcpRuntimeAdds);

/// MCP › Add (P2.4): browse the catalogue, or enter a server by hand (the
/// existing form). The setup assistant is not offered: it is not built yet
/// (owner, 2026-09-28), and a row that cannot be used would be a dead end.
/// Returns the chosen path, or null when dismissed.
Future<McpAddPath?> showMcpAddSheet(
  BuildContext context, {
  required ServerCapabilities capabilities,
}) {
  final l10n = AppLocalizations.of(context);
  final supported = mcpAddSupported(capabilities);
  return showKitChoiceSheet<McpAddPath>(
    context,
    title: l10n.e7LibraryAddAnMCPServer,
    sheetKey: const ValueKey('mcp-add-sheet'),
    choices: [
      KitChoice(
        key: const ValueKey('mcp-add-catalog'),
        value: McpAddPath.catalog,
        leading: KitRow.icon(context, AppIconography.search),
        title: l10n.mcpAddBrowseTitle,
        // Unavailable: the reason alone is the supporting line.
        supporting: supported ? l10n.mcpAddBrowseDetail : null,
        enabled: supported,
        disabledReason: supported ? null : l10n.mcpAddBrowseNone,
      ),
      KitChoice(
        key: const ValueKey('mcp-add-manual'),
        value: McpAddPath.manual,
        leading: KitRow.icon(context, AppIconography.edit),
        title: l10n.mcpAddManualTitle,
        supporting: l10n.mcpAddManualDetail,
      ),
    ],
  );
}

/// What turning on a server that needs Node offers on a phone host.
enum _NodeChoice { addNode, continueToForm }

/// The MCP catalogue (map `mcp-catalog`, P2.5): servers from the public MCP
/// registry (registry.modelcontextprotocol.io) as switches, one list with
/// the ones on this server first.
///
/// Turning one on opens the manual form ([McpSetupScreen]) filled in from
/// the listing, so it is saved by the same checks and the same Save as a
/// server typed by hand, and the person sees the address or command first.
/// Turning one off removes it the way the MCP page does (same question,
/// same call), where the server supports removal; otherwise the row says
/// why it stays on.
///
/// Registry text is untrusted: titles and descriptions are plain text, no
/// link in them opens, and nothing from the registry runs without the form.
/// Loading the list is the person's choice, remembered per server
/// (`oc.setupRegistry.<profileId>`), and can be stopped from the menu.
///
/// States: not supported by this server; consent; loading; list; list with
/// a failed refresh; nothing found; registry unreachable; this server's
/// MCP list unreadable; a row adding or removing.
class McpCatalogScreen extends StatefulWidget {
  const McpCatalogScreen({
    super.key,
    required this.controller,
    this.client,
    this.onThisPhone = isPhoneHostedProfile,
    this.openPhoneTools,
  });

  final ConnectionController controller;

  /// The registry client; tests pass one over a fake transport.
  final SetupRegistryClient? client;

  /// Whether a profile is OpenCode on this phone.
  final bool Function(ServerProfile? profile) onThisPhone;

  /// Opens phone setup's Add tools; This phone by default.
  final Future<void> Function(BuildContext context)? openPhoneTools;

  @override
  State<McpCatalogScreen> createState() => _McpCatalogScreenState();
}

class _McpCatalogScreenState extends State<McpCatalogScreen> {
  SetupRegistryStore? _store;
  StreamSubscription<SetupRegistrySnapshot>? _changes;
  SetupRegistrySnapshot? _registry;
  String? _profileId;
  int? _location;
  Set<String>? _installed;
  bool _inventoryFailed = false;
  int _inventoryGeneration = 0;
  final Set<String> _busy = {};
  String? _notice;
  final _search = TextEditingController();
  Timer? _searchDebounce;

  AppLocalizations get _l10n => AppLocalizations.of(context);
  ConnectionController get _controller => widget.controller;
  bool get _supported => mcpAddSupported(_controller.capabilities);

  @override
  void initState() {
    super.initState();
    _controller.addListener(_connectionChanged);
    unawaited(_open());
  }

  @override
  void dispose() {
    _controller.removeListener(_connectionChanged);
    _searchDebounce?.cancel();
    _search.dispose();
    unawaited(_closeStore());
    super.dispose();
  }

  /// A new server gets its own consent and list; a new project on the same
  /// server only needs its MCP list read again.
  void _connectionChanged() {
    if (!mounted) return;
    if (_controller.profile?.id != _profileId) {
      unawaited(_open());
    } else if (_controller.locationRevision != _location) {
      unawaited(_loadInventory());
    }
  }

  Future<void> _closeStore() async {
    final store = _store;
    _store = null;
    await _changes?.cancel();
    _changes = null;
    await store?.dispose();
  }

  Future<void> _open() async {
    final profileId = _controller.profile?.id;
    _profileId = profileId;
    await _closeStore();
    if (!mounted || profileId == null || profileId.isEmpty) return;
    setState(() {
      _registry = null;
      _installed = null;
      _search.clear();
    });
    final SetupRegistryStore store;
    try {
      final prefs = await SharedPreferences.getInstance();
      if (!mounted || _profileId != profileId) return;
      store = SetupRegistryStore(
        prefs,
        profileId: profileId,
        client: widget.client,
      );
    } catch (_) {
      return;
    }
    _store = store;
    _changes = store.changes.listen((snapshot) {
      if (mounted && identical(_store, store)) {
        setState(() => _registry = snapshot);
      }
    });
    await store.load();
    if (!mounted || !identical(_store, store)) return;
    setState(() => _registry = store.snapshot);
    unawaited(_loadInventory());
    // Opening the catalogue after saying yes is asking for the list.
    if (store.snapshot.optedIn) unawaited(store.refresh());
  }

  /// The names of the MCP servers this server has now, for the switches.
  Future<void> _loadInventory() async {
    if (!_supported) return;
    final generation = ++_inventoryGeneration;
    final location = _controller.locationRevision;
    _location = location;
    setState(() {
      _installed = null;
      _inventoryFailed = false;
    });
    try {
      final repository = await _controller.prepareActionRepository();
      if (repository == null) throw StateError('not connected');
      final servers = await repository.listMcpServers();
      if (!mounted || generation != _inventoryGeneration) return;
      setState(() => _installed = {for (final s in servers) s.name});
    } catch (_) {
      if (!mounted || generation != _inventoryGeneration) return;
      setState(() => _inventoryFailed = true);
    }
  }

  Future<void> _optIn() async {
    final store = _store;
    if (store == null) return;
    try {
      await store.setOptIn(true);
    } catch (_) {
      return;
    }
    if (mounted && identical(_store, store)) await store.refresh();
  }

  Future<void> _stopUsingRegistry() async {
    final store = _store;
    if (store == null) return;
    _search.clear();
    try {
      await store.clear();
    } catch (_) {
      if (mounted) setState(() => _notice = _l10n.mcpCatalogForgetFailed);
    }
  }

  Future<void> _refresh() async {
    await Future.wait([
      _loadInventory(),
      if (_store case final store? when store.snapshot.optedIn)
        store.refresh(query: _search.text),
    ]);
  }

  void _searchChanged(String value) {
    _searchDebounce?.cancel();
    final store = _store;
    if (store == null) return;
    if (value.trim().isEmpty) {
      store.showSaved();
      return;
    }
    _searchDebounce = Timer(const Duration(milliseconds: 500), () {
      if (mounted && identical(_store, store)) {
        unawaited(store.refresh(query: value));
      }
    });
  }

  void _searchSubmitted(String value) {
    _searchDebounce?.cancel();
    final store = _store;
    if (store == null) return;
    if (value.trim().isEmpty) {
      store.showSaved();
    } else {
      unawaited(store.refresh(query: value));
    }
  }

  Future<void> _openManual() => Navigator.of(context)
      .push<bool>(
        KitPageRoute<bool>(
          builder: (_) => McpSetupScreen(controller: _controller),
        ),
      )
      .then((saved) {
        if (saved == true && mounted) unawaited(_loadInventory());
      });

  /// On: the listing opens the manual form, filled in. The form's Save is
  /// the confirmation, exactly as for a server entered by hand.
  Future<void> _turnOn(McpCatalogItem item) async {
    final draft = item.draft;
    if (draft == null || _busy.contains(item.serverName)) return;
    final phone = widget.onThisPhone(_controller.profile);
    if (item.runtime == McpCatalogRuntime.node && phone) {
      final choice = await _askNode(item);
      if (!mounted || choice == null) return;
      if (choice == _NodeChoice.addNode) {
        await (widget.openPhoneTools ?? openThisPhone)(context);
        return;
      }
    }
    if (!mounted) return;
    setState(() => _busy.add(item.serverName));
    try {
      final saved = await Navigator.of(context).push<bool>(
        KitPageRoute<bool>(
          builder: (_) => McpSetupScreen(
            controller: _controller,
            prefill: draft,
            prefillSource: item.title,
          ),
        ),
      );
      if (saved == true && mounted) await _loadInventory();
    } finally {
      if (mounted) setState(() => _busy.remove(item.serverName));
    }
  }

  Future<_NodeChoice?> _askNode(McpCatalogItem item) {
    final l10n = _l10n;
    return showKitChoiceSheet<_NodeChoice>(
      context,
      title: l10n.mcpCatalogNodeTitle(item.title),
      sheetKey: const ValueKey('mcp-catalog-node-sheet'),
      choices: [
        KitChoice(
          key: const ValueKey('mcp-catalog-add-node'),
          value: _NodeChoice.addNode,
          leading: KitRow.icon(context, AppIconography.tools),
          title: l10n.mcpCatalogNodeAdd,
          supporting: l10n.mcpCatalogNodeAddDetail,
        ),
        KitChoice(
          key: const ValueKey('mcp-catalog-node-continue'),
          value: _NodeChoice.continueToForm,
          leading: KitRow.icon(context, AppIconography.edit),
          title: l10n.mcpCatalogNodeHave,
          supporting: l10n.mcpCatalogNodeHaveDetail,
        ),
      ],
    );
  }

  /// Off: the MCP page's removal, with its question.
  Future<void> _turnOff(McpCatalogItem item) async {
    final name = item.serverName;
    if (_busy.contains(name) || !_controller.capabilities.mcpRuntimeRemovals) {
      return;
    }
    final l10n = _l10n;
    final location = _controller.locationRevision;
    setState(() => _busy.add(name));
    try {
      final confirmed = await showKitConfirm(
        context,
        icon: AppIconography.delete,
        title: l10n.integrationsMcpRemoveTitle(name),
        body: l10n.integrationsMcpRemoveBody,
        confirmLabel: l10n.integrationsMcpRemoveConfirm,
        cancelLabel: l10n.workCancel,
        sheetKey: const ValueKey('mcp-remove-confirm-sheet'),
        confirmKey: const ValueKey('confirm-mcp-remove'),
      );
      if (!confirmed || !mounted) return;
      setState(() => _notice = null);
      try {
        await _controller.removeMcpServer(name, locationRevision: location);
      } catch (_) {
        if (mounted) setState(() => _notice = l10n.mcpRemoveFailed);
      }
      // The removal may have landed even when its answer was lost: read
      // the list again either way.
      if (mounted) await _loadInventory();
    } finally {
      if (mounted) setState(() => _busy.remove(name));
    }
  }

  // --- build ----------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final registry = _registry;
    final optedIn = registry?.optedIn == true;
    return KitScreen(
      width: KitScreenWidth.list,
      topBar: KitTopBar(
        title: l10n.mcpCatalogTitle,
        subtitle: _controller.profile?.name,
        menuKey: const ValueKey('mcp-catalog-menu'),
        menu: [
          if (optedIn)
            KitMenuItem(
              key: const ValueKey('mcp-catalog-forget'),
              label: l10n.mcpCatalogForget,
              icon: AppIconography.privacy,
              onSelected: () => unawaited(_stopUsingRegistry()),
            ),
        ],
      ),
      body: _body(context, l10n, registry),
    );
  }

  Widget _body(
    BuildContext context,
    AppLocalizations l10n,
    SetupRegistrySnapshot? registry,
  ) {
    if (!_supported || _controller.profile == null) {
      return KitStateView.missing(
        key: const ValueKey('mcp-catalog-unavailable'),
        capability: 'mcp.any',
        icon: AppIconography.extensions,
        title: l10n.mcpSetupUnavailableTitle,
        why: l10n.mcpSetupUnavailableBody,
        size: KitStateSize.page,
      );
    }
    if (registry == null) {
      return const KitSkeletonRows(key: ValueKey('mcp-catalog-loading'));
    }
    if (!registry.optedIn) {
      return KitStateView(
        key: const ValueKey('mcp-catalog-consent'),
        icon: AppIconography.extensions,
        title: l10n.mcpCatalogConsentTitle,
        body: l10n.mcpCatalogConsentBody,
        primary: KitAction(
          key: const ValueKey('mcp-catalog-load'),
          label: l10n.mcpCatalogConsentLoad,
          icon: AppIconography.systemDownload,
          onPressed: () => unawaited(_optIn()),
        ),
        secondary: KitAction(
          key: const ValueKey('mcp-catalog-manual'),
          label: l10n.mcpAddManualTitle,
          onPressed: () => unawaited(_openManual()),
        ),
      );
    }
    final tokens = KitTokens.of(context);
    final rails = EdgeInsets.symmetric(horizontal: tokens.gutter);
    final notice = _notice;
    return KitRefresh(
      onRefresh: _refresh,
      child: ListView(
        key: const ValueKey('mcp-catalog-list'),
        physics: const AlwaysScrollableScrollPhysics(),
        padding: EdgeInsetsDirectional.only(
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          Padding(
            padding: rails,
            child: KitSearchField(
              key: const ValueKey('mcp-catalog-search'),
              fieldKey: const ValueKey('mcp-catalog-search-field'),
              label: l10n.mcpCatalogSearch,
              controller: _search,
              onChanged: _searchChanged,
              onSubmitted: _searchSubmitted,
            ),
          ),
          SizedBox(height: tokens.space3),
          if (notice != null)
            Padding(
              padding: rails.add(
                EdgeInsetsDirectional.only(bottom: tokens.space3),
              ),
              child: KitNotice(
                key: const ValueKey('mcp-catalog-notice'),
                message: notice,
                tone: AppStatusTone.failure,
                icon: AppIconography.warning,
                onDismiss: () => setState(() => _notice = null),
                dismissLabel: l10n.workspaceDismissNotice,
              ),
            ),
          ..._content(context, l10n, registry, rails),
        ],
      ),
    );
  }

  List<Widget> _content(
    BuildContext context,
    AppLocalizations l10n,
    SetupRegistrySnapshot registry,
    EdgeInsets rails,
  ) {
    final tokens = KitTokens.of(context);
    final entries = registry.entries;
    final loading = registry.status == SetupRegistryStatus.loading;
    final failed = registry.status == SetupRegistryStatus.error;
    if (_inventoryFailed) {
      return [
        Padding(
          padding: rails,
          child: KitStateView.error(
            key: const ValueKey('mcp-catalog-inventory-failed'),
            title: l10n.mcpCatalogInventoryFailed,
            body: l10n.mcpCatalogInventoryFailedBody,
            size: KitStateSize.inline,
            retry: KitAction(
              label: l10n.commonRetry,
              onPressed: () => unawaited(_loadInventory()),
            ),
          ),
        ),
      ];
    }
    if ((loading && entries.isEmpty) || _installed == null) {
      return const [KitSkeletonRows(key: ValueKey('mcp-catalog-loading'))];
    }
    if (failed && entries.isEmpty) {
      return [
        Padding(
          padding: rails,
          child: KitStateView.error(
            key: const ValueKey('mcp-catalog-failed'),
            title: registry.query == null
                ? l10n.mcpCatalogFailed
                : l10n.mcpCatalogSearchFailed,
            body: l10n.mcpCatalogFailedBody,
            size: KitStateSize.inline,
            retry: KitAction(
              label: l10n.commonRetry,
              onPressed: () =>
                  unawaited(_store?.refresh(query: registry.query)),
            ),
            secondary: KitAction(
              label: l10n.mcpAddManualTitle,
              onPressed: () => unawaited(_openManual()),
            ),
          ),
        ),
      ];
    }
    if (entries.isEmpty) {
      final query = registry.query;
      return [
        Padding(
          padding: rails,
          child: KitStateView(
            key: const ValueKey('mcp-catalog-empty'),
            icon: AppIconography.search,
            title: query == null
                ? l10n.mcpCatalogEmpty
                : l10n.mcpCatalogNoMatch(query),
            body: l10n.mcpCatalogEmptyBody,
            size: KitStateSize.inline,
            secondary: KitAction(
              label: l10n.mcpAddManualTitle,
              onPressed: () => unawaited(_openManual()),
            ),
          ),
        ),
      ];
    }
    final installed = _installed!;
    final items = orderCatalog(entries, installed: installed);
    return [
      if (failed)
        Padding(
          padding: rails.add(EdgeInsetsDirectional.only(bottom: tokens.space3)),
          child: KitNotice.error(
            key: const ValueKey('mcp-catalog-stale'),
            message: l10n.mcpCatalogStale,
            retry: KitAction(
              label: l10n.commonRetry,
              onPressed: () =>
                  unawaited(_store?.refresh(query: registry.query)),
            ),
          ),
        ),
      KitRowGroup(
        key: const ValueKey('mcp-catalog-rows'),
        children: [for (final item in items) _row(context, l10n, item)],
      ),
      Padding(
        padding: rails,
        child: KitGroupNote(message: l10n.mcpCatalogPriceNote),
      ),
    ];
  }

  Widget _row(
    BuildContext context,
    AppLocalizations l10n,
    McpCatalogItem item,
  ) {
    final on = _installed?.contains(item.serverName) == true;
    final busy = _busy.contains(item.serverName);
    final canRemove = _controller.capabilities.mcpRuntimeRemovals;
    final phone = widget.onThisPhone(_controller.profile);
    final String? disabledReason = busy
        ? (on ? l10n.mcpCatalogRemoving : l10n.mcpCatalogAdding)
        : on && !canRemove
        ? l10n.mcpCatalogCannotRemove
        : !on && !item.addable
        ? (item.runtime == McpCatalogRuntime.docker
              ? l10n.mcpCatalogNeedsDocker
              : l10n.mcpCatalogNoEndpoint)
        : null;
    final facts = [
      switch (item.runtime) {
        McpCatalogRuntime.hosted => l10n.mcpCatalogHostedBy(
          item.hostedBy ?? item.serverName,
        ),
        McpCatalogRuntime.node =>
          phone ? l10n.mcpCatalogNeedsNodePhone : l10n.mcpCatalogNeedsNode,
        McpCatalogRuntime.python => l10n.mcpCatalogNeedsPython,
        McpCatalogRuntime.docker || McpCatalogRuntime.none => null,
      },
      if (item.needsKey) l10n.mcpCatalogNeedsKey,
      if (item.needsSettings) l10n.mcpCatalogNeedsSettings,
    ].whereType<String>().join(' · ');
    return KitSwitchRow(
      key: ValueKey('mcp-catalog-row-${item.entry.name}'),
      switchKey: ValueKey('mcp-catalog-switch-${item.serverName}'),
      title: item.title,
      supporting: item.description.isEmpty ? null : item.description,
      below: facts.isEmpty
          ? null
          : KitText(
              facts,
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
            ),
      value: on,
      disabledReason: disabledReason,
      onChanged: disabledReason != null
          ? null
          : (value) => unawaited(value ? _turnOn(item) : _turnOff(item)),
    );
  }
}
