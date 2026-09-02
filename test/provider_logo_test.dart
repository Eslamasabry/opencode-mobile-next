import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/provider_presentation.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';

/// A valid 1x1 opaque PNG so a "known provider" logo can decode without the
/// network.
final Uint8List _onePixelPng = Uint8List.fromList(const [
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // signature
  0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00,
  0x90, 0x77, 0x53, 0xDE,
  0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, // IDAT
  0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00, 0x00, 0x03, 0x01, 0x01, 0x00,
  0x18, 0xDD, 0x8D, 0xB0,
  0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82, //
]);

Widget _app(Widget child, {Brightness brightness = Brightness.light}) =>
    MaterialApp(
      theme: brightness == Brightness.dark ? AppTheme.dark() : AppTheme.light(),
      home: Scaffold(body: Center(child: child)),
    );

void main() {
  tearDown(() => ProviderLogo.imageProviderOverride = null);

  group('provider logo domains', () {
    test('known ids map to the provider website', () {
      expect(providerLogoDomain('anthropic'), 'anthropic.com');
      expect(providerLogoDomain('OpenAI'), 'openai.com');
      expect(providerLogoDomain('github-copilot'), 'github.com');
      expect(providerLogoDomain('google-vertex'), 'gemini.google.com');
      expect(providerLogoDomain('zhipuai-coding-plan'), 'zhipuai.cn');
      expect(providerLogoDomain('amazon-bedrock'), 'aws.amazon.com');
      expect(providerLogoDomain('opencode-zen'), 'opencode.ai');
    });

    test('unknown ids fall back to <id>.com', () {
      expect(providerLogoDomain('some-lab'), 'some-lab.com');
      expect(providerLogoDomain('Weird Lab!'), 'weirdlab.com');
    });

    test('logo URL addresses the favicon service directly', () {
      final url = providerLogoUrl('groq');
      expect(url.host, 't1.gstatic.com');
      expect(url.path, '/faviconV2');
      expect(url.queryParameters['url'], 'https://groq.com');
      expect(url.queryParameters['size'], '128');
    });

    test('monogram takes initials of the presented name', () {
      expect(providerMonogram('fireworks-ai'), 'FA');
      expect(providerMonogram('groq'), 'GR');
      expect(providerMonogram('zai'), 'ZA');
      expect(providerMonogram('x'), 'X');
    });
  });

  testWidgets('known provider renders the fetched image', (tester) async {
    final requested = <String>[];
    ProviderLogo.imageProviderOverride = (url) {
      requested.add(url);
      return MemoryImage(_onePixelPng);
    };

    await tester.pumpWidget(_app(const ProviderLogo('anthropic')));
    // Image decoding is real async work; let it finish outside FakeAsync.
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await tester.pumpAndSettle();

    expect(requested.single, contains('anthropic.com'));
    final image = tester.widget<Image>(
      find.descendant(
        of: find.byType(ProviderLogo),
        matching: find.byType(Image),
      ),
    );
    expect(image.width, 28 - 8);
    expect(image.fit, BoxFit.contain);
    // The monogram is only the loading placeholder; once decoded it is gone.
    expect(find.text('AN'), findsNothing);
  });

  testWidgets('a logo that cannot load falls back to the monogram', (
    tester,
  ) async {
    ProviderLogo.imageProviderOverride = (_) => MemoryImage(Uint8List(0));

    await tester.pumpWidget(_app(const ProviderLogo('fireworks-ai')));
    await tester.pumpAndSettle();

    expect(find.text('FA'), findsOneWidget);
  });

  testWidgets('skipping the image renders the monogram deterministically', (
    tester,
  ) async {
    ProviderLogo.imageProviderOverride = (_) => null;

    await tester.pumpWidget(_app(const ProviderLogo('groq', size: 18)));
    await tester.pump();

    expect(find.byType(Image), findsNothing);
    expect(find.text('GR'), findsOneWidget);
    final tile = tester.widget<BrandTile>(find.byType(BrandTile));
    expect(tile.size, 18);
  });

  testWidgets('OpenCode providers draw the prompt glyph, never an image', (
    tester,
  ) async {
    var requests = 0;
    ProviderLogo.imageProviderOverride = (_) {
      requests++;
      return MemoryImage(_onePixelPng);
    };

    await tester.pumpWidget(
      _app(const ProviderLogo('opencode'), brightness: Brightness.dark),
    );
    await tester.pumpAndSettle();

    expect(requests, 0);
    expect(find.byType(Image), findsNothing);
    final glyph = tester.widget<Text>(
      find.byKey(const ValueKey('provider-logo-prompt-glyph')),
    );
    expect(glyph.data, '❯');
    expect(glyph.style?.color, AppTheme.dark().colorScheme.primary);
  });

  testWidgets('logo is decorative and excluded from semantics', (tester) async {
    ProviderLogo.imageProviderOverride = (_) => null;
    final handle = tester.ensureSemantics();

    await tester.pumpWidget(_app(const ProviderLogo('groq')));
    await tester.pump();

    expect(find.bySemanticsLabel('GR'), findsNothing);
    handle.dispose();
  });
}
