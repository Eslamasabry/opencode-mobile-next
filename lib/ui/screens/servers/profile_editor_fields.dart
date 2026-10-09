part of '../servers_screen.dart';

extension _EditorFields on _ProfileEditorScreenState {
  /// The Codex and Paseo fields: address, project folder, token, then the
  /// name most people never change (KIT-20: a labelled kit field each; the
  /// token is the secret kind, never prefilled).
  List<Widget> _buildCodexFields(AppLocalizations copy, KitTokens tokens) => [
    SizedBox(height: tokens.space3),
    KeyedSubtree(
      key: _addressKey,
      child: KitField(
        label: copy.connectionServerAddress,
        kind: KitFieldKind.url,
        controller: _url,
        focusNode: _urlFocus,
        fieldKey: const ValueKey('codex-server-address-field'),
        hint: _isPaseo ? copy.paseoAddressHint : copy.codexAddressHint,
        // No standing ws:// / wss:// rule: the field checks the address
        // when the person moves on and says what is wrong right here.
        error: _error == null ? null : setupUiMessage(copy, _error!),
        enabled: !_submitting,
        disabledReason: _submitting ? copy.e7SetupSaving : null,
        textInputAction: TextInputAction.next,
        onSubmitted: (_) => _codexDirectoryFocus.requestFocus(),
        onChanged: _urlChanged,
      ),
    ),
    ?_notSameNetworkLink(),
    _verdicts(copy, tokens),
    SizedBox(height: tokens.space4),
    KitField(
      label: copy.codexProjectFolder,
      kind: KitFieldKind.path,
      controller: _codexDirectory,
      focusNode: _codexDirectoryFocus,
      fieldKey: const ValueKey('codex-project-directory-field'),
      hint: '/work/my-project',
      enabled: !_submitting,
      disabledReason: _submitting ? copy.e7SetupSaving : null,
      textInputAction: TextInputAction.next,
      onSubmitted: (_) => _codexTokenFocus.requestFocus(),
      onChanged: (_) => _fieldChanged(),
    ),
    SizedBox(height: tokens.space4),
    KitField.secret(
      label: _needsCodexToken
          ? copy.codexTokenReentry
          : _isPaseo
          ? copy.paseoPasswordLabel
          : copy.codexTokenLabel,
      controller: _codexToken,
      focusNode: _codexTokenFocus,
      fieldKey: const ValueKey('codex-connection-token-field'),
      revealKey: const ValueKey('codex-token-visibility'),
      pasteKey: const ValueKey('codex-token-paste'),
      replaceKey: const ValueKey('codex-token-replace'),
      helper: _isPaseo ? copy.paseoPasswordHelp : copy.codexTokenStorageHelp,
      saved: _heldToken != null,
      onReplace: () => _set(() {
        _heldToken = null;
        _invalidateProbe();
      }),
      autofocus: _needsCodexToken || widget.focusPassword,
      enabled: !_submitting,
      disabledReason: _submitting ? copy.e7SetupSaving : null,
      textInputAction: TextInputAction.done,
      onSubmitted: (_) => _save(),
      onChanged: _tokenChanged,
    ),
    // What most people never change, after what they must fill in.
    SizedBox(height: tokens.space4),
    KitField(
      label: copy.connectionDisplayName,
      controller: _name,
      focusNode: _nameFocus,
      fieldKey: const ValueKey('codex-server-name-field'),
      hint: copy.connectionDisplayNameHint,
      enabled: !_submitting,
      disabledReason: _submitting ? copy.e7SetupSaving : null,
      textInputAction: TextInputAction.done,
      onSubmitted: (_) => _save(),
      onChanged: (_) => _fieldChanged(),
    ),
    SizedBox(height: tokens.space4),
  ];

  Widget _buildCodexProbeVerdict(AppLocalizations copy) {
    final result = _codexTestResult!;
    // A verdict is a notice on the form's rails, not a filled block (§3).
    return KeyedSubtree(
      key: const ValueKey('codex-probe-verdict'),
      child: KitNotice(
        key: ValueKey(result.ok ? 'codex-test-success' : 'codex-test-failure'),
        tone: result.ok ? AppStatusTone.ok : AppStatusTone.failure,
        message: result.ok
            ? copy.codexConnectionVerified
            : setupUiMessage(copy, result.message),
        // What the check found out, in words: the daemon's version and
        // which agents it can run right now.
        notes: [
          if (result.ok && (result.version?.trim().isNotEmpty ?? false))
            copy.e7SettingsVersion(result.version!.trim()),
          if (result.ok && result.runtimes != null)
            result.runtimes!.isEmpty
                ? copy.paseoCheckNoneReady
                : copy.paseoCheckReady(result.runtimes!.join(', ')),
        ],
        actions: [if (!result.ok && _verdictFromSave) _saveAnywayAction(copy)],
      ),
    );
  }

  KitAction _saveAnywayAction(AppLocalizations copy) => KitAction(
    key: const ValueKey('server-save-anyway'),
    label: copy.addServerSaveAnyway,
    onPressed: _submitting ? null : () => unawaited(_save(anyway: true)),
  );

  /// The host the person is connecting to, for the drawing's caption.
  String get _host {
    final url = _isCodex
        ? (_isPaseo
              ? normalizePaseoServerUrl(_url.text)
              : normalizeCodexServerUrl(_url.text))
        : normalizeServerProfileUrl(_url.text);
    final host = Uri.tryParse(url)?.host ?? '';
    return host.isEmpty ? _url.text.trim() : host;
  }

  /// The phone and the computer linking up (design standard §10): at the
  /// head of a new server's form, moving only while it pairs, checks or
  /// connects, with one line saying what it is doing then.
  Widget _linkMoment(AppLocalizations copy, KitTokens tokens) {
    final state = _linkState;
    if (state != ServersLinkState.idle) _linkMoved = true;
    final caption = switch (state) {
      ServersLinkState.linking when _pairing => copy.e7SetupPairing,
      ServersLinkState.linking when _submitting => copy.addServerConnectingHost(
        _host,
      ),
      ServersLinkState.linking => copy.addServerCheckingHost(_host),
      ServersLinkState.linked when _readyProfile != null =>
        copy.addServerConnectedHost(_host),
      _ => null,
    };
    return Column(
      key: const ValueKey('server-link-moment'),
      children: [
        Center(
          child: KitIllustration(
            // Each state plays its own entrance: the link drawing across,
            // the spark landing, the link breaking.
            key: ValueKey('server-link-${state.name}'),
            scene: ServersLinkScene(state, intro: !_linkMoved),
            ambient: state == ServersLinkState.linking,
            // Paired: a finished moment, so the spark takes the longer
            // celebration entrance (design standard §10).
            entranceDuration: state == ServersLinkState.linked
                ? KitMotion.celebration
                : KitMotion.entrance,
          ),
        ),
        // One line, held open so the form does not jump when it speaks.
        SizedBox(
          height: tokens.space6,
          child: Semantics(
            liveRegion: true,
            child: KitText(
              caption ?? '',
              key: const ValueKey('server-link-caption'),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
            ),
          ),
        ),
      ],
    );
  }

  /// What the old connection help explained, said where it matters: an
  /// http:// address on the network (not this device) is refused, and a
  /// private HTTPS name through Tailscale is the way (never a public relay).
  /// Shown only with the field's error; on Add server it offers the
  /// Tailscale step.
  Widget? _remoteHttpAdvice(AppLocalizations copy, KitTokens tokens) {
    if (_error == null ||
        _isCodex ||
        _tailscale ||
        explainConnectionAddress(_url.text) != ConnectionAdvice.remoteHttp) {
      return null;
    }
    final offersTailscale =
        _stepped && platformCapabilities.supportsTailscaleHandoff;
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: KitNotice(
        key: const ValueKey('server-remote-http-advice'),
        tone: AppStatusTone.neutral,
        message: copy.addServerRemoteHttpAdvice,
        actions: [
          if (offersTailscale)
            KitAction(
              key: const ValueKey('server-remote-http-tailscale'),
              label: copy.addServerUseTailscale,
              onPressed: _submitting ? null : _chooseTailscale,
            ),
        ],
      ),
    );
  }

  /// The warning for plain HTTP to a private network address, under the
  /// field it is about, with the explicit confirm. Once confirmed it stays
  /// as one quiet line, so the person can see what they chose.
  Widget? _cleartextWarning(AppLocalizations copy, KitTokens tokens) {
    if (_isCodex || _tailscale) return null;
    final url = normalizeServerProfileUrl(_url.text);
    if (!serverUrlNeedsCleartextConfirmation(url) ||
        validateServerProfileUrl(url) != null) {
      return null;
    }
    final pending = _cleartextPending(url);
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: KitNotice(
        key: const ValueKey('server-cleartext-warning'),
        tone: AppStatusTone.neutral,
        icon: pending ? AppIconography.warning : null,
        message: pending
            ? copy.addServerCleartextWarning
            : copy.addServerCleartextConfirmed,
        actions: [
          if (pending &&
              _stepped &&
              platformCapabilities.supportsTailscaleHandoff)
            KitAction(
              key: const ValueKey('server-cleartext-tailscale'),
              label: copy.addServerUseTailscale,
              onPressed: _submitting ? null : _chooseTailscale,
            ),
          if (pending)
            KitAction(
              key: const ValueKey('server-cleartext-confirm'),
              label: copy.addServerCleartextConfirm,
              onPressed: _submitting
                  ? null
                  : () {
                      _set(() {
                        _cleartextConfirmed = cleartextOriginOf(url);
                        _invalidateProbe();
                      });
                      // With a password in hand (a pairing code's, or one
                      // typed) the check runs now; otherwise the first-run
                      // pause applies as for any other address.
                      if (_password.isNotEmpty) {
                        unawaited(_testConnection());
                      } else {
                        _scheduleAutoTest();
                      }
                    },
            ),
        ],
      ),
    );
  }

  /// The address and password of an OpenCode server, and the check. Folded
  /// under "Enter the address instead" for a new server (pairing is the
  /// main path); shown at once where the fields are what the person came
  /// to change.
  Widget _manualAddress(AppLocalizations copy, KitTokens tokens) {
    final fields = _Rails(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(height: _foldsManualAddress ? tokens.space1 : tokens.space3),
          KeyedSubtree(
            key: _addressKey,
            child: KitField(
              label: copy.e7SetupServerUrl,
              kind: KitFieldKind.url,
              controller: _url,
              focusNode: _urlFocus,
              fieldKey: const ValueKey('server-url-field'),
              hint: 'https://server.example',
              helper: _tailscale
                  ? copy.tailscaleAddressDetail
                  : copy.e7SetupHttpsHint,
              error: _error,
              enabled: !_submitting,
              disabledReason: _submitting ? copy.e7SetupSaving : null,
              textInputAction: TextInputAction.next,
              // Straight to the password: the name and username are under
              // More options and rarely needed.
              onSubmitted: (_) => _passFocus.requestFocus(),
              onChanged: _urlChanged,
            ),
          ),
          ?_notSameNetworkLink(),
          ?_remoteHttpAdvice(copy, tokens),
          ?_cleartextWarning(copy, tokens),
          // What the check (or the save) found, under the field it is
          // about.
          _verdicts(copy, tokens),
          SizedBox(height: tokens.space4),
          // Paste is the main way in for the per-run random serve password;
          // the kit field carries it beside the reveal toggle.
          KitField.secret(
            label: _needsPassword
                ? copy.e7SetupReenterPassword
                : copy.e7SetupServerPassword,
            controller: _pass,
            focusNode: _passFocus,
            fieldKey: const ValueKey('server-password-field'),
            revealKey: const ValueKey('server-password-visibility'),
            pasteKey: const ValueKey('server-password-paste'),
            replaceKey: const ValueKey('server-password-replace'),
            helper: _needsPassword
                ? copy.e7SetupEmptyPasswordHint
                : copy.e7SetupPasswordStartupHint,
            saved: _heldPassword != null,
            onReplace: () => _set(() {
              _heldPassword = null;
              _invalidateProbe();
            }),
            autofocus: _needsPassword || widget.focusPassword,
            enabled: !_submitting,
            disabledReason: _submitting ? copy.e7SetupSaving : null,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _save(),
            onChanged: _passwordChanged,
          ),
          SizedBox(height: tokens.space1),
          _testAction(copy),
        ],
      ),
    );
    if (!_foldsManualAddress) return fields;
    return KitExpandRow(
      key: ValueKey('server-manual-address-$_manualFold'),
      headerKey: const ValueKey('server-manual-address'),
      title: copy.addServerManual,
      initiallyExpanded: _manualForcedOpen,
      children: [
        fields,
        SizedBox(height: tokens.space2),
      ],
    );
  }

  /// Test connection, a tertiary at most (§2): Save & connect checks by
  /// itself, this is for checking before deciding.
  Widget _testAction(AppLocalizations copy) => KitInset(
    child: KitButton.tertiary(
      key: const ValueKey('test-server-connection'),
      onPressed: _testing || _submitting ? null : _testConnection,
      icon: AppIconography.networkCheck,
      label: _testing ? copy.e7SetupTesting : copy.e7SetupTestConnection,
    ),
  );

  /// Each verdict unfolds in when it arrives and folds away when it goes
  /// (design standard §10); the slots stay in place so a new check that
  /// clears the old verdict and brings the next one moves smoothly.
  Widget _slot(KitTokens tokens, Widget? child) => KitReveal(
    child: child == null
        ? null
        : Padding(
            padding: EdgeInsetsDirectional.only(top: tokens.space2),
            child: child,
          ),
  );

  /// A check that has not answered for a while offers to stop, so a server
  /// that never answers never holds the person. At the head of the form,
  /// under the drawing that shows the check.
  Widget _progress(AppLocalizations copy, KitTokens tokens) => _Rails(
    child: _slot(
      tokens,
      !_slowCheck
          ? null
          : KitNotice(
              key: const ValueKey('server-check-slow'),
              tone: AppStatusTone.neutral,
              message: copy.addServerCheckSlow(_host),
              actions: [
                KitAction(
                  key: const ValueKey('server-check-cancel'),
                  label: copy.addServerCheckCancel,
                  onPressed: _cancelCheck,
                ),
              ],
            ),
    ),
  );

  /// The verdicts, under the address field they are about: a save that
  /// failed, and what the check found. Plain words; the technical text is
  /// folded under Details.
  Widget _verdicts(AppLocalizations copy, KitTokens tokens) {
    final failure = _submitFailure;
    final result = _isCodex ? null : _testResult;
    final codex = _isCodex ? _codexTestResult : null;
    return Column(
      key: _verdictKey,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _slot(
          tokens,
          failure == null
              ? null
              : _WithDetails(
                  details: _submitDetails,
                  detailsKey: const ValueKey('server-save-failure-details'),
                  child: KitNotice(
                    key: const ValueKey('server-save-failure'),
                    tone: AppStatusTone.failure,
                    message: failure,
                  ),
                ),
        ),
        _slot(tokens, codex == null ? null : _buildCodexProbeVerdict(copy)),
        _slot(
          tokens,
          result == null
              ? null
              : KeyedSubtree(
                  key: const ValueKey('server-probe-verdict'),
                  child: _ProbeVerdict(
                    result: result,
                    saveAnyway: !result.ok && _verdictFromSave
                        ? _saveAnywayAction(copy)
                        : null,
                  ),
                ),
        ),
      ],
    );
  }

  /// Name, username and the AI Team host: what most people never change,
  /// out of the way. The name comes from the address and the username is
  /// "opencode" unless the server was started with another.
  Widget _moreOptions(AppLocalizations copy, KitTokens tokens) => KitExpandRow(
    key: const ValueKey('server-editor-more-options'),
    headerKey: const ValueKey('server-editor-more-options-header'),
    title: copy.serverEditorMoreOptions,
    initiallyExpanded: _moreOptionsOpen,
    children: [
      Padding(
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space1,
          tokens.gutter,
          tokens.space2,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitField(
              label: copy.connectionDisplayName,
              controller: _name,
              focusNode: _nameFocus,
              fieldKey: const ValueKey('server-name-field'),
              hint: copy.connectionDisplayNameHint,
              enabled: !_submitting,
              disabledReason: _submitting ? copy.e7SetupSaving : null,
              textInputAction: TextInputAction.next,
              onSubmitted: (_) => _userFocus.requestFocus(),
              onChanged: (_) => _fieldChanged(),
            ),
            SizedBox(height: tokens.space4),
            KitField(
              label: copy.e7SetupUsername,
              kind: KitFieldKind.mono,
              controller: _user,
              focusNode: _userFocus,
              fieldKey: const ValueKey('server-username-field'),
              hint: 'opencode',
              enabled: !_submitting,
              disabledReason: _submitting ? copy.e7SetupSaving : null,
              textInputAction: TextInputAction.next,
              onSubmitted: (_) => _passFocus.requestFocus(),
              onChanged: (_) => _fieldChanged(),
            ),
            SizedBox(height: tokens.space5),
            Semantics(
              header: true,
              child: KitText(
                copy.teamUiEditorTitle,
                key: const ValueKey('server-editor-team-section'),
                role: KitTextRole.label,
              ),
            ),
            SizedBox(height: tokens.space1),
            KitText(
              _orchestration == null
                  ? copy.teamUiEditorBody
                  : copy.teamUiEditorConfigured(_orchestration!.url),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
            KitInset(
              child: Wrap(
                spacing: tokens.space1,
                children: [
                  KitButton.tertiary(
                    key: const ValueKey('server-editor-team-learn'),
                    onPressed: _submitting
                        ? null
                        : () => showTeamHostGuideSheet(
                            context,
                            enterAddress: _addTeamHost,
                          ),
                    label: copy.teamUiLearnHow,
                  ),
                  KitButton.tertiary(
                    key: const ValueKey('server-editor-team-add'),
                    onPressed: _submitting ? null : _addTeamHost,
                    label: _orchestration == null
                        ? copy.teamUiAddManually
                        : copy.teamUiChange,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ],
  );
}
