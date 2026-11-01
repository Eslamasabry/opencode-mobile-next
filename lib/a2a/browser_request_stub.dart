const a2aIsWeb = false;

/// Native A2A requests retain their existing injected HttpClient.
class BrowserA2aRequests {
  Future<Map<String, dynamic>> request(
    Uri url, {
    Map<String, dynamic>? body,
    String? bearer,
  }) => throw UnsupportedError('Browser transport requires a browser.');

  void close() {}
}
