import 'dart:async';

import 'package:flutter/material.dart';

import '../../demo/demo_copy.dart';

enum _DemoPhase { prompt, streaming, review, complete }

/// An in-memory walkthrough, deliberately independent of ChatScreen, Riverpod,
/// profiles, gateways and native bridges. Opening this route performs no I/O.
class DemoScreen extends StatefulWidget {
  const DemoScreen({super.key});

  @override
  State<DemoScreen> createState() => _DemoScreenState();
}

class _DemoScreenState extends State<DemoScreen> {
  _DemoPhase _phase = _DemoPhase.prompt;
  Timer? _timer;
  int _visibleWords = 0;
  bool _allowed = false;
  bool _reducedMotion = false;
  final _scroll = ScrollController();
  static final _words = DemoCopy.reply.split(' ');

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _reducedMotion =
        MediaQuery.disableAnimationsOf(context) ||
        MediaQuery.accessibleNavigationOf(context);
    if (_reducedMotion && _phase == _DemoPhase.streaming) {
      _timer?.cancel();
      _timer = null;
      _visibleWords = _words.length;
      _phase = _DemoPhase.review;
    }
  }

  void _send() {
    if (_phase != _DemoPhase.prompt) return;
    setState(() {
      _visibleWords = _reducedMotion ? _words.length : 1;
      _phase = _reducedMotion ? _DemoPhase.review : _DemoPhase.streaming;
    });
    if (_reducedMotion) return;
    _timer = Timer.periodic(const Duration(milliseconds: 100), (_) {
      if (!mounted) return;
      setState(() {
        _visibleWords++;
        if (_visibleWords >= _words.length) {
          _timer?.cancel();
          _timer = null;
          _phase = _DemoPhase.review;
        }
      });
    });
  }

  void _showReply() {
    _timer?.cancel();
    _timer = null;
    setState(() {
      _visibleWords = _words.length;
      _phase = _DemoPhase.review;
    });
  }

  void _decide(bool allow) {
    if (_phase != _DemoPhase.review) return;
    setState(() {
      _allowed = allow;
      _phase = _DemoPhase.complete;
    });
  }

  void _reset() {
    _timer?.cancel();
    _timer = null;
    setState(() {
      _phase = _DemoPhase.prompt;
      _visibleWords = 0;
      _allowed = false;
    });
    if (_scroll.hasClients) _scroll.jumpTo(0);
  }

  void _exit() {
    _timer?.cancel();
    _timer = null;
    Navigator.of(context).pop();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final buttonStyle = FilledButton.styleFrom(
      minimumSize: const Size(48, 48),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      tapTargetSize: MaterialTapTargetSize.padded,
    );
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text(DemoCopy.title),
        actions: [
          IconButton(
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            tooltip: DemoCopy.exit,
            onPressed: _exit,
            icon: const Icon(Icons.close_rounded),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 640),
            child: ListView(
              controller: _scroll,
              padding: const EdgeInsets.all(16),
              children: [
                Text(DemoCopy.title, style: theme.textTheme.headlineSmall),
                const SizedBox(height: 8),
                const Text(DemoCopy.disclosure),
                const SizedBox(height: 16),
                _card(context, DemoCopy.promptTitle, [
                  const Text(DemoCopy.prompt),
                  if (_phase == _DemoPhase.prompt) ...[
                    const SizedBox(height: 12),
                    FilledButton(
                      style: buttonStyle,
                      onPressed: _send,
                      child: const Text(DemoCopy.send),
                    ),
                  ],
                ]),
                if (_phase != _DemoPhase.prompt)
                  _card(context, DemoCopy.replyTitle, [
                    // Do not announce every streamed word to assistive tech.
                    Semantics(
                      label: _phase == _DemoPhase.streaming
                          ? DemoCopy.streaming
                          : DemoCopy.reply,
                      excludeSemantics: true,
                      child: Text(_words.take(_visibleWords).join(' ')),
                    ),
                    if (_phase == _DemoPhase.streaming) ...[
                      const SizedBox(height: 12),
                      const Text(DemoCopy.streaming),
                      TextButton(
                        style: buttonStyle,
                        onPressed: _showReply,
                        child: const Text(DemoCopy.showReply),
                      ),
                    ],
                  ]),
                if (_phase == _DemoPhase.review ||
                    _phase == _DemoPhase.complete)
                  _card(context, DemoCopy.reviewTitle, [
                    const Text(DemoCopy.before),
                    const SizedBox(height: 8),
                    const Text(DemoCopy.after),
                    if (_phase == _DemoPhase.review) ...[
                      const SizedBox(height: 16),
                      Semantics(
                        liveRegion: true,
                        child: const Text(DemoCopy.permission),
                      ),
                      const SizedBox(height: 12),
                      FilledButton(
                        style: buttonStyle,
                        onPressed: () => _decide(true),
                        child: const Text(DemoCopy.allow),
                      ),
                      const SizedBox(height: 8),
                      OutlinedButton(
                        style: buttonStyle,
                        onPressed: () => _decide(false),
                        child: const Text(DemoCopy.deny),
                      ),
                    ],
                  ]),
                if (_phase == _DemoPhase.complete)
                  Semantics(
                    liveRegion: true,
                    child: _card(context, DemoCopy.complete, [
                      Text(_allowed ? DemoCopy.allowed : DemoCopy.denied),
                    ]),
                  ),
                const SizedBox(height: 8),
                OutlinedButton(
                  style: buttonStyle,
                  onPressed: _reset,
                  child: const Text(DemoCopy.reset),
                ),
                const SizedBox(height: 8),
                TextButton(
                  style: buttonStyle,
                  onPressed: _exit,
                  child: const Text(DemoCopy.exit),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _card(BuildContext context, String title, List<Widget> children) {
    return Card.filled(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              header: true,
              child: Text(
                title,
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
            const SizedBox(height: 12),
            ...children,
          ],
        ),
      ),
    );
  }
}
