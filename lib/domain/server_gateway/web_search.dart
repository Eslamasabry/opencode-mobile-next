/// Search performs an explicit provider request, never a session mutation.
abstract interface class WebSearchGateway {
  Future<List<WebSearchProvider>> webSearchProviders();
  Future<WebSearchResponse> searchWeb(
    String query, {
    required String providerID,
  });
}

class WebSearchProvider {
  final String id;
  final String name;
  const WebSearchProvider({required this.id, required this.name});
}

class WebSearchResult {
  final String url;
  final String? title;
  final String? content;
  const WebSearchResult({required this.url, this.title, this.content});
}

class WebSearchResponse {
  final String providerID;
  final List<WebSearchResult> results;
  const WebSearchResponse({required this.providerID, required this.results});
}

enum WebSearchFailureKind {
  unavailable,
  authentication,
  invalidResponse,
  failed,
}

/// Deliberately excludes remote error bodies, URLs, queries and credentials.
class WebSearchFailure implements Exception {
  final WebSearchFailureKind kind;
  const WebSearchFailure(this.kind);
}
