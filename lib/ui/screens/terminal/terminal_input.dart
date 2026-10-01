part of '../terminal_screen.dart';

/// Serialises terminal keystrokes into one ordered write per flush.
///
/// Every keystroke used to reach the channel as its own send; under fast
/// typing that let sends interleave and the PTY echo arrive out of order or
/// twice. The queue appends to a single FIFO buffer and flushes it as one
/// write on the next microtask, so bytes leave in exactly the order typed.
class TerminalInputQueue {
  TerminalInputQueue(this._send);

  final void Function(String value) _send;
  final _buffer = StringBuffer();
  bool _flushScheduled = false;
  bool _closed = false;

  bool get hasPending => _buffer.isNotEmpty;

  void write(String value) {
    if (_closed || value.isEmpty) return;
    _buffer.write(value);
    if (_flushScheduled) return;
    _flushScheduled = true;
    scheduleMicrotask(flush);
  }

  /// Sends everything queued so far as one write, in order.
  void flush() {
    _flushScheduled = false;
    if (_closed || _buffer.isEmpty) return;
    final pending = _buffer.toString();
    _buffer.clear();
    _send(pending);
  }

  /// Drops what has not been sent; used when the channel goes away.
  void discard() {
    _buffer.clear();
  }

  void close() {
    _closed = true;
    _buffer.clear();
  }
}

/// Whether a hardware key event carries a printable character the terminal
/// should receive as text; modifier chords and control keys are left to
/// xterm's own key table.
bool terminalKeyEventText(KeyEvent event) {
  if (event is KeyUpEvent) return false;
  final character = event.character;
  if (character == null || character.isEmpty) return false;
  final code = character.codeUnitAt(0);
  if (code < 0x20 || code == 0x7f) return false;
  final keyboard = HardwareKeyboard.instance;
  return !keyboard.isControlPressed && !keyboard.isMetaPressed;
}

AppLocalizations _l10nOf(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The capability a server's terminals need (docs/ux-system/capabilities.json).
const _terminalCapability = 'flag:terminal';
