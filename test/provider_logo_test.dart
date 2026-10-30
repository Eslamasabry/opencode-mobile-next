import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:opencode_mobile/domain/provider_presentation.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';

const String _svg =
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
    '<path fill="currentColor" d="M2 2h20v20H2z"/></svg>';

Widget _app(Widget child, {Brightness brightness = Brightness.light}) =>
    MaterialApp(
      theme: brightness == Brightness.dark ? AppTheme.dark() : AppTheme.light(),
      home: Scaffold(body: Center(child: child)),
    );

void main() {
  tearDown(() => ProviderLogo.logoLoaderOverride = null);

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

    test('monogram takes initials of the presented name', () {
      expect(providerMonogram('fireworks-ai'), 'FA');
      expect(providerMonogram('groq'), 'GR');
      expect(providerMonogram('zai'), 'ZA');
      expect(providerMonogram('x'), 'X');
    });
  });

  testWidgets('known provider renders the tinted vector logo', (tester) async {
    final requested = <String>[];
    ProviderLogo.logoLoaderOverride = (id) {
      requested.add(id);
      return Future.value(_svg);
    };

    await tester.pumpWidget(_app(const ProviderLogo('anthropic')));
    // Initials while loading.
    expect(find.text('A'), findsOneWidget);
    await tester.pump();
    await tester.pump();

    expect(requested.single, 'anthropic');
    final picture = tester.widget<SvgPicture>(find.byType(SvgPicture));
    final context = tester.element(find.byType(SvgPicture));
    expect(
      picture.colorFilter,
      ColorFilter.mode(KitTokens.of(context).roles.text1, BlendMode.srcIn),
    );
    // The default is the kit's tile size, so it lines up with row tiles.
    expect(tester.getSize(find.byType(ProviderLogo)), const Size(28, 28));
  });

  testWidgets('a logo that cannot load keeps the monogram, no badge', (
    tester,
  ) async {
    ProviderLogo.logoLoaderOverride = (_) => Future.value(null);

    await tester.pumpWidget(_app(const ProviderLogo('fireworks-ai')));
    await tester.pumpAndSettle();

    expect(find.text('FA'), findsOneWidget);
    expect(find.byType(SvgPicture), findsNothing);
  });

  testWidgets('skipping the image renders the monogram deterministically', (
    tester,
  ) async {
    ProviderLogo.logoLoaderOverride = (_) => null;

    await tester.pumpWidget(_app(const ProviderLogo('groq', size: 18)));
    await tester.pump();

    expect(find.byType(SvgPicture), findsNothing);
    // KitAvatar's initials: the first grapheme of each of the presented
    // name's first two words ("Groq" -> "G").
    expect(find.text('G'), findsOneWidget);
    expect(tester.getSize(find.byType(ProviderLogo)), const Size(18, 18));
  });

  testWidgets('OpenCode providers draw the prompt glyph, never an image', (
    tester,
  ) async {
    var requests = 0;
    ProviderLogo.logoLoaderOverride = (_) {
      requests++;
      return Future.value(_svg);
    };

    await tester.pumpWidget(
      _app(const ProviderLogo('opencode'), brightness: Brightness.dark),
    );
    await tester.pumpAndSettle();

    expect(requests, 0);
    expect(find.byType(SvgPicture), findsNothing);
    final glyph = find.byKey(const ValueKey('provider-logo-prompt-glyph'));
    expect(glyph, findsOneWidget);
    expect(
      find.descendant(
        of: glyph,
        matching: find.byIcon(AppIconography.terminal),
      ),
      findsOneWidget,
    );
  });

  testWidgets('logo is decorative and excluded from semantics', (tester) async {
    ProviderLogo.logoLoaderOverride = (_) => null;
    final handle = tester.ensureSemantics();

    await tester.pumpWidget(_app(const ProviderLogo('groq')));
    await tester.pump();

    expect(find.bySemanticsLabel('G'), findsNothing);
    expect(find.bySemanticsLabel('Groq'), findsNothing);
    handle.dispose();
  });
}
