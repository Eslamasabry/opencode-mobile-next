import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/product_failure.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart' show ConnectionController;
import '../app_theme.dart';
import '../kit/kit_dialog.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_redact.dart';
import '../kit/kit_technical_value.dart';

// The shared error words (the no-raw-errors sweep and the audit's
// domain-owned failure categories): what failed, in plain words, for any
// thrown object ([productErrorText], [productErrorKind]), the redacted
// technical text for a Details fold ([productErrorDetails]) and the one
// blocking alert for a failed act ([showProductError]). Not a kit part.
//
// The "not the normal content" widgets that used to live here were thin
// wrappers over KitStateView and SectionLabel; kit-hygiene deleted them
// once no screen used them (KitStateView, KitSectionLabel).

AppLocalizations _copy(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(Localizations.localeOf(context));

/// The words of the running app's locale, kept for [productErrorText] when a
/// caller has no [AppLocalizations] to hand (most of them). Set by
/// [ProductErrorLocale] near the root, so an Arabic app says its errors in
/// Arabic without every call site passing the locale.
AppLocalizations? _ambientCopy;

/// Keeps [productErrorText]'s default words in the app's locale. Put it once
/// below the app's `Localizations` (the `MaterialApp.builder`).
class ProductErrorLocale extends StatefulWidget {
  const ProductErrorLocale({super.key, required this.child});

  final Widget child;

  @override
  State<ProductErrorLocale> createState() => _ProductErrorLocaleState();
}

class _ProductErrorLocaleState extends State<ProductErrorLocale> {
  AppLocalizations? _mine;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _mine = Localizations.of<AppLocalizations>(context, AppLocalizations);
    _ambientCopy = _mine;
  }

  @override
  void dispose() {
    if (identical(_ambientCopy, _mine)) _ambientCopy = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// The connection's last failure in words: mapped from the failure itself
/// when it kept one (never its raw text), else its app-authored line.
String? connectionErrorText(ConnectionController c, {AppLocalizations? l10n}) {
  final failure = c.lastFailure;
  if (failure != null) return productErrorText(failure, l10n: l10n);
  final line = c.lastError;
  return line == null ? null : productErrorText(line, l10n: l10n);
}

/// Localized words for a domain-owned failure category. Server messages never
/// enter the body; their redacted technical reason is available only in Details.
String productErrorText(Object error, {AppLocalizations? l10n}) {
  final copy =
      l10n ?? _ambientCopy ?? lookupAppLocalizations(const Locale('en'));
  final failure = ProductFailure.from(error);
  return switch (failure.category) {
    ProductFailureCategory.words =>
      failure.authoredMessage ?? copy.productErrorUnknown,
    ProductFailureCategory.stagedRevert => copy.productErrorStagedRevert,
    ProductFailureCategory.folderNotInstalled =>
      copy.folderBrowserErrorNotInstalled,
    ProductFailureCategory.folderMissing => copy.folderBrowserErrorMissing,
    ProductFailureCategory.folderDenied => copy.folderBrowserErrorDenied,
    ProductFailureCategory.folderLinked => copy.folderBrowserErrorLinked,
    ProductFailureCategory.network => copy.e7SharedOpenCodeUnreachableTryAgain,
    ProductFailureCategory.timedOut => copy.productErrorTimedOut,
    ProductFailureCategory.certificate => copy.productErrorCertificate,
    ProductFailureCategory.signIn => copy.productErrorSignIn,
    ProductFailureCategory.notFound => copy.productErrorNotFound,
    ProductFailureCategory.conflict => copy.productErrorConflict,
    ProductFailureCategory.computer => copy.productErrorComputer,
    ProductFailureCategory.unreadableReply => copy.productErrorUnreadableReply,
    ProductFailureCategory.busy => copy.productErrorBusy,
    ProductFailureCategory.server => copy.productErrorServer(
      failure.statusCode ?? 500,
    ),
    ProductFailureCategory.rejected => copy.productErrorRejected,
    ProductFailureCategory.unexpected => copy.productErrorUnexpected,
    ProductFailureCategory.device => copy.productErrorDevice,
    ProductFailureCategory.storage => copy.productErrorStorage,
    ProductFailureCategory.projectGit => copy.productErrorProjectGit,
    ProductFailureCategory.termux => copy.productErrorTermux,
    // An error nobody classified is not a network failure: saying
    // "unreachable" here sent the Add tools investigation the wrong way.
    ProductFailureCategory.unknown => copy.productErrorUnknown,
  };
}

/// Whether the domain failure should offer a network fix first.
KitErrorKind productErrorKind(Object? error) {
  if (error == null) return KitErrorKind.other;
  return switch (ProductFailure.from(error).category) {
    ProductFailureCategory.network ||
    ProductFailureCategory.timedOut => KitErrorKind.network,
    _ => KitErrorKind.of(error),
  };
}

/// Redacted technical text for a Details fold, Copy details, or a report.
/// This must never be used for the headline or body.
String? productErrorDetails(Object? error) {
  if (error == null) return null;
  final details = ProductFailure.from(error).technicalDetails;
  return details == null ? null : KitRedact.text(details);
}

/// Tells the person that an act they just started failed.
///
/// A snackbar is only ever done-with-undo (KIT-34), so a failure with no
/// part of its own to sit on is a blocking alert (kit-v2 §4.8): [title]
/// (default "Couldn't finish that"; better, what failed) and the thrown
/// object in words through [productErrorText], so raw exceptions never
/// reach users. The technical text ([productErrorDetails]) is folded under
/// "Error details", redacted. Returns at once; the alert closes on Close,
/// back or Esc.
void showProductError(BuildContext context, Object error, {String? title}) {
  final l10n = _copy(context);
  final details = productErrorDetails(error);
  unawaited(
    showKitAlert(
      context,
      title: title ?? l10n.productStatesActionFailedTitle,
      body: productErrorText(error, l10n: l10n),
      details: [
        if (details != null)
          KitTechnicalValue(l10n.productErrorDetailsLabel, details),
      ],
      icon: AppIconography.error,
      alertKey: const ValueKey('product-error-alert'),
    ),
  );
}
