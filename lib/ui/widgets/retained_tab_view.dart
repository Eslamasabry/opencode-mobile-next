import 'package:flutter/material.dart';

/// Retains destination state while a short dissolve connects tab selections.
///
/// Incoming content responds immediately. Outgoing content is visual only:
/// it cannot receive focus, gestures or accessibility traversal, and its tickers
/// stop as soon as the selection changes. Rapid selections start from the
/// currently painted opacity, so they never queue animations or flash old tabs.
class RetainedTabView extends StatefulWidget {
  const RetainedTabView({
    super.key,
    required this.index,
    required this.children,
    this.reduceMotion = false,
  }) : assert(index >= 0 && index < children.length);

  static const duration = Duration(milliseconds: 180);

  final int index;
  final List<Widget> children;
  final bool reduceMotion;

  @override
  State<RetainedTabView> createState() => _RetainedTabViewState();
}

class _RetainedTabViewState extends State<RetainedTabView>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: RetainedTabView.duration,
    value: 1,
  );
  late List<double> _starts = _target(widget.index);
  late int _targetIndex = widget.index;

  List<double> _target(int index) => [
    for (var i = 0; i < widget.children.length; i++) i == index ? 1 : 0,
  ];

  double _opacity(int index) {
    final progress = Curves.easeOutCubic.transform(_controller.value);
    final end = index == _targetIndex ? 1.0 : 0.0;
    return _starts[index] + (end - _starts[index]) * progress;
  }

  @override
  void didUpdateWidget(RetainedTabView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.reduceMotion ||
        oldWidget.children.length != widget.children.length) {
      _starts = _target(widget.index);
      _targetIndex = widget.index;
      _controller.value = 1;
    } else if (oldWidget.index != widget.index) {
      _starts = [for (var i = 0; i < widget.children.length; i++) _opacity(i)];
      _targetIndex = widget.index;
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _controller,
    builder: (context, _) => Stack(
      fit: StackFit.expand,
      children: [
        for (var i = 0; i < widget.children.length; i++)
          Offstage(
            offstage: i != widget.index && _opacity(i) == 0,
            child: TickerMode(
              enabled: i == widget.index,
              child: ExcludeFocus(
                excluding: i != widget.index,
                child: ExcludeSemantics(
                  excluding: i != widget.index,
                  child: IgnorePointer(
                    ignoring: i != widget.index,
                    child: Opacity(
                      opacity: _opacity(i),
                      alwaysIncludeSemantics: i == widget.index,
                      child: RepaintBoundary(child: widget.children[i]),
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
