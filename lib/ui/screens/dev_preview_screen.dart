import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/dev_preview.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../voice/controller.dart';
import '../../voice/voice_ui.dart' show showVoiceAutomaticSetupSheet;
import '../app_iconography.dart';
import '../kit/kit.dart';
import '../widgets/external_link.dart';
import 'dev_preview_conversation.dart';

export 'dev_preview_conversation.dart'
    show DevPreviewConversation, DevPreviewDelivery;

/// Opens the in-app preview for [link] on the connected server, or says in
/// plain words why it cannot be shown. Called when a local link is tapped in
/// a conversation and when a port is chosen from the conversation menu.
///
/// [serverBaseUrl] is the saved server's address (null when none);
/// [leased] is true for a runtime-only private transport, which has no
/// address to point a link at. [supported] overrides the platform check
/// (tests).
///
/// [conversation] is where "Ask for a change…" sends. Null reads it from
/// [context] when that is a conversation screen's own (the chat opens the
/// preview with its context); a preview opened from anywhere else has no
/// conversation and so no field.
Future<void> openDevPreview(
  BuildContext context,
  DevPreviewLink link, {
  required String? serverBaseUrl,
  bool leased = false,
  bool? supported,
  Future<bool> Function(Uri uri)? launcher,
  VoidCallback? onLogs,
  DevPreviewConversation? conversation,
}) async {
  final target = DevPreviewTarget.resolve(
    link,
    serverBaseUrl: serverBaseUrl,
    leased: leased,
  );
  final l10n = AppLocalizations.of(context);
  if (target.route == DevPreviewRoute.blocked) {
    final noServer = target.block == DevPreviewBlock.noServer;
    await showKitAlert(
      context,
      title: noServer
          ? l10n.devPreviewNoServerTitle
          : l10n.devPreviewNoHostTitle,
      body: noServer ? l10n.devPreviewNoServerBody : l10n.devPreviewNoHostBody,
      icon: AppIconography.browser,
      alertKey: const ValueKey('dev-preview-blocked'),
    );
    return;
  }
  final ask = conversation ?? DevPreviewConversation.of(context);
  await Navigator.of(context).push<void>(
    KitPageRoute<void>(
      builder: (_) => DevPreviewScreen(
        target: target,
        supported: supported,
        launcher: launcher,
        onLogs: onLogs,
        conversation: ask,
      ),
    ),
  );
}

/// The in-app preview of a dev server (map page `dev-preview`, vision
/// 2026-10-20 Preview A): the running page fills the screen under one slim
/// bar ("Preview · localhost:8080", Refresh page, Open in browser), and,
/// when the preview came from a conversation, one glowing field at the
/// bottom ("Ask for a change…") sends a change request into it. The real
/// address, Copy address, the dev server's logs and the remote-host note
/// sit one tap down, under the title. Back steps back through the page's
/// own history first, then leaves.
class DevPreviewScreen extends StatefulWidget {
  DevPreviewScreen({
    super.key,
    required this.target,
    this.supported,
    this.launcher,
    this.onLogs,
    this.conversation,
    this.voice,
    this.voiceSupported,
  }) : assert(target.uri != null);

  final DevPreviewTarget target;
  final bool? supported;

  /// Test seam for [openExternalLink].
  final Future<bool> Function(Uri uri)? launcher;

  /// Opens this server's logs when they are known; Details then offers them.
  final VoidCallback? onLogs;

  /// Where "Ask for a change…" sends; null: no field.
  final DevPreviewConversation? conversation;

  /// Makes the dictation controller (tests); null: the phone's own.
  final Future<VoiceComposerController> Function()? voice;

  /// Overrides whether this platform has voice (tests).
  final bool? voiceSupported;

  @override
  State<DevPreviewScreen> createState() => _DevPreviewScreenState();
}

class _DevPreviewScreenState extends State<DevPreviewScreen> {
  final _web = KitWebPreviewController();
  final _ask = TextEditingController();
  final _askFocus = FocusNode();

  KitAskFieldPhase _phase = KitAskFieldPhase.ready;
  String? _note;
  KitAskFieldNoteTone _noteTone = KitAskFieldNoteTone.done;
  Timer? _noteTimer;

  Future<VoiceComposerController>? _voiceFuture;
  VoiceComposerController? _voice;

  Uri get _origin => widget.target.uri!;
  Uri get _shown => _web.current ?? _origin;

  bool get _voiceSupported =>
      widget.voiceSupported ?? platformCapabilities.supportsVoice;

  @override
  void initState() {
    super.initState();
    _web.addListener(_changed);
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _noteTimer?.cancel();
    _web
      ..removeListener(_changed)
      ..dispose();
    _ask.dispose();
    _askFocus.dispose();
    final voice = _voice;
    if (voice != null) {
      voice.removeListener(_voiceChanged);
      unawaited(voice.cancel());
      voice.dispose();
    }
    super.dispose();
  }

  void _openInBrowser() => unawaited(
    openExternalLink(context, _shown.toString(), launcher: widget.launcher),
  );

  void _say(String note, KitAskFieldNoteTone tone) {
    _noteTimer?.cancel();
    setState(() {
      _note = note;
      _noteTone = tone;
    });
    _noteTimer = Timer(const Duration(seconds: 5), () {
      if (mounted) setState(() => _note = null);
    });
  }

  // --- Ask for a change -----------------------------------------------------

  Future<void> _send() async {
    final conversation = widget.conversation;
    final text = _ask.text.trim();
    if (conversation == null || text.isEmpty) return;
    if (_phase != KitAskFieldPhase.ready) return;
    final l10n = AppLocalizations.of(context);
    final delivery = conversation.delivery();
    setState(() => _phase = KitAskFieldPhase.working);
    try {
      await conversation.send(text);
    } catch (_) {
      if (!mounted) return;
      setState(() => _phase = KitAskFieldPhase.ready);
      _say(l10n.devPreviewAskNotSent, KitAskFieldNoteTone.notDone);
      return;
    }
    if (!mounted) return;
    _ask.clear();
    _askFocus.unfocus();
    setState(() => _phase = KitAskFieldPhase.ready);
    _say(switch (delivery) {
      DevPreviewDelivery.now => l10n.devPreviewAskSent,
      DevPreviewDelivery.joinsTurn => l10n.devPreviewAskSentJoins,
      DevPreviewDelivery.afterReply => l10n.devPreviewAskSentAfter,
      DevPreviewDelivery.restartsTurn => l10n.devPreviewAskSentRestart,
    }, KitAskFieldNoteTone.done);
  }

  Future<VoiceComposerController> _getVoice() => _voiceFuture ??=
      (widget.voice ?? VoiceComposerController.create)().then((voice) {
        if (!mounted) {
          voice.dispose();
          throw StateError('closed');
        }
        _voice = voice..addListener(_voiceChanged);
        return voice;
      });

  /// The mic: the first tap sets the speech model up when it is missing,
  /// then records; the second finishes, and what was said lands in the
  /// field. Nothing is sent until Send.
  Future<void> _toggleVoice() async {
    final l10n = AppLocalizations.of(context);
    if (_phase == KitAskFieldPhase.listening) {
      setState(() => _phase = KitAskFieldPhase.working);
      await _voice?.stopListening();
      return;
    }
    if (_phase != KitAskFieldPhase.ready) return;
    try {
      final voice = await _getVoice();
      if (!mounted) return;
      if (!voice.models.isReady) {
        final ready = await showVoiceAutomaticSetupSheet(context, voice);
        if (!mounted) return;
        if (!ready) {
          if (voice.state == VoiceComposerState.listening) {
            unawaited(voice.cancel());
          }
          return;
        }
      }
      _askFocus.unfocus();
      if (voice.state != VoiceComposerState.listening) {
        await voice.startListening();
      }
      if (mounted && voice.state == VoiceComposerState.listening) {
        setState(() => _phase = KitAskFieldPhase.listening);
      }
    } catch (_) {
      if (!mounted) return;
      setState(() => _phase = KitAskFieldPhase.ready);
      _say(l10n.devPreviewAskNotHeard, KitAskFieldNoteTone.notDone);
    }
  }

  void _voiceChanged() {
    final voice = _voice;
    if (voice == null || !mounted) return;
    switch (voice.state) {
      case VoiceComposerState.listening:
        if (_phase != KitAskFieldPhase.listening) {
          setState(() => _phase = KitAskFieldPhase.listening);
        }
      case VoiceComposerState.draft:
        final heard = voice.draft.trim();
        unawaited(voice.cancel());
        if (heard.isEmpty) {
          setState(() => _phase = KitAskFieldPhase.ready);
          _say(
            AppLocalizations.of(context).devPreviewAskNotHeard,
            KitAskFieldNoteTone.notDone,
          );
          return;
        }
        final before = _ask.text.trimRight();
        final joined = before.isEmpty ? heard : '$before $heard';
        _ask.value = TextEditingValue(
          text: joined,
          selection: TextSelection.collapsed(offset: joined.length),
        );
        setState(() => _phase = KitAskFieldPhase.ready);
      case VoiceComposerState.error:
        if (_phase == KitAskFieldPhase.ready) return;
        unawaited(voice.cancel(clearError: true));
        setState(() => _phase = KitAskFieldPhase.ready);
        _say(
          AppLocalizations.of(context).devPreviewAskNotHeard,
          KitAskFieldNoteTone.notDone,
        );
      default:
        break;
    }
  }

  // --- Details ----------------------------------------------------------------

  Future<void> _details() {
    final l10n = AppLocalizations.of(context);
    final address = _shown.toString();
    final target = widget.target;
    final onLogs = widget.onLogs;
    return showKitSheet<void>(
      context,
      title: l10n.devPreviewDetails,
      sheetKey: const ValueKey('dev-preview-details'),
      body: (sheet) => Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitRow(
            key: const ValueKey('dev-preview-address'),
            title: l10n.devPreviewAddress,
            supporting: TextSpan(text: KitBidi.ltr(address)),
            trailing: KitIconButton.copy(
              key: const ValueKey('dev-preview-copy'),
              tooltip: l10n.devPreviewCopyAddress,
              text: () => address,
            ),
          ),
          if (target.needsAllInterfaces)
            KitNotice(
              key: const ValueKey('dev-preview-remote-note'),
              icon: AppIconography.info,
              message: l10n.devPreviewRemoteNote(
                '${_origin.port}',
                target.host ?? '',
              ),
            ),
        ],
      ),
      tertiary: [
        if (onLogs != null)
          KitAction(
            key: const ValueKey('dev-preview-logs'),
            label: l10n.servicesLogs,
            icon: AppIconography.text,
            onPressed: () {
              // No sheet on a sheet: Details closes, then the logs open.
              Navigator.of(context).pop();
              onLogs();
            },
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final browser = KitAction(
      key: const ValueKey('dev-preview-browser'),
      label: l10n.devPreviewOpenInBrowser,
      icon: AppIconography.externalLink,
      onPressed: _openInBrowser,
    );
    // The bar always says "Preview"; the address lives under Details.
    // Large text on a phone: Open in browser and Details move to the
    // overflow.
    final roomy =
        KitLayout.windowOf(context) != KitWindow.compact ||
        MediaQuery.textScalerOf(context).scale(1) < 1.3;
    final conversation = widget.conversation;
    final restarts =
        conversation != null &&
        _ask.text.trim().isNotEmpty &&
        conversation.delivery() == DevPreviewDelivery.restartsTurn;
    return KitScreen(
      topBar: KitTopBar(
        title: l10n.devPreviewTitle,
        titleKey: const ValueKey('dev-preview-title'),
        onTitleTap: roomy ? () => unawaited(_details()) : null,
        titleTapLabel: l10n.devPreviewShowDetails,
        actions: [
          KitAction(
            key: const ValueKey('dev-preview-reload'),
            label: l10n.devPreviewReload,
            icon: AppIconography.retry,
            onPressed: () => unawaited(_web.reload()),
          ),
          // Large text on a phone leaves the title no room beside two
          // icons: Open in browser and Details wait in the overflow then,
          // as the bar does at 200 % text.
          if (!roomy) browser,
        ],
        menu: [
          if (!roomy)
            KitMenuItem(
              key: const ValueKey('dev-preview-details-item'),
              label: l10n.devPreviewDetails,
              icon: AppIconography.info,
              onSelected: () => unawaited(_details()),
            ),
        ],
        // Otherwise Open in browser stays beside Refresh: the two page
        // actions, no overflow.
        accessory: roomy
            ? KitIconButton(
                key: const ValueKey('dev-preview-browser'),
                icon: AppIconography.externalLink,
                tooltip: l10n.devPreviewOpenInBrowser,
                onPressed: _openInBrowser,
              )
            : null,
      ),
      loading:
          (widget.supported ?? KitWebPreview.platformSupported()) &&
          _web.phase == KitWebPreviewPhase.loading,
      loadingLabel: l10n.devPreviewLoading,
      header: [
        // The bar ends on a hairline: the page starts under it.
        const KitDivider(),
        // A remote server's page that does not answer: the likely reason,
        // in the one place it helps.
        if (widget.target.needsAllInterfaces &&
            _web.phase == KitWebPreviewPhase.failed)
          Padding(
            padding: EdgeInsets.symmetric(
              horizontal: tokens.gutter,
              vertical: tokens.space2,
            ),
            child: KitNotice(
              key: const ValueKey('dev-preview-remote-note'),
              icon: AppIconography.info,
              message: l10n.devPreviewRemoteNote(
                '${_origin.port}',
                widget.target.host ?? '',
              ),
            ),
          ),
      ],
      bottom: conversation == null
          ? null
          : Padding(
              padding: EdgeInsetsDirectional.only(top: tokens.space1),
              child: KitAskField(
                controller: _ask,
                focusNode: _askFocus,
                hint: l10n.devPreviewAskHint,
                sendLabel: restarts
                    ? l10n.devPreviewAskStopAndSend
                    : l10n.devPreviewAskSend,
                onSend: () => unawaited(_send()),
                phase: _phase,
                onVoice: _voiceSupported
                    ? () => unawaited(_toggleVoice())
                    : null,
                voiceLabel: l10n.devPreviewAskVoice,
                listeningHint: l10n.devPreviewAskListening,
                stopListeningLabel: l10n.devPreviewAskDoneSpeaking,
                note: _note,
                noteTone: _noteTone,
                // The page's Try again is the main action while nothing
                // answers.
                prominent: _web.phase != KitWebPreviewPhase.failed,
                fieldKey: const ValueKey('dev-preview-ask-field'),
                sendKey: const ValueKey('dev-preview-ask-send'),
                voiceKey: const ValueKey('dev-preview-ask-voice'),
              ),
            ),
      body: KitWebPreview(
        origin: _origin,
        controller: _web,
        supported: widget.supported,
        unsupportedAction: browser,
        onLeave: (uri) => unawaited(
          openExternalLink(context, uri.toString(), launcher: widget.launcher),
        ),
      ),
    );
  }
}
