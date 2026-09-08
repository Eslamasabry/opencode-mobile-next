import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/static_svg.dart';

void main() {
  test('local shapes, text and gradient paints retain their exact source', () {
    const source =
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 180"><defs><linearGradient id="paint"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="#123456"/></linearGradient></defs><rect width="300" height="180" fill="url(#paint)"/><text x="10" y="40">Local result</text></svg>';
    final svg = StaticSvg.parse(source)!;
    expect(svg.source, source);
    expect(svg.width, 300);
    expect(svg.height, 180);
  });
  for (final content in [
    '<script>alert(1)</script>',
    '<foreignObject/>',
    '<image href="https://example.org/private"/>',
    '<use href="#cycle"/>',
    '<animate attributeName="x"/>',
    '<rect onclick="run()"/>',
    '<rect fill="url(https://example.org/a)"/>',
    '<rect style="fill: u\\72l(https://example.org/a)"/>',
    '<style>@import "https://example.org/a";</style>',
    '<rect xmlns="http://example.org/other"/>',
    '<rect id="same"/><circle id="same"/>',
  ]) {
    test(
      'unsupported or externally active SVG keeps Source fallback: $content',
      () {
        expect(
          StaticSvg.parse('<svg viewBox="0 0 10 10">$content</svg>'),
          isNull,
        );
      },
    );
  }
  test('declarations, malformed XML and resource bounds are rejected', () {
    expect(
      StaticSvg.parse('<!DOCTYPE svg [<!ENTITY a "value">]><svg/>'),
      isNull,
    );
    expect(
      StaticSvg.parse('<?xml-stylesheet href="https://example.org/a"?><svg/>'),
      isNull,
    );
    expect(StaticSvg.parse('<svg><g>'), isNull);
    expect(StaticSvg.parse('<svg width="Infinity"/>'), isNull);
    expect(StaticSvg.parse('<svg viewBox="0 0 0 10"/>'), isNull);
    expect(StaticSvg.parse('<svg>${'<g>' * 33}${'</g>' * 33}</svg>'), isNull);
    expect(StaticSvg.parse('<svg>${'<rect/>' * 1001}</svg>'), isNull);
    expect(
      StaticSvg.parse('<svg><path d="${'M 0 0 ' * 3000}"/></svg>'),
      isNull,
    );
  });
  test('dash expansion is rejected before the SVG compiler is invoked', () {
    for (final attributes in [
      'stroke-dasharray="1e-300 1e-300"',
      'stroke-dashoffset="1"',
      'style="stroke-dasharray:1e-300 1e-300"',
      'style="stroke-dashoffset:1"',
    ]) {
      expect(
        StaticSvg.parse(
          '<svg viewBox="0 0 10 10"><path d="M0 0 L10 0" fill="none" stroke="#000" $attributes/></svg>',
        ),
        isNull,
      );
    }
  });
}
