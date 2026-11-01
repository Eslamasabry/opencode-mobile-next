// Hearing the person stop talking, and deciding whether what was heard is
// worth sending. Pure logic, so the timing is tested without a microphone.

/// How long of quiet ends a recording.
const voiceSilenceToStop = Duration(milliseconds: 1500);

/// How long the words wait, visibly, before they are sent.
const voiceAutoSendCountdown = Duration(seconds: 3);

/// Watches the loudness of the audio as it arrives and says when the person
/// has stopped talking: [silence] of quiet after they have spoken.
///
/// Quiet before any speech never ends a recording (the person may be
/// gathering their thoughts), and a short click or cough ([minSpeech]) is not
/// speech. Loudness is the 0..1 root-mean-square level of one buffer.
class SilenceDetector {
  SilenceDetector({
    this.silence = voiceSilenceToStop,
    this.speechLevel = 0.015,
    this.minSpeech = const Duration(milliseconds: 300),
  });

  /// Quiet that ends the recording once speech was heard.
  final Duration silence;

  /// Loudness at or above which a buffer counts as speech.
  final double speechLevel;

  /// Loud time that has to add up before it counts as speech.
  final Duration minSpeech;

  Duration _loud = Duration.zero;
  Duration _quiet = Duration.zero;

  /// Speech has been heard in this recording.
  bool get heardSpeech => _loud >= minSpeech;

  /// Adds one buffer: its [level] and how long it [span]s. True when the
  /// person has stopped talking.
  bool add(double level, Duration span) {
    if (level >= speechLevel) {
      _loud += span;
      _quiet = Duration.zero;
      return false;
    }
    if (!heardSpeech) return false;
    _quiet += span;
    return _quiet >= silence;
  }

  void reset() {
    _loud = Duration.zero;
    _quiet = Duration.zero;
  }
}

/// Whether [transcript] holds enough to send without a person looking at it:
/// at least two letters or digits once the speech model's notes about the
/// sound ("[BLANK_AUDIO]", "(music)", "♪") are taken out. Nothing and a
/// stray "uh" or a lone sound never go out by themselves.
bool isSendableTranscript(String transcript) {
  final words = transcript
      .replaceAll(RegExp(r'\[[^\]]*\]|\([^)]*\)|\*[^*]*\*|[♪♫]'), ' ')
      .replaceAll(RegExp(r'[^\p{L}\p{N}]+', unicode: true), '');
  return words.runes.length >= 2;
}

/// Whether the words may go out by themselves after the countdown:
/// [isSendableTranscript], and not something the chat would run as a
/// command ("/..." or "!...") - a mis-heard sound must never run one.
bool mayAutoSend(String transcript) {
  final text = transcript.trimLeft();
  if (text.startsWith('/') || text.startsWith('!')) return false;
  return isSendableTranscript(text);
}
