part of '../servers_screen.dart';

extension _EditorSteps on _ProfileEditorScreenState {
  /// Closes the editor (nothing is saved yet), then takes the other way.
  VoidCallback? _leaveFor(VoidCallback? way) {
    if (way == null) return null;
    return () {
      if (_submitting) return;
      Navigator.of(context).pop();
      way();
    };
  }

  /// The kind's own name, the connect step's title: the person sees what
  /// they chose on the step that asks for its address.
  String _kindTitle(AppLocalizations copy) => switch (_backend) {
    ServerBackend.openCode => copy.firstRunAgentOpenCode,
    ServerBackend.paseo => copy.firstRunPaseoTitle,
    ServerBackend.codex => copy.firstRunAgentCodex,
  };

  /// Where Add server is, as the kit's staged progress: "Step 2 of 4 ·
  /// Pair or enter the address". The check is a step of its own while it
  /// runs, and the step before it again when it did not answer.
  Widget _stepLine(AppLocalizations copy, KitTokens tokens) {
    final of = _tailscale ? 5 : 4;
    final connect = _tailscale ? 3 : 2;
    final checking = _testing || _pairing || _submitting;
    final (step, label) = switch (_step) {
      _AddStep.kind => (1, copy.addServerStepKind),
      _AddStep.tailscale => (2, copy.addServerStepTailscale),
      _AddStep.connect when checking => (connect + 1, copy.addServerStepCheck),
      _AddStep.connect => (
        connect,
        _isCodex ? copy.addServerStepAddress : copy.addServerStepPair,
      ),
      _AddStep.ready => (of, copy.addServerStepReady),
    };
    return _Rails(
      key: _stepLineKey,
      child: KitProgressView(
        key: ValueKey('server-add-steps-$of'),
        progress: KitProgress.staged(
          key: const ValueKey('server-add-step-bar'),
          step: step,
          of: of,
          label: label,
          // Ready is the last step done, not begun: the bar is full.
          stepValue: _step == _AddStep.ready ? 1 : null,
          semanticsLabel: copy.addServerStepsLabel,
        ),
      ),
    );
  }

  /// Step 1: what runs on the computer, one choice that acts on tap, and
  /// the other ways in under it (this phone, Tailscale, outside agents).
  List<Widget> _kindStep(AppLocalizations copy, KitTokens tokens) => [
    SizedBox(height: tokens.space2),
    // The agents are not alternatives: one computer runs all of them at
    // once. This step only picks where to begin.
    _Rails(
      child: KitText(
        copy.firstRunAgentsSideBySide,
        key: const ValueKey('agent-choice-side-by-side'),
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
    ),
    _BackendChoice(
      key: const ValueKey('server-kind-step'),
      selected: null,
      onSelected: _chooseKind,
    ),
    _OtherWays(
      onPhoneSetup: _leaveFor(widget.onPhoneSetup),
      onTailscale: platformCapabilities.supportsTailscaleHandoff
          ? _chooseTailscale
          : null,
      onExternalAgents: _leaveFor(widget.onExternalAgents),
    ),
  ];

  /// Tailscale as a step: the phone's side (the app, the VPN), then the
  /// address and the server's own sign-in on the connect step after it.
  List<Widget> _tailscaleStep(AppLocalizations copy, KitTokens tokens) => [
    SizedBox(height: tokens.space2),
    _Rails(
      child: Column(
        key: const ValueKey('server-tailscale-step'),
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitText(copy.tailscaleIntro, tone: KitTextTone.secondary),
          SizedBox(height: tokens.sectionGap),
          const TailscalePhoneSteps(),
          SizedBox(height: tokens.sectionGap),
          const TailscaleHelpFold(),
        ],
      ),
    ),
  ];

  /// The finished flow: the drawing linked, what it reached, and the one
  /// way on.
  List<Widget> _readyStep(AppLocalizations copy, KitTokens tokens) {
    final profile = _readyProfile!;
    return [
      _linkMoment(copy, tokens),
      _Rails(
        child: Column(
          key: const ValueKey('server-ready-step'),
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              header: true,
              child: KitText(
                copy.addServerReadyTitle(profile.name),
                role: KitTextRole.headline,
              ),
            ),
            SizedBox(height: tokens.space2),
            KitText(copy.addServerReadyBody, tone: KitTextTone.secondary),
          ],
        ),
      ),
    ];
  }

  /// The address or the pairing code, the check and its verdicts: the
  /// connect step of Add server, and the whole form everywhere else.
  List<Widget> _connectStep(
    AppLocalizations copy,
    KitTokens tokens, {
    required bool isNew,
    required bool showsCommand,
  }) => [
    if (_tailscale)
      _Rails(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(copy.tailscaleEditorDetail),
            KitInset(
              child: KitButton.tertiary(
                // On Add server the Tailscale step is one
                // step back; a saved server opens the page.
                onPressed: _stepped
                    ? () => _goTo(_AddStep.tailscale)
                    : _tailscaleHelp,
                icon: AppIconography.secureNetwork,
                label: copy.tailscaleHelp,
              ),
            ),
            if (_testResult?.ok == false || _submitFailure != null)
              KitText(copy.tailscaleRecovery),
          ],
        ),
      ),
    // The connection's moment at the head of the form: the
    // drawing, its line, and a slow check's offer to stop.
    // Every check and save starts by scrolling back to it; the
    // verdict itself is under the address field.
    Column(
      key: _statusKey,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [if (isNew) _linkMoment(copy, tokens), _progress(copy, tokens)],
    ),
    if (showsCommand) ...[
      SizedBox(height: tokens.space1),
      _ComputerCommand(
        key: ValueKey('connect-computer-command-${_backend.name}'),
        backend: _backend,
        // Pairing, the main path, right under the command
        // that prints the code.
        below: _isCodex
            ? null
            : _PairingActions(
                // The command is on screen above, with its
                // copy button; only the next move is left
                // to say.
                instructions: platformCapabilities.supportsQrPairing
                    ? copy.firstRunPairingNextScan
                    : copy.firstRunPairingNextPaste,
                busy: _pairing,
                notice: _pairingNotice,
                failure: _pairingFailure,
                onPaste: _pairing || _submitting
                    ? null
                    : () => unawaited(_pastePairing()),
                // Rendered only where a camera path exists.
                // Desktop gets no affordance at all rather
                // than one that opens and fails.
                onScan: platformCapabilities.supportsQrPairing
                    ? () => unawaited(_scanPairing())
                    : null,
              ),
      ),
    ],
    if (_secureStorageNotice case final notice?)
      _Rails(
        child: KitNotice(
          key: const ValueKey('server-secure-storage-notice'),
          tone: AppStatusTone.neutral,
          message: notice,
        ),
      ),
    // Unfolds when a check finds the password missing,
    // folds away once it is typed (design standard §10).
    KitReveal(
      child: !_needsPassword
          ? null
          : _Rails(
              child: Semantics(
                container: true,
                liveRegion: true,
                excludeSemantics: true,
                label: copy.e7SetupMissingPasswordLong,
                child: KitNotice(
                  tone: AppStatusTone.neutral,
                  icon: AppIconography.locked,
                  liveRegion: false,
                  message: copy.e7SetupMissingPasswordShort,
                ),
              ),
            ),
    ),
    if (_isCodex) ...[
      _Rails(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: _buildCodexFields(copy, tokens),
        ),
      ),
      _Rails(
        child: KitText(
          _isPaseo ? copy.paseoSetupNotice : copy.codexApprovalRecoveryNotice,
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
      ),
      _Rails(child: _testAction(copy)),
    ] else ...[
      if (_foldsManualAddress) SizedBox(height: tokens.space2),
      _manualAddress(copy, tokens),
      if (!_tailscale && !showsCommand)
        // Editing a saved server: pairing again (a rotated
        // password) stays one tap away, under the fields
        // the person came to change.
        _Rails(
          child: _PairingActions(
            compact: true,
            busy: _pairing,
            notice: _pairingNotice,
            failure: _pairingFailure,
            onPaste: _pairing || _submitting
                ? null
                : () => unawaited(_pastePairing()),
            onScan: platformCapabilities.supportsQrPairing
                ? () => unawaited(_scanPairing())
                : null,
          ),
        ),
      SizedBox(height: tokens.space1),
      _moreOptions(copy, tokens),
    ],
  ];
}

/// Add server's first step (ledger row 15, P3.9): what runs on the
/// computer, as the kit's single choice list (KIT-25), a row each with a
/// line saying what it is. It acts on tap: the answer is the next step.
class _BackendChoice extends StatelessWidget {
  const _BackendChoice({
    super.key,
    required this.selected,
    required this.onSelected,
  });

  final ServerBackend? selected;
  final ValueChanged<ServerBackend> onSelected;

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    return Padding(
      key: const ValueKey('server-backend-selector'),
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        top: tokens.space3,
        end: tokens.gutter,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitChoiceList<ServerBackend>.single(
            semanticsLabel: copy.addServerConnectTo,
            selected: selected,
            onSelected: onSelected,
            choices: [
              KitChoice(
                key: const ValueKey('server-backend-opencode'),
                value: ServerBackend.openCode,
                leading: KitRow.icon(context, AppIconography.computer),
                title: copy.addServerTypeOpenCode,
                supporting: copy.addServerTypeOpenCodeDetail,
              ),
              KitChoice(
                key: const ValueKey('server-backend-codex'),
                value: ServerBackend.codex,
                leading: KitRow.icon(context, AppIconography.code),
                title: copy.addServerTypeCodex,
                supporting: copy.addServerTypeCodexDetail,
              ),
              KitChoice(
                key: const ValueKey('server-backend-paseo'),
                value: ServerBackend.paseo,
                leading: KitRow.icon(context, AppIconography.agent),
                title: copy.addServerTypePaseo,
                supporting: copy.addServerTypePaseoDetail,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Add server's other ways in, one row each (R3: they live here, not as a
/// second panel on the servers list): this phone, Tailscale and external
/// agents. This phone and external agents close the form and open their own
/// setup; Tailscale is the flow's next step. Null hides a row.
class _OtherWays extends StatelessWidget {
  const _OtherWays({
    required this.onPhoneSetup,
    required this.onTailscale,
    required this.onExternalAgents,
  });

  final VoidCallback? onPhoneSetup;
  final VoidCallback? onTailscale;
  final VoidCallback? onExternalAgents;

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final phone = onPhoneSetup;
    final tailscale = onTailscale;
    final external = onExternalAgents;
    if (phone == null && tailscale == null && external == null) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.sectionGap),
      child: KitRowGroup(
        key: const ValueKey('server-editor-other-ways'),
        label: copy.serversAddOtherWays,
        children: [
          if (phone != null)
            _PhoneSetupEntry(
              key: const ValueKey('quick-add-phone-card'),
              onTap: phone,
            ),
          if (tailscale != null)
            KitRow(
              key: const ValueKey('welcome-tailscale-card'),
              leading: KitRow.icon(context, AppIconography.secureNetwork),
              title: copy.tailscaleTitle,
              supporting: TextSpan(text: copy.onboardingPrivateNetwork),
              supportingMaxLines: 2,
              trailing: const KitChevron(),
              onTap: tailscale,
            ),
          if (external != null)
            KitRow(
              key: const ValueKey('server-editor-external-agents'),
              leading: KitRow.icon(context, AppIconography.network),
              title: copy.a2aTitle,
              trailing: const KitChevron(),
              onTap: external,
            ),
        ],
      ),
    );
  }
}
