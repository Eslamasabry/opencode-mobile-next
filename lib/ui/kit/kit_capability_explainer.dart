// KitCapabilityExplainer: the one registry that maps a capability id
// (docs/ux-system/capabilities.json `matrix`) to why it is missing, which
// hosts can do it and the flow that turns it on, and the parts that say so
// in place. Frozen API: docs/ux-system/kit-api/KitCapabilityExplainer.md.
import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../l10n/app_localizations.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_max_width.dart';
import 'kit_notice.dart';
import 'kit_row.dart';
import 'kit_state_view.dart';
import 'motion/kit_reveal.dart';

/// Where a capability can run: the capabilities.json matrix host columns.
enum KitHost {
  /// builtin: OpenCode in the app's own Linux.
  thisPhone,

  /// A Termux-managed server on this phone.
  termux,

  /// oc2Computer.
  openCode2,
  codex,
  paseo,
  demo,
}

/// One registry entry, written by hand from capabilities.json and kept
/// equal to it by `test/kit/kit_capability_explainer_test.dart`.
@immutable
class KitCapability {
  const KitCapability({
    required this.id,
    required this.supported,
    this.partly = const {},
    this.enableFlow,
  });

  /// The matrix id, verbatim: "voice.model", "flag:sessionDiff".
  final String id;

  /// Hosts with status "supported".
  final Set<KitHost> supported;

  /// Hosts with status "partly".
  final Set<KitHost> partly;

  /// A [KitEnableFlows] id; null explains only.
  final String? enableFlow;
}

/// The 21 enable-flow ids, one per capabilities.json enableFlows entry.
abstract final class KitEnableFlows {
  /// server.any → servers-welcome.
  static const addServer = 'add-server';

  /// phone.builtin → phone-setup-start (setup v2).
  static const phoneSetup = 'phone-setup';

  /// phone.termux → setup v2, host Termux.
  static const phoneSetupTermux = 'phone-setup-termux';

  /// server.oc2 → the This phone card's switch.
  static const serverGeneration = 'server-generation';

  /// server.codex.
  static const addServerCodex = 'add-server-codex';

  /// server.paseo.
  static const addServerPaseo = 'add-server-paseo';

  /// claude.local → setup v2 Add tools.
  static const addToolClaude = 'add-tool-claude';

  /// model.auth → sign-in at the composer.
  static const modelSignIn = 'model-sign-in';

  /// team.on → team intro, setup per server kind.
  static const teamTurnOn = 'team-turn-on';

  /// team.control → run the host front on the computer.
  static const teamHostGuide = 'team-host-guide';

  /// voice.model → voice-model-setup-sheet.
  static const voiceModelSetup = 'voice-model-setup';

  /// mcp.any → mcp-setup (no assistant option: P2 deferred).
  static const mcpAdd = 'mcp-add';

  /// project.open → the chooser.
  static const projectChoose = 'project-choose';

  /// project.git → at the Git-needing action.
  static const projectGitInit = 'project-git-init';

  /// perm.notifications.
  static const allowNotifications = 'allow-notifications';

  /// perm.battery.
  static const allowBackground = 'allow-background';

  /// perm.camera.
  static const allowCamera = 'allow-camera';

  /// perm.mic.
  static const allowMicrophone = 'allow-microphone';

  /// network.tailscale.
  static const tailscaleSetup = 'tailscale-setup';

  /// quota.collector.
  static const quotaCollectorGuide = 'quota-collector-guide';

  /// agent.a2a → Integrations.
  static const addExternalAgent = 'add-external-agent';

  static const all = <String>[
    addServer,
    phoneSetup,
    phoneSetupTermux,
    serverGeneration,
    addServerCodex,
    addServerPaseo,
    addToolClaude,
    modelSignIn,
    teamTurnOn,
    teamHostGuide,
    voiceModelSetup,
    mcpAdd,
    projectChoose,
    projectGitInit,
    allowNotifications,
    allowBackground,
    allowCamera,
    allowMicrophone,
    tailscaleSetup,
    quotaCollectorGuide,
    addExternalAgent,
  ];
}

/// What a handler gets: the capability, its flow, and where it was asked.
@immutable
class KitEnableRequest {
  const KitEnableRequest({
    required this.capability,
    required this.flow,
    this.host,
    this.serverName,
    this.source,
  });

  final String capability;
  final String flow;

  /// The current server's host kind, when known.
  final KitHost? host;
  final String? serverName;

  /// The page id the offer was on.
  final String? source;

  @override
  bool operator ==(Object other) =>
      other is KitEnableRequest &&
      other.capability == capability &&
      other.flow == flow &&
      other.host == host &&
      other.serverName == serverName &&
      other.source == source;

  @override
  int get hashCode => Object.hash(capability, flow, host, serverName, source);

  @override
  String toString() =>
      'KitEnableRequest($capability, $flow, $host, $serverName, $source)';
}

typedef KitEnableFlowHandler =
    Future<void> Function(BuildContext context, KitEnableRequest request);

/// The registry: every matrix capability, and the enable-flow handlers the
/// app registers at start-up (coord-main), so the kit imports no screens.
abstract final class KitCapabilities {
  /// Every matrix capability (35), 21 of them with an enableFlow.
  static const List<KitCapability> all = [
    KitCapability(
      id: 'server.any',
      supported: {
        KitHost.thisPhone,
        KitHost.termux,
        KitHost.openCode2,
        KitHost.codex,
        KitHost.paseo,
      },
      partly: {KitHost.demo},
      enableFlow: KitEnableFlows.addServer,
    ),
    KitCapability(
      id: 'server.oc2',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {},
      enableFlow: KitEnableFlows.serverGeneration,
    ),
    KitCapability(
      id: 'server.codex',
      supported: {KitHost.codex},
      partly: {KitHost.openCode2, KitHost.paseo},
      enableFlow: KitEnableFlows.addServerCodex,
    ),
    KitCapability(
      id: 'server.paseo',
      supported: {KitHost.termux, KitHost.paseo},
      partly: {KitHost.openCode2},
      enableFlow: KitEnableFlows.addServerPaseo,
    ),
    KitCapability(
      id: 'phone.builtin',
      supported: {KitHost.thisPhone},
      partly: {KitHost.termux},
      enableFlow: KitEnableFlows.phoneSetup,
    ),
    KitCapability(
      id: 'phone.termux',
      supported: {KitHost.termux},
      partly: {KitHost.thisPhone},
      enableFlow: KitEnableFlows.phoneSetupTermux,
    ),
    KitCapability(
      id: 'phone.any',
      supported: {KitHost.thisPhone, KitHost.termux},
    ),
    KitCapability(
      id: 'model.auth',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {KitHost.codex, KitHost.paseo},
      enableFlow: KitEnableFlows.modelSignIn,
    ),
    KitCapability(
      id: 'team.on',
      supported: {KitHost.thisPhone, KitHost.openCode2},
      partly: {KitHost.termux, KitHost.codex, KitHost.paseo},
      enableFlow: KitEnableFlows.teamTurnOn,
    ),
    KitCapability(
      id: 'team.phone',
      supported: {KitHost.thisPhone},
      partly: {KitHost.termux},
    ),
    KitCapability(
      id: 'team.control',
      supported: {KitHost.thisPhone, KitHost.termux},
      partly: {KitHost.openCode2, KitHost.codex, KitHost.paseo},
      enableFlow: KitEnableFlows.teamHostGuide,
    ),
    KitCapability(
      id: 'claude.local',
      supported: {KitHost.termux},
      enableFlow: KitEnableFlows.addToolClaude,
    ),
    KitCapability(
      id: 'voice.model',
      supported: {
        KitHost.thisPhone,
        KitHost.termux,
        KitHost.openCode2,
        KitHost.codex,
        KitHost.paseo,
        KitHost.demo,
      },
      enableFlow: KitEnableFlows.voiceModelSetup,
    ),
    KitCapability(
      id: 'mcp.any',
      supported: {},
      partly: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      enableFlow: KitEnableFlows.mcpAdd,
    ),
    KitCapability(
      id: 'project.open',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {KitHost.codex, KitHost.paseo},
      enableFlow: KitEnableFlows.projectChoose,
    ),
    KitCapability(
      id: 'project.git',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {},
      enableFlow: KitEnableFlows.projectGitInit,
    ),
    KitCapability(
      id: 'perm.notifications',
      supported: {
        KitHost.thisPhone,
        KitHost.termux,
        KitHost.openCode2,
        KitHost.codex,
        KitHost.paseo,
      },
      enableFlow: KitEnableFlows.allowNotifications,
    ),
    KitCapability(
      id: 'perm.battery',
      supported: {
        KitHost.thisPhone,
        KitHost.termux,
        KitHost.openCode2,
        KitHost.codex,
        KitHost.paseo,
      },
      enableFlow: KitEnableFlows.allowBackground,
    ),
    KitCapability(
      id: 'perm.camera',
      supported: {KitHost.openCode2},
      partly: {KitHost.thisPhone, KitHost.termux},
      enableFlow: KitEnableFlows.allowCamera,
    ),
    KitCapability(
      id: 'perm.mic',
      supported: {
        KitHost.thisPhone,
        KitHost.termux,
        KitHost.openCode2,
        KitHost.codex,
        KitHost.paseo,
      },
      partly: {KitHost.demo},
      enableFlow: KitEnableFlows.allowMicrophone,
    ),
    KitCapability(
      id: 'network.tailscale',
      supported: {KitHost.openCode2, KitHost.paseo},
      partly: {KitHost.codex},
      enableFlow: KitEnableFlows.tailscaleSetup,
    ),
    KitCapability(
      id: 'quota.collector',
      supported: <KitHost>{},
      partly: {KitHost.openCode2},
      enableFlow: KitEnableFlows.quotaCollectorGuide,
    ),
    KitCapability(
      id: 'agent.a2a',
      supported: {
        KitHost.thisPhone,
        KitHost.termux,
        KitHost.openCode2,
        KitHost.codex,
        KitHost.paseo,
      },
      partly: {KitHost.demo},
      enableFlow: KitEnableFlows.addExternalAgent,
    ),
    KitCapability(
      id: 'flag:fileBrowsing+terminal',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {KitHost.demo},
    ),
    KitCapability(
      id: 'flag:terminal',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {KitHost.demo},
    ),
    KitCapability(
      id: 'flag:sessionDiff',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {KitHost.demo},
    ),
    KitCapability(
      id: 'flag:serverCatalog',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {KitHost.demo},
    ),
    KitCapability(id: 'flag:toolInventory', supported: {}, partly: {}),
    KitCapability(
      id: 'flag:usageStatistics',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {KitHost.codex},
    ),
    KitCapability(
      id: 'flag:stagedRevert+sessionNotes',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {},
    ),
    KitCapability(
      id: 'flag:worktreeCreate+sessionShare+managedWorkspaces',
      supported: {},
      partly: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
    ),
    KitCapability(
      id: 'flag:developmentServices',
      supported: {KitHost.thisPhone, KitHost.termux, KitHost.openCode2},
      partly: {},
    ),
    KitCapability(id: 'flag:remoteUpgrade', supported: {}, partly: {}),
    KitCapability(
      id: 'flag:promptAttachments',
      supported: {
        KitHost.thisPhone,
        KitHost.termux,
        KitHost.openCode2,
        KitHost.demo,
      },
    ),
  ];

  static final Map<String, KitCapability> _byId = {
    for (final capability in all) capability.id: capability,
  };

  static final Map<String, KitEnableFlowHandler> _handlers = {};

  /// Ids already reported as unknown in release, so each is reported once.
  static final Set<String> _reported = {};

  static KitCapability? byId(String id) => _byId[id];

  /// Set by the app at start-up (coord-main). Replaces an earlier handler.
  static void registerFlow(String flow, KitEnableFlowHandler handler) {
    assert(
      KitEnableFlows.all.contains(flow),
      'KitCapabilities.registerFlow: "$flow" is not a KitEnableFlows id',
    );
    _handlers[flow] = handler;
  }

  /// The entry has an enableFlow and its handler is registered.
  static bool canEnable(String capability) {
    final flow = _byId[capability]?.enableFlow;
    return flow != null && _handlers.containsKey(flow);
  }

  /// Runs the flow's handler; a no-op returning false when none is
  /// registered. A handler that throws is reported through
  /// [FlutterError.reportError] and returns false: a tap never crashes, and
  /// the handler's own page reports the failure to the person.
  static Future<bool> enable(
    BuildContext context,
    String capability, {
    KitHost? host,
    String? serverName,
    String? source,
  }) async {
    final flow = _byId[capability]?.enableFlow;
    final handler = flow == null ? null : _handlers[flow];
    if (flow == null || handler == null) return false;
    try {
      await handler(
        context,
        KitEnableRequest(
          capability: capability,
          flow: flow,
          host: host,
          serverName: serverName,
          source: source,
        ),
      );
      return true;
    } catch (error, stack) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stack,
          library: 'kit',
          context: ErrorDescription(
            'while running the enable flow "$flow" for "$capability"',
          ),
        ),
      );
      return false;
    }
  }

  /// For the coord-main acceptance test: flows with no handler.
  static Iterable<String> get unregisteredFlows =>
      KitEnableFlows.all.where((flow) => !_handlers.containsKey(flow));

  @visibleForTesting
  static void debugReset() {
    _handlers.clear();
    _reported.clear();
  }

  /// Looks [id] up; an unknown id asserts in debug and, in release, is
  /// reported once through [FlutterError.reportError] (never a dead row).
  static KitCapability? _lookup(String id) {
    final capability = _byId[id];
    assert(
      capability != null,
      'KitCapabilityExplainer: unknown capability "$id"; the ids are the '
      'capabilities.json matrix ids (KitCapabilities.all)',
    );
    if (capability == null && _reported.add(id)) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: ArgumentError.value(id, 'capability', 'unknown'),
          library: 'kit',
          context: ErrorDescription('while explaining a missing capability'),
        ),
      );
    }
    return capability;
  }
}

enum _Variant { row, state, offer }

/// A missing capability, explained in place and offered where it can be
/// turned on (kit-v2.md §2.2, §2.5; capabilities.json rules; STATE-12,
/// STATE-13, AUTO-18, KIT-37). It looks the capability up in
/// [KitCapabilities] and builds [KitRow.unavailable], [KitStateView.missing]
/// or [KitNotice.offer]; the enable action appears only when the app has
/// registered the capability's flow, so it never leads nowhere.
///
/// States: explains, offers-enable, prerequisite, offer, folded, working.
class KitCapabilityExplainer extends StatelessWidget {
  /// A dimmed row that says why and offers the enable flow (§2.5; the
  /// "KitCapabilityRow"): builds KitRow.unavailable(title, reason, enable).
  const KitCapabilityExplainer.row({
    super.key,
    required this.capability,
    this.host,
    this.serverName,
    this.title,
    this.source,
    this.rowKey,
  }) : cost = const [],
       prerequisite = false,
       size = KitStateSize.inline,
       stateKey = null,
       onNotNow = null,
       folded = false,
       message = null,
       offerKey = null,
       _variant = _Variant.row;

  /// A missing-capability state (§2.2): builds KitStateView.missing with
  /// the registry's title, why and enable action.
  const KitCapabilityExplainer.state({
    super.key,
    required this.capability,
    this.host,
    this.serverName,
    this.cost = const [],
    this.prerequisite = false,
    this.size = KitStateSize.inline,
    this.source,
    this.stateKey,
  }) : title = null,
       rowKey = null,
       onNotNow = null,
       folded = false,
       message = null,
       offerKey = null,
       _variant = _Variant.state;

  /// A turn-on offer at the moment of need (AUTO-18, KIT-37): KitNotice.offer
  /// with the enable action and "Not now". After "Not now" the caller passes
  /// folded: true, which renders the quiet row instead.
  const KitCapabilityExplainer.offer({
    super.key,
    required this.capability,
    required VoidCallback this.onNotNow,
    this.folded = false,
    this.message,
    this.host,
    this.serverName,
    this.source,
    this.offerKey,
  }) : title = null,
       cost = const [],
       prerequisite = false,
       size = KitStateSize.inline,
       rowKey = null,
       stateKey = null,
       _variant = _Variant.offer;

  /// The capabilities.json matrix id ("voice.model").
  final String capability;

  /// The current server's host: "Files and Terminal aren't available on
  /// Codex".
  final KitHost? host;

  /// The current server's name, shown beside the host word.
  final String? serverName;

  /// [KitCapabilityExplainer.row]: the title; default the registry title.
  final String? title;

  /// The page id the explainer is on, passed to the handler.
  final String? source;

  /// [KitCapabilityExplainer.row]: the row's key.
  final Key? rowKey;

  /// [KitCapabilityExplainer.state]: KitNotice.cost items (download size,
  /// time, battery…) from the caller; the registry does not guess sizes.
  final List<String> cost;

  /// [KitCapabilityExplainer.state]: "finish X first" (STATE-13); asserts
  /// that an enable action resolves.
  final bool prerequisite;

  /// [KitCapabilityExplainer.state]: inline, or page when the whole screen
  /// is the missing feature.
  final KitStateSize size;

  /// [KitCapabilityExplainer.state]: the state's key.
  final Key? stateKey;

  /// [KitCapabilityExplainer.offer]: "Not now"; the caller remembers it.
  final VoidCallback? onNotNow;

  /// [KitCapabilityExplainer.offer]: after "Not now", the quiet row.
  final bool folded;

  /// [KitCapabilityExplainer.offer]: default the registry's offer sentence.
  final String? message;

  /// [KitCapabilityExplainer.offer]: the notice's key.
  final Key? offerKey;

  final _Variant _variant;

  /// The capability's name ("Voice typing").
  static String titleOf(BuildContext context, String capability) {
    if (KitCapabilities._lookup(capability) == null) return '';
    return _wordsOf(AppLocalizations.of(context), capability).title;
  }

  /// Why the capability is missing. When [host] cannot do it at all, it
  /// names the host (and [serverName]) and the hosts that can (STATE-12);
  /// otherwise the registry's reason.
  static String whyOf(
    BuildContext context,
    String capability, {
    KitHost? host,
    String? serverName,
  }) {
    final entry = KitCapabilities._lookup(capability);
    if (entry == null) return '';
    final l10n = AppLocalizations.of(context);
    final words = _wordsOf(l10n, capability);
    if (host == null ||
        entry.supported.contains(host) ||
        entry.partly.contains(host)) {
      return words.why;
    }
    final hostText = KitBidi.auto(hostWord(context, host));
    final name = serverName?.trim();
    final place = name == null || name.isEmpty
        ? hostText
        : l10n.kitCapServerOnHost(KitBidi.auto(name), hostText);
    final notHere = l10n.kitCapNotOnHost(
      _pluralTitles.contains(capability) ? 2 : 1,
      words.title,
      place,
    );
    final hosts = _hostList(context, entry);
    if (hosts.isEmpty) return l10n.kitCapWhyElsewhere(notHere, words.why);
    return l10n.kitCapWhyElsewhere(notHere, l10n.kitCapWorksOn(hosts));
  }

  /// The enable action's label ("Download voice model"); null explains
  /// only.
  static String? enableLabelOf(BuildContext context, String capability) {
    if (KitCapabilities._lookup(capability) == null) return null;
    return _wordsOf(AppLocalizations.of(context), capability).enable;
  }

  /// "Works on this phone and computers with OpenCode 2".
  static String hostsLineOf(BuildContext context, String capability) {
    final entry = KitCapabilities._lookup(capability);
    if (entry == null) return '';
    final hosts = _hostList(context, entry);
    if (hosts.isEmpty) return '';
    return AppLocalizations.of(context).kitCapWorksOn(hosts);
  }

  /// The host, read after "on": "this phone", "Codex".
  static String hostWord(BuildContext context, KitHost host) {
    final l10n = AppLocalizations.of(context);
    return switch (host) {
      KitHost.thisPhone => l10n.kitHostThisPhone,
      KitHost.termux => l10n.kitHostTermux,
      KitHost.openCode2 => l10n.kitHostOpenCode2,
      KitHost.codex => l10n.kitHostCodex,
      KitHost.paseo => l10n.kitHostPaseo,
      KitHost.demo => l10n.kitHostDemo,
    };
  }

  /// The hosts that can (supported, then partly), as one phrase. The demo
  /// is left out unless nothing else can; this phone covers Termux on it,
  /// and both OpenCode generations read as one.
  static String _hostList(BuildContext context, KitCapability entry) {
    final l10n = AppLocalizations.of(context);
    final can = {...entry.supported, ...entry.partly};
    final ordered = [
      for (final host in KitHost.values)
        if (can.contains(host)) host,
    ];
    final real = ordered.where((host) => host != KitHost.demo).toList();
    final hosts = real.isEmpty ? ordered : real;
    if (hosts.contains(KitHost.thisPhone)) hosts.remove(KitHost.termux);
    final words = <String>[];
    for (final host in hosts) {
      words.add(hostWord(context, host));
    }
    final isolated = [for (final word in words) KitBidi.auto(word)];
    if (isolated.isEmpty) return '';
    if (isolated.length == 1) return isolated.single;
    var head = isolated.first;
    for (final word in isolated.sublist(1, isolated.length - 1)) {
      head = l10n.kitCapComma(head, word);
    }
    return l10n.kitCapAnd(head, isolated.last);
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final entry = KitCapabilities._lookup(capability);
    if (entry == null) return const SizedBox.shrink();
    return _KitCapabilityExplainerBody(explainer: this);
  }
}

class _KitCapabilityExplainerBody extends StatefulWidget {
  const _KitCapabilityExplainerBody({required this.explainer});

  final KitCapabilityExplainer explainer;

  @override
  State<_KitCapabilityExplainerBody> createState() =>
      _KitCapabilityExplainerBodyState();
}

class _KitCapabilityExplainerBodyState
    extends State<_KitCapabilityExplainerBody> {
  /// The enable action's own tap is running (KitAction.working; STATE-7:
  /// no lasting status, the flow shows its progress on its own page).
  bool _working = false;

  KitCapabilityExplainer get _e => widget.explainer;

  Future<void> _run() async {
    if (_working) return;
    setState(() => _working = true);
    try {
      await KitCapabilities.enable(
        context,
        _e.capability,
        host: _e.host,
        serverName: _e.serverName,
        source: _e.source,
      );
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  /// The enable action, or null when there is no flow or no handler (the
  /// explains variant: hidden rather than disabled, STATE-8).
  KitAction? _enable(BuildContext context) {
    if (!KitCapabilities.canEnable(_e.capability)) return null;
    final label = KitCapabilityExplainer.enableLabelOf(context, _e.capability);
    if (label == null) return null;
    return KitAction(
      label: label,
      onPressed: () => unawaited(_run()),
      working: _working,
    );
  }

  Widget _row(BuildContext context, {Key? key, String? title}) {
    return KitRow.unavailable(
      key: key,
      title: title ?? KitCapabilityExplainer.titleOf(context, _e.capability),
      reason: KitCapabilityExplainer.whyOf(
        context,
        _e.capability,
        host: _e.host,
        serverName: _e.serverName,
      ),
      enable: _enable(context),
      capability: _e.capability,
    );
  }

  @override
  Widget build(BuildContext context) {
    final enable = _enable(context);
    switch (_e._variant) {
      case _Variant.row:
        return _row(context, key: _e.rowKey, title: _e.title);
      case _Variant.state:
        assert(
          !_e.prerequisite || enable != null,
          'KitCapabilityExplainer.state: a prerequisite needs an enable '
          'action, so "${_e.capability}" needs an enableFlow and a '
          'registered handler (STATE-13, G37): never a dead end.',
        );
        return KitStateView.missing(
          key: _e.stateKey,
          capability: _e.capability,
          title: KitCapabilityExplainer.titleOf(context, _e.capability),
          why: KitCapabilityExplainer.whyOf(
            context,
            _e.capability,
            host: _e.host,
            serverName: _e.serverName,
          ),
          enable: enable,
          cost: enable == null ? const [] : _e.cost,
          prerequisite: _e.prerequisite && enable != null,
          size: _e.size,
        );
      case _Variant.offer:
        // No handler: nothing to offer, so the quiet row explains instead.
        final open = !_e.folded && enable != null;
        final l10n = AppLocalizations.of(context);
        final words = _wordsOf(l10n, _e.capability);
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitReveal(
              child: open
                  ? KitNotice.offer(
                      key: _e.offerKey,
                      message: _e.message ?? words.offer ?? words.why,
                      action: enable,
                      onDismiss: _e.onNotNow!,
                      dismissLabel: l10n.kitCapNotNow,
                    )
                  : null,
            ),
            KitReveal(child: open ? null : _row(context)),
          ],
        );
    }
  }
}

/// Titles that read as plural ("Files and Terminal aren't available").
const _pluralTitles = <String>{
  'team.control',
  'mcp.any',
  'perm.notifications',
  'agent.a2a',
  'flag:fileBrowsing+terminal',
  'flag:serverCatalog',
  'flag:stagedRevert+sessionNotes',
  'flag:worktreeCreate+sessionShare+managedWorkspaces',
  'flag:developmentServices',
  'flag:promptAttachments',
};

typedef _Words = ({String title, String why, String? enable, String? offer});

_Words _words(String title, String why, [String? enable, String? offer]) =>
    (title: title, why: why, enable: enable, offer: offer);

/// The registry's words (kit ARB keys `kitCap<Id>Title`, `…Why`, `…Enable`,
/// `…Offer`). [id] is known: callers look it up first.
_Words _wordsOf(AppLocalizations l, String id) => switch (id) {
  'server.any' => _words(
    l.kitCapServerAnyTitle,
    l.kitCapServerAnyWhy,
    l.kitCapServerAnyEnable,
    l.kitCapServerAnyOffer,
  ),
  'server.oc2' => _words(
    l.kitCapServerOc2Title,
    l.kitCapServerOc2Why,
    l.kitCapServerOc2Enable,
    l.kitCapServerOc2Offer,
  ),
  'server.codex' => _words(
    l.kitCapServerCodexTitle,
    l.kitCapServerCodexWhy,
    l.kitCapServerCodexEnable,
    l.kitCapServerCodexOffer,
  ),
  'server.paseo' => _words(
    l.kitCapServerPaseoTitle,
    l.kitCapServerPaseoWhy,
    l.kitCapServerPaseoEnable,
    l.kitCapServerPaseoOffer,
  ),
  'phone.builtin' => _words(
    l.kitCapPhoneBuiltinTitle,
    l.kitCapPhoneBuiltinWhy,
    l.kitCapPhoneBuiltinEnable,
    l.kitCapPhoneBuiltinOffer,
  ),
  'phone.termux' => _words(
    l.kitCapPhoneTermuxTitle,
    l.kitCapPhoneTermuxWhy,
    l.kitCapPhoneTermuxEnable,
    l.kitCapPhoneTermuxOffer,
  ),
  'phone.any' => _words(l.kitCapPhoneAnyTitle, l.kitCapPhoneAnyWhy),
  'model.auth' => _words(
    l.kitCapModelAuthTitle,
    l.kitCapModelAuthWhy,
    l.kitCapModelAuthEnable,
    l.kitCapModelAuthOffer,
  ),
  'team.on' => _words(
    l.kitCapTeamOnTitle,
    l.kitCapTeamOnWhy,
    l.kitCapTeamOnEnable,
    l.kitCapTeamOnOffer,
  ),
  'team.phone' => _words(l.kitCapTeamPhoneTitle, l.kitCapTeamPhoneWhy),
  'team.control' => _words(
    l.kitCapTeamControlTitle,
    l.kitCapTeamControlWhy,
    l.kitCapTeamControlEnable,
    l.kitCapTeamControlOffer,
  ),
  'claude.local' => _words(
    l.kitCapClaudeLocalTitle,
    l.kitCapClaudeLocalWhy,
    l.kitCapClaudeLocalEnable,
    l.kitCapClaudeLocalOffer,
  ),
  'voice.model' => _words(
    l.kitCapVoiceModelTitle,
    l.kitCapVoiceModelWhy,
    l.kitCapVoiceModelEnable,
    l.kitCapVoiceModelOffer,
  ),
  'mcp.any' => _words(
    l.kitCapMcpAnyTitle,
    l.kitCapMcpAnyWhy,
    l.kitCapMcpAnyEnable,
    l.kitCapMcpAnyOffer,
  ),
  'project.open' => _words(
    l.kitCapProjectOpenTitle,
    l.kitCapProjectOpenWhy,
    l.kitCapProjectOpenEnable,
    l.kitCapProjectOpenOffer,
  ),
  'project.git' => _words(
    l.kitCapProjectGitTitle,
    l.kitCapProjectGitWhy,
    l.kitCapProjectGitEnable,
    l.kitCapProjectGitOffer,
  ),
  'perm.notifications' => _words(
    l.kitCapPermNotificationsTitle,
    l.kitCapPermNotificationsWhy,
    l.kitCapPermNotificationsEnable,
    l.kitCapPermNotificationsOffer,
  ),
  'perm.battery' => _words(
    l.kitCapPermBatteryTitle,
    l.kitCapPermBatteryWhy,
    l.kitCapPermBatteryEnable,
    l.kitCapPermBatteryOffer,
  ),
  'perm.camera' => _words(
    l.kitCapPermCameraTitle,
    l.kitCapPermCameraWhy,
    l.kitCapPermCameraEnable,
    l.kitCapPermCameraOffer,
  ),
  'perm.mic' => _words(
    l.kitCapPermMicTitle,
    l.kitCapPermMicWhy,
    l.kitCapPermMicEnable,
    l.kitCapPermMicOffer,
  ),
  'network.tailscale' => _words(
    l.kitCapNetworkTailscaleTitle,
    l.kitCapNetworkTailscaleWhy,
    l.kitCapNetworkTailscaleEnable,
    l.kitCapNetworkTailscaleOffer,
  ),
  'quota.collector' => _words(
    l.kitCapQuotaCollectorTitle,
    l.kitCapQuotaCollectorWhy,
    l.kitCapQuotaCollectorEnable,
    l.kitCapQuotaCollectorOffer,
  ),
  'agent.a2a' => _words(
    l.kitCapAgentA2aTitle,
    l.kitCapAgentA2aWhy,
    l.kitCapAgentA2aEnable,
    l.kitCapAgentA2aOffer,
  ),
  'flag:fileBrowsing+terminal' => _words(
    l.kitCapFlagFileBrowsingTerminalTitle,
    l.kitCapFlagFileBrowsingTerminalWhy,
  ),
  'flag:terminal' => _words(l.kitCapFlagTerminalTitle, l.kitCapFlagTerminalWhy),
  'flag:sessionDiff' => _words(
    l.kitCapFlagSessionDiffTitle,
    l.kitCapFlagSessionDiffWhy,
  ),
  'flag:serverCatalog' => _words(
    l.kitCapFlagServerCatalogTitle,
    l.kitCapFlagServerCatalogWhy,
  ),
  'flag:toolInventory' => _words(
    l.kitCapFlagToolInventoryTitle,
    l.kitCapFlagToolInventoryWhy,
  ),
  'flag:usageStatistics' => _words(
    l.kitCapFlagUsageStatisticsTitle,
    l.kitCapFlagUsageStatisticsWhy,
  ),
  'flag:stagedRevert+sessionNotes' => _words(
    l.kitCapFlagStagedRevertSessionNotesTitle,
    l.kitCapFlagStagedRevertSessionNotesWhy,
  ),
  'flag:worktreeCreate+sessionShare+managedWorkspaces' => _words(
    l.kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle,
    l.kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy,
  ),
  'flag:developmentServices' => _words(
    l.kitCapFlagDevelopmentServicesTitle,
    l.kitCapFlagDevelopmentServicesWhy,
  ),
  'flag:remoteUpgrade' => _words(
    l.kitCapFlagRemoteUpgradeTitle,
    l.kitCapFlagRemoteUpgradeWhy,
  ),
  'flag:promptAttachments' => _words(
    l.kitCapFlagPromptAttachmentsTitle,
    l.kitCapFlagPromptAttachmentsWhy,
  ),
  _ => _words(id, ''),
};
