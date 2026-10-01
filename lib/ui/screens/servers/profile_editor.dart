part of '../servers_screen.dart';

class _ProfileEditorScreen extends StatefulWidget {
  final ServerProfile? existing;
  final ServerBackend? initialBackend;
  final bool tailscale;
  final bool reconnectOnSave;
  final bool openCode2Intent;
  final String? initialUrl;

  /// The other ways in, offered on a new server's first step (null hides
  /// each): the editor closes, then the way opens. Tailscale is not one of
  /// them: it is a step of this flow.
  final VoidCallback? onPhoneSetup;
  final VoidCallback? onExternalAgents;

  /// Focus the password field on open — the path taken from the connection
  /// banner after a mid-session 401 (the serve password rotated).
  final bool focusPassword;

  /// Saves (and where promised, connects) the profile; [tailscale] says it
  /// was reached through the Tailscale step. The editor finishes only when
  /// this reports no failure; otherwise the failure is rendered inline and
  /// the fields stay editable.
  final Future<_SubmitOutcome> Function(
    ServerProfile profile, {
    required bool tailscale,
  })
  onSubmit;

  /// Resolves to a sentence when the device cannot keep a password (a Linux
  /// desktop without a keyring), shown above the form before the user types
  /// one that would be lost on save. Null skips the probe.
  final Future<String?> Function()? secureStorageProbe;
  const _ProfileEditorScreen({
    this.existing,
    this.initialBackend,
    this.tailscale = false,
    this.reconnectOnSave = false,
    this.openCode2Intent = false,
    this.initialUrl,
    this.onPhoneSetup,
    this.onExternalAgents,
    this.focusPassword = false,
    required this.onSubmit,
    this.secureStorageProbe,
  });

  @override
  State<_ProfileEditorScreen> createState() => _ProfileEditorScreenState();
}

class _ProfileEditorScreenState extends State<_ProfileEditorScreen> {
  /// "More options" starts open only when something in it was already set:
  /// a username other than the default, or an AI Team host.
  late final bool _moreOptionsOpen =
      (widget.existing?.username.isNotEmpty == true &&
          widget.existing?.username != 'opencode') ||
      widget.existing?.orchestration != null;

  late ServerBackend _backend =
      widget.existing?.backend ??
      widget.initialBackend ??
      ServerBackend.openCode;

  /// A new server with nothing preset is added in steps (P3.9): what runs
  /// there, then Tailscale when that is the way, then the address or the
  /// pairing code, the check, and a ready moment. Everything else (editing,
  /// a password to re-enter, the phone's own server) is one form.
  late final bool _stepped =
      widget.existing == null &&
      !widget.tailscale &&
      widget.initialUrl == null &&
      !widget.focusPassword;

  // An explicit entry point already answered the first question. Keep the
  // stepped flow so Back can change that answer without saving anything.
  late _AddStep _step = _stepped && widget.initialBackend == null
      ? _AddStep.kind
      : _AddStep.connect;

  /// The server is reached through Tailscale: the address must be a
  /// tailnet one, and the save remembers the way for the next edit.
  late bool _tailscale = widget.tailscale;

  /// A check or a pairing has run for [slowCheckAfter]: Cancel is offered.
  bool _slowCheck = false;

  Timer? _slowTimer;

  /// What the ready step opens: the profile as saved and connected.
  ServerProfile? _readyProfile;

  late final TextEditingController _name = TextEditingController(
    text: widget.existing?.name ?? '',
  );

  // New profiles start empty: the normalizer on Test/Save adds the scheme,
  // and a pre-seeded 'https://' fought typed bare hosts.
  late final TextEditingController _url = TextEditingController(
    text: widget.existing?.baseUrl ?? widget.initialUrl ?? '',
  );

  late final TextEditingController _user = TextEditingController(
    text: widget.existing?.username ?? '',
  );

  /// Always empty when the field mounts (SEC-3): a stored password stays
  /// in [_heldPassword] and the field says "Saved · Replace".
  final TextEditingController _pass = TextEditingController();

  late final TextEditingController _codexDirectory = TextEditingController(
    text: widget.existing?.codexDirectory ?? '',
  );

  /// Always empty when the field mounts, like [_pass]; see [_heldToken].
  final TextEditingController _codexToken = TextEditingController();

  /// The password this editor holds without showing it: the saved one, or
  /// the one a pairing code brought. A stored secret is never put back into
  /// a field (SEC-3, KIT-40): while this is set the field says "Saved ·
  /// Replace" and a save keeps it; Replace (or a check that found it
  /// refused) drops it for whatever is typed.
  late String? _heldPassword = _held(
    widget.existing?.password,
    reentry: widget.existing?.requiresPasswordReentry ?? false,
  );

  /// The Codex token or Paseo password held the same way.
  late String? _heldToken = _held(
    widget.existing?.codexToken,
    reentry: widget.existing?.requiresCodexTokenReentry ?? false,
  );

  final _urlFocus = FocusNode();

  final _nameFocus = FocusNode();

  final _userFocus = FocusNode();

  final _passFocus = FocusNode();

  final _codexDirectoryFocus = FocusNode();

  final _codexTokenFocus = FocusNode();

  String? _error;

  bool _closing = false;

  bool _testing = false;

  /// True while [_ProfileEditorScreen.onSubmit] runs.
  bool _submitting = false;

  /// Why the last save or connect did not finish, in product copy.
  String? _submitFailure;

  /// The failed save's technical text, folded under its verdict.
  String? _submitDetails;

  /// The keyring problem [_ProfileEditorScreen.secureStorageProbe] found.
  String? _secureStorageNotice;

  /// The profile as last written to the store from this editor, so a save
  /// that stored but could not connect no longer counts as unsaved edits.
  ServerProfile? _savedProfile;

  ServerProbeResult? _testResult;

  CodexConnectionProbeResult? _codexTestResult;

  int _urlLength = 0;

  int _probeGeneration = 0;

  /// The pause before the first-run test runs by itself; see [_autoTests].
  Timer? _autoTestTimer;

  /// True while a pairing payload's addresses are being probed.
  bool _pairing = false;

  /// The AI Team host chosen in this editor (TEAM-106); the existing
  /// profile's config until "Add manually" replaces it.
  late OrchestrationConfig? _orchestration = widget.existing?.orchestration;

  /// Which address pairing settled on, as a sentence. Never contains the
  /// password.
  String? _pairingNotice;

  /// Why pairing could not finish — including the per-address verdicts, which
  /// are the only thing that tells the user whether to bridge a port or to
  /// put the server behind TLS.
  String? _pairingFailure;

  /// The head of the form: the link drawing and a slow check's offer to
  /// stop. A check or a save scrolls back to it as it starts.
  final _statusKey = GlobalKey();

  /// The verdicts (the check's, a failed save's), under the address field
  /// they are about; an answer scrolls them into view with that field.
  final _verdictKey = GlobalKey();

  /// The address field, with its label: the verdict is revealed under it.
  final _addressKey = GlobalKey();

  /// Add server's step line, at the head of the form: a check scrolls back
  /// to it, so the step it is on stays in view.
  final _stepLineKey = GlobalKey();

  /// "Enter the address instead" was opened by the editor itself (a check
  /// that needs a field, an empty address on save). Bumping [_manualFold]
  /// rebuilds the fold open.
  bool _manualForcedOpen = false;

  int _manualFold = 0;

  /// The last failed check came from Save & connect, so its verdict offers
  /// "Save anyway".
  bool _verdictFromSave = false;

  /// The plain-HTTP origin the person confirmed ("Use it anyway"). It only
  /// stands for that exact address: another one asks again. A saved server
  /// starts with the confirmation it was saved with.
  late String? _cleartextConfirmed = widget.existing?.cleartextConfirmedOrigin;

  /// Save & connect is checking the connection before it stores anything.
  bool _checkingForSave = false;

  /// The link drawing has left its first, idle picture: coming back to idle
  /// keeps the devices drawn instead of drawing them in again.
  bool _linkMoved = false;

  Timer? _verdictRevealTimer;

  @override
  void initState() {
    super.initState();
    if (!_isCodex) _probeSecureStorage();
    _urlLength = _url.text.length;
    _urlFocus.addListener(_checkSocketAddressOnLeave);
  }

  @override
  void dispose() {
    _autoTestTimer?.cancel();
    _slowTimer?.cancel();
    _verdictRevealTimer?.cancel();
    _name.dispose();
    _url.dispose();
    _user.dispose();
    _pass.dispose();
    _codexDirectory.dispose();
    _codexToken.dispose();
    _urlFocus
      ..removeListener(_checkSocketAddressOnLeave)
      ..dispose();
    _nameFocus.dispose();
    _userFocus.dispose();
    _passFocus.dispose();
    _codexDirectoryFocus.dispose();
    _codexTokenFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final copy = _connectionL10n(context);
    final tokens = KitTokens.of(context);
    final isNew = widget.existing == null;
    final title = switch (_step) {
      _AddStep.kind => copy.e7SetupAddServer,
      _AddStep.tailscale => copy.tailscaleTitle,
      _ when _stepped => _tailscale ? copy.tailscaleTitle : _kindTitle(copy),
      _ when isNew =>
        widget.openCode2Intent && !_isCodex
            ? copy.oc2DiscoveryEditorTitle
            : copy.e7SetupAddServer,
      _ when _needsCodexToken => copy.codexTokenReentry,
      _ when _needsPassword => copy.e7SetupReenterPassword,
      _ => copy.e7SetupEditServer,
    };
    // A new server starts from the command to run on the computer, for the
    // kind chosen (the phone's own server and Tailscale have their own).
    final showsCommand = isNew && !_tailscale && widget.initialUrl == null;
    final back = _previousStep != null;
    final Widget? bottom = switch (_step) {
      _AddStep.kind => null,
      _AddStep.tailscale => KitButton.primary(
        key: const ValueKey('server-tailscale-continue'),
        onPressed: () => _goTo(_AddStep.connect),
        icon: AppIconography.forward,
        label: copy.addServerTailscaleNext,
      ),
      _AddStep.ready => KitButton.primary(
        key: const ValueKey('server-ready-open'),
        onPressed: _exit,
        label: copy.addServerReadyOpen(_readyProfile!.name),
      ),
      // The one primary (§2), pinned below the form and lifted above the
      // keyboard. Its tap checks the connection, saves and connects; the
      // spinner is that tap in flight, the drawing and its line say which
      // step.
      _AddStep.connect => KitButton.primary(
        key: const ValueKey('save-server-profile'),
        onPressed: _submitting || _testing ? null : _save,
        working: _submitting || (_testing && _checkingForSave),
        label: _testing && _checkingForSave
            ? copy.addServerChecking
            : _submitting
            ? copy.e7SetupSaving
            : _connectsOnSave
            ? copy.onboardingSaveConnect
            : copy.onboardingSaveChanges,
      ),
    };
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _exit();
      },
      child: KitScreen(
        key: const ValueKey('server-profile-editor'),
        width: KitScreenWidth.reading,
        topBar: KitTopBar(
          title: title,
          // Back steps within Add server; Close leaves (asking first when
          // something is unsaved). Nothing closes while a save is in flight.
          exit: back ? KitTopBarExit.back : KitTopBarExit.close,
          exitKey: ValueKey(
            back ? 'server-editor-back' : 'server-editor-close',
          ),
          onExit: _exit,
        ),
        bottom: bottom,
        body: AbsorbPointer(
          absorbing: _submitting,
          // One box, not a lazy list: every field exists while the form is
          // open, so focus can move to one that is scrolled away.
          child: CustomScrollView(
            key: const ValueKey('server-profile-fields'),
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            slivers: [
              SliverPadding(
                padding: EdgeInsetsDirectional.only(
                  top: tokens.space1,
                  bottom: KitScreen.endPadding(context),
                ),
                sliver: SliverToBoxAdapter(
                  // Each step replaces the last in place.
                  child: Column(
                    key: ValueKey('server-add-step-${_step.name}'),
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (_stepped) _stepLine(copy, tokens),
                      ...switch (_step) {
                        _AddStep.kind => _kindStep(copy, tokens),
                        _AddStep.tailscale => _tailscaleStep(copy, tokens),
                        _AddStep.ready => _readyStep(copy, tokens),
                        _AddStep.connect => _connectStep(
                          copy,
                          tokens,
                          isNew: isNew,
                          showsCommand: showsCommand,
                        ),
                      },
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // setState for the extension members in the sibling part files: an
  // extension may not call the protected State.setState directly.
  void _set(VoidCallback fn) => setState(fn);
}

/// Where Add server is (P3.9): what runs there, Tailscale when that is the
/// way, the address or pairing code (whose check is a step while it runs),
/// and the ready moment.
enum _AddStep { kind, tailscale, connect, ready }

/// How long a connection check or a pairing runs before it offers Cancel.
@visibleForTesting
const slowCheckAfter = Duration(seconds: 8);
