part of '../servers_screen.dart';

extension _EditorPairingAndSave on _ProfileEditorScreenState {
  /// Reads a pairing payload from the clipboard and applies it.
  ///
  /// The whole point of `opencode2 pair` is that the address, the username,
  /// and a 32-byte random password arrive together, so this fills all three
  /// rather than making the user shuttle between fields.
  Future<void> _pastePairing() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final raw = data?.text ?? '';
    if (!mounted) return;
    if (raw.trim().isEmpty) {
      _set(() {
        _pairingNotice = null;
        _pairingFailure = lookupAppLocalizations(
          Localizations.localeOf(context),
        ).e7SetupEmptyPairClipboard;
      });
      return;
    }
    final parsed = parsePairingPayload(raw);
    if (!parsed.ok) {
      _set(() {
        _pairingNotice = null;
        _pairingFailure = parsed.error;
      });
      return;
    }
    await _applyPairing(parsed.payload!);
  }

  /// Opens the camera scanner and applies whatever pairing code it decodes.
  ///
  /// The scanner owns every camera failure — permission, hardware, a QR that
  /// is not a pairing code — and returns null for all of them, so there is
  /// nothing to explain here beyond a payload that arrived.
  Future<void> _scanPairing() async {
    if (!platformCapabilities.supportsQrPairing) return;
    final payload = await pushKitPage<PairingPayload>(
      context,
      (_) => const PairingScannerScreen(),
    );
    if (payload == null || !mounted) {
      payload?.consume();
      return;
    }
    await _applyPairing(payload);
  }

  /// Probes a pairing payload's addresses and fills the editor from the one
  /// that answers.
  ///
  /// The payload is consumed on every exit path: it carries the serve
  /// password, and nothing beyond this method should still be holding it.
  /// The password reaches the password field and Keystore from there — it is
  /// never logged, never put in [_pairingNotice] or [_pairingFailure], and
  /// never in a URL.
  Future<void> _applyPairing(PairingPayload payload) async {
    if (_tailscale) {
      payload.consume();
      _set(
        () => _pairingFailure = _connectionL10n(context).tailscaleReviewDetail,
      );
      return;
    }
    if (_pairing) {
      payload.consume();
      return;
    }
    final username = payload.username;
    final password = payload.password;
    final firstUrl = payload.urls.first;
    final generation = ++_probeGeneration;
    _set(() {
      _testing = false;
      _pairing = true;
      _error = null;
      _testResult = null;
      _pairingNotice = null;
      _pairingFailure = null;
      _watchSlowCheck();
    });

    final PairingSelection selection;
    try {
      selection = await selectPairingUrl(
        payload,
        confirmedCleartextOrigins: {?_cleartextConfirmed},
      );
    } finally {
      payload.consume();
    }
    if (!mounted || generation != _probeGeneration) return;

    // Fill the fields either way. Even when nothing answered, the user now
    // has the address and credentials in front of them and can fix the tunnel
    // rather than re-copying everything by hand.
    //
    // A private-network http:// address is held for confirmation before
    // anything is sent: it fills the address, the warning appears under it,
    // and "Use it anyway" runs the check with the pairing password.
    final held = selection.outcomes
        .where((o) => o.needsCleartextConfirm)
        .map((o) => o.url)
        .firstOrNull;
    final chosen =
        selection.chosenUrl ?? held ?? normalizeServerProfileUrl(firstUrl);
    _url.value = TextEditingValue(text: chosen);
    _urlLength = chosen.length;
    _user.text = username;
    _heldPassword = password;
    _pass.clear();

    final host = Uri.tryParse(chosen)?.host ?? chosen;
    final tried = selection.outcomes.length;
    final result = selection.chosenResult;
    _set(() {
      _pairing = false;
      _stopSlowWatch();
      // The existing probe-verdict row already says the flavor, the version,
      // and "Connected — save to finish", and it is what `_save` reads to
      // cache the detected flavor. So pairing hands it the result and says
      // only the thing it cannot: *which* address was chosen, out of how
      // many. Repeating the verdict here would be two widgets telling the
      // user the same thing.
      _testResult = selection.ok ? result : null;
      if (!selection.ok && held != null) {
        _pairingNotice = null;
        _pairingFailure = null;
        _manualForcedOpen = true;
        _manualFold++;
      } else if (selection.ok) {
        _pairingNotice = tried > 1
            ? lookupAppLocalizations(
                Localizations.localeOf(context),
              ).e7SetupPairedChoice(host, tried)
            : lookupAppLocalizations(
                Localizations.localeOf(context),
              ).e7SetupPaired(host);
        _pairingFailure = null;
      } else {
        _pairingNotice = null;
        _pairingFailure =
            lookupAppLocalizations(
              Localizations.localeOf(context),
            ).e7SetupPairingFailed(
              setupUiMessage(
                lookupAppLocalizations(Localizations.localeOf(context)),
                selection.failureDetail,
              ),
              _pairingHint,
            );
      }
    });
    if (!selection.connected && (result?.needsPassword ?? false)) {
      _focusField(_passFocus);
    }
  }

  /// What to do about a pairing code whose addresses all failed. A phone and
  /// a desktop have genuinely different answers, so they get different ones.
  String get _pairingHint => platformCapabilities.supportsUsbHostBridge
      ? lookupAppLocalizations(
          Localizations.localeOf(context),
        ).e7SetupPairingPhoneHint
      : lookupAppLocalizations(
          Localizations.localeOf(context),
        ).e7SetupPairingDesktopHint;

  /// The password field changed: typed, or pasted with the field's own
  /// Paste (paste-first entry for the per-run serve password; nobody types
  /// a random 32-byte base64url string). A copied `server password ` line
  /// prefix is dropped.
  ///
  /// A whole pairing payload landing here is routed to [_applyPairing]
  /// instead, and the field is emptied at once — leaving that JSON in the
  /// password field would be both wrong and a way to get the credential
  /// rendered on screen.
  void _passwordChanged(String value) {
    if (looksLikePairingPayload(value)) {
      _pass.clear();
      final parsed = parsePairingPayload(value);
      if (!parsed.ok) {
        _set(() {
          _invalidateProbe();
          _pairingNotice = null;
          _pairingFailure = parsed.error;
        });
        return;
      }
      _set(_invalidateProbe);
      unawaited(_applyPairing(parsed.payload!));
      return;
    }
    const prefix = 'server password';
    final trimmed = value.trim();
    if (trimmed.toLowerCase().startsWith(prefix)) {
      final bare = trimmed.substring(prefix.length).trim();
      _pass.value = TextEditingValue(
        text: bare,
        selection: TextSelection.collapsed(offset: bare.length),
      );
    }
    _fieldChanged();
  }

  /// The token field changed; a pasted token loses the whitespace a copy
  /// from a terminal carries.
  void _tokenChanged(String value) {
    final trimmed = value.trim();
    if (trimmed != value && trimmed.isNotEmpty) {
      _codexToken.value = TextEditingValue(
        text: trimmed,
        selection: TextSelection.collapsed(offset: trimmed.length),
      );
    }
    _fieldChanged();
  }

  bool get _dirty {
    final baseline = _savedProfile ?? widget.existing;
    // A new server's kind is a step, not an edit: choosing one and leaving
    // loses nothing typed.
    return (baseline != null && _backend != baseline.backend) ||
        _name.text != (baseline?.name ?? '') ||
        _url.text != (baseline?.baseUrl ?? '') ||
        _user.text != (baseline?.username ?? '') ||
        _password != (baseline?.password ?? '') ||
        _codexDirectory.text != (baseline?.codexDirectory ?? '') ||
        _token != (baseline?.codexToken ?? '');
  }

  Future<void> _close() async {
    if (_closing || _submitting) return;
    // The first step (what runs there) has nothing to type: closing it
    // never asks, even when Back brought the person here from an address
    // they had started (emulator QA B4). What they left behind is on a step
    // they already walked away from.
    if (!_dirty || _step == _AddStep.kind) {
      Navigator.pop(context);
      return;
    }
    _closing = true;
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final discard = await showKitConfirm(
      context,
      kind: KitConfirmKind.discard,
      title: copy.e7SetupDiscardChanges,
      body: copy.e7SetupUnsavedProfile,
      confirmLabel: copy.e7SetupDiscard,
      cancelLabel: copy.draftKeepEditing,
      icon: AppIconography.editOff,
      sheetKey: const ValueKey('server-discard-sheet'),
      confirmKey: const ValueKey('server-discard-confirm'),
    );
    _closing = false;
    if (discard && mounted) Navigator.pop(context);
  }

  Future<void> _tailscaleHelp() async {
    if (_submitting) return;
    final before = _url.text;
    final generation = ++_probeGeneration;
    _set(() => _testing = false);
    final reviewed = await pushKitPage<String>(
      context,
      (_) => TailscaleSetupScreen(initialAddress: before),
    );
    if (!mounted ||
        reviewed == null ||
        _url.text != before ||
        generation != _probeGeneration) {
      return;
    }
    _set(() {
      _invalidateProbe();
      _url.text = reviewed;
      _urlLength = reviewed.length;
    });
    _scheduleAutoTest();
  }

  /// "Not on the same network?" on the connect step. For an OpenCode server
  /// it is the flow's Tailscale step, and the address is then typed on the
  /// connect step that follows. Paseo and Codex listen on `ws://`, which a
  /// tailnet HTTPS name does not give, so there the Tailscale page opens for
  /// its guidance and the field is left to the person.
  Future<void> _notSameNetwork() async {
    if (_submitting) return;
    if (!_isCodex) return _chooseTailscale();
    await pushKitPage<String>(context, (_) => const TailscaleSetupScreen());
  }

  /// Moves the flow to [step]; whatever a check was doing is retired.
  void _goTo(_AddStep step) {
    FocusScope.of(context).unfocus();
    _set(() {
      _invalidateProbe();
      if (_step == _AddStep.connect && step != _AddStep.connect) {
        // A secret field never mounts filled (SEC-3): what was typed is
        // held, like a saved one, and the field says so when it returns.
        if (_pass.text.isNotEmpty) {
          _heldPassword = _pass.text;
          _pass.clear();
        }
        if (_codexToken.text.isNotEmpty) {
          _heldToken = _codexToken.text;
          _codexToken.clear();
        }
      }
      _step = step;
    });
    if (step == _AddStep.connect) _scheduleAutoTest();
  }

  /// The first step's answer: the connect step for that kind of server.
  void _chooseKind(ServerBackend backend) {
    if (_submitting) return;
    _backend = backend;
    _tailscale = false;
    _goTo(_AddStep.connect);
  }

  /// Tailscale, as a step of this flow (not a page it leaves for): an
  /// OpenCode server reached over the tailnet.
  void _chooseTailscale() {
    if (_submitting) return;
    _backend = ServerBackend.openCode;
    _tailscale = true;
    _goTo(_AddStep.tailscale);
  }

  /// Back within the flow: the connect step returns to Tailscale or to the
  /// first step, Tailscale to the first step. Null where Back leaves.
  _AddStep? get _previousStep => switch (_step) {
    _ when !_stepped => null,
    _AddStep.connect => _tailscale ? _AddStep.tailscale : _AddStep.kind,
    _AddStep.tailscale => _AddStep.kind,
    _AddStep.kind || _AddStep.ready => null,
  };

  /// Close or Back from the top bar and the system: a step back, the
  /// finished flow's way in, or the discard check.
  void _exit() {
    if (_submitting) return;
    if (_readyProfile case final profile?) {
      Navigator.pop(context, profile);
      return;
    }
    if (_previousStep case final previous?) {
      if (previous == _AddStep.kind) _tailscale = false;
      _goTo(previous);
      return;
    }
    unawaited(_close());
  }

  /// A check or a pairing started: after [slowCheckAfter] it offers Cancel,
  /// so a server that never answers never holds the person.
  void _watchSlowCheck() {
    _slowTimer?.cancel();
    _slowCheck = false;
    _slowTimer = Timer(slowCheckAfter, () {
      _slowTimer = null;
      if (!mounted || !(_testing || _pairing)) return;
      _set(() => _slowCheck = true);
    });
  }

  void _stopSlowWatch() {
    _slowTimer?.cancel();
    _slowTimer = null;
    _slowCheck = false;
  }

  /// Cancel on a slow check: the answer, when it comes, is ignored, and the
  /// fields are the person's again.
  void _cancelCheck() => _set(_invalidateProbe);

  /// Null where the link must not exist: off the connect step of Add
  /// server, once Tailscale is the way, and on a platform with no Tailscale
  /// handoff (hide, don't disable).
  Widget? _notSameNetworkLink() {
    if (!_stepped ||
        _step != _AddStep.connect ||
        _tailscale ||
        !platformCapabilities.supportsTailscaleHandoff) {
      return null;
    }
    return KitInset(
      child: KitButton.tertiary(
        key: const ValueKey('connect-not-same-network'),
        onPressed: _submitting ? null : () => unawaited(_notSameNetwork()),
        label: _connectionL10n(context).firstRunNotSameNetwork,
      ),
    );
  }

  /// "AI Team (optional)" › Add manually: the shared form; a found host is
  /// kept on the profile the next save writes.
  Future<void> _addTeamHost() async {
    final config = await showTeamHostSheet(
      context,
      initialUrl:
          _orchestration?.url ??
          teamDiscoveryUrlFor(normalizeServerProfileUrl(_url.text)) ??
          '',
      initialCity: _orchestration?.city ?? '',
      initialHostKind: _orchestration?.hostKind,
    );
    if (config == null || !mounted) return;
    _set(() => _orchestration = config);
  }

  /// Save & connect checks the connection first (unless a check since the
  /// last edit already found it answering), so a server that does not
  /// answer is explained — refused, timed out, wrong password — before it is
  /// stored. [anyway] is the verdict's "Save anyway": the person keeps a
  /// server that is not running right now.
  Future<void> _save({bool anyway = false}) async {
    if (_submitting || _testing) return;
    if (!anyway && _connectsOnSave && !_checkedOk) {
      FocusScope.of(context).unfocus();
      _checkingForSave = true;
      final bool answered;
      try {
        answered = await _testConnection(forSave: true);
      } finally {
        _checkingForSave = false;
      }
      if (!answered || !mounted) return;
    }
    if (_isCodex) {
      await _saveCodex();
      return;
    }
    var url = normalizeServerProfileUrl(_url.text);
    if (_tailscale && !isValidTailscaleAddress(url)) {
      _set(() => _error = _connectionL10n(context).tailscaleAddressError);
      return;
    }
    final error = validateServerProfileUrl(
      url,
      username: _user.text,
      password: _password,
    );
    if (error != null) {
      _set(() => _error = error);
      _focusField(_urlFocus);
      return;
    }
    if (_cleartextPending(url)) {
      _revealVerdict();
      return;
    }
    final uri = Uri.parse(url);
    url = uri.replace(scheme: uri.scheme.toLowerCase()).toString();
    // Cache what Test connection detected; the connection layer re-verifies
    // on every cold connect and a failed connect re-probes, so a save without
    // a test (default v1) still self-corrects.
    final probed = _testResult;
    final normalizedUrl = url.endsWith('/')
        ? url.substring(0, url.length - 1)
        : url;
    final previousUrl = widget.existing == null
        ? null
        : normalizeServerProfileUrl(
            widget.existing!.baseUrl,
          ).replaceFirst(RegExp(r'/$'), '');
    final endpointChanged = previousUrl != null && previousUrl != normalizedUrl;
    // Cached identity belongs to an endpoint, not merely this profile name.
    // A new untested endpoint uses the same safe default as a new profile;
    // connect/probe will detect it rather than inherit the old server's v2 proof.
    final detected = probed != null && probed.flavor != ServerFlavor.unknown
        ? probed.flavor
        : endpointChanged
        ? ServerFlavor.v1
        : widget.existing?.flavor ?? ServerFlavor.v1;
    final profile = ServerProfile(
      id:
          _savedProfile?.id ??
          widget.existing?.id ??
          DateTime.now().microsecondsSinceEpoch.toString(),
      name: _name.text.trim().isEmpty
          ? plainServerName(uri.host)
          : _name.text.trim(),
      baseUrl: normalizedUrl,
      username: _user.text.trim(),
      password: _password,
      flavor: detected,
      serverVersion:
          probed?.version ??
          (endpointChanged ? null : widget.existing?.serverVersion),
      orchestration: _orchestration,
    );
    if (serverUrlNeedsCleartextConfirmation(normalizedUrl)) {
      profile.cleartextConfirmedOrigin = _cleartextConfirmed;
    }
    FocusScope.of(context).unfocus();
    _set(() {
      _invalidateProbe();
      _submitting = true;
      _submitFailure = null;
      _submitDetails = null;
    });
    await _submit(profile);
  }

  Future<void> _saveCodex() async {
    var url = _isPaseo
        ? normalizePaseoServerUrl(_url.text)
        : normalizeCodexServerUrl(_url.text);
    final error = _validateSocketFields(url);
    if (error != null) {
      _set(() => _error = error);
      return;
    }
    final uri = Uri.parse(url);
    url = uri.toString().replaceAll(RegExp(r'/$'), '');
    final probed = _codexTestResult;
    final profile = ServerProfile(
      id:
          _savedProfile?.id ??
          widget.existing?.id ??
          DateTime.now().microsecondsSinceEpoch.toString(),
      name: _name.text.trim().isEmpty
          ? plainServerName(uri.host)
          : _name.text.trim(),
      baseUrl: url,
      backend: _backend,
      codexDirectory: _codexDirectory.text.trim(),
      codexToken: _token,
      serverVersion: probed?.version ?? widget.existing?.serverVersion,
    );
    FocusScope.of(context).unfocus();
    _set(() {
      _invalidateProbe();
      _submitting = true;
      _submitFailure = null;
      _submitDetails = null;
    });
    await _submit(profile);
  }

  /// Hands [profile] to the save (and connect), then finishes: Add server
  /// ends on its ready step, every other edit closes. A failure stays on
  /// the form, said where the fields that fix it are.
  Future<void> _submit(ServerProfile profile) async {
    _revealStatus();
    final outcome = await widget.onSubmit(profile, tailscale: _tailscale);
    if (!mounted) return;
    if (outcome.saved) _savedProfile = profile;
    if (outcome.failure == null) {
      if (!_stepped) {
        Navigator.pop(context, profile);
        return;
      }
      _set(() {
        _submitting = false;
        _readyProfile = profile;
        _step = _AddStep.ready;
      });
      return;
    }
    _set(() {
      _submitting = false;
      _submitFailure = outcome.failure;
      _submitDetails = outcome.details;
    });
    _revealVerdict();
  }
}
