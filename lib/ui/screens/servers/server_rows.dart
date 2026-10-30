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
    required this.name,
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
    required this.onSettings,
    required this.onRemove,
    required this.onAccount,
  });

  final ServerProfile profile;

  /// The shared disambiguated name ([serverDisplayName]), port and all.
  final String name;
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
  final VoidCallback onSettings;
  final VoidCallback onRemove;
  final VoidCallback onAccount;

  /// Where the server is, which the row itself no longer says: its full
  /// address and, for Codex and Paseo, the project folder.
  Future<void> _showDetails(BuildContext context, AppLocalizations copy) =>
      showKitTechnicalDetails(
        context,
        title: copy.serverRowDetailsTitle(name),
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
    final menu = <KitMenuItem>[
      if (showAccount)
        KitMenuItem(label: copy.agentAccountTitle, onSelected: onAccount),
      KitMenuItem(label: copy.e7SetupConnect, onSelected: onConnect),
      if (queued > 0 && moveDestination != null)
        KitMenuItem(
          key: ValueKey('server-move-queued-${p.id}'),
          label: copy.serverRowMoveQueued(queued, moveDestination!),
          onSelected: onMoveQueued,
        ),
      // The server's own page: connection, what it keeps in this app, and
      // what is saved on it (Servers › the server).
      KitMenuItem(
        key: ValueKey('server-settings-${p.id}'),
        label: copy.settingsV3ServerSettings,
        onSelected: onSettings,
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
    ];
    return Semantics(
      container: true,
      selected: connected,
      child: KitRow(
        key: ValueKey('server-row-${p.id}'),
        leading: waiting > 0
            ? KitNeedsYou.mark()
            : KitRowIcon(
                isPhoneOwnServer(p)
                    ? AppIconography.phone
                    : AppIconography.server,
                current: connected,
              ),
        title: name,
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
            if (reentry != null)
              TextSpan(text: '$reentry · ', style: wordStyle),
            if (queued > 0)
              TextSpan(
                text: '${copy.serverRowQueuedWaiting(queued)} · ',
                style: wordStyle,
              ),
            // Every row says its state: a saved server that is not connected
            // says so, like the connected one does.
            if (!connected && waiting == 0 && running == 0)
              TextSpan(text: '${copy.settingsV3NotConnected} · '),
            TextSpan(text: kind),
          ],
        ),
        supportingMaxLines: 2,
        selected: connected,
        enabled: !busy,
        disabledReason: busy ? copy.e7SetupServerOperation : null,
        // The row opens the server's own page; connecting is a named action
        // there (and in this menu), never what a tap on the name does.
        onTap: onSettings,
        menuLabel: name,
        menu: menu,
        // The same visible "More" as the phone's own row, so every row of the
        // list offers its actions the same way.
        trailing: KitRowMenu(
          key: ValueKey('server-row-menu-${p.id}'),
          menuLabel: name,
          items: menu,
        ),
      ),
    );
  }
}

/// A version as a person reads it: the number alone, without the label an
/// older build saved beside it ("Paseo daemon 0.9.2 (experimental)").
String _plainServerVersion(String raw) =>
    RegExp(r'\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?').firstMatch(raw)?.group(0) ??
    raw.trim();

/// Where the first-run welcome's one question can be answered.
enum _WelcomeChoice { phone, computer }

/// First run asks the only real fork, one question with two plain answers
/// (vision 2026-10-20, concept A): "Where should your agent run?", a large
/// card for this phone with what setting up costs, a card for a computer,
/// and one main button that names the chosen answer. The demo and phone
/// setup's own page (Termux, what to install) are quiet text actions under
/// it. Product names, private networks and the guide are met later, at the
/// step where each one matters.
class _WelcomeView extends StatelessWidget {
  final KitTopBar topBar;
  final bool busy;

  /// The detected on-device server. It renders nothing unless a running
  /// server was actually observed: a live thing the app found outranks
  /// every generic choice.
  final Widget runningServer;

  /// The phone card's words and the main button's action for it; null
  /// where this platform has no phone path.
  final PhoneWelcomeOffer? phone;
  final _WelcomeChoice choice;
  final ValueChanged<_WelcomeChoice> onChoice;
  final VoidCallback onComputer;
  final VoidCallback onPhoneSetup;
  final VoidCallback onDemo;

  /// OpenCode or Termux was found on this phone: [runningServer] leads the
  /// page, the in-app server is the fresh start after it, and the other
  /// ways follow in a compact list. No question.
  final bool found;

  const _WelcomeView({
    required this.topBar,
    required this.busy,
    this.found = false,
    required this.runningServer,
    required this.phone,
    required this.choice,
    required this.onChoice,
    required this.onComputer,
    required this.onPhoneSetup,
    required this.onDemo,
  });

  @override
  Widget build(BuildContext context) {
    final copy = _connectionL10n(context);
    final tokens = KitTokens.of(context);
    // Keyed, so the found server keeps its state while the page around it
    // changes shape.
    final lead = KeyedSubtree(
      key: const ValueKey('welcome-termux'),
      child: runningServer,
    );
    if (found) return _found(context, copy, tokens, lead);
    final phone = this.phone;
    // Off the phone path the computer is the only answer, chosen.
    final chosen = phone == null ? _WelcomeChoice.computer : choice;
    final phoneWorking = phone != null && phone.working;
    final KitButton primary = switch (chosen) {
      _WelcomeChoice.phone => KitButton.primary(
        key: const ValueKey('welcome-primary'),
        hero: true,
        label: phone!.action,
        working: phone.working,
        onPressed: busy || phone.working ? null : phone.onPressed,
      ),
      _WelcomeChoice.computer => KitButton.primary(
        key: const ValueKey('welcome-primary'),
        hero: true,
        label: copy.welcomeComputerConnect,
        onPressed: busy || phoneWorking ? null : onComputer,
      ),
    };
    final quiet = <Widget>[
      KitButton.tertiary(
        key: const ValueKey('welcome-choice-demo'),
        label: copy.firstRunJustShowMe,
        onPressed: busy || phoneWorking ? null : onDemo,
      ),
      if (phone != null)
        KitButton.tertiary(
          key: const ValueKey('welcome-more-ways'),
          label: copy.welcomeMoreWays,
          onPressed: busy || phoneWorking ? null : onPhoneSetup,
        ),
    ];
    return KitScreen(
      topBar: topBar,
      width: KitScreenWidth.reading,
      // The one main action at the thumb, naming the chosen answer.
      bottom: primary,
      body: ListView(
        key: const ValueKey('first-run-welcome'),
        padding: EdgeInsetsDirectional.only(
          // Air above the question; large text needs the room for words.
          top: AppTheme.stackedActions(context)
              ? tokens.space4
              : tokens.space6 + tokens.space6,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          if (platformCapabilities.supportsTermux) lead,
          _Rails(
            child: KitHeroQuestion(
              copy.firstRunWhereQuestion,
              textKey: const ValueKey('welcome-question'),
            ),
          ),
          SizedBox(height: tokens.space6 + tokens.space2),
          _Rails(
            child: KitChoiceCards<_WelcomeChoice>(
              key: const ValueKey('welcome-choices'),
              semanticsLabel: copy.firstRunWhereQuestion,
              selected: chosen,
              busy: phoneWorking ? _WelcomeChoice.phone : null,
              onSelected: onChoice,
              choices: [
                if (phone != null)
                  KitChoice(
                    key: const ValueKey('welcome-choice-phone'),
                    value: _WelcomeChoice.phone,
                    leading: const KitIcon(AppIconography.phone),
                    title: copy.onboardingTermuxSetup,
                    supporting: phone.line,
                  ),
                KitChoice(
                  key: const ValueKey('welcome-choice-computer'),
                  value: _WelcomeChoice.computer,
                  leading: const KitIcon(AppIconography.computer),
                  title: copy.firstRunOnComputer,
                  supporting: copy.welcomeComputerLine,
                ),
              ],
            ),
          ),
          // The quiet ways in, under the answers they sit beside.
          SizedBox(height: tokens.space2),
          _Rails(
            child: Wrap(spacing: tokens.space4, children: quiet),
          ),
        ],
      ),
    );
  }

  /// OpenCode or Termux was found on this phone: that server leads (with
  /// its move to the in-app server under it), then a fresh in-app setup,
  /// then the other ways.
  Widget _found(
    BuildContext context,
    AppLocalizations copy,
    KitTokens tokens,
    Widget lead,
  ) {
    final busyReason = busy ? copy.e7SetupServerOperation : null;
    Widget choice({
      required String key,
      required IconData icon,
      required String title,
      required String detail,
      required VoidCallback onTap,
    }) => KitRow(
      key: ValueKey(key),
      leading: KitRow.icon(context, icon),
      title: title,
      titleMaxLines: 2,
      supporting: TextSpan(text: detail),
      supportingMaxLines: 3,
      trailing: const KitChevron(),
      enabled: !busy,
      disabledReason: busyReason,
      onTap: onTap,
    );
    final phone = this.phone;
    // A setup in the app that already started says where it stands.
    final started = phone != null && phone.state != SetupState.idle;
    return KitScreen(
      topBar: topBar,
      width: KitScreenWidth.reading,
      body: ListView(
        key: const ValueKey('first-run-welcome'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space6,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          lead,
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            key: const ValueKey('welcome-in-app-instead'),
            children: [
              choice(
                key: 'welcome-choice-in-app',
                icon: AppIconography.phone,
                title: copy.termuxInAppInstead,
                detail: started ? phone.line : copy.termuxInAppInsteadDetail,
                onTap: started ? phone.onPressed : onPhoneSetup,
              ),
            ],
          ),
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            key: const ValueKey('welcome-other-ways'),
            label: copy.phoneSetupStartOtherWays,
            children: [
              choice(
                key: 'welcome-choice-computer',
                icon: AppIconography.computer,
                title: copy.firstRunOnComputer,
                detail: copy.welcomeComputerLine,
                onTap: onComputer,
              ),
              choice(
                key: 'welcome-choice-demo',
                icon: AppIconography.playCircle,
                title: copy.firstRunJustShowMe,
                detail: copy.welcomeDemoLine,
                onTap: onDemo,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

String _knownOpenCodeGeneration(ServerProfile profile) => 'OpenCode 2';

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
