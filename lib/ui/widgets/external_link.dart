import 'dart:async';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../domain/agent_sign_in.dart' show AgentAuthorizationUrl;
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_copy.dart';
import '../kit/kit_redact.dart';
import '../kit/kit_dialog.dart';
import '../kit/kit_sheet.dart' show KitConfirmKind, showKitConfirm;
import '../kit/kit_technical_value.dart';
import 'product_states.dart';

/// What [openExternalLink] did, so callers can react without re-deriving it.
enum ExternalLinkOutcome {
  /// The URL failed the policy below and was never handed to the platform.
  blocked,

  /// The URL was allowed but the user declined the confirmation (or copied
  /// the link instead of opening it).
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

/// The platform launch [openExternalLink] ends in: [uri] in the app that
/// handles it, outside this one. Only for a `launcher` handed to
/// [openExternalLink] that adds its own guard around the default; nothing
/// else calls it, so every URL still passes the link policy first (SEC-1).
Future<bool> launchExternalUri(Uri uri) =>
    launchUrl(uri, mode: LaunchMode.externalApplication);

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
/// Every answer is a kit modal ([showKitConfirm], [showKitAlert]); a
/// snackbar is only ever done-with-undo (KIT-34).
///
/// [launcher] exists for tests; production goes to `url_launcher`.
Future<ExternalLinkOutcome> openExternalLink(
  BuildContext context,
  String? value, {
  Future<bool> Function(Uri uri)? launcher,
}) async {
  final copy = _sharedCopy(context);
  final uri = safeExternalLinkUri(value);
  if (uri == null) {
    // Blocked is the answer to the person's tap, so it is said in a
    // blocking alert; the outcome returns at once. The refused value is not
    // echoed: it may be long, hostile or unreadable.
    if (context.mounted) {
      unawaited(
        showKitAlert(
          context,
          title: copy.externalLinkBlockedTitle,
          body: copy.externalLinkBlockedBody,
          icon: AppIconography.locked,
          alertKey: const ValueKey('external-link-blocked'),
        ),
      );
    }
    return ExternalLinkOutcome.blocked;
  }
  final insecure = uri.scheme == 'http';
  final safeAddress = _safeLinkAddress(uri);

  // The destination host stays in sight (not folded under Details): it is
  // what the person checks before anything opens. The whole address is one
  // tap away under Details, and "Copy link" uses it without leaving the
  // app. On http the risky choice is the error-toned one, Enter never
  // confirms it (KitConfirmKind.destructive), and "Don't open" is the way
  // back.
  if (!context.mounted) return ExternalLinkOutcome.cancelled;
  final confirmed = await showKitConfirm(
    context,
    title: insecure
        ? copy.e7SharedOpenInsecureHTTPLink
        : copy.e7SharedOpenExternalLink,
    body: copy.externalLinkOpensHost(KitRedact.text(externalLinkHost(uri))),
    confirmLabel: insecure ? copy.e7SharedOpenHTTPLink : copy.e7SharedOpenLink,
    kind: insecure ? KitConfirmKind.destructive : KitConfirmKind.neutral,
    cancelLabel: insecure ? copy.externalLinkDontOpen : null,
    icon: insecure ? AppIconography.warning : AppIconography.externalLink,
    consequences: [if (insecure) copy.e7SharedHTTPIsNotEncryptedOtherDevicesOn],
    alternative: KitAction(
      key: const ValueKey('external-link-copy'),
      label: copy.externalLinkCopy,
      icon: AppIconography.copy,
      onPressed: () {
        // Untrusted links may contain credentials. The approved launcher
        // alone receives the original URI; display and clipboard stay masked.
        if (context.mounted) {
          unawaited(KitCopy.copy(context, safeAddress));
        }
      },
    ),
    details: [KitTechnicalValue(copy.externalLinkAddress, safeAddress)],
    sheetKey: const ValueKey('external-link-confirm'),
  );
  if (!confirmed) return ExternalLinkOutcome.cancelled;
  if (!context.mounted) return ExternalLinkOutcome.cancelled;
  return _launchChecked(context, uri, safeAddress, launcher);
}

/// An agent's sign-in page, which the person asked for with "Open the …
/// sign-in page". It passes the same link policy, and must also be the exact
/// page the app already verified (`https://claude.com/cai/oauth/authorize`,
/// no port, user info or fragment: [AgentAuthorizationUrl.validate]). The
/// destination is then fixed and known, so no "Open external link?" sheet is
/// stacked on the sign-in sheet. Anything else takes the confirmed path.
Future<ExternalLinkOutcome> openAgentSignInPage(
  BuildContext context,
  String value, {
  Future<bool> Function(Uri uri)? launcher,
}) async {
  final uri = safeExternalLinkUri(value);
  bool verified;
  try {
    AgentAuthorizationUrl.validate('claude', value);
    verified = uri != null && uri.scheme == 'https';
  } catch (_) {
    verified = false;
  }
  if (!verified) return openExternalLink(context, value, launcher: launcher);
  return _launchChecked(context, uri!, _safeLinkAddress(uri), launcher);
}

Future<ExternalLinkOutcome> _launchChecked(
  BuildContext context,
  Uri uri,
  String safeAddress,
  Future<bool> Function(Uri uri)? launcher,
) async {
  final copy = _sharedCopy(context);
  try {
    final opened = await (launcher?.call(uri) ?? launchExternalUri(uri));
    if (opened) return ExternalLinkOutcome.opened;
    if (context.mounted) {
      unawaited(
        showKitAlert(
          context,
          title: copy.externalLinkOpenFailedTitle,
          body: copy.e7SharedNoAppCouldOpenThisLink,
          icon: AppIconography.externalLink,
          details: [KitTechnicalValue(copy.externalLinkAddress, safeAddress)],
          alertKey: const ValueKey('external-link-no-app'),
        ),
      );
    }
    return ExternalLinkOutcome.noHandler;
  } catch (error) {
    if (context.mounted) {
      unawaited(
        showKitAlert(
          context,
          title: copy.externalLinkOpenFailedTitle,
          body: productErrorText(error, l10n: copy),
          icon: AppIconography.error,
          details: [KitTechnicalValue(copy.externalLinkAddress, safeAddress)],
          alertKey: const ValueKey('external-link-failed'),
        ),
      );
    }
    return ExternalLinkOutcome.failed;
  }
}

// Authorization URLs also carry opaque codes and CSRF state. Those names are
// ordinary words in prose, so mask them only in URI parameters. Decode each
// component before checking registered values; retain untouched URL spelling
// for ordinary links. This representation never goes to the launcher.
String _safeLinkAddress(Uri uri) {
  String redactParameters(String value) => value
      .split('&')
      .map((part) {
        final separator = part.indexOf('=');
        if (separator < 0) return KitRedact.text(part);
        try {
          final name = Uri.decodeQueryComponent(part.substring(0, separator));
          final decoded = Uri.decodeQueryComponent(
            part.substring(separator + 1),
          );
          final sensitive = const {
            'code',
            'state',
            'session_state',
            'code_verifier',
          }.contains(name.toLowerCase());
          if (sensitive ||
              KitRedact.containsSecret('$name=$decoded') ||
              KitRedact.containsSecret(decoded)) {
            return '${part.substring(0, separator + 1)}${KitRedact.mask}';
          }
        } on FormatException {
          // A malformed encoded component cannot be inspected reliably.
          return '${part.substring(0, separator + 1)}${KitRedact.mask}';
        }
        return part;
      })
      .join('&');

  final address = uri.toString();
  final fragmentStart = address.indexOf('#');
  final head = fragmentStart < 0
      ? address
      : address.substring(0, fragmentStart);
  final queryStart = head.indexOf('?');
  final safeHead = queryStart < 0
      ? head
      : '${head.substring(0, queryStart + 1)}'
            '${redactParameters(head.substring(queryStart + 1))}';
  if (fragmentStart < 0) return KitRedact.text(safeHead);
  final fragment = address.substring(fragmentStart + 1);
  // SPA callbacks may use #/callback?code=... rather than #code=....
  final fragmentQuery = fragment.indexOf('?');
  final safeFragment = fragmentQuery < 0
      ? redactParameters(fragment)
      : '${fragment.substring(0, fragmentQuery + 1)}'
            '${redactParameters(fragment.substring(fragmentQuery + 1))}';
  return KitRedact.text('$safeHead#$safeFragment');
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

AppLocalizations _sharedCopy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));
