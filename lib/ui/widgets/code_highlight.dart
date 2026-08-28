import 'package:flutter/material.dart';
import 'package:highlight/highlight.dart' show Node, highlight;

/// Maps fenced-code language hints to the grammar names registered by the
/// highlight package. Unknown hints render as plain text; nothing is guessed.
const _languageAliases = <String, String>{
  'dart': 'dart',
  'js': 'javascript',
  'javascript': 'javascript',
  'jsx': 'javascript',
  'ts': 'typescript',
  'typescript': 'typescript',
  'tsx': 'typescript',
  'py': 'python',
  'python': 'python',
  'rb': 'ruby',
  'ruby': 'ruby',
  'sh': 'bash',
  'bash': 'bash',
  'shell': 'bash',
  'zsh': 'bash',
  'console': 'bash',
  'json': 'json',
  'yaml': 'yaml',
  'yml': 'yaml',
  'toml': 'ini',
  'ini': 'ini',
  'html': 'xml',
  'xml': 'xml',
  'svg': 'xml',
  'css': 'css',
  'scss': 'scss',
  'sql': 'sql',
  'go': 'go',
  'rust': 'rust',
  'rs': 'rust',
  'kotlin': 'kotlin',
  'kt': 'kotlin',
  'java': 'java',
  'swift': 'swift',
  'c': 'cpp',
  'h': 'cpp',
  'cc': 'cpp',
  'cpp': 'cpp',
  'c++': 'cpp',
  'cs': 'cs',
  'csharp': 'cs',
  'php': 'php',
  'diff': 'diff',
  'patch': 'diff',
  'gradle': 'gradle',
  'dockerfile': 'dockerfile',
  'makefile': 'makefile',
  'proto': 'protobuf',
  'md': 'markdown',
  'markdown': 'markdown',
};

/// Blocks longer than this render unhighlighted; parsing pathological
/// payloads on the UI thread is worse than plain text.
const _highlightSizeLimit = 20000;

/// Token colors are derived from the active [ColorScheme] so code reads as
/// part of the app's own palette in both themes rather than an imported IDE
/// theme.
class CodeHighlightTheme {
  final TextStyle keyword;
  final TextStyle string;
  final TextStyle comment;
  final TextStyle number;
  final TextStyle title;
  final TextStyle meta;
  final TextStyle addition;
  final TextStyle deletion;

  CodeHighlightTheme.of(BuildContext context)
    : this._(Theme.of(context).colorScheme, Theme.of(context).hintColor);

  CodeHighlightTheme._(ColorScheme scheme, Color hint)
    : keyword = TextStyle(
        color: scheme.primary,
        fontWeight: FontWeight.w600,
      ),
      string = TextStyle(color: scheme.tertiary),
      comment = TextStyle(color: hint, fontStyle: FontStyle.italic),
      number = TextStyle(color: scheme.secondary),
      title = TextStyle(color: scheme.onSurface, fontWeight: FontWeight.w600),
      meta = TextStyle(color: scheme.secondary),
      addition = TextStyle(color: scheme.primary),
      deletion = TextStyle(color: scheme.error);

  TextStyle? _forClass(String? className) => switch (className) {
    'keyword' || 'built_in' || 'literal' || 'type' || 'tag' => keyword,
    'string' || 'regexp' || 'symbol' || 'template-variable' => string,
    'comment' || 'quote' => comment,
    'number' || 'attr' || 'attribute' || 'variable' => number,
    'title' || 'class' || 'function' || 'section' || 'name' => title,
    'meta' || 'meta-string' || 'selector-tag' || 'selector-class' => meta,
    'addition' => addition,
    'deletion' => deletion,
    _ => null,
  };
}

/// Converts [source] into styled spans for the given fenced-language hint.
/// Falls back to one plain span when the language is unknown, the block is
/// oversized, or the grammar fails.
TextSpan highlightedCode(
  String source,
  String? language,
  CodeHighlightTheme theme,
) {
  final grammar = _languageAliases[language?.trim().toLowerCase()];
  if (grammar == null || source.length > _highlightSizeLimit) {
    return TextSpan(text: source);
  }
  try {
    final nodes = highlight.parse(source, language: grammar).nodes;
    if (nodes == null) return TextSpan(text: source);
    return TextSpan(children: _spansFor(nodes, theme));
  } catch (_) {
    return TextSpan(text: source);
  }
}

List<InlineSpan> _spansFor(List<Node> nodes, CodeHighlightTheme theme) {
  final spans = <InlineSpan>[];
  for (final node in nodes) {
    final style = theme._forClass(node.className);
    final children = node.children;
    if (children == null) {
      if (node.value?.isNotEmpty == true) {
        spans.add(TextSpan(text: node.value, style: style));
      }
      continue;
    }
    spans.add(TextSpan(children: _spansFor(children, theme), style: style));
  }
  return spans;
}
