import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

/// What [openExternalLink] did, so callers can react without re-deriving it.
enum ExternalLinkOutcome {
  /// The URL failed the policy below and was never handed to the platform.
  blocked,

  /// The URL was allowed but the user declined the confirmation.
  cancelled,

  /// Handed to the platform and accepted.
  opened,

  /// Handed to the platform, which had no app for it.
  noHandler,

  /// The platform threw while opening it.
  failed,
}

/// Hard ceiling on a link this app will even consider. A server can put an
/// arbitrarily long string in a markdown link or a form field, and neither the
/// confirmation dialog nor the platform intent should have to carry it.
const _maxExternalLinkLength = 2048;

/// The single gate every URL the app did not author must pass before it can
/// reach the platform launcher — markdown links in agent output, OpenCode 2
/// external form fields, update notices, and anything added later.
///
/// The policy is deliberately narrow, because the source is a server that may
/// be malicious or compromised:
///
/// - only `https:` opens directly;
/// - `http:` opens only after a separate, explicitly insecure confirmation;
/// - every other scheme is refused — `intent:`, `file:`, `content:`,
///   `javascript:`, `data:`, and any app-private scheme, none of which the
///   user can evaluate from a link label;
/// - embedded credentials (`https://user:pass@host`) are refused, because they
///   move secrets into browser history and make the host unreadable;
/// - a host is required, so opaque URLs cannot slip through;
/// - the effective destination host is shown before anything opens.
///
/// [launcher] exists for tests; production goes to `url_launcher`.
Future<ExternalLinkOutcome> openExternalLink(
  BuildContext context,
  String? value, {
  Future<bool> Function(Uri uri)? launcher,
}) async {
  final uri = safeExternalLinkUri(value);
  if (uri == null) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Link blocked. This app may open only https:// URLs, or confirmed '
            'http:// URLs.',
          ),
        ),
      );
    }
    return ExternalLinkOutcome.blocked;
  }
  final insecure = uri.scheme == 'http';

  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      icon: Icon(
        insecure ? Icons.warning_amber_rounded : Icons.open_in_new_rounded,
      ),
      title: Text(
        insecure ? 'Open insecure HTTP link?' : 'Open external link?',
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Host'),
          const SizedBox(height: 4),
          SelectableText(
            externalLinkHost(uri),
            style: const TextStyle(fontFamily: 'AppMono'),
          ),
          if (insecure) ...[
            const SizedBox(height: 12),
            const Text(
              'HTTP is not encrypted. Other devices on the network may read or '
              'change what you send and receive.',
            ),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, true),
          child: Text(insecure ? 'Open HTTP link' : 'Open link'),
        ),
      ],
    ),
  );
  if (confirmed != true) return ExternalLinkOutcome.cancelled;
  if (!context.mounted) return ExternalLinkOutcome.cancelled;

  try {
    final opened =
        await (launcher?.call(uri) ??
            launchUrl(uri, mode: LaunchMode.externalApplication));
    if (opened) return ExternalLinkOutcome.opened;
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No app could open this link.')),
      );
    }
    return ExternalLinkOutcome.noHandler;
  } catch (error) {
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Could not open link: $error')));
    }
    return ExternalLinkOutcome.failed;
  }
}

/// The parsed URL when [value] passes the policy documented on
/// [openExternalLink], otherwise null. Surfaces can call this to describe the
/// destination — or to withhold the affordance entirely — without duplicating
/// the rules.
Uri? safeExternalLinkUri(String? value) {
  if (value == null) return null;
  final trimmed = value.trim();
  if (trimmed.isEmpty || trimmed.length > _maxExternalLinkLength) return null;
  final uri = Uri.tryParse(trimmed);
  if (uri == null) return null;
  final scheme = uri.scheme.toLowerCase();
  if (scheme != 'https' && scheme != 'http') return null;
  if (uri.host.isEmpty || uri.userInfo.isNotEmpty) return null;
  // Normalize the scheme so `HTTPS://` cannot present itself as something the
  // policy never inspected.
  return uri.scheme == scheme ? uri : uri.replace(scheme: scheme);
}

/// `example.com`, or `example.com:8443` when the URL names a port — what the
/// user is asked to approve.
String externalLinkHost(Uri uri) =>
    uri.hasPort ? '${uri.host}:${uri.port}' : uri.host;
