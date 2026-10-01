part of '../terminal_screen.dart';

/// The terminal for a screen reader: the readable output (control codes
/// stripped) and one labelled command field that sends a line.
class _AccessibleTerminal extends StatelessWidget {
  final String transcript;
  final TextEditingController input;
  final bool enabled;
  final VoidCallback onSend;

  const _AccessibleTerminal({
    required this.transcript,
    required this.input,
    required this.enabled,
    required this.onSend,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = _l10nOf(context);
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space2,
        tokens.gutter,
        tokens.space2,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: Semantics(
              label: l10n.e7SetupTranscript,
              textField: true,
              readOnly: true,
              child: KitSurface(
                level: KitSurfaceLevel.surface1,
                child: ListView(
                  reverse: true,
                  children: [
                    if (transcript.isEmpty)
                      KitText(l10n.e7SetupNoOutput, tone: KitTextTone.secondary)
                    else
                      KitText.mono(transcript, selectable: true),
                  ],
                ),
              ),
            ),
          ),
          SizedBox(height: tokens.space2),
          KitField(
            label: l10n.e7SetupCommandInput,
            hint: l10n.e7SetupCommandHint,
            kind: KitFieldKind.mono,
            controller: input,
            enabled: enabled,
            disabledReason: enabled ? null : l10n.e7SetupInputDisconnected,
            textInputAction: TextInputAction.send,
            onSubmitted: (_) => onSend(),
            fieldKey: const Key('terminal-accessible-input'),
            action: KitAction(
              key: const ValueKey('terminal-accessible-send'),
              label: enabled
                  ? l10n.e7SetupSendCommand
                  : l10n.e7SetupInputUnavailable,
              icon: AppIconography.returnKey,
              onPressed: enabled ? onSend : null,
            ),
          ),
        ],
      ),
    );
  }
}

/// Converts a stream of terminal bytes decoded as text into a readable,
/// append-only transcript. Terminal rendering commands are intentionally not
/// exposed to assistive technology.
class _TerminalTranscriptSanitizer {
  static const _normal = 0;
  static const _escape = 1;
  static const _escapeIntermediate = 2;
  static const _csi = 3;
  static const _osc = 4;
  static const _oscEscape = 5;
  static const _controlString = 6;
  static const _controlStringEscape = 7;

  int _state = _normal;
  bool _pendingCarriageReturn = false;

  void reset() {
    _state = _normal;
    _pendingCarriageReturn = false;
  }

  String add(String chunk) {
    // Keep a defensive guard for older/custom transports that expose OpenCode's
    // NUL-prefixed metadata as text instead of consuming it at the channel.
    if (chunk.isEmpty || (_state == _normal && chunk.startsWith('\x00'))) {
      return '';
    }
    final output = StringBuffer();

    for (final rune in chunk.runes) {
      if (_state == _normal && _pendingCarriageReturn) {
        if (rune == 0x0a) {
          output.write('\n');
          _pendingCarriageReturn = false;
          continue;
        }
        output.write('\n');
        _pendingCarriageReturn = false;
      }

      switch (_state) {
        case _normal:
          if (rune == 0x1b) {
            _state = _escape;
          } else if (rune == 0x9b) {
            _state = _csi;
          } else if (rune == 0x9d) {
            _state = _osc;
          } else if (rune == 0x90 ||
              rune == 0x98 ||
              rune == 0x9e ||
              rune == 0x9f) {
            _state = _controlString;
          } else if (rune == 0x0d) {
            _pendingCarriageReturn = true;
          } else if (rune == 0x0a || rune == 0x09) {
            output.writeCharCode(rune);
          } else if ((rune >= 0x20 && rune != 0x7f) &&
              !(rune >= 0x80 && rune <= 0x9f)) {
            output.writeCharCode(rune);
          }
        case _escape:
          if (rune == 0x5b) {
            _state = _csi;
          } else if (rune == 0x5d) {
            _state = _osc;
          } else if (rune == 0x50 ||
              rune == 0x58 ||
              rune == 0x5e ||
              rune == 0x5f) {
            _state = _controlString;
          } else if (rune >= 0x20 && rune <= 0x2f) {
            _state = _escapeIntermediate;
          } else if (rune != 0x1b) {
            _state = _normal;
          }
        case _escapeIntermediate:
          if (rune == 0x1b) {
            _state = _escape;
          } else if (rune >= 0x30 && rune <= 0x7e) {
            _state = _normal;
          } else if (rune < 0x20 || rune > 0x2f) {
            _state = _normal;
          }
        case _csi:
          if (rune == 0x1b) {
            _state = _escape;
          } else if (rune >= 0x40 && rune <= 0x7e) {
            _state = _normal;
          }
        case _osc:
          if (rune == 0x07 || rune == 0x9c) {
            _state = _normal;
          } else if (rune == 0x1b) {
            _state = _oscEscape;
          }
        case _oscEscape:
          if (rune == 0x5c) {
            _state = _normal;
          } else if (rune != 0x1b) {
            _state = _osc;
          }
        case _controlString:
          if (rune == 0x9c) {
            _state = _normal;
          } else if (rune == 0x1b) {
            _state = _controlStringEscape;
          }
        case _controlStringEscape:
          if (rune == 0x5c) {
            _state = _normal;
          } else if (rune != 0x1b) {
            _state = _controlString;
          }
      }
    }
    return output.toString();
  }
}
