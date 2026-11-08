import '../api2/transport.dart' show Api2Error;
import 'models.dart' show ApiException;
import 'product_failure.dart';
import 'product_repository.dart' show ProductException;

/// Why a server list (providers, MCP servers, MCP resources) did not load,
/// sorted into what the person can do about it. Never shown itself: the
/// screen words each kind, and the technical text stays in Details.
enum ListLoadProblem {
  /// The server has no such list (an older or newer server without the
  /// route: a tagless 404, 405 or 501). An expected condition, not a
  /// failure: the section says the server does not offer it.
  unsupported,

  /// The open project's folder is not on the server
  /// (`LocationNotFoundError`).
  folderMissing,

  /// The server refused the saved password (401/403).
  signIn,

  /// The server could not be reached at all.
  unreachable,

  /// The server took too long to answer.
  timedOut,

  /// Anything else: the server answered, but not with the list.
  failed,
}

/// What [error] says about a list that did not load. Looks through the
/// gateway's [ProductException] wrapper to the transport failure it keeps
/// as its cause.
ListLoadProblem listLoadProblemOf(Object error) {
  final cause = switch (error) {
    ProductException(:final cause?) => cause,
    _ => error,
  };
  if (cause is UnsupportedError || cause is UnimplementedError) {
    return ListLoadProblem.unsupported;
  }
  final (status, tag) = switch (cause) {
    Api2Error(:final statusCode, :final tag) => (statusCode, tag),
    ApiException(:final statusCode, :final errorTag) => (statusCode, errorTag),
    _ => (null, null),
  };
  if (tag == 'LocationNotFoundError') return ListLoadProblem.folderMissing;
  // A route the server does not have answers 404 with no tagged body; a
  // tagged 404 is about something it looked for (a session, a folder).
  if ((status == 404 && (tag == null || tag.isEmpty)) ||
      status == 405 ||
      status == 501) {
    return ListLoadProblem.unsupported;
  }
  return switch (ProductFailure.from(cause).category) {
    ProductFailureCategory.signIn => ListLoadProblem.signIn,
    ProductFailureCategory.network => ListLoadProblem.unreachable,
    ProductFailureCategory.timedOut => ListLoadProblem.timedOut,
    _ => ListLoadProblem.failed,
  };
}
