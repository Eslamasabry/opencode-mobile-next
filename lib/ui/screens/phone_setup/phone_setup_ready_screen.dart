import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/builtin_linux.dart';
import '../../../builtin/builtin_server.dart';
import '../../../builtin/setup/setup_contract.dart' show SetupHostKind;
import '../../../builtin/setup/termux_setup_host.dart';
import '../../../domain/workspace_paths.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/profiles.dart';
import '../../../termux/bridge.dart' show TermuxBridge;
import '../../kit/kit.dart';
import '../../navigation/chat_route.dart';
import '../../kit/scenes/setup_ready_scene.dart';
import '../../widgets/product_states.dart';
import '../project_folder_actions.dart';
import 'phone_setup_hero.dart';

/// Screen C of phone setup (docs/design/phone-setup-v2-2026-09-24.md): the
/// agent is running, so the only thing left is a place to work. One name
/// makes a project and lands in its first conversation with the keyboard up;
/// the existing folder sheet is the quiet alternative, opened only when the
/// person taps it, so there is never a second name field on top.
///
/// It opens with a celebration ([SetupReadyScene], played once) at the top
/// of the page, not a lone check floating mid-screen.
class PhoneSetupReadyScreen extends ConsumerStatefulWidget {
  const PhoneSetupReadyScreen({
    super.key,
    this.linux,
    this.host = SetupHostKind.builtin,
  });

  /// Tests pass a fake bridge; the app uses [builtinLinuxProvider], or
  /// Termux's Ubuntu ([TermuxSetupHost]) when setup ran in Termux.
  final BuiltinLinux? linux;

  /// Where setup put OpenCode: the project is made there, and a dropped
  /// connection is made again to that server.
  final SetupHostKind host;

  /// The suggestion in the name field: short, valid, and obviously a
  /// placeholder the person may keep.
  static const suggestedName = 'my-app';

  @override
  ConsumerState<PhoneSetupReadyScreen> createState() =>
      _PhoneSetupReadyScreenState();
}

class _PhoneSetupReadyScreenState extends ConsumerState<PhoneSetupReadyScreen> {
  final _name = TextEditingController(
    text: PhoneSetupReadyScreen.suggestedName,
  );
  final _focus = FocusNode();
  String? _problem;
  bool _busy = false;

  late final BuiltinLinux _linux =
      widget.linux ??
      (widget.host == SetupHostKind.termux
          ? TermuxSetupHost()
          : ref.read(builtinLinuxProvider));

  bool _isSetupServer(ServerProfile profile) =>
      widget.host == SetupHostKind.termux
      ? TermuxBridge.managesServerUrl(profile.baseUrl)
      : looksLikeInAppServer(profile);

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    // Selected, so typing replaces the suggestion and Create keeps it.
    _name.selection = TextSelection(
      baseOffset: 0,
      extentOffset: _name.text.length,
    );
    // The finish arrives on a screen of its own, so no working → finished
    // state view is there to confirm it: this screen does, once, as the
    // celebration starts (skipped under reduced motion).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) KitHaptics.done(context);
    });
  }

  @override
  void dispose() {
    _name.dispose();
    _focus.dispose();
    super.dispose();
  }

  /// The same rule as every other in-app project name
  /// ([projectFolderNameProblem]), said in the person's language: that
  /// function answers in English only.
  String? _nameProblem(String raw) {
    if (projectFolderNameProblem(raw) == null) return null;
    final value = raw.trim();
    if (value.isEmpty) return _l10n.phoneSetupReadyNameEmpty;
    if (value == '.' ||
        value == '..' ||
        value.contains('/') ||
        value.contains('\\')) {
      return _l10n.phoneSetupReadyNameOneFolder;
    }
    return _l10n.phoneSetupReadyNameInvalid;
  }

  /// Setup ends connected; this covers a connection that dropped while the
  /// person read this screen, by reconnecting to the server setup started.
  Future<bool> _ensureConnected(ConnectionController connection) async {
    if (connection.api != null) return true;
    ServerProfile? target;
    for (final profile in connection.store.profiles) {
      if (!_isSetupServer(profile)) continue;
      if (target == null || profile.id == connection.store.activeId) {
        target = profile;
      }
    }
    if (target == null) return false;
    await connection.connect(target);
    return connection.api != null;
  }

  Future<void> _create() async {
    if (_busy) return;
    final name = _name.text.trim();
    final problem = _nameProblem(name);
    if (problem != null) {
      setState(() => _problem = problem);
      return;
    }
    setState(() {
      _busy = true;
      _problem = null;
    });
    final connection = ref.read(connProvider);
    final navigator = Navigator.of(context);
    String? failure;
    try {
      final made = await BuiltinProjectFolders(
        _linux,
      ).create(BuiltinProjectFolders.pathFor(name));
      if (!mounted) return;
      if (!await _ensureConnected(connection)) {
        failure = _l10n.phoneSetupReadyOpenFailed(
          _connectionProblem(connection),
        );
      } else {
        // Opened first, so the new conversation is made inside it.
        await connection.selectLocation(directory: made.path);
        final locationProblem = connection.locationError;
        if (locationProblem != null) {
          failure = _l10n.phoneSetupReadyOpenFailed(
            productErrorText(locationProblem, l10n: _l10n),
          );
        } else {
          final session = await connection.createSession();
          if (!mounted) return;
          // Straight into the conversation, with the app's home beneath it
          // for Back: no Work screen to pass through first.
          unawaited(navigator.pushNamedAndRemoveUntil('/home', (_) => false));
          unawaited(
            navigator.pushNamed(
              '/chat/${session.id}',
              arguments: const ChatRouteArguments.firstRun(),
            ),
          );
          return;
        }
      }
    } on BuiltinLinuxException catch (error) {
      failure = _l10n.phoneSetupReadyCreateFailed(
        productErrorText(error, l10n: _l10n),
      );
    } catch (error) {
      failure = _l10n.phoneSetupReadyOpenFailed(
        productErrorText(error, l10n: _l10n),
      );
    }
    if (!mounted) return;
    setState(() {
      _busy = false;
      _problem = failure;
    });
  }

  /// Why connecting failed, in words: the connection's last error mapped
  /// to plain words (never its raw text), else that the server stopped.
  String _connectionProblem(ConnectionController connection) {
    return connectionErrorText(connection, l10n: _l10n) ??
        _l10n.builtinServerStopped;
  }

  Future<void> _openExisting() async {
    if (_busy) return;
    final connection = ref.read(connProvider);
    final navigator = Navigator.of(context);
    setState(() => _busy = true);
    final connected = await _ensureConnected(connection);
    if (!mounted) return;
    setState(() => _busy = false);
    if (!connected) {
      setState(
        () => _problem = _l10n.phoneSetupReadyOpenFailed(
          _connectionProblem(connection),
        ),
      );
      return;
    }
    // The very sheet Work and the project picker use for this server.
    final path = await ProjectFolderActions.openFolder(context, connection);
    if (path == null || !navigator.mounted) return;
    // An existing project may already have conversations: land where they
    // are listed rather than starting another one.
    unawaited(navigator.pushNamedAndRemoveUntil('/home', (_) => false));
  }

  /// Leaving without a project goes to the app's root, which shows the
  /// connected server; returning to the setup screens would only repeat
  /// "set up" for something already done.
  void _leave() => unawaited(
    Navigator.of(context).pushNamedAndRemoveUntil('/', (_) => false),
  );

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    // Folder names are Latin: a path field reads left to right in every
    // locale (KitFieldKind.path).
    final field = KitField(
      fieldKey: const ValueKey('phone-setup-ready-name'),
      label: l10n.phoneSetupReadyNameLabel,
      helper: l10n.phoneSetupReadyNameHelp,
      error: _problem,
      controller: _name,
      focusNode: _focus,
      kind: KitFieldKind.path,
      enabled: !_busy,
      disabledReason: _busy ? l10n.phoneSetupReadyCreating : null,
      textInputAction: TextInputAction.done,
      onChanged: (_) {
        if (_problem != null) setState(() => _problem = null);
      },
      onSubmitted: (_) => _create(),
    );
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && !_busy) _leave();
      },
      child: KitScreen(
        // B6: a saved server's connection problem is not about this
        // phone's own setup: one line, its ways out behind More.
        bodyQuiets: const {KitStatusKind.connection},
        // Close only (map chrome "close-only"): the celebration below is
        // the page's title.
        topBar: KitTopBar(
          title: '',
          exit: KitTopBarExit.close,
          exitKey: const ValueKey('phone-setup-ready-close'),
          onExit: _busy ? () {} : _leave,
        ),
        width: KitScreenWidth.reading,
        // One step: the celebration, what is ready, the one way on (a
        // name, then Create and open) and the quiet other way, a folder
        // that exists, which opens the folder sheet only when asked.
        body: PhoneSetupHero(
          scene: const SetupReadyScene(),
          // A finished moment: the longer celebration entrance (§10).
          entranceDuration: KitMotion.celebration,
          title: l10n.phoneSetupReadyTitle,
          titleKey: const ValueKey('phone-setup-ready-title'),
          body: l10n.phoneSetupReadyNameTitle,
          liveRegion: false,
          content: field,
          primary: KitAction(
            key: const ValueKey('phone-setup-ready-create'),
            label: l10n.phoneSetupReadyCreateOpen,
            onPressed: _busy ? null : _create,
            working: _busy,
          ),
          tertiary: [
            KitAction(
              key: const ValueKey('phone-setup-ready-open-existing'),
              label: l10n.phoneSetupReadyOpenFolderInstead,
              onPressed: _busy ? null : _openExisting,
            ),
          ],
        ),
      ),
    );
  }
}
