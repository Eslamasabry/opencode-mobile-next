import 'package:flutter/material.dart';

import '../kit_motion.dart';

/// A short list whose rows come and go gently (design standard §10): a
/// conversation that appears, a task added, a server forgotten.
///
/// Give it the rows as they are now, each with its own key (the thing's
/// id). A row whose key is new unfolds into place, fading in and rising a
/// few dp; a row whose key is gone folds away where it was, then leaves;
/// rows that stay keep their state and are not animated. The first build
/// shows the rows at once, and reduced motion makes every change instant.
///
/// For a list built all at once (a Column, `ListView(children: …)`), up to
/// a few dozen rows. A leaving row ignores touches and screen readers skip
/// it, so a tap never lands on something that is going away; its own
/// tickers stop (TickerMode), so it no longer counts as shown.
class KitAnimatedRows extends StatefulWidget {
  const KitAnimatedRows({
    super.key,
    required this.children,
    this.crossAxisAlignment = CrossAxisAlignment.stretch,
  });

  /// The rows, each with a unique non-null key.
  final List<Widget> children;
  final CrossAxisAlignment crossAxisAlignment;

  @override
  State<KitAnimatedRows> createState() => _KitAnimatedRowsState();
}

class _Entry {
  _Entry(this.child);

  Widget child;
  AnimationController? controller;
  bool leaving = false;

  Key get key => child.key!;
}

class _KitAnimatedRowsState extends State<KitAnimatedRows>
    with TickerProviderStateMixin {
  late List<_Entry> _entries = [
    for (final child in widget.children) _Entry(child),
  ];

  @override
  void initState() {
    super.initState();
    assert(
      widget.children.every((child) => child.key != null),
      'KitAnimatedRows needs a key on every row.',
    );
  }

  @override
  void didUpdateWidget(KitAnimatedRows old) {
    super.didUpdateWidget(old);
    final reduced = KitMotion.reduced(context);
    final previous = _entries;
    final byKey = {for (final entry in previous) entry.key: entry};
    final nextKeys = {for (final child in widget.children) child.key!};
    final next = <_Entry>[];
    var cursor = 0;

    // A row that is gone stays where it was while it folds away.
    void leave(_Entry gone) {
      if (reduced) {
        gone.controller?.dispose();
        gone.controller = null;
        return;
      }
      if (!gone.leaving) {
        gone.leaving = true;
        final controller = gone.controller ??= _controller(value: 1);
        controller.reverse().whenCompleteOrCancel(() => _drop(gone));
      }
      next.add(gone);
    }

    void flushGone() {
      while (cursor < previous.length &&
          !nextKeys.contains(previous[cursor].key)) {
        leave(previous[cursor++]);
      }
    }

    for (final child in widget.children) {
      flushGone();
      final existing = byKey[child.key];
      if (existing == null) {
        final entry = _Entry(child);
        if (!reduced) {
          final controller = entry.controller = _controller(value: 0);
          controller.forward().whenCompleteOrCancel(() => _settle(entry));
        }
        next.add(entry);
        continue;
      }
      if (cursor < previous.length && previous[cursor] == existing) cursor++;
      existing.child = child;
      if (existing.leaving) {
        // It came back before it had gone: unfold it again from here.
        existing.leaving = false;
        existing.controller?.forward().whenCompleteOrCancel(
          () => _settle(existing),
        );
      }
      next.add(existing);
    }
    // What is left: rows that moved earlier (already placed) and rows that
    // are gone.
    for (; cursor < previous.length; cursor++) {
      if (!nextKeys.contains(previous[cursor].key)) leave(previous[cursor]);
    }
    _entries = next;
  }

  AnimationController _controller({required double value}) =>
      AnimationController(
        vsync: this,
        duration: KitMotion.standard,
        reverseDuration: KitMotion.quick,
        value: value,
      );

  void _settle(_Entry entry) {
    final controller = entry.controller;
    if (!mounted || entry.leaving || controller == null) return;
    if (!controller.isCompleted || !_entries.contains(entry)) return;
    setState(() => entry.controller = null);
    controller.dispose();
  }

  void _drop(_Entry entry) {
    final controller = entry.controller;
    if (!mounted || !entry.leaving || !_entries.contains(entry)) return;
    if (controller != null && !controller.isDismissed) return;
    setState(() {
      _entries.remove(entry);
      entry.controller = null;
    });
    controller?.dispose();
  }

  @override
  void dispose() {
    for (final entry in _entries) {
      entry.controller?.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    crossAxisAlignment: widget.crossAxisAlignment,
    children: [
      for (final entry in _entries)
        _KitRowMotion(
          key: _RowKey(entry.key),
          animation: entry.controller ?? kAlwaysCompleteAnimation,
          leaving: entry.leaving,
          child: entry.child,
        ),
    ],
  );
}

class _RowKey extends ValueKey<Key> {
  const _RowKey(super.value);
}

/// One row's fold: the same widgets whether it moves or rests, so a row
/// that settles keeps its state.
class _KitRowMotion extends StatefulWidget {
  const _KitRowMotion({
    super.key,
    required this.animation,
    required this.leaving,
    required this.child,
  });

  final Animation<double> animation;
  final bool leaving;
  final Widget child;

  @override
  State<_KitRowMotion> createState() => _KitRowMotionState();
}

class _KitRowMotionState extends State<_KitRowMotion> {
  static final _fade = CurveTween(curve: const Interval(.3, 1));

  late CurvedAnimation _size = _curve(widget.animation);
  late Animation<double> _opacity = widget.animation.drive(_fade);

  static CurvedAnimation _curve(Animation<double> parent) => CurvedAnimation(
    parent: parent,
    curve: KitMotion.enter,
    reverseCurve: KitMotion.exit,
  );

  @override
  void didUpdateWidget(_KitRowMotion old) {
    super.didUpdateWidget(old);
    if (old.animation != widget.animation) {
      _size.dispose();
      _size = _curve(widget.animation);
      _opacity = widget.animation.drive(_fade);
    }
  }

  @override
  void dispose() {
    _size.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => IgnorePointer(
    ignoring: widget.leaving,
    child: ExcludeSemantics(
      excluding: widget.leaving,
      child: SizeTransition(
        sizeFactor: _size,
        alignment: AlignmentDirectional.topStart,
        child: FadeTransition(
          opacity: _opacity,
          child: AnimatedBuilder(
            animation: _size,
            builder: (context, child) => Transform.translate(
              offset: Offset(0, 6 * (1 - _size.value)),
              child: child,
            ),
            // A leaving row is on its way off the page: its own motion
            // stops, and the screen's checks (one primary per screen) no
            // longer count what it shows.
            child: TickerMode(enabled: !widget.leaving, child: widget.child),
          ),
        ),
      ),
    ),
  );
}
