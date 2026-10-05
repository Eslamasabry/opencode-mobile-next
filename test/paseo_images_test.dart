import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ProductException;
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

void main() {
  test('pictures go to the agent as base64 with their type', () {
    final images = paseoImages(const [
      PromptAttachment(
        mime: 'image/png',
        filename: 'shot.png',
        url: 'data:image/png;base64,iVBORw0KGgo=',
      ),
    ]);
    expect(images, [
      {'data': 'iVBORw0KGgo=', 'mimeType': 'image/png'},
    ]);
  });

  test('other files are refused in words', () {
    expect(
      () => paseoImages(const [
        PromptAttachment(
          mime: 'application/pdf',
          filename: 'plan.pdf',
          url: 'data:application/pdf;base64,JVBERi0=',
        ),
      ]),
      throwsA(
        isA<ProductException>().having(
          (e) => e.message,
          'message',
          contains('takes pictures only'),
        ),
      ),
    );
  });

  test('agents on this phone take pictures, not other files', () {
    expect(paseoServerCapabilities.promptAttachments, isTrue);
    expect(paseoServerCapabilities.promptImagesOnly, isTrue);
  });
}
