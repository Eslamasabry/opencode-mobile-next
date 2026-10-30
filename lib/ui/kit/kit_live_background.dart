import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../theme_roles.dart';
import 'kit_effects.dart';
import 'kit_motion.dart';
import 'kit_text_halo.dart';
import 'kit_tokens.dart';
import 'style/kit_style.dart';
import 'live_background/kit_live_clock.dart';
import 'live_background/kit_live_styles.dart';

export 'live_background/kit_live_clock.dart';
export 'live_background/kit_live_styles.dart';

/// The ground of a page with a calm, slowly moving look on it (the person's
/// chosen [KitLiveStyle]; Settings › Appearance › Live background), the same
/// family as the composer's running glow: it brightens while a reply runs.
///
/// Off by default (the page is its plain colour until the person picks a
/// look). Nothing is blurred and nothing is see-through; a card, row or
/// sheet laid on top keeps its own solid surface. A look never exceeds
/// [KitLivePalette.ceiling] so text keeps its contrast
/// (test/kit_live_background_test.dart checks every look).
///
/// Cost: one repaint of a [RepaintBoundary] on each tick of the shared
/// [KitLiveClock] (12 a second, fewer for a look that asks for fewer:
/// [KitLiveStyle.framesPerSecond]; every page on the same tick), and no
/// frame at all between ticks or after half a minute with no touch
/// ([KitLiveClock.idleAfter]; the next touch carries on where it stopped).
/// It stops (a still frame) when the person turned Live background off,
/// when motion is reduced, calm or off ([KitMotion.loopsIn]; Saver turns
/// it off), when the page is hidden ([TickerMode]), when the app is not
/// resumed, and under `flutter test` (where [KitMotion.loops] is false and
/// only the flat ground is painted unless [paintWithoutLoops] is set).
///
/// States: none — it draws a ground; every look is always ready.
class KitLiveBackground extends StatefulWidget {
  const KitLiveBackground({
    super.key,
    required this.child,
    this.style,
    this.strength,
    this.ignoreSetting = false,
    this.replyRunning,
    this.roles,
    this.still = false,
  });

  /// Null: the person's chosen look ([KitEffects.liveStyle]).
  final KitLiveStyle? style;

  /// Null follows the person's Intensity choice; 1 is the shipping strength.
  final double? strength;

  /// A picker tile or the full-screen preview shows its look whatever the
  /// Live background switch says.
  final bool ignoreSetting;

  /// A reply is running (a preview's own switch); null follows the app's
  /// composers ([KitLiveBackground.running]).
  final ValueListenable<bool>? replyRunning;

  /// Paint in these colours instead of the app's (a theme card shows the
  /// person's look in its own theme). Null: the app's roles.
  final ThemeRoles? roles;

  /// One frame, never ticking: a small sample among many (theme cards), so a
  /// carousel costs no battery.
  final bool still;

  /// How long the fields take to brighten or settle.
  static const Duration brighten = Duration(milliseconds: 600);

  /// Tests only: paint the (still) look although [KitMotion.loops] is off,
  /// for evidence images. Goldens leave it false and show the flat ground.
  static bool paintWithoutLoops = false;

  /// Tests only: hold the clock at this second (evidence frames).
  static double? frozenSeconds;

  /// Replies running anywhere visible. The composer's glowing border adds
  /// one while it is active; the look brightens while this is above zero.
  static final ValueNotifier<int> running = ValueNotifier<int>(0);

  /// The colours a look may use under [tokens].
  static KitLivePalette paletteOf(KitTokens tokens) =>
      paletteOfRoles(tokens.roles);

  /// The colours a look may use in the theme [r] (a theme card paints
  /// another theme's look).
  static KitLivePalette paletteOfRoles(ThemeRoles r) {
    final dark = r.brightness == Brightness.dark;
    return KitLivePalette(
      ground: r.ground,
      ink: r.text1,
      muted: r.text2,
      faint: r.text3,
      green: r.accent,
      teal: Color.lerp(r.accent, r.codeKeyword, .35)!,
      blue: r.codeKeyword,
      glow: r.accent,
      dark: dark,
    );
  }

  final Widget child;

  @override
  State<KitLiveBackground> createState() => _KitLiveBackgroundState();
}

class _KitLiveBackgroundState extends State<KitLiveBackground>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  final ValueNotifier<double> _time = ValueNotifier<double>(0);
  final KitLiveMemory _memory = KitLiveMemory();
  final KitLiveClock _clock = KitLiveClock.instance;
  bool _attached = false;
  late final AnimationController _lift = AnimationController(
    vsync: this,
    duration: KitLiveBackground.brighten,
  );
  AppLifecycleState? _lifecycle = WidgetsBinding.instance.lifecycleState;
  bool _animate = false;
  bool _ready = false;
  KitLiveStyle? _warmed;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    KitLiveBackground.running.addListener(_runningChanged);
    widget.replyRunning?.addListener(_runningChanged);
    _lift.value = _isRunning ? 1 : 0;
    _time.value = _seconds;
  }

  bool get _isRunning =>
      widget.replyRunning?.value ?? KitLiveBackground.running.value > 0;

  double get _seconds =>
      KitLiveBackground.frozenSeconds ?? _clock.seconds.value;

  void _runningChanged() {
    if (!_animate) {
      _lift.value = _isRunning ? 1 : 0;
      return;
    }
    unawaited(_isRunning ? _lift.forward() : _lift.reverse());
  }

  /// A tick of the shared clock: the look moves on, or waits for its own
  /// slower rate ([KitLiveStyle.framesPerSecond]).
  void _tick() {
    final fps = _style.framesPerSecond.clamp(1, KitLiveClock.framesPerSecond);
    final every = (KitLiveClock.framesPerSecond / fps).round();
    if (_clock.ticks % every == 0) _time.value = _seconds;
  }

  void _attach(bool on) {
    if (on == _attached) return;
    _attached = on;
    if (on) {
      _clock.seconds.addListener(_tick);
      _clock.attach();
      _time.value = _seconds;
    } else {
      _clock.seconds.removeListener(_tick);
      _clock.detach();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _lifecycle = state;
    _sync();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(KitLiveBackground old) {
    super.didUpdateWidget(old);
    if (old.replyRunning != widget.replyRunning) {
      old.replyRunning?.removeListener(_runningChanged);
      widget.replyRunning?.addListener(_runningChanged);
      _runningChanged();
    }
    _sync();
  }

  KitLiveStyle get _style =>
      widget.style ?? kitLiveStyleById(KitEffects.read(context).liveStyle);

  /// Starts or stops the ticker to match the setting, motion, lifecycle, and
  /// loads the look.
  void _sync() {
    final effects = KitEffects.of(context);
    final hidden =
        widget.strength == null &&
        kitStyleSpec(
              KitStyles.styleOf(context),
            ).tokens.live.cap(effects.liveIntensity.strength) <=
            0;
    final should =
        (effects.liveBackground || widget.ignoreSetting) &&
        !hidden &&
        !widget.still &&
        KitMotion.loopsIn(context) &&
        TickerMode.valuesOf(context).enabled &&
        (_lifecycle == null || _lifecycle == AppLifecycleState.resumed);
    _animate = should;
    _attach(should);
    if (!should) _lift.value = _isRunning ? 1 : 0;
    final style = _style;
    if (!identical(style, _warmed)) {
      _warmed = style;
      _ready = false;
      _memory.clear();
      unawaited(
        style.warmUp().then((_) {
          if (mounted && identical(_warmed, style)) {
            setState(() => _ready = true);
          }
        }),
      );
    }
  }

  /// How far the look is lifted towards the running look (tests).
  @visibleForTesting
  double get lift => _lift.value;

  /// Whether the look moves on the shared clock (tests).
  @visibleForTesting
  bool get ticking => _attached;

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    KitLiveBackground.running.removeListener(_runningChanged);
    widget.replyRunning?.removeListener(_runningChanged);
    _attach(false);
    _lift.dispose();
    _time.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final effects = KitEffects.of(context);
    // Hook H7: a style may cap the look (Subtle) or hide it (0); a preview
    // that names its own strength is never capped.
    final policy = kitStyleSpec(KitStyles.styleOf(context)).tokens.live;
    final strength =
        widget.strength ?? policy.cap(effects.liveIntensity.strength);
    final paint =
        (effects.liveBackground || widget.ignoreSetting) &&
        _ready &&
        strength > 0 &&
        (KitMotion.loops || KitLiveBackground.paintWithoutLoops);
    final roles = widget.roles ?? tokens.roles;
    final ground = roles.ground;
    final palette = KitLiveBackground.paletteOfRoles(roles);
    // One shape whether or not the look is painted yet: the page above must
    // keep its state (a typed draft, a scroll position) when the look finishes
    // loading, so its place in the tree never changes.
    return ColoredBox(
      color: ground,
      child: Stack(
        fit: StackFit.passthrough,
        children: [
          Positioned.fill(
            child: !paint
                ? const SizedBox.shrink()
                : ExcludeSemantics(
                    child: RepaintBoundary(
                      child: CustomPaint(
                        painter: _StylePainter(
                          style: _style,
                          time: _time,
                          lift: _lift,
                          palette: palette,
                          memory: _memory,
                          strength: strength,
                          still:
                              !_animate &&
                              KitLiveBackground.frozenSeconds == null,
                        ),
                      ),
                    ),
                  ),
          ),
          KitTextHalo(
            ground: paint && _style.wantsTextHalo(palette) ? ground : null,
            child: widget.child,
          ),
        ],
      ),
    );
  }
}

class _StylePainter extends CustomPainter {
  _StylePainter({
    required this.style,
    required this.time,
    required this.lift,
    required this.palette,
    required this.memory,
    required this.strength,
    required this.still,
  }) : super(repaint: Listenable.merge([time, lift]));

  final KitLiveStyle style;
  final ValueListenable<double> time;
  final Animation<double> lift;
  final KitLivePalette palette;
  final KitLiveMemory memory;
  final double strength;
  final bool still;

  @override
  void paint(Canvas canvas, Size size) {
    style.paint(
      canvas,
      size,
      KitLiveFrame(
        t: still ? 0 : time.value,
        running: KitMotion.pulse.transform(lift.value),
        strength: strength,
        palette: palette,
        memory: memory,
      ),
    );
  }

  @override
  bool shouldRepaint(_StylePainter old) =>
      old.style != style ||
      old.palette.ground != palette.ground ||
      old.palette.green != palette.green ||
      old.palette.muted != palette.muted ||
      old.palette.faint != palette.faint ||
      old.strength != strength ||
      old.still != still;
}
