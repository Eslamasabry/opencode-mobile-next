import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

/// Whether the app is out of sight (hidden, paused or detached), for work
/// that can wait a little longer when nobody is looking.
///
/// It reads Flutter's lifecycle only; where Flutter's binding is not running
/// (plain Dart tests) the app counts as visible.
class AppVisibility with WidgetsBindingObserver {
  AppVisibility._();

  static final AppVisibility instance = AppVisibility._();

  /// True while the app is not resumed or inactive-but-visible.
  final ValueNotifier<bool> hidden = ValueNotifier<bool>(false);

  bool _attached = false;

  /// Starts listening (once). Safe to call from any thread of start-up.
  void ensure() {
    if (_attached) return;
    try {
      final binding = WidgetsBinding.instance;
      binding.addObserver(this);
      _attached = true;
      _set(binding.lifecycleState);
    } on Object {
      // No binding: stay visible.
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) => _set(state);

  void _set(AppLifecycleState? state) {
    hidden.value =
        state == AppLifecycleState.hidden ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached;
  }
}

/// A repeating timer that ticks every [visible] while the app is in sight and
/// every [hidden] (slower) while it is not, for checks that wake the CPU for
/// nothing when nobody is looking. A change of visibility moves it to the
/// other pace at once, so the foreground pace comes back the moment the app
/// does; it does not tick by itself on the change.
class VisibilityPacedTimer {
  VisibilityPacedTimer({
    required this.visible,
    required this.hidden,
    required this.onTick,
    ValueListenable<bool>? isHidden,
  }) : _isHidden = isHidden ?? AppVisibility.instance.hidden;

  final Duration visible;
  final Duration hidden;
  final void Function() onTick;
  final ValueListenable<bool> _isHidden;

  Timer? _timer;
  bool _running = false;

  /// Whether it still ticks (false before [start] and after [cancel]).
  bool get isActive => _running;

  /// Starts at the pace the app's visibility asks for now. Does nothing when
  /// it already runs.
  void start() {
    if (_running) return;
    _running = true;
    _isHidden.addListener(_rearm);
    _arm();
  }

  void cancel() {
    if (!_running) return;
    _running = false;
    _isHidden.removeListener(_rearm);
    _timer?.cancel();
    _timer = null;
  }

  void _rearm() {
    if (_running) _arm();
  }

  void _arm() {
    _timer?.cancel();
    _timer = Timer.periodic(_isHidden.value ? hidden : visible, (_) {
      if (_running) onTick();
    });
  }
}
