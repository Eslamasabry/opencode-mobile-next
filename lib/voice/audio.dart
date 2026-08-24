import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:record/record.dart';

import 'device.dart';

const voiceSampleRate = 16000;
const voiceMaximumDuration = Duration(seconds: 30);
const voiceMaximumSamples = voiceSampleRate * 30;

class VoicePermissionDenied implements Exception {
  const VoicePermissionDenied({this.permanent = false});

  final bool permanent;

  @override
  String toString() => permanent
      ? 'Microphone access is blocked. Allow it in Android app settings.'
      : 'Microphone permission is required for local voice input.';
}

enum VoiceRecorderEvent { interrupted }

abstract interface class VoiceRecorder {
  Stream<VoiceRecorderEvent> get events;
  Future<Stream<Uint8List>> start();
  Future<void> stop();
  Future<void> cancel();
  Future<void> dispose();
}

class RecordVoiceRecorder implements VoiceRecorder {
  RecordVoiceRecorder({VoiceDevicePlatform platform = voiceDevicePlatform})
    : _platform = platform,
      _recorder = AudioRecorder() {
    _stateSubscription = _recorder.onStateChanged().listen((state) {
      if (state == RecordState.pause) {
        _events.add(VoiceRecorderEvent.interrupted);
      }
    }, onError: _events.addError);
  }

  final VoiceDevicePlatform _platform;
  final AudioRecorder _recorder;
  final StreamController<VoiceRecorderEvent> _events =
      StreamController.broadcast();
  late final StreamSubscription<RecordState> _stateSubscription;

  @override
  Stream<VoiceRecorderEvent> get events => _events.stream;

  @override
  Future<Stream<Uint8List>> start() async {
    final permission = await _platform.requestMicrophonePermission();
    if (permission != VoiceMicrophonePermission.granted) {
      throw VoicePermissionDenied(
        permanent: permission == VoiceMicrophonePermission.permanentlyDenied,
      );
    }
    return _recorder.startStream(
      const RecordConfig(
        encoder: AudioEncoder.pcm16bits,
        sampleRate: voiceSampleRate,
        numChannels: 1,
        audioInterruption: AudioInterruptionMode.pause,
        streamBufferSize: 4096,
        androidConfig: AndroidRecordConfig(
          manageBluetooth: false,
          audioSource: AndroidAudioSource.mic,
        ),
      ),
    );
  }

  @override
  Future<void> stop() async => _recorder.stop();

  @override
  Future<void> cancel() => _recorder.cancel();

  @override
  Future<void> dispose() async {
    await _stateSubscription.cancel();
    await _recorder.dispose();
    await _events.close();
  }
}

class Pcm16Accumulator {
  Pcm16Accumulator({this.maximumSamples = voiceMaximumSamples})
    : _samples = Float32List(maximumSamples);

  final int maximumSamples;
  final Float32List _samples;
  int _length = 0;
  int? _pendingLowByte;
  double level = 0;

  int get length => _length;
  bool get isFull => _length >= maximumSamples;
  Duration get duration =>
      Duration(microseconds: (_length * 1000000) ~/ voiceSampleRate);

  void add(Uint8List bytes) {
    if (bytes.isEmpty || isFull) return;
    var index = 0;
    var sumSquares = 0.0;
    var levelSamples = 0;
    if (_pendingLowByte != null && index < bytes.length) {
      final value = _decode(_pendingLowByte!, bytes[index++]);
      _pendingLowByte = null;
      _samples[_length++] = value;
      sumSquares += value * value;
      levelSamples++;
    }
    while (index + 1 < bytes.length && !isFull) {
      final value = _decode(bytes[index], bytes[index + 1]);
      index += 2;
      _samples[_length++] = value;
      sumSquares += value * value;
      levelSamples++;
    }
    if (!isFull && index < bytes.length) _pendingLowByte = bytes[index];
    if (levelSamples > 0) {
      level = math.sqrt(sumSquares / levelSamples).clamp(0, 1);
    }
  }

  double _decode(int low, int high) {
    var signed = low | (high << 8);
    if (signed >= 0x8000) signed -= 0x10000;
    return signed / 32768.0;
  }

  Float32List takeSamples() {
    final result = Float32List(_length)..setRange(0, _length, _samples);
    clear();
    return result;
  }

  void clear() {
    _length = 0;
    _pendingLowByte = null;
    level = 0;
  }
}
