part of 'kit_composer.dart';

/// Voice mode inside the pill: exit, the level meter with the phase words
/// and elapsed time, the read-aloud toggle, and the trailing circle.
class _VoiceContent extends StatefulWidget {
  const _VoiceContent({required this.voice, required this.l10n});

  final KitComposerVoice voice;
  final AppLocalizations l10n;

  @override
  State<_VoiceContent> createState() => _VoiceContentState();
}

class _VoiceContentState extends State<_VoiceContent> {
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    _syncTimer();
  }

  @override
  void didUpdateWidget(_VoiceContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncTimer();
  }

  void _syncTimer() {
    final ticking =
        widget.voice.phase == KitVoicePhase.listening &&
        widget.voice.listeningSince != null;
    if (ticking && _tick == null) {
      _tick = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() {});
      });
    } else if (!ticking) {
      _tick?.cancel();
      _tick = null;
    }
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  String _words(AppLocalizations l10n) => switch (widget.voice.phase) {
    KitVoicePhase.starting => l10n.kitVoiceStarting,
    KitVoicePhase.listening => l10n.kitVoiceListening,
    KitVoicePhase.transcribing => l10n.kitVoiceTranscribing,
    KitVoicePhase.waitingReply => l10n.kitVoiceWaitingReply,
    KitVoicePhase.speakingReply => l10n.kitVoiceSpeaking,
    KitVoicePhase.replyReady => l10n.kitVoiceReplyReady,
    KitVoicePhase.paused => l10n.kitVoicePaused,
    KitVoicePhase.micDenied => l10n.kitVoiceMicDenied,
    KitVoicePhase.failed => l10n.kitVoiceFailed,
  };

  String? _elapsed(AppLocalizations l10n) {
    final since = widget.voice.listeningSince;
    if (widget.voice.phase != KitVoicePhase.listening || since == null) {
      return null;
    }
    var seconds = clock.now().difference(since).inSeconds;
    if (seconds < 0) seconds = 0;
    return l10n.kitVoiceElapsed(
      '${seconds ~/ 60}',
      (seconds % 60).toString().padLeft(2, '0'),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = widget.l10n;
    final voice = widget.voice;
    final phase = voice.phase;
    final words = _words(l10n);
    final elapsed = _elapsed(l10n);
    final level = voice.level;
    final problem =
        phase == KitVoicePhase.micDenied || phase == KitVoicePhase.failed;
    final reason = problem || phase == KitVoicePhase.replyReady
        ? voice.reason
        : null;

    final Widget? trailing = switch (phase) {
      KitVoicePhase.listening => _Circle(
        key: const ValueKey('kit-voice-stop-listening'),
        kind: voice.conversation
            ? _CircleKind.voiceSend
            : _CircleKind.voiceDone,
        label: voice.conversation ? l10n.kitVoiceSend : l10n.kitVoiceDone,
        onTap: voice.onStopListening == null
            ? null
            : () {
                if (voice.conversation) KitHaptics.send(context);
                voice.onStopListening!();
              },
        disabledReason: words,
      ),
      KitVoicePhase.speakingReply => _Circle(
        key: const ValueKey('kit-voice-stop-reading'),
        kind: _CircleKind.stop,
        label: l10n.kitVoiceStopReading,
        onTap: voice.onStopSpeaking,
        disabledReason: words,
      ),
      KitVoicePhase.replyReady when voice.onListen != null => _Circle(
        key: const ValueKey('kit-voice-listen'),
        kind: _CircleKind.listen,
        label: l10n.kitVoiceListen,
        onTap: voice.onListen,
      ),
      _ => null,
    };

    final extras = <Widget>[
      if (phase == KitVoicePhase.replyReady && voice.onReadReply != null)
        KitButton.tertiary(
          label: l10n.kitVoiceReadReply,
          onPressed: voice.onReadReply,
        ),
      if (problem && voice.fix != null)
        KitButton.fromAction(
          voice.fix!,
          role: KitButtonRole.tertiary,
          expand: false,
        ),
      if (voice.onReadRepliesAloudChanged != null)
        KitChip.action(
          label: l10n.kitVoiceReadAloud,
          selected: voice.readRepliesAloud,
          onPressed: () =>
              voice.onReadRepliesAloudChanged!(!voice.readRepliesAloud),
        ),
    ];

    final wordsBlock = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // A Wrap, not a Row: at 320 dp with large text the meter and the
        // elapsed time move to their own line instead of squeezing the words.
        Wrap(
          spacing: tokens.space2,
          runSpacing: tokens.space1,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            if (phase == KitVoicePhase.listening && level != null)
              ExcludeSemantics(
                child: KitLevelMeter.listen(listenable: level, active: true),
              ),
            Semantics(
              liveRegion: true,
              container: true,
              label: words,
              excludeSemantics: true,
              // Body, not secondary: the phase words take the field's
              // place as the pill's main line, and 14 dp words beside the
              // meter fall under G5's measured contrast (record §1).
              child: KitText(
                words,
                role: KitTextRole.body,
                tone: KitTextTone.primary,
              ),
            ),
            if (elapsed != null)
              ExcludeSemantics(
                child: KitText(
                  elapsed,
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                  tabular: true,
                ),
              ),
          ],
        ),
        if (reason != null && reason.isNotEmpty)
          KitText(
            reason,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
      ],
    );

    return Column(
      key: voice.voiceKey,
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            KitIconButton(
              icon: AppIconography.close,
              tooltip: l10n.kitVoiceLeave,
              onPressed: voice.onExit,
            ),
            SizedBox(width: tokens.space1),
            Expanded(child: wordsBlock),
            if (trailing != null) KitSwap(child: trailing),
          ],
        ),
        if (extras.isNotEmpty)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.space2,
              end: tokens.space2,
              bottom: tokens.space1,
            ),
            child: Wrap(
              spacing: tokens.space2,
              runSpacing: tokens.space1,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: extras,
            ),
          ),
      ],
    );
  }
}

/// [KitComposer.layer]: the body fills the space and scrolls under the
/// glass; the composer floats at the bottom, lifted by the keyboard, and
/// its measured height is published to the body through
/// [KitBottomInset.add].
class _KitComposerLayer extends StatefulWidget {
  const _KitComposerLayer({
    super.key,
    required this.body,
    required this.composer,
    this.above,
    this.aboveMinHeight = 0,
  });

  final Widget body;
  final Widget composer;
  final Widget? above;
  final double aboveMinHeight;

  @override
  State<_KitComposerLayer> createState() => _KitComposerLayerState();
}

class _KitComposerLayerState extends State<_KitComposerLayer> {
  double _height = 0;

  void _onSize(Size size) {
    if (!mounted || size.height == _height) return;
    setState(() => _height = size.height);
  }

  /// [above] on the ground over [composer], within [maxHeight] (null:
  /// unbounded): the composer keeps its height and [above] gets the rest.
  Widget _aboveAndComposer(
    KitTokens tokens,
    double? maxHeight,
    Widget above,
    Widget composer,
  ) {
    final band = ColoredBox(
      color: tokens.roles.ground,
      child: Center(
        heightFactor: 1,
        child: ConstrainedBox(
          // The composer's width plus the gutters the parts above draw
          // themselves.
          constraints: BoxConstraints(
            maxWidth: KitLayout.paneDetailMaxWidth + 2 * tokens.gutter,
          ),
          child: above,
        ),
      ),
    );
    final column = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        maxHeight == null ? band : Flexible(child: band),
        if (maxHeight == null)
          composer
        else
          // Never taller than the room: on a small window at a large text
          // size (320 dp, 2.5x) the composer scrolls within it, its field
          // and Send in view, instead of running off the screen.
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: (maxHeight - widget.aboveMinHeight).clamp(
                0.0,
                maxHeight,
              ),
            ),
            child: SingleChildScrollView(
              key: const ValueKey('kit-composer-room'),
              reverse: true,
              primary: false,
              child: composer,
            ),
          ),
      ],
    );
    if (maxHeight == null) return column;
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: maxHeight),
      child: column,
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final inherited = KitBottomInset.of(context).bottom;
    final composer = Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        bottom: tokens.space2,
      ),
      child: Center(
        heightFactor: 1,
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: KitLayout.paneDetailMaxWidth,
          ),
          child: widget.composer,
        ),
      ),
    );
    return LayoutBuilder(
      builder: (context, constraints) => Stack(
        children: [
          PositionedDirectional(
            start: 0,
            end: 0,
            top: 0,
            bottom: 0,
            child: KitBottomInset.add(extraBottom: _height, child: widget.body),
          ),
          // The composer's band is solid ground from its top edge to the
          // window's bottom edge, across the full width: the transcript
          // scrolls out of sight at the composer and never shows beside
          // or beneath it (owner report, build 2055). The measured height
          // excludes [inherited], which the body already clears.
          PositionedDirectional(
            start: 0,
            end: 0,
            bottom: 0,
            child: ColoredBox(
              key: const ValueKey('kit-composer-band'),
              color: tokens.roles.ground,
              child: Padding(
                padding: EdgeInsetsDirectional.only(bottom: inherited),
                child: _SizeReporter(
                  onSize: _onSize,
                  // One structure with or without [above], so the composer's
                  // field keeps its state and focus when a part comes or goes.
                  child: _aboveAndComposer(
                    tokens,
                    constraints.hasBoundedHeight
                        ? (constraints.maxHeight - inherited).clamp(
                            0.0,
                            double.infinity,
                          )
                        : null,
                    widget.above ?? const SizedBox.shrink(),
                    _KitComposerRoom(
                      height: constraints.hasBoundedHeight
                          ? constraints.maxHeight
                          : null,
                      child: composer,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// The height [KitComposer.layer] lays its composer out in: the field's
/// cap is a share of it (KitLayout.composerMaxShare).
class _KitComposerRoom extends InheritedWidget {
  const _KitComposerRoom({required this.height, required super.child});

  final double? height;

  static double? of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitComposerRoom>()?.height;

  @override
  bool updateShouldNotify(_KitComposerRoom oldWidget) =>
      height != oldWidget.height;
}

/// Reports its child's laid-out size after each layout that changes it.
class _SizeReporter extends SingleChildRenderObjectWidget {
  const _SizeReporter({required this.onSize, super.child});

  final ValueChanged<Size> onSize;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderSizeReporter(onSize);

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderSizeReporter renderObject,
  ) {
    renderObject.onSize = onSize;
  }
}

class _RenderSizeReporter extends RenderProxyBox {
  _RenderSizeReporter(this.onSize);

  ValueChanged<Size> onSize;
  Size? _last;

  @override
  void performLayout() {
    super.performLayout();
    if (size != _last) {
      _last = size;
      final reported = size;
      WidgetsBinding.instance.addPostFrameCallback((_) => onSize(reported));
    }
  }
}
