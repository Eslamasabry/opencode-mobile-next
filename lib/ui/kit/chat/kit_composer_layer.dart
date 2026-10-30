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

  /// The composer pane's own size, for the transcript's clip around it.
  Size _composerSize = Size.zero;

  /// Content of the body is scrolled under the composer now (frost
  /// policy: blur only while there is something under it).
  bool _under = false;

  /// How far the body runs up under the page's bar (the header's glass,
  /// [KitFrostHeaderHost]); 0 keeps it below the bar.
  double _extendUp = 0;

  /// Whether glass may show here at all ([KitFrost.strengthOf]), read in
  /// build for the header measure after the frame.
  bool _frostAllowed = false;

  KitFrostHeaderHost? _host;
  final KitFrostTopClaim _claim = KitFrostTopClaim();

  void _onSize(Size size) {
    if (!mounted || size.height == _height) return;
    setState(() => _height = size.height);
  }

  void _onComposerSize(Size size) {
    if (!mounted || size == _composerSize) return;
    setState(() => _composerSize = size);
  }

  @override
  void initState() {
    super.initState();
    KitFrost.unawaitedLoad();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _host = KitFrostHeader.hostOf(context);
  }

  @override
  void dispose() {
    _host?.release();
    super.dispose();
  }

  /// Reads the body's own scrolling: what is under the composer (below the
  /// viewport's bottom edge) and under the bar (above its top edge).
  bool _onScroll(Notification notification) {
    final ScrollMetrics metrics;
    if (notification is ScrollNotification && notification.depth == 0) {
      metrics = notification.metrics;
    } else if (notification is ScrollMetricsNotification &&
        notification.depth == 0) {
      metrics = notification.metrics;
    } else {
      return false;
    }
    if (metrics.axis != Axis.vertical) return false;
    final up = metrics.axisDirection == AxisDirection.up;
    final below = up ? metrics.extentBefore : metrics.extentAfter;
    final above = up ? metrics.extentAfter : metrics.extentBefore;
    _seen = (under: below > .5, above: above > .5);
    // A list that reports its size while it is laid out (the transcript's
    // positioned list) cannot rebuild us now: after this frame.
    if (SchedulerBinding.instance.schedulerPhase ==
        SchedulerPhase.persistentCallbacks) {
      if (!_applyScheduled) {
        _applyScheduled = true;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          _applyScheduled = false;
          if (mounted) _applySeen();
        });
      }
    } else {
      _applySeen();
    }
    return false;
  }

  ({bool under, bool above}) _seen = (under: false, above: false);
  bool _applyScheduled = false;

  void _applySeen() {
    if (_seen.under != _under) setState(() => _under = _seen.under);
    _host?.under.value = _seen.above;
  }

  /// After a frame: whether the body sits straight under the page's bar
  /// (nothing between them, the loading bar idle) and has opted in
  /// ([KitFrostHeader.topClearanceOf]); if so, how far to run it up.
  void _measureHeader(Duration _) {
    if (!mounted) return;
    final host = _host;
    var extend = 0.0;
    if (host != null &&
        _frostAllowed &&
        !host.loading &&
        KitFrostHeader.honoured(_claim)) {
      final me = context.findRenderObject();
      if (me is RenderBox) extend = KitFrostHeader.extendUpFor(host, me);
    }
    if ((extend - _extendUp).abs() > .5) setState(() => _extendUp = extend);
    host?.extendUp.value = _extendUp;
  }

  /// [above] on the ground over [composer], within [maxHeight] (null:
  /// unbounded): the composer keeps its height and [above] gets the rest.
  Widget _aboveAndComposer(
    KitTokens tokens,
    double? maxHeight,
    Widget above,
    Widget composer,
  ) {
    // Transparent: the page's live background shows through the parts over
    // the composer. The body is clipped above this band, so nothing shows
    // behind it.
    final band = ColoredBox(
      color: Colors.transparent,
      child: Center(
        heightFactor: 1,
        child: ConstrainedBox(
          // The composer's width plus the gutters the parts above draw
          // themselves.
          constraints: BoxConstraints(
            maxWidth: KitLayout.paneDetailMaxWidth + 2 * tokens.gutter,
          ),
          // A sideways line of chips ends inside the glass pane's inner
          // edge (the composer's own edge), never across the pane.
          child: KitFrost.lineInset(
            value: _frostAllowed ? tokens.gutter : 0,
            child: above,
          ),
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
  Widget build(BuildContext context) => ValueListenableBuilder<KitGlassChoice>(
    valueListenable: GlassChoiceStore.choice,
    builder: (context, _, _) => _build(context),
  );

  Widget _build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final inherited = KitBottomInset.of(context).bottom;
    final strength = KitFrost.strengthOf(context);
    _frostAllowed = strength != null;
    final radius = KitLayout.windowOf(context).isWide
        ? tokens.composerRadiusWide
        : tokens.composerRadius;
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
          child: _SizeReporter(onSize: _onComposerSize, child: widget.composer),
        ),
      ),
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        // Measured after every layout that may have moved the layer (a
        // status line coming or going above it).
        WidgetsBinding.instance.addPostFrameCallback(_measureHeader);
        final width = constraints.maxWidth;
        final bodyHeight = constraints.maxHeight + _extendUp;
        // The glass pane: with chip rows over the composer it stands one
        // space2 around them and the composer, from the band's top to its
        // bottom, corners concentric with the composer's; it adds no
        // height. With the composer alone (the keyboard up) the field
        // itself is the glass, with no second outline around its own.
        final aboveEmpty = _height - _composerSize.height - tokens.space2 < 1;
        final margin = aboveEmpty ? 0.0 : tokens.space2;
        final paneRadius = BorderRadius.circular(radius + margin);
        final side = width.isFinite
            ? ((width - _composerSize.width) / 2 - margin).clamp(0.0, width)
            : 0.0;
        // Under the field alone the pane stops at the field's bottom edge.
        final paneInsets = EdgeInsets.symmetric(
          horizontal: side,
        ).copyWith(bottom: aboveEmpty ? tokens.space2 : null);
        // While the pane is glass and content is under it, the transcript
        // runs on under the pane (and only there), in the body's
        // coordinates.
        final under = strength != null && _under && _composerSize != Size.zero;
        final bandTop = bodyHeight - inherited - _height;
        final pane = under && width.isFinite
            ? paneRadius.toRRect(
                Rect.fromLTRB(
                  side,
                  bandTop,
                  width - side,
                  bodyHeight - inherited - paneInsets.bottom,
                ),
              )
            : null;
        return Stack(
          clipBehavior: Clip.none,
          children: [
            PositionedDirectional(
              start: 0,
              end: 0,
              // Run up under the page's bar while it frosts.
              top: -_extendUp,
              bottom: 0,
              child: ClipRect(
                clipper: _AboveBand(
                  pane == null ? _height + inherited : inherited,
                ),
                child: ClipPath(
                  clipper: _PaneCut(pane),
                  // No clip layer at all while the pane is not glass.
                  clipBehavior: pane == null ? Clip.none : Clip.hardEdge,
                  child: KitBottomInset.add(
                    extraBottom: _height,
                    child: KitFrostHeader.clearance(
                      value: _extendUp,
                      claim: _claim,
                      child: NotificationListener<Notification>(
                        onNotification: _onScroll,
                        child: widget.body,
                      ),
                    ),
                  ),
                ),
              ),
            ),
            // The composer's band runs from its top edge to the window's
            // bottom edge, across the full width. It paints nothing, so the
            // live background shows through; the transcript is clipped at
            // its top edge instead and never shows beside or beneath the
            // composer (owner reports, build 2055 and 2316) — only under the
            // composer's own pane while it is glass. The measured height
            // excludes [inherited], which the body already clears.
            PositionedDirectional(
              start: 0,
              end: 0,
              bottom: 0,
              child: ColoredBox(
                key: const ValueKey('kit-composer-band'),
                color: Colors.transparent,
                child: Padding(
                  padding: EdgeInsetsDirectional.only(bottom: inherited),
                  child: _SizeReporter(
                    onSize: _onSize,
                    // The chip row and the field as one pane, frosted while
                    // the transcript is scrolled under it (owner,
                    // 2026-10-30). One structure with or without [above] or
                    // glass, so the composer's field keeps its state and
                    // focus when a part comes or goes.
                    child: KitFrost(
                      role: KitFrostRole.composer,
                      strength: strength,
                      shown: _under,
                      tint: tokens.roles.ground,
                      borderRadius: paneRadius,
                      paneInsets: paneInsets,
                      edges: !aboveEmpty,
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
            ),
          ],
        );
      },
    );
  }
}

/// Clips the transcript at the composer band's top edge, or at the
/// composer pane's bottom edge while the pane is glass with content under
/// it.
class _AboveBand extends CustomClipper<Rect> {
  const _AboveBand(this.bottom);

  final double bottom;

  @override
  Rect getClip(Size size) => Rect.fromLTRB(
    0,
    0,
    size.width,
    (size.height - bottom).clamp(0, size.height),
  );

  @override
  bool shouldReclip(_AboveBand old) => old.bottom != bottom;
}

/// While the pane is glass: everything above its top edge, and the pane's
/// own rounded rectangle, never the gutters beside it or the corners.
class _PaneCut extends CustomClipper<Path> {
  const _PaneCut(this.pane);

  final RRect? pane;

  @override
  Path getClip(Size size) {
    final pane = this.pane;
    if (pane == null) return Path()..addRect(Offset.zero & size);
    return Path()
      ..addRect(Rect.fromLTRB(0, 0, size.width, pane.top))
      ..addRRect(pane);
  }

  @override
  bool shouldReclip(_PaneCut old) => old.pane != pane;
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
