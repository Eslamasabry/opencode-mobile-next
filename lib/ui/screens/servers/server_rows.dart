part of '../servers_screen.dart';

/// The page's side rails for a part that does not pad itself (the kit's
/// rows and groups do), with a little air above and below.
class _Rails extends StatelessWidget {
  const _Rails({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: tokens.gutter,
        vertical: tokens.space1,
      ),
      child: child,
    );
  }
}

/// One saved server as a kit row (design standard §6): its kind as the
/// icon, the name, then what it runs and its address, with a credential that
/// must be re-entered said first. The server the app is connected to
/// carries the current mark and "Connected" leading its line. Tapping
/// connects; the rest is on long-press or right-click (KIT-28), destructive
/// last.
class _ServerRow extends StatelessWidget {
  const _ServerRow({
    required this.profile,
    required this.connected,
    required this.snapshot,
    required this.working,
    required this.busy,
    required this.showAccount,
    required this.queued,
    required this.moveDestination,
    required this.onMoveQueued,
    required this.onConnect,
    required this.onEdit,
    required this.onRemove,
    required this.onAccount,
  });

  final ServerProfile profile;
  final bool connected;

  /// The monitor's current words about this server; null says nothing.
  final ProfileAttentionSnapshot? snapshot;

  /// Conversations running on the connected server; null for the others,
  /// whose count comes from [snapshot].
  final int? working;
  final bool busy;
  final bool showAccount;

  /// Prompts queued for this server, waiting until it can be reached.
  final int queued;

  /// The connected server they can move to, by name; null: none.
  final String? moveDestination;
  final VoidCallback onMoveQueued;
  final VoidCallback onConnect;
  final VoidCallback onEdit;
  final VoidCallback onRemove;
  final VoidCallback onAccount;

  /// Where the server is, which the row itself no longer says: its full
  /// address and, for Codex and Paseo, the project folder.
  Future<void> _showDetails(BuildContext context, AppLocalizations copy) =>
      showKitTechnicalDetails(
        context,
        title: copy.serverRowDetailsTitle(profile.name),
        text: '',
        sheetKey: ValueKey('server-details-sheet-${profile.id}'),
        values: [
          KitTechnicalValue(copy.connectionServerAddress, profile.baseUrl),
          // What the last check found out, so a person can tell which
          // build a server runs without leaving the list.
          if (profile.serverVersion?.trim().isNotEmpty == true)
            KitTechnicalValue(
              copy.serverRowDetailsVersion,
              _plainServerVersion(profile.serverVersion!),
            ),
          if (profile.usesAgentSocket && profile.codexDirectory.isNotEmpty)
            KitTechnicalValue(copy.codexProjectFolder, profile.codexDirectory),
        ],
      );

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final p = profile;
    // What it is, never where: the address and the folder are in the
    // menu's Details.
    final kind = switch (p.backend) {
      ServerBackend.paseo => copy.addServerTypePaseo,
      ServerBackend.codex => copy.addServerTypeCodex,
      ServerBackend.openCode => _knownOpenCodeGeneration(p),
    };
    final reentry = p.requiresPasswordReentry
        ? copy.e7SetupPasswordRequired
        : p.requiresCodexTokenReentry
        ? copy.e7SetupTokenRequired
        : null;
    final waiting = connected ? 0 : (snapshot?.requests.length ?? 0);
    final running = working ?? snapshot?.runningCount ?? 0;
    final wordStyle = KitText.styleOf(
      context,
      KitTextRole.label,
      tone: KitTextTone.primary,
    );
    // The state first, in words (R1, the switcher's words): "Needs you",
    // "2 working", a credential to enter again, then what it is.
    return KitRow(
      key: ValueKey('server-row-${p.id}'),
      leading: waiting > 0
          ? KitNeedsYou.mark()
          : KitRowIcon(
              isPhoneOwnServer(p)
                  ? AppIconography.phone
                  : AppIconography.server,
              current: connected,
            ),
      title: p.name,
      supporting: TextSpan(
        children: [
          if (connected) kitCurrentSpan(context, copy.serverRowConnected),
          if (waiting > 0) KitNeedsYou.span(context, count: waiting),
          if (running > 0)
            TextSpan(
              text: '${copy.otherServerWorking(running)} · ',
              style: wordStyle,
            ),
          // LOOK-5 interim: a credential to re-enter is said in words, in
          // the strong label weight, never in the danger colour.
          if (reentry != null) TextSpan(text: '$reentry · ', style: wordStyle),
          if (queued > 0)
            TextSpan(
              text: '${copy.serverRowQueuedWaiting(queued)} · ',
              style: wordStyle,
            ),
          TextSpan(text: kind),
        ],
      ),
      supportingMaxLines: 2,
      selected: connected,
      enabled: !busy,
      disabledReason: busy ? copy.e7SetupServerOperation : null,
      onTap: onConnect,
      menuLabel: p.name,
      menu: [
        if (showAccount)
          KitMenuItem(label: copy.agentAccountTitle, onSelected: onAccount),
        KitMenuItem(label: copy.e7SetupConnect, onSelected: onConnect),
        if (queued > 0 && moveDestination != null)
          KitMenuItem(
            key: ValueKey('server-move-queued-${p.id}'),
            label: copy.serverRowMoveQueued(queued, moveDestination!),
            onSelected: onMoveQueued,
          ),
        KitMenuItem(label: copy.e7SetupEdit, onSelected: onEdit),
        KitMenuItem(
          key: ValueKey('server-details-${p.id}'),
          label: copy.kitDetails,
          onSelected: () => unawaited(_showDetails(context, copy)),
        ),
        // Destructive: last, confirmed by the sheet it opens.
        KitMenuItem(
          label: copy.capsuleRemove,
          destructive: true,
          onSelected: onRemove,
        ),
      ],
    );
  }
}

/// A version as a person reads it: the number alone, without the label an
/// older build saved beside it ("Paseo daemon 0.9.2 (experimental)").
String _plainServerVersion(String raw) =>
    RegExp(r'\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?').firstMatch(raw)?.group(0) ??
    raw.trim();

/// First run asks the only real fork, one question with plain answers (UX
/// plan 5.6 step 1). Product names, private networks and the guide are met
/// later, at the step where each one matters.
class _WelcomeView extends StatelessWidget {
  final bool busy;

  /// The detected on-device server, above the question. It renders nothing
  /// unless a running server was actually observed: a live thing the app
  /// found outranks every generic choice.
  final Widget runningServer;

  /// A phone setup that was started and not finished (or finished with
  /// nothing saved). Like [runningServer] it renders nothing when there is
  /// no such job, and it outranks the generic question when there is.
  final Widget phoneSetup;
  final VoidCallback onComputer;
  final VoidCallback onPhone;
  final VoidCallback onDemo;

  /// OpenCode or Termux was found on this phone: [runningServer] leads the
  /// page, the in-app server is the fresh start after it, and the other
  /// ways follow in a compact list. No welcome hero.
  final bool found;

  const _WelcomeView({
    required this.busy,
    this.found = false,
    required this.runningServer,
    required this.phoneSetup,
    required this.onComputer,
    required this.onPhone,
    required this.onDemo,
  });

  @override
  Widget build(BuildContext context) {
    final copy = _connectionL10n(context);
    final tokens = KitTokens.of(context);
    final busyReason = busy ? copy.e7SetupServerOperation : null;
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    Widget choice({
      required String key,
      required IconData icon,
      required String title,
      required String detail,
      required VoidCallback onTap,
      bool recommended = false,
    }) => KitRow(
      key: ValueKey(key),
      leading: KitRow.icon(context, icon),
      title: title,
      titleMaxLines: 2,
      supporting: TextSpan(
        children: [
          if (recommended) kitRecommendedSpan(context),
          TextSpan(text: detail),
        ],
      ),
      supportingMaxLines: 3,
      trailing: const KitChevron(),
      enabled: !busy,
      disabledReason: busyReason,
      onTap: onTap,
    );
    // Keyed, so the found server keeps its state while the page around it
    // changes shape.
    final lead = KeyedSubtree(
      key: const ValueKey('welcome-termux'),
      child: runningServer,
    );
    final computer = choice(
      key: 'welcome-choice-computer',
      icon: AppIconography.server,
      title: copy.firstRunOnComputer,
      detail: copy.firstRunOnComputerDetail,
      onTap: onComputer,
    );
    final demo = choice(
      key: 'welcome-choice-demo',
      icon: AppIconography.playCircle,
      title: copy.firstRunJustShowMe,
      detail: copy.onboardingDemoNote,
      onTap: onDemo,
    );
    if (found) {
      return ListView(
        key: const ValueKey('first-run-welcome'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space6,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          lead,
          SizedBox(height: tokens.sectionGap),
          if (platformCapabilities.supportsTermux) phoneSetup,
          KitRowGroup(
            key: const ValueKey('welcome-in-app-instead'),
            children: [
              choice(
                key: 'welcome-choice-in-app',
                icon: AppIconography.phone,
                title: copy.termuxInAppInstead,
                detail: copy.termuxInAppInsteadDetail,
                onTap: onPhone,
              ),
            ],
          ),
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            key: const ValueKey('welcome-other-ways'),
            label: copy.phoneSetupStartOtherWays,
            children: [computer, demo],
          ),
        ],
      );
    }
    return ListView(
      key: const ValueKey('first-run-welcome'),
      padding: EdgeInsetsDirectional.only(
        top: tokens.space6,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        // The hero (design standard §10): this phone and the computer the
        // agent runs on. Drawn in once; the welcome is a resting screen, so
        // it never loops. From 1.3x text it steps aside: the picture says
        // nothing the words don't, and the choices are what the person came
        // for (emulator QA B3: at 2.0 they started below the fold).
        if (!largeText) ...[
          const _Rails(
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: KitIllustration(
                key: ValueKey('servers-welcome-hero'),
                scene: ServersWelcomeScene(),
              ),
            ),
          ),
          SizedBox(height: tokens.space5),
        ],
        _Rails(
          child: Semantics(
            header: true,
            child: KitText(
              copy.onboardingValueTitle,
              role: KitTextRole.largeTitle,
            ),
          ),
        ),
        SizedBox(height: tokens.space2),
        _Rails(
          child: KitText(copy.onboardingValueBody, tone: KitTextTone.secondary),
        ),
        SizedBox(height: tokens.space6),
        if (platformCapabilities.supportsTermux) phoneSetup,
        lead,
        _Rails(
          child: Semantics(
            header: true,
            child: KitText(
              copy.firstRunWhereQuestion,
              key: const ValueKey('welcome-question'),
              role: KitTextRole.headline,
            ),
          ),
        ),
        SizedBox(height: tokens.space2),
        // FB3: someone with no server gets one clear default, as the
        // phone needs nothing else. The computer and the demo stay one tap
        // away, under "Other ways".
        if (platformCapabilities.supportsTermux) ...[
          KitRowGroup(
            key: const ValueKey('welcome-recommended'),
            children: [
              choice(
                key: 'welcome-choice-phone',
                icon: AppIconography.phone,
                title: copy.onboardingTermuxSetup,
                detail: copy.firstRunOnPhoneDetail,
                onTap: onPhone,
                recommended: true,
              ),
            ],
          ),
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            key: const ValueKey('welcome-other-ways'),
            label: copy.phoneSetupStartOtherWays,
            children: [computer, demo],
          ),
        ] else
          KitRowGroup(children: [computer, demo]),
      ],
    );
  }
}

/// The phone has one managed listener. Choosing between retained generation
/// profiles is a runtime decision, not permission to redetect/rewrite either.
bool _needsManagedRuntimeChoice(
  ServerProfile profile,
  List<ServerProfile> profiles,
) {
  if (!platformCapabilities.supportsTermux ||
      profile.backend != ServerBackend.openCode ||
      !TermuxBridge.managesServerUrl(profile.baseUrl) ||
      _knownOpenCodeFlavor(profile) == null) {
    return false;
  }
  return profiles.any(
    (other) =>
        other.id != profile.id &&
        other.backend == ServerBackend.openCode &&
        TermuxBridge.managesServerUrl(other.baseUrl) &&
        _knownOpenCodeFlavor(other) != null &&
        _knownOpenCodeFlavor(other) != _knownOpenCodeFlavor(profile),
  );
}

ServerFlavor? _knownOpenCodeFlavor(ServerProfile profile) {
  if (profile.flavor == ServerFlavor.v2) return ServerFlavor.v2;
  if (profile.flavor == ServerFlavor.v1 &&
      profile.serverVersion?.trim().isNotEmpty == true) {
    return ServerFlavor.v1;
  }
  return null;
}

/// Legacy profiles default to v1 without a probe. Only the cached version
/// proves that this default was confirmed. v2 never comes from that default.
String _knownOpenCodeGeneration(ServerProfile profile) =>
    switch (_knownOpenCodeFlavor(profile)) {
      ServerFlavor.v2 => 'OpenCode 2',
      ServerFlavor.v1 => 'OpenCode 1',
      _ => 'OpenCode',
    };

/// A phone feature must stay discoverable when the current server is remote.
/// One entry for every way of running an agent on this phone: it opens phone
/// setup (screen A), where the in-app setup leads and Termux is one of the
/// "Other ways".
class _PhoneSetupEntry extends StatelessWidget {
  const _PhoneSetupEntry({super.key, required this.onTap});
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => KitRow(
    leading: KitRow.icon(context, AppIconography.phone),
    title: _connectionL10n(context).onboardingTermuxSetup,
    supporting: TextSpan(
      text: _connectionL10n(context).phoneSetupStartEntryDetail,
    ),
    supportingMaxLines: 2,
    trailing: const KitChevron(),
    enabled: onTap != null,
    disabledReason: onTap == null
        ? _connectionL10n(context).e7SetupServerOperation
        : null,
    onTap: onTap,
  );
}
