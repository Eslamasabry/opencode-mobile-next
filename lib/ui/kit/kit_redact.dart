import 'dart:convert';

/// Display-safe masking of credentials in text the app shows, copies or
/// shares (AGENTS.md security invariants: provider keys must never reach
/// logs, diagnostics, notification copy or the clipboard).
///
/// The app must register provider keys and server passwords as it loads them
/// using [registerKnownSecret]. Exact values are masked before the patterns.
/// Registration is process-local; tests must call [clearKnownSecrets].
///
/// A secret becomes "•••". Where a key's prefix is public (it only names the
/// provider), the prefix stays as a hint: `sk-ant-•••`, `ghp_•••`, `AIza•••`.
///
/// Masked:
/// - provider keys: `sk-`, `sk-ant-`, `sk-proj-`, `AIza`, `ghp_`/`gho_`/
///   `ghs_`/`ghu_`/`ghr_`, `github_pat_`, `xoxa-`/`xoxb-`/`xoxp-`/`xoxr-`;
/// - `Bearer <token>`;
/// - the whole value of an `Authorization`, `Proxy-Authorization`, `Cookie`,
///   `Set-Cookie` or `x-api-key` header,
///   at line start (allowing indentation) or in an explicitly quoted header,
///   written `Authorization: x` or `Authorization=x`: to the end of the line,
///   through the closing quote of a quoted value, or to the end of the quoted
///   string the header itself sits in (`-H "Authorization: …"`);
/// - the value after `api_key`, `apikey`, `token`, `secret`, `password` or
///   `passwd` (also as the tail of a longer name such as `client_secret`)
///   and `=` or `:`; also UPPER_SNAKE names ending in `_KEY`, `_SECRET`,
///   `_TOKEN` or `_PASSWORD`, and `access_key`, `secret_key`, `private_key`
///   in any case. A quoted value through its closing quote (escaped
///   quotes and embedded delimiters included), an unquoted one up to
///   whitespace or `"',;&#<>`. Inside a file path (`/tmp/token=cache/x`) the
///   name is not a credential; after `?`, `&` or `#` in a URL it is;
/// - URL user-info, including connection strings:
///   `postgres://user:pass@host` → `postgres://•••@host`;
/// - PEM private key blocks, including their BEGIN/END markers;
/// - JWTs: three base64url segments whose first two decode to JSON objects
///   (or both start `eyJ`). Signatures are not verified.
///
/// Never touched: file paths, git SHAs, UUIDs, ordinary long words.
abstract final class KitRedact {
  /// What a secret is replaced with.
  static const String mask = '•••';

  static final List<String> _knownSecrets = [];

  /// Registers a loaded provider key or server password for exact masking.
  /// Values shorter than six characters are ignored; values are not trimmed.
  /// Longer values take precedence over registered substrings.
  static void registerKnownSecret(String value) {
    if (value.length < 6 || _knownSecrets.contains(value)) return;
    _knownSecrets.add(value);
    _knownSecrets.sort((a, b) => b.length.compareTo(a.length));
  }

  /// Registers secret fields in a trusted credential/configuration response.
  /// Call at ingress, before model decoding or diagnostic capture. This never
  /// retains the response, and must not be used on arbitrary user content.
  static void registerCredentialValues(Object? value) {
    if (value is List) {
      for (final item in value) {
        registerCredentialValues(item);
      }
    } else if (value is Map) {
      for (final entry in value.entries) {
        final name = entry.key.toString().toLowerCase().replaceAll(
          RegExp(r'[-_]'),
          '',
        );
        final field = entry.value;
        if (field is String &&
            const {
              'key',
              'apikey',
              'password',
              'passwd',
              'secret',
              'clientsecret',
              'token',
              'accesstoken',
              'refreshtoken',
              'access',
              'refresh',
              'authorization',
              'proxyauthorization',
              'xapikey',
              'cookie',
              'setcookie',
            }.contains(name)) {
          registerKnownSecret(field);
          if (name == 'authorization' || name == 'proxyauthorization') {
            final separator = field.indexOf(' ');
            if (separator >= 0) {
              registerKnownSecret(field.substring(separator + 1));
            }
          }
        }
        if (field is Map || field is List) registerCredentialValues(field);
      }
    }
  }

  /// Drops process-local registrations. Tests must clear these in setup and
  /// teardown; the app must re-register still-loaded secrets after clearing.
  static void clearKnownSecrets() => _knownSecrets.clear();

  /// Group 1: a quote right before the name (the header sits in a quoted
  /// string, or is a quoted key); group 2: a quote closing the key.
  static final RegExp _authHeader = RegExp(
    r'''(["']?)\b(?:(?:Proxy-)?Authorization|(?:Set-)?Cookie|x-api-key)(["']?)\s*[:=]\s*''',
    caseSensitive: false,
  );

  static final RegExp _bearer = RegExp(
    r'\b(Bearer)\s+[A-Za-z0-9\-._~+/]+=*',
    caseSensitive: false,
  );

  static final RegExp _namedValue = RegExp(
    r'''(?<![A-Za-z0-9_\-])([A-Za-z0-9_\-]*?(?:api[_\-]?key|apikey|_key|token|secret|password|passwd))["']?\s*[:=]\s*''',
    caseSensitive: false,
  );

  static final RegExp _envName = RegExp(
    r'^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*_(?:KEY|SECRET|TOKEN|PASSWORD)$',
  );
  static final RegExp _credentialName = RegExp(
    r'^(?:api[_\-]?key|apikey|access_key|secret_key|private_key|x-api-key|[A-Za-z0-9_\-]*(?:token|secret|password|passwd))$',
    caseSensitive: false,
  );

  static final RegExp _urlUserInfo = RegExp(
    r'\b([A-Za-z][A-Za-z0-9+.\-]*://)[^\s/?#@]+@',
  );

  static final RegExp _urlScheme = RegExp(
    r'(?:\b[A-Za-z][A-Za-z0-9+.\-]*:|^|=)//',
  );

  static final RegExp _privateKey = RegExp(
    r'-----BEGIN ((?:[A-Z0-9]+ )*PRIVATE KEY)-----[\s\S]*?-----END \1-----',
  );

  // Only declarations distinguish invocations from credentials with parentheses.
  static final RegExp _declaration = RegExp(r'\b(?:const|let|var|final)\s+$');
  static final RegExp _invocation = RegExp(
    r'(?:await\s+)?[A-Za-z_$][A-Za-z0-9_$.]*\s*\(',
  );

  static final RegExp _jwtCandidate = RegExp(
    r'(?<![A-Za-z0-9_\-])([A-Za-z0-9_\-]{2,})\.([A-Za-z0-9_\-]{2,})\.[A-Za-z0-9_\-]*(?![A-Za-z0-9_\-])',
  );

  /// Provider keys, most specific prefix first. Group 1 is the public
  /// prefix that stays as a hint.
  static final List<RegExp> _providerKeys = [
    RegExp(r'(?<![A-Za-z0-9_\-])(sk-ant-)[A-Za-z0-9_\-]{8,}'),
    RegExp(r'(?<![A-Za-z0-9_\-])(sk-proj-)[A-Za-z0-9_\-]{8,}'),
    RegExp(r'(?<![A-Za-z0-9_\-])(sk-)[A-Za-z0-9_\-]{16,}'),
    RegExp(r'(?<![A-Za-z0-9_\-])(AIza)[A-Za-z0-9_\-]{30,}'),
    RegExp(r'(?<![A-Za-z0-9_\-])(github_pat_)[A-Za-z0-9_]{20,}'),
    RegExp(r'(?<![A-Za-z0-9_\-])(gh[pousr]_)[A-Za-z0-9]{20,}'),
    RegExp(r'(?<![A-Za-z0-9_\-])(xox[abpr]-)[A-Za-z0-9\-]{10,}'),
  ];

  /// Long opaque tokens: 32 or more letters, digits and `_+./=-` in one run
  /// that include a digit, replaced by [mask]. Long letters-only words are
  /// code names a diagnostic needs, so they stay. For text that leaves the
  /// phone (reports, logs); apply after [text].
  static String opaqueTokens(String s) => s.replaceAll(
    RegExp(r'\b(?=[A-Za-z0-9_+./=-]*[0-9])[A-Za-z0-9_+./=-]{32,}\b'),
    mask,
  );

  /// [s] with registered exact values masked longest first, followed by the
  /// credential patterns. Exact matching is case-sensitive and literal.
  static String text(String s) {
    if (s.isEmpty) return s;
    var out = s;
    for (final secret in _knownSecrets) {
      out = out.replaceAll(secret, mask);
    }
    out = out.replaceAll(_privateKey, mask);
    out = _scan(out, _authHeader, _authValue);
    out = out.replaceAllMapped(_bearer, (m) => '${m[1]} $mask');
    out = _scan(out, _namedValue, _namedValueEnd);
    out = out.replaceAllMapped(_urlUserInfo, (m) => '${m[1]}$mask@');
    for (final key in _providerKeys) {
      out = out.replaceAllMapped(key, (m) => '${m[1]}$mask');
    }
    return _maskJwts(out);
  }

  /// Whether [text] would change [s]: it holds something that looks like a
  /// secret.
  static bool containsSecret(String s) => text(s) != s;

  /// Replaces, for each match of [re] in [s], the match plus the value
  /// [value] finds after it. [value] returns null to leave a match alone.
  static String _scan(
    String s,
    RegExp re,
    _Masked? Function(String s, RegExpMatch m) value,
  ) {
    final out = StringBuffer();
    var cursor = 0;
    var from = 0;
    while (from < s.length) {
      final it = re.allMatches(s, from).iterator;
      if (!it.moveNext()) break;
      final m = it.current;
      final hit = value(s, m);
      if (hit == null) {
        from = m.end > m.start ? m.end : m.start + 1;
        continue;
      }
      out
        ..write(s.substring(cursor, m.start))
        ..write(m[0])
        ..write(hit.text);
      cursor = hit.end;
      from = hit.end;
    }
    out.write(s.substring(cursor));
    return out.toString();
  }

  static _Masked? _authValue(String s, RegExpMatch m) {
    if (_inPath(s, m.start)) return null;
    final lead = m[1]!;
    final keyQuote = m[2]!;
    // Unquoted headers occupy their own line. Quoted keys and header strings
    // (JSON diagnostics and curl -H) have explicit value boundaries instead.
    if (lead.isEmpty) {
      var lineStart = m.start;
      while (lineStart > 0 && !'\r\n'.contains(s[lineStart - 1])) {
        lineStart--;
      }
      if (s.substring(lineStart, m.start).trim().isNotEmpty) return null;
    }
    final start = m.end;
    if (lead.isNotEmpty && keyQuote.isEmpty) {
      // `"Authorization: …"`: the header is the content of a quoted string,
      // so its value runs to that string's closing quote.
      final close = _closingQuote(s, start, lead);
      return _masked(start, close < 0 ? _lineEnd(s, start) : close);
    }
    if (start < s.length && _isQuote(s[start])) return _quoted(s, start);
    if (lead.isNotEmpty) {
      // A quoted key with an unquoted value: `"Authorization": null`.
      var end = start;
      while (end < s.length && !',}]\r\n'.contains(s[end])) {
        end++;
      }
      return _masked(start, end);
    }
    return _masked(start, _lineEnd(s, start));
  }

  static _Masked? _namedValueEnd(String s, RegExpMatch m) {
    if (_inPath(s, m.start)) return null;
    final name = m[1]!;
    if (!_credentialName.hasMatch(name) && !_envName.hasMatch(name)) {
      return null;
    }
    final start = m.end;
    if (start < s.length && s[start] == r'\') {
      final quoted = _serializedQuoted(s, start);
      if (quoted != null) return quoted;
    }
    if (start < s.length && _isQuote(s[start])) return _quoted(s, start);
    if (_invocation.matchAsPrefix(s, start) != null &&
        _declaration.hasMatch(s.substring(0, m.start))) {
      return null;
    }
    var end = start;
    while (end < s.length && !' \t\r\n"\',;&#<>'.contains(s[end])) {
      if (s.startsWith('*/', end)) break;
      end++;
    }
    return _masked(start, end);
  }

  /// Quotes encoded inside a serialized string include a backslash run in the
  /// delimiter. A longer run escapes a quote inside that value.
  static _Masked? _serializedQuoted(String s, int open) {
    var quoteAt = open;
    while (quoteAt < s.length && s[quoteAt] == r'\') {
      quoteAt++;
    }
    if (quoteAt == s.length || !_isQuote(s[quoteAt])) return null;
    final width = quoteAt - open;
    final delimiter = s.substring(open, quoteAt + 1);
    var i = quoteAt + 1;
    while (i < s.length && s[i] != '\r' && s[i] != '\n') {
      if (s[i] != r'\') {
        i++;
        continue;
      }
      final run = i;
      while (i < s.length && s[i] == r'\') {
        i++;
      }
      if (i < s.length && s[i] == s[quoteAt] && i - run == width) {
        return run > quoteAt + 1
            ? (text: '$delimiter$mask$delimiter', end: i + 1)
            : (text: '$delimiter$delimiter', end: i + 1);
      }
      if (i < s.length) i++;
    }
    return null;
  }

  /// The value `[start, end)` as a mask; null when it is empty.
  static _Masked? _masked(int start, int end) =>
      end > start ? (text: mask, end: end) : null;

  /// A value opening with the quote at [open], masked through its closing
  /// quote, or to the end of the line when the quote never closes there.
  static _Masked? _quoted(String s, int open) {
    final q = s[open];
    final close = _closingQuote(s, open + 1, q);
    if (close < 0) {
      final end = _lineEnd(s, open + 1);
      return end > open + 1 ? (text: '$q$mask', end: end) : null;
    }
    return close > open + 1 ? (text: '$q$mask$q', end: close + 1) : null;
  }

  /// The index of the unescaped [quote] at or after [i], however many lines
  /// the value spans; -1 when the text ends first. A quoted secret must not
  /// leak a partial redaction just because it was written across lines.
  static int _closingQuote(String s, int i, String quote) {
    while (i < s.length) {
      final c = s[i];
      if (c == '\\') {
        i += 2;
        continue;
      }
      if (c == quote) return i;
      i++;
    }
    return -1;
  }

  static int _lineEnd(String s, int i) {
    while (i < s.length && s[i] != '\n' && s[i] != '\r') {
      i++;
    }
    return i;
  }

  static bool _isQuote(String c) => c == '"' || c == "'";

  /// Whether the name starting at [i] sits inside a file path segment
  /// (`/tmp/token=x`). A name right after a URL's `?`, `&` or `#` is a query
  /// or fragment parameter, not a path.
  static bool _inPath(String s, int i) {
    var start = i;
    while (start > 0 && !' \t\r\n"\'`,;(){}[]<>|'.contains(s[start - 1])) {
      start--;
    }
    final prefix = s.substring(start, i);
    // Adjacent comment delimiters are not directory separators.
    if (prefix == '//' || prefix == '/*') return false;
    final scheme = _urlScheme.firstMatch(prefix);
    if (scheme != null &&
        prefix.substring(scheme.end).contains(RegExp(r'[?&#]'))) {
      return false;
    }
    return prefix.contains('/') || prefix.contains('\\');
  }

  static String _maskJwts(String s) {
    final out = StringBuffer();
    var cursor = 0;
    var from = 0;
    while (from < s.length) {
      final candidates = _jwtCandidate.allMatches(s, from).iterator;
      if (!candidates.moveNext()) break;
      final m = candidates.current;
      if (!_isJwt(m[1]!, m[2]!)) {
        // Retry inside a rejected candidate: its claims may be a JWT header.
        from = m.start + 1;
        continue;
      }
      out
        ..write(s.substring(cursor, m.start))
        ..write(mask);
      cursor = m.end;
      from = m.end;
    }
    out.write(s.substring(cursor));
    return out.toString();
  }

  /// A compact JWT: header and claims decode to JSON objects, or both keep
  /// the `eyJ` (`{"`) spelling even when truncated.
  static bool _isJwt(String header, String claims) =>
      (header.startsWith('eyJ') && claims.startsWith('eyJ')) ||
      (_isJsonObject(header) && _isJsonObject(claims));

  static bool _isJsonObject(String segment) {
    try {
      final bytes = base64Url.decode(base64Url.normalize(segment));
      return jsonDecode(utf8.decode(bytes)) is Map;
    } on FormatException {
      return false;
    }
  }
}

typedef _Masked = ({String text, int end});
