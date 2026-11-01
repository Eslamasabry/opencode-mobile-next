import 'dart:async';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:sherpa_onnx/sherpa_onnx.dart' as sherpa;

import 'natural_voice.dart';

/// A running synthesis. [cancel] ends it at once (the worker isolate is
/// killed, so a long sentence stops mid-word).
abstract interface class NaturalSynthesis {
  void cancel();
}

/// Speaks [text] with the natural voice in a worker isolate, never on the UI
/// isolate. Each finished sentence arrives as 16-bit little-endian mono PCM
/// at [NaturalSpeech.sampleRate] through [onChunk]; [onDone] runs after the
/// last one, [onError] once if loading or synthesis fails (and then nothing
/// else is delivered).
NaturalSynthesis startNaturalSynthesis(
  NaturalSpeech speech,
  String text, {
  required void Function(Uint8List pcm) onChunk,
  required void Function() onDone,
  required void Function() onError,
}) {
  final job = _IsolateSynthesis(
    speech,
    text,
    onChunk: onChunk,
    onDone: onDone,
    onError: onError,
  );
  unawaited(job._start());
  return job;
}

class _IsolateSynthesis implements NaturalSynthesis {
  _IsolateSynthesis(
    this.speech,
    this.text, {
    required this.onChunk,
    required this.onDone,
    required this.onError,
  });

  final NaturalSpeech speech;
  final String text;
  final void Function(Uint8List pcm) onChunk;
  final void Function() onDone;
  final void Function() onError;

  final ReceivePort _messages = ReceivePort();
  final ReceivePort _errors = ReceivePort();
  final ReceivePort _exits = ReceivePort();
  Isolate? _isolate;
  bool _finished = false;
  bool _cancelled = false;

  Future<void> _start() async {
    _messages.listen((message) {
      if (_finished) return;
      if (message is TransferableTypedData) {
        onChunk(message.materialize().asUint8List());
      } else if (message == 'done') {
        _finish();
        onDone();
      } else if (message == 'failed') {
        _finish();
        onError();
      }
    });
    _errors.listen((_) {
      if (_finished) return;
      _finish();
      onError();
    });
    _exits.listen((_) {
      if (_finished) return;
      _finish();
      onError();
    });
    try {
      _isolate = await Isolate.spawn<Map<String, Object>>(
        _naturalWorker,
        {
          'sendPort': _messages.sendPort,
          'model': speech.modelPath,
          'voices': speech.voicesPath,
          'tokens': speech.tokensPath,
          'lexicon': speech.lexiconPath,
          'sid': speech.speakerId,
          'rate': speech.sampleRate,
          'threads': speech.numThreads,
          'text': text,
        },
        onError: _errors.sendPort,
        onExit: _exits.sendPort,
        errorsAreFatal: true,
      );
      if (_cancelled) _isolate?.kill(priority: Isolate.immediate);
    } catch (_) {
      if (!_finished) {
        _finish();
        onError();
      }
    }
  }

  void _finish() {
    _finished = true;
    _messages.close();
    _errors.close();
    _exits.close();
  }

  @override
  void cancel() {
    if (_cancelled) return;
    _cancelled = true;
    _finished = true;
    _isolate?.kill(priority: Isolate.immediate);
    _messages.close();
    _errors.close();
    _exits.close();
  }
}

/// Splits [text] into pieces a model reads in one go: sentence ends first,
/// then the nearest space once a piece passes [limit] characters.
List<String> splitForSynthesis(String text, {int limit = 280}) {
  final pieces = <String>[];
  final sentence = RegExp(r'[^.!?\n]+[.!?]*\s*|\n+');
  for (final match in sentence.allMatches(text)) {
    var part = match.group(0)!.trim();
    while (part.length > limit) {
      var cut = part.lastIndexOf(' ', limit);
      if (cut <= 0) cut = limit;
      pieces.add(part.substring(0, cut).trim());
      part = part.substring(cut).trim();
    }
    if (part.isNotEmpty) pieces.add(part);
  }
  return pieces;
}

void _naturalWorker(Map<String, Object> message) {
  final sendPort = message['sendPort']! as SendPort;
  sherpa.OfflineTts? tts;
  try {
    sherpa.initBindings();
    tts = sherpa.OfflineTts(
      sherpa.OfflineTtsConfig(
        model: sherpa.OfflineTtsModelConfig(
          kokoro: sherpa.OfflineTtsKokoroModelConfig(
            model: message['model']! as String,
            voices: message['voices']! as String,
            tokens: message['tokens']! as String,
            lexicon: message['lexicon']! as String,
          ),
          numThreads: message['threads']! as int,
          debug: false,
          provider: 'cpu',
        ),
        maxNumSentences: 1,
      ),
    );
    final rate = message['rate']! as int;
    if (tts.sampleRate != rate) {
      sendPort.send('failed');
      return;
    }
    final sid = message['sid']! as int;
    for (final piece in splitForSynthesis(message['text']! as String)) {
      final audio = tts.generate(text: piece, sid: sid, speed: 1.0);
      final samples = audio.samples;
      if (samples.isEmpty) continue;
      final pcm = Int16List(samples.length);
      for (var i = 0; i < samples.length; i++) {
        final value = (samples[i] * 32767).round();
        pcm[i] = value > 32767 ? 32767 : (value < -32768 ? -32768 : value);
      }
      sendPort.send(TransferableTypedData.fromList([pcm.buffer.asUint8List()]));
    }
    sendPort.send('done');
  } catch (_) {
    sendPort.send('failed');
  } finally {
    tts?.free();
  }
}
