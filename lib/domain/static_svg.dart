import 'dart:convert';

import 'package:xml/xml.dart';
import 'package:xml/xml_events.dart';

/// A deliberately small static SVG contract. Rejection preserves Source/Save.
class StaticSvg {
  const StaticSvg._(this.source, this.width, this.height);
  final String source;
  final double width;
  final double height;

  static const _elements = {
    'svg',
    'g',
    'defs',
    'title',
    'desc',
    'path',
    'rect',
    'circle',
    'ellipse',
    'line',
    'polyline',
    'polygon',
    'text',
    'tspan',
    'linearGradient',
    'radialGradient',
    'stop',
  };
  static const _attributes = {
    'id',
    'viewBox',
    'width',
    'height',
    'x',
    'y',
    'x1',
    'y1',
    'x2',
    'y2',
    'cx',
    'cy',
    'r',
    'rx',
    'ry',
    'fx',
    'fy',
    'd',
    'points',
    'transform',
    'fill',
    'fill-rule',
    'fill-opacity',
    'stroke',
    'stroke-width',
    'stroke-linecap',
    'stroke-linejoin',
    'stroke-miterlimit',
    // Dashing can expand a short path into unbounded geometry for tiny
    // intervals. Keep dashed content in Source, including style declarations.
    'stroke-opacity',
    'opacity',
    'font-family',
    'font-size',
    'font-weight',
    'font-style',
    'text-anchor',
    'dx',
    'dy',
    'offset',
    'stop-color',
    'stop-opacity',
    'gradientUnits',
    'gradientTransform',
    'spreadMethod',
    'preserveAspectRatio',
    'version',
    'style',
  };

  static StaticSvg? parse(String source) {
    if (source.length > 256 * 1024 ||
        utf8.encode(source).length > 256 * 1024 ||
        RegExp(
          r'<!\s*(DOCTYPE|ENTITY)',
          caseSensitive: false,
        ).hasMatch(source)) {
      return null;
    }
    try {
      var depth = 0;
      var count = 0;
      for (final event in parseEvents(source)) {
        if (event is XmlStartElementEvent) {
          if (++count > 1000 || ++depth > 32) return null;
          if (event.isSelfClosing) depth--;
        } else if (event is XmlEndElementEvent) {
          depth--;
        }
      }
      final doc = XmlDocument.parse(source);
      if (doc.descendants.whereType<XmlProcessing>().any(
        (node) => node.target != 'xml',
      )) {
        return null;
      }
      final root = doc.rootElement;
      if (root.name.local != 'svg') return null;
      final gradients = <String>{};
      final ids = <String>{};
      final references = <String>[];
      for (final element in doc.descendants.whereType<XmlElement>()) {
        if (!_elements.contains(element.name.local) ||
            (element.namespaceUri != null &&
                element.namespaceUri != 'http://www.w3.org/2000/svg')) {
          return null;
        }
        final id = element.getAttribute('id');
        if (id != null && !ids.add(id)) return null;
        if (element.name.local.endsWith('Gradient')) {
          if (element.childElements.any(
            (child) => child.name.local != 'stop',
          )) {
            return null;
          }
          final id = element.getAttribute('id');
          if (id != null) gradients.add(id);
        }
        for (final attr in element.attributes) {
          if (attr.name.qualified == 'xmlns' || attr.name.prefix == 'xmlns') {
            continue;
          }
          if (attr.name.prefix != null ||
              !_attributes.contains(attr.name.local)) {
            return null;
          }
          final name = attr.name.local;
          final value = attr.value;
          if (value.length > 16384) return null;
          if (name == 'style') {
            for (final declaration in value.split(';')) {
              if (declaration.trim().isEmpty) continue;
              final colon = declaration.indexOf(':');
              if (colon < 1) return null;
              final property = declaration.substring(0, colon).trim();
              if (!_attributes.contains(property) ||
                  property == 'style' ||
                  !_safeValue(
                    property,
                    declaration.substring(colon + 1).trim(),
                    references,
                  )) {
                return null;
              }
            }
          } else if (!_safeValue(name, value, references)) {
            return null;
          }
        }
      }
      if (references.any((id) => !gradients.contains(id))) return null;
      final viewBox = root
          .getAttribute('viewBox')
          ?.trim()
          .split(RegExp(r'[\s,]+'));
      double? width;
      double? height;
      if (viewBox != null) {
        final numbers = viewBox.map(double.tryParse).toList();
        if (numbers.length != 4 ||
            numbers.any((n) => n == null || !n.isFinite || n.abs() > 1000000)) {
          return null;
        }
        width = numbers[2];
        height = numbers[3];
      } else {
        width = double.tryParse(
          (root.getAttribute('width') ?? '300').replaceFirst(
            RegExp(r'px$'),
            '',
          ),
        );
        height = double.tryParse(
          (root.getAttribute('height') ?? '150').replaceFirst(
            RegExp(r'px$'),
            '',
          ),
        );
      }
      if (width == null ||
          height == null ||
          !width.isFinite ||
          !height.isFinite ||
          width <= 0 ||
          height <= 0 ||
          width > 16384 ||
          height > 16384) {
        return null;
      }
      return StaticSvg._(source, width, height);
    } on Exception {
      return null;
    }
  }

  static bool _safeValue(String name, String value, List<String> references) {
    // No CSS escapes, imports or URL syntax except a local gradient paint.
    if (value.contains('\\') || value.contains('@') || value.contains('/*')) {
      return false;
    }
    if (const {
      'width',
      'height',
      'x',
      'y',
      'x1',
      'x2',
      'y1',
      'y2',
      'cx',
      'cy',
      'r',
      'rx',
      'ry',
      'fx',
      'fy',
      'd',
      'points',
      'transform',
      'gradientTransform',
      'stroke-width',
      'font-size',
      'dx',
      'dy',
    }.contains(name)) {
      for (final match in RegExp(
        r'[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?',
      ).allMatches(value)) {
        final number = double.tryParse(match.group(0)!);
        if (number == null || !number.isFinite || number.abs() > 1000000) {
          return false;
        }
      }
      if (RegExp(r'NaN|Infinity', caseSensitive: false).hasMatch(value)) {
        return false;
      }
    }
    if (RegExp(r'url\s*\(', caseSensitive: false).hasMatch(value)) {
      final match = RegExp(
        r'^url\(\s*#([A-Za-z_][\w.-]*)\s*\)$',
      ).firstMatch(value);
      if ((name != 'fill' && name != 'stroke') || match == null) return false;
      references.add(match.group(1)!);
    }
    return true;
  }
}
