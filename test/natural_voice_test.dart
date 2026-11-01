import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/voice/natural_synthesis.dart';
import 'package:opencode_mobile/voice/natural_voice.dart';

void main() {
  test('natural voice packs are pinned like the speech models', () {
    for (final pack in naturalVoicePacks) {
      expect(pack.files, hasLength(4));
      expect(pack.languages, isNotEmpty);
      for (final file in pack.files) {
        final url = pack.urlFor(file);
        expect(url.scheme, 'https');
        expect(url.host, 'huggingface.co');
        expect(url.path, contains('/resolve/${pack.revision}/'));
        expect(file.sha256, hasLength(64));
        expect(file.length, greaterThan(0));
      }
    }
  });

  test('a reply\'s language follows its script, then the app', () {
    expect(detectReadLanguage('Hello there, how are you?', 'en'), 'en');
    expect(detectReadLanguage('مرحبا بك في التطبيق', 'en'), 'ar');
    expect(detectReadLanguage('Hello there', 'ar'), 'en');
    expect(detectReadLanguage('Hola, ¿cómo estás?', 'es'), 'es');
    expect(detectReadLanguage('你好,世界', 'en'), 'other');
    expect(detectReadLanguage('12345', 'ar'), 'ar');
  });

  test('text is cut into sentences a model reads in one go', () {
    expect(splitForSynthesis('One. Two! Three?'), ['One.', 'Two!', 'Three?']);
    final long = List.filled(200, 'word').join(' ');
    final pieces = splitForSynthesis(long, limit: 100);
    expect(pieces.every((piece) => piece.length <= 100), isTrue);
    expect(pieces.join(' '), long);
  });
}
