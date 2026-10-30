part of '../settings_screen.dart';

/// The servers the Settings hub speaks about: every saved one, unless this is
/// an isolated window that must not see the others.
List<ServerProfile> settingsServers(ConnectionController controller) =>
    controller.isIsolated
    ? [?controller.profile]
    : List.unmodifiable(controller.store.profiles);

/// More than one saved server: the moment server-owned rows start saying "By
/// server" (Settings Standard SC4). With one there are no pickers at all.
bool hasSeveralServers(ConnectionController controller) =>
    settingsServers(controller).length >= 2;

/// Whether [profile] is the server the app is connected to now.
bool _isConnectedServer(ConnectionController controller, ServerProfile p) =>
    controller.profile?.id == p.id && controller.isConnected;

/// The Servers row's end: the one server and whether it is connected, or how
/// many are saved.
String? serversRowValue(
  ConnectionController controller,
  AppLocalizations l10n,
) {
  final servers = settingsServers(controller);
  if (servers.isEmpty) return null;
  if (servers.length > 1) return l10n.settingsV3ServersCount(servers.length);
  final only = servers.single;
  final name = serverDisplayName(only, l10n, among: servers);
  return _isConnectedServer(controller, only)
      ? l10n.settingsV3ServerConnected(name)
      : name;
}

/// This phone's end: set up or not, and "Running" only while the app really
/// is connected to the server it hosts.
String? phoneRowValue(ConnectionController controller, AppLocalizations l10n) {
  bool onPhone(ServerProfile p) =>
      looksLikeInAppServer(p) || TermuxBridge.managesServerUrl(p.baseUrl);
  final hosted = settingsServers(controller).where(onPhone).toList();
  if (hosted.isEmpty) return l10n.settingsV3PhoneSetUp;
  return hosted.any((p) => _isConnectedServer(controller, p))
      ? l10n.settingsV3PhoneRunning
      : l10n.settingsV3PhoneInstalled;
}

/// The servers whose notification choice differs from the personal one: a
/// watched server whose own "notify when attention is needed" is off.
List<ServerProfile> serversWithDifferentNotifications(
  ConnectionController controller,
) {
  final monitor = controller.profileMonitor;
  return [
    for (final p in settingsServers(controller))
      if (monitor.rulesFor(p.id).enabled &&
          !monitor.rulesFor(p.id).notifications)
        p,
  ];
}

/// The Notifications row's end: "Different on 1 server" once a server
/// overrides the personal choice (like Slack's channel-specific count), else
/// whether this phone's own notifications are on.
String notificationsRowValue(
  ConnectionController controller,
  AppLocalizations l10n,
) {
  final different = serversWithDifferentNotifications(controller).length;
  if (different > 0) return l10n.settingsV3DifferentOn(different);
  final preferences = controller.notificationPreferences;
  return preferences.finishedRuns || preferences.requests
      ? l10n.settingsV3On
      : l10n.settingsV3Off;
}

/// "You can change some of these for one server": each saved server, opening
/// its own page. Shown once there are several.
class SettingsServersBlock extends StatelessWidget {
  const SettingsServersBlock({super.key, required this.controller});

  final ConnectionController controller;

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final servers = settingsServers(controller);
    return KitRowGroup(
      label: copy.settingsV3ServersBlock,
      children: [
        for (final p in servers)
          KitRow(
            key: ValueKey('settings-server-${p.id}'),
            leading: KitRow.icon(
              context,
              isPhoneOwnServer(p)
                  ? AppIconography.phone
                  : AppIconography.server,
            ),
            title: serverDisplayName(p, l10n, among: servers),
            // The state under the name, not beside it: the list pane of a
            // tablet is narrow, and the name must never be the part that
            // gives way.
            supporting: TextSpan(
              text: _isConnectedServer(controller, p)
                  ? copy.settingsV3Connected
                  : copy.settingsV3NotConnected,
            ),
            trailing: const KitChevron(),
            onTap: () => unawaited(openServerPage(context, controller, p.id)),
          ),
      ],
    );
  }
}

/// Opens the page of the saved server [profileId], at [arrive] when a row
/// of it is meant.
Future<void> openServerPage(
  BuildContext context,
  ConnectionController controller,
  String profileId, {
  String? arrive,
}) {
  final page = ServerPageScreen(controller: controller, profileId: profileId);
  return pushKitPage<void>(
    context,
    (_) => arrive == null ? page : KitArrivalScope(rowId: arrive, child: page),
  );
}

/// The Language row: a quick pick (N5) whose value is the language in use.
/// Language is app-wide; it lives here once and nowhere else (G4).
class LanguageSettingsRow extends StatefulWidget {
  const LanguageSettingsRow({super.key, required this.controller});

  final ConnectionController controller;

  @override
  State<LanguageSettingsRow> createState() => _LanguageSettingsRowState();
}

class _LanguageSettingsRowState extends State<LanguageSettingsRow> {
  bool _failed = false;

  Future<void> _save(String code) async {
    setState(() => _failed = false);
    try {
      await widget.controller.setAppLocale(code.isEmpty ? null : Locale(code));
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    }
  }

  Future<void> _pick(List<KitChoice<String>> choices, String code) async {
    final chosen = await showKitChoiceSheet<String>(
      context,
      title: _settingsCopy(context).settingsV3Language,
      choices: choices,
      selected: code,
    );
    if (chosen != null && mounted) await _save(chosen);
  }

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final controller = widget.controller;
    return ListenableBuilder(
      listenable: controller.appLocale,
      builder: (context, _) {
        final code = controller.appLocale.value?.languageCode ?? '';
        final choices = [
          for (final choice in const ['', ...AppLocaleStore.pickerLanguages])
            KitChoice<String>(
              value: choice,
              title: languageChoiceLabel(copy, choice),
              supporting: languageChoiceNote(copy, choice),
              key: ValueKey('settings-language-$choice'),
            ),
        ];
        final value = languageChoiceLabel(copy, code);
        // The same row as its siblings, so the chevron lines up with theirs;
        // at large text the value moves under the title instead of being cut.
        final large = MediaQuery.textScalerOf(context).scale(10) > 13;
        return KitRow(
          key: const ValueKey('settings-category-language'),
          leading: KitRow.icon(context, AppIconography.globe),
          title: copy.settingsV3Language,
          titleMaxLines: large ? 2 : 1,
          supporting: _failed
              ? TextSpan(text: copy.effectsSaveFailed)
              : large
              ? TextSpan(text: value)
              : null,
          supportingMaxLines: 2,
          trailing: large ? const KitChevron() : KitRowValue(value),
          onTap: () => unawaited(_pick(choices, code)),
        );
      },
    );
  }
}

/// The server-owned things a "By server" page lists.
enum ByServerKind { model, providers, tools, shell, readState }

/// What a hub row that several servers own opens: one row per saved server
/// with that server's own answer, each opening the server's page at the right
/// row. Naming the owner is the point (SC4, SC6); nothing here is edited
/// without going to the server it belongs to (SC11).
class ByServerScreen extends StatelessWidget {
  const ByServerScreen({
    super.key,
    required this.controller,
    required this.kind,
  });

  final ConnectionController controller;
  final ByServerKind kind;

  String _title(AppLocalizations copy) => switch (kind) {
    ByServerKind.model => copy.settingsHubModelRow,
    ByServerKind.providers => copy.settingsV3Providers,
    ByServerKind.tools => copy.settingsHubToolsRow,
    ByServerKind.shell => copy.settingsV3Shell,
    ByServerKind.readState => copy.shareSessionViewsTitle,
  };

  String? _note(AppLocalizations copy) => switch (kind) {
    ByServerKind.model => copy.settingsV3ModelByServerNote,
    ByServerKind.providers => copy.settingsV3ProvidersByServerNote,
    ByServerKind.tools => copy.settingsV3ToolsByServerNote,
    ByServerKind.shell => copy.settingsV3ShellByServerNote,
    ByServerKind.readState => copy.settingsV3ReadStateByServerNote,
  };

  /// The row on a server's page this page's rows lead to.
  String get _arrive => switch (kind) {
    ByServerKind.model => 'server-row-model',
    ByServerKind.providers => 'server-row-providers',
    ByServerKind.tools => 'server-row-tools',
    ByServerKind.shell => 'server-row-shell',
    ByServerKind.readState => 'server-row-share-read',
  };

  String? _value(AppLocalizations copy, ServerProfile p) {
    if (kind == ByServerKind.readState) {
      return controller.store.appPreferences.readStateConsent.enabled(p.id)
          ? copy.settingsV3On
          : copy.settingsV3Off;
    }
    if (kind != ByServerKind.model) return null;
    final (provider, model) = controller.store.modelFor(p.id);
    return provider == null || model == null
        ? copy.settingsV3ServerPick
        : serverModelLabel(controller, p, provider, model);
  }

  /// A connected server's row goes straight to what the row is about (the
  /// model picker, the providers or the tools page), so the person does not
  /// tap the same thing twice. A server that is not connected opens its page,
  /// where it can be connected. Shell and read state keep the server page:
  /// their control lives inside the row there.
  Future<void> _open(
    BuildContext context,
    ServerProfile p,
    AppLocalizations l10n,
    List<ServerProfile> servers,
  ) async {
    final copy = _settingsCopy(context);
    if (_isConnectedServer(controller, p)) {
      switch (kind) {
        case ByServerKind.model:
          await showModelPicker(
            context,
            title: newConversationsTitle(
              copy,
              serverDisplayName(p, l10n, among: servers),
            ),
          );
          return;
        case ByServerKind.providers:
        case ByServerKind.tools:
          final id = kind == ByServerKind.providers
              ? 'settings-providers'
              : 'settings-tools';
          final entry = allSearchEntries(
            copy,
          ).where((entry) => entry.id == id).firstOrNull;
          if (entry != null) {
            await entry.open(context, SearchScope.of(context, controller));
            return;
          }
        case ByServerKind.shell:
        case ByServerKind.readState:
          break;
      }
    }
    if (!context.mounted) return;
    await openServerPage(context, controller, p.id, arrive: _arrive);
  }

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final note = _note(copy);
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final servers = settingsServers(controller);
        return KitScreen(
          topBar: KitTopBar(title: _title(copy)),
          width: KitScreenWidth.reading,
          body: ListView(
            key: const ValueKey('by-server-list'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              if (note != null)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    bottom: tokens.space3,
                  ),
                  child: KitText(
                    note,
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                  ),
                ),
              KitRowGroup(
                children: [
                  for (final p in servers)
                    KitRow(
                      key: ValueKey('by-server-${p.id}'),
                      leading: KitRow.icon(
                        context,
                        isPhoneOwnServer(p)
                            ? AppIconography.phone
                            : AppIconography.server,
                      ),
                      title: serverDisplayName(p, l10n, among: servers),
                      supporting: TextSpan(
                        text: _isConnectedServer(controller, p)
                            ? copy.settingsV3Connected
                            : copy.settingsV3NotConnected,
                      ),
                      trailing: _value(copy, p) == null
                          ? const KitChevron()
                          : KitRowValue(_value(copy, p)!),
                      onTap: () => unawaited(_open(context, p, l10n, servers)),
                    ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}

/// A version as a person reads it: the number alone, without the label an
/// older build saved beside it ("Paseo daemon 0.9.2 (experimental)").
String _plainServerVersion(String raw) =>
    RegExp(r'\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?').firstMatch(raw)?.group(0) ??
    raw.trim();

/// The new-conversation model of [profile] as a person reads it, the same
/// way whether or not the app is connected to it: the catalog's own names
/// while it is loaded, the tidied ids otherwise.
String serverModelLabel(
  ConnectionController controller,
  ServerProfile profile,
  String providerID,
  String modelID,
) {
  final catalog = _isConnectedServer(controller, profile)
      ? controller.catalog
      : null;
  final model = catalog?.models
      .where((m) => m.providerID == providerID && m.id == modelID)
      .firstOrNull;
  final provider = catalog?.providers
      .where((p) => p.id == providerID)
      .firstOrNull;
  return readableModelLabel(
    providerID,
    modelID,
    modelName: model?.name,
    providerName: provider?.name,
  );
}
