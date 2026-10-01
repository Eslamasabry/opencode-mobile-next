part of '../servers_screen.dart';

extension _EditorProbe on _ProfileEditorScreenState {
  /// A stored secret is held unless it must be re-entered or the person
  /// came to paste a new one (the connection banner's "Update password").
  String? _held(String? stored, {required bool reentry}) =>
      stored == null || stored.isEmpty || reentry || widget.focusPassword
      ? null
      : stored;

  /// The password a check or a save uses.
  String get _password => _heldPassword ?? _pass.text;

  /// The token a check or a save uses.
  String get _token => _heldToken ?? _codexToken.text;

  /// True when [url] is plain HTTP to a private network address the person
  /// has not confirmed yet. Nothing is sent to it, and nothing is saved,
  /// until they do.
  bool _cleartextPending(String url) =>
      !_isCodex &&
      serverUrlNeedsCleartextConfirmation(url) &&
      cleartextOriginOf(url) != _cleartextConfirmed;

  /// A new OpenCode server is paired first; its address and password wait
  /// under "Enter the address instead". Everywhere else (editing a saved
  /// server, a password to re-enter, the phone's own server, a Tailscale
  /// address) the fields are what the person came for and show at once.
  bool get _foldsManualAddress =>
      !_isCodex &&
      widget.existing == null &&
      !_tailscale &&
      widget.initialUrl == null &&
      !widget.focusPassword;

  /// Save & connect (a new server, any Codex or Paseo server, the server in
  /// use): the save checks the connection first, so a server that does not
  /// answer is explained before anything is stored.
  bool get _connectsOnSave =>
      _isCodex || widget.existing == null || widget.reconnectOnSave;

  /// A check since the fields last changed found the server answering.
  bool get _checkedOk =>
      _isCodex ? _codexTestResult?.ok == true : _testResult?.ok == true;

  /// What the link drawing shows: linking while pairing, checking or
  /// connecting; linked once the server answered; broken when it did not.
  ServersLinkState get _linkState {
    if (_readyProfile != null) return ServersLinkState.linked;
    if (_pairing || _testing || _submitting) return ServersLinkState.linking;
    final ok = _isCodex ? _codexTestResult?.ok : _testResult?.ok;
    if (ok == true) return ServersLinkState.linked;
    if (ok == false || _pairingFailure != null || _submitFailure != null) {
      return ServersLinkState.failed;
    }
    return ServersLinkState.idle;
  }

  /// Opens "Enter the address instead" (when folded and closed) and then
  /// focuses [node], once the field it belongs to is built.
  void _focusField(FocusNode node) {
    // Not built yet: under the closed fold, or a held secret's field that
    // is opening for a new value this frame.
    if (node.context == null) {
      if (_foldsManualAddress) {
        _set(() {
          _manualForcedOpen = true;
          _manualFold++;
        });
      }
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) node.requestFocus();
      });
      return;
    }
    node.requestFocus();
  }

  /// Brings the address field and the verdict under it into view once a
  /// check or a save answered: the field at the top, so what went wrong and
  /// the field that fixes it are read together above the pinned button.
  /// "Enter the address instead" opens first when the verdict is under it.
  void _revealVerdict() {
    if (_foldsManualAddress && _addressKey.currentContext == null) {
      _set(() {
        _manualForcedOpen = true;
        _manualFold++;
      });
    }
    // After the verdict has unfolded (KitReveal, KitMotion.standard): until
    // then the form is not yet tall enough to bring the field to the top.
    _verdictRevealTimer?.cancel();
    _verdictRevealTimer = Timer(KitMotion.standard, () {
      _verdictRevealTimer = null;
      final context = _addressKey.currentContext ?? _verdictKey.currentContext;
      if (!mounted || context == null) return;
      unawaited(
        Scrollable.ensureVisible(
          context,
          duration: KitMotion.standard,
          curve: KitMotion.enter,
          alignment: 0,
        ),
      );
    });
  }

  /// Brings the drawing (and the step it is on) into view as a check or a
  /// connect starts from a button further down.
  void _revealStatus() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final context = _stepLineKey.currentContext ?? _statusKey.currentContext;
      if (!mounted || context == null) return;
      unawaited(
        Scrollable.ensureVisible(
          context,
          duration: KitMotion.standard,
          curve: KitMotion.enter,
          // The head of the form, where the drawing and the verdict are.
          alignment: 0,
        ),
      );
    });
  }

  /// True for both socket-style backends (Codex app-server and the Paseo
  /// daemon): they share the address, project folder and secret fields.
  bool get _isCodex => _backend != ServerBackend.openCode;

  bool get _isPaseo => _backend == ServerBackend.paseo;

  bool get _needsPassword =>
      !_isCodex && (widget.existing?.requiresPasswordReentry ?? false);

  bool get _needsCodexToken =>
      _isCodex && (widget.existing?.requiresCodexTokenReentry ?? false);

  Future<void> _probeSecureStorage() async {
    final probe = widget.secureStorageProbe;
    if (probe == null) return;
    String? notice;
    try {
      notice = await probe();
    } catch (_) {
      notice = null;
    }
    if (!mounted || notice == null || _isCodex) return;
    _set(() => _secureStorageNotice = notice);
  }

  /// A Codex or Paseo address is checked when the person moves on from its
  /// field (the rule a standing helper used to recite): a wrong one says
  /// why under the field at once, a right one clears it.
  void _checkSocketAddressOnLeave() {
    if (!mounted || !_isCodex || _urlFocus.hasFocus || _submitting) return;
    final typed = _url.text.trim();
    if (typed.isEmpty) return;
    final url = _isPaseo
        ? normalizePaseoServerUrl(typed)
        : normalizeCodexServerUrl(typed);
    final error = _isPaseo
        ? validatePaseoServerUrl(url)
        : validateCodexServerUrl(url);
    if (error != _error) _set(() => _error = error);
  }

  /// A paste is a jump of several characters at once. When it lands without a
  /// scheme, expand it in place so `192.0.2.7:4096` just works.
  ///
  /// A pasted *pairing* payload is intercepted before anything else. It is
  /// JSON carrying the serve password, and it must not be left sitting in a
  /// text field: the field renders it, a screenshot captures it, and the
  /// platform may offer it to autofill. So the field is emptied first and the
  /// payload is routed to [_applyPairing].
  void _urlChanged(String value) {
    _applyUrlChange(value);
    _scheduleAutoTest();
  }

  void _applyUrlChange(String value) {
    _set(_invalidateProbe);
    if (_isCodex) {
      final pasted = value.length - _urlLength >= 4;
      _urlLength = value.length;
      if (pasted && !value.contains('://')) {
        final normalized = _isPaseo
            ? normalizePaseoServerUrl(value)
            : normalizeCodexServerUrl(value);
        if (normalized != value.trim()) {
          _urlLength = normalized.length;
          _url.value = TextEditingValue(
            text: normalized,
            selection: TextSelection.collapsed(offset: normalized.length),
          );
        }
      }
      return;
    }
    if (looksLikePairingPayload(value)) {
      final parsed = parsePairingPayload(value);
      _url.value = TextEditingValue.empty;
      _urlLength = 0;
      if (!parsed.ok) {
        _set(() {
          _pairingNotice = null;
          _pairingFailure = parsed.error;
        });
        return;
      }
      unawaited(_applyPairing(parsed.payload!));
      return;
    }
    final pasted = value.length - _urlLength >= 4;
    _urlLength = value.length;
    if (pasted && !value.contains('://')) {
      final normalized = normalizeServerProfileUrl(value);
      if (normalized != value.trim()) {
        _urlLength = normalized.length;
        _url.value = TextEditingValue(
          text: normalized,
          selection: TextSelection.collapsed(offset: normalized.length),
        );
      }
    }
  }

  /// Every field's change handler: what was tested is no longer what is
  /// typed, so the verdict goes and, on the first-run path, a new test is
  /// queued behind a pause.
  void _fieldChanged() {
    _set(_invalidateProbe);
    _scheduleAutoTest();
  }

  /// True on the connect step of Add server only (UX plan 5.6 step 3).
  /// Editing a saved server never tests by itself: that person came to
  /// change one value, and a probe of the half-edited profile is noise.
  bool get _autoTests => _stepped && _step == _AddStep.connect;

  /// The required fields as [_testConnection] would accept them, checked
  /// without its side effects (no error text, no focus move).
  bool get _readyForAutoTest {
    if (_url.text.trim().isEmpty) return false;
    if (_isCodex) {
      final url = _isPaseo
          ? normalizePaseoServerUrl(_url.text)
          : normalizeCodexServerUrl(_url.text);
      return _validateSocketFields(url) == null;
    }
    final url = normalizeServerProfileUrl(_url.text);
    return validateServerProfileUrl(
              url,
              username: _user.text,
              password: _password,
            ) ==
            null &&
        !_cleartextPending(url);
  }

  /// One test after the person pauses, never one per keystroke: each change
  /// restarts the wait, and [_invalidateProbe] has already retired whatever
  /// probe was in flight.
  void _scheduleAutoTest() {
    _autoTestTimer?.cancel();
    _autoTestTimer = null;
    if (!_autoTests || !_readyForAutoTest) return;
    _autoTestTimer = Timer(autoTestPause, () {
      _autoTestTimer = null;
      if (!mounted || _submitting || _pairing || _testing || _closing) return;
      if (!_readyForAutoTest) return;
      unawaited(_testConnection(auto: true));
    });
  }

  void _invalidateProbe() {
    _autoTestTimer?.cancel();
    _autoTestTimer = null;
    _stopSlowWatch();
    _probeGeneration += 1;
    _testing = false;
    _verdictFromSave = false;
    _submitFailure = null;
    _submitDetails = null;
    _pairing = false;
    _error = null;
    _testResult = null;
    _codexTestResult = null;
    _pairingNotice = null;
    _pairingFailure = null;
  }

  /// Checks the connection and shows the verdict; completes with whether the
  /// server answered.
  ///
  /// [auto] is the first-run test that runs by itself. It reports the same
  /// verdict but never moves focus: the person is still typing somewhere.
  /// [forSave] is Save & connect checking before it stores anything: the
  /// verdict then offers "Save anyway".
  Future<bool> _testConnection({
    bool auto = false,
    bool forSave = false,
  }) async {
    if (_testing) return false;
    _autoTestTimer?.cancel();
    _autoTestTimer = null;
    if (_isCodex) {
      return _testCodexConnection(auto: auto, forSave: forSave);
    }
    final url = normalizeServerProfileUrl(_url.text);
    if (_tailscale && !isValidTailscaleAddress(url)) {
      _set(() {
        _error = _connectionL10n(context).tailscaleAddressError;
        _testResult = null;
      });
      return false;
    }
    if (url != _url.text.trim()) {
      _urlLength = url.length;
      _url.value = TextEditingValue(
        text: url,
        selection: TextSelection.collapsed(offset: url.length),
      );
    }
    final error = validateServerProfileUrl(
      url,
      username: _user.text,
      password: _password,
    );
    if (error != null) {
      _set(() {
        _error = error;
        _testResult = null;
      });
      _focusField(_urlFocus);
      return false;
    }
    if (_cleartextPending(url)) {
      // The warning under the address field asks first; no request is made.
      _set(() {
        _error = null;
        _testResult = null;
      });
      if (!auto) _revealVerdict();
      return false;
    }
    final generation = ++_probeGeneration;
    _set(() {
      _testing = true;
      _testResult = null;
      _error = null;
      _watchSlowCheck();
    });
    if (!auto) {
      // The keyboard goes, and the form scrolls back to the drawing that
      // shows the check (a focused field would pull the scroll back to
      // itself).
      FocusScope.of(context).unfocus();
      _revealStatus();
    }
    final result = await serverProbe(
      baseUrl: url,
      username: _user.text.trim(),
      password: _password,
    );
    if (!mounted || generation != _probeGeneration) return false;
    _set(() {
      _testing = false;
      _stopSlowWatch();
      _submitFailure = null;
      _submitDetails = null;
      _testResult = result;
      _verdictFromSave = forSave && !result.ok;
    });
    // Per the v2 auth taxonomy: a 401 without a password sends the user to
    // the password field; a rejected password selects it for a clean repaste.
    if (!auto && result.flavor == ServerFlavor.v2 && result.needsPassword) {
      // A held password was refused: the field opens for a new one.
      if (_heldPassword != null) {
        _set(() => _heldPassword = null);
      } else if (_pass.text.isNotEmpty) {
        _pass.selection = TextSelection(
          baseOffset: 0,
          extentOffset: _pass.text.length,
        );
      }
      _focusField(_passFocus);
    }
    if (!auto) _revealVerdict();
    return result.ok;
  }

  String? _validateSocketFields(String url) => _isPaseo
      ? validatePaseoServerUrl(url) ??
            validateCodexProjectDirectory(_codexDirectory.text.trim()) ??
            validatePaseoPassword(_token)
      : validateCodexServerUrl(url) ??
            validateCodexProjectDirectory(_codexDirectory.text.trim()) ??
            validateCodexConnectionToken(_token);

  Future<bool> _testCodexConnection({
    bool auto = false,
    bool forSave = false,
  }) async {
    var url = _isPaseo
        ? normalizePaseoServerUrl(_url.text)
        : normalizeCodexServerUrl(_url.text);
    if (url != _url.text.trim()) {
      _urlLength = url.length;
      _url.value = TextEditingValue(
        text: url,
        selection: TextSelection.collapsed(offset: url.length),
      );
    }
    final error = _validateSocketFields(url);
    if (error != null) {
      _set(() {
        _error = error;
        _codexTestResult = null;
      });
      if ((_isPaseo
              ? validatePaseoServerUrl(url)
              : validateCodexServerUrl(url)) !=
          null) {
        _urlFocus.requestFocus();
      } else if (validateCodexProjectDirectory(_codexDirectory.text.trim()) !=
          null) {
        _codexDirectoryFocus.requestFocus();
      } else {
        _codexTokenFocus.requestFocus();
      }
      return false;
    }
    final generation = ++_probeGeneration;
    _set(() {
      _testing = true;
      _codexTestResult = null;
      _error = null;
      _watchSlowCheck();
    });
    if (!auto) {
      FocusScope.of(context).unfocus();
      _revealStatus();
    }
    try {
      final result = await socketAgentProbe(
        backend: _backend,
        baseUrl: url,
        secret: _token,
        directory: _codexDirectory.text.trim(),
      );
      if (!mounted || generation != _probeGeneration) return false;
      _set(() {
        _testing = false;
        _stopSlowWatch();
        _submitFailure = null;
        _submitDetails = null;
        _codexTestResult = result;
        _verdictFromSave = forSave && !result.ok;
      });
      if (!result.ok && !auto) _codexTokenFocus.requestFocus();
      if (!auto) _revealVerdict();
      return result.ok;
    } catch (error) {
      if (!mounted || generation != _probeGeneration) return false;
      _set(() {
        _testing = false;
        _stopSlowWatch();
        _error = productErrorText(error);
      });
      return false;
    }
  }
}
