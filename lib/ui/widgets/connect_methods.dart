import '../../domain/server_gateway.dart';

/// True for OAuth methods that finish through a browser redirect back to the
/// machine running the server. From a phone or another computer the redirect
/// lands on the wrong device, so those methods go last and get a warning.
bool connectMethodNeedsServerBrowser(IntegrationMethodInfo method) =>
    method.type == 'oauth' && method.label.toLowerCase().contains('browser');

/// Headless and device-code flows first, browser-redirect flows last, keys
/// after OAuth. Stable otherwise.
List<IntegrationMethodInfo> orderConnectMethods(
  List<IntegrationMethodInfo> methods,
) {
  int rank(IntegrationMethodInfo m) => switch (m.type) {
    'oauth' => connectMethodNeedsServerBrowser(m) ? 2 : 0,
    'key' => 1,
    _ => 3,
  };
  final sorted = List<IntegrationMethodInfo>.of(methods);
  sorted.sort((a, b) => rank(a).compareTo(rank(b)));
  return sorted;
}

/// The one line under a connect option that says how it finishes.
String connectMethodHint(IntegrationMethodInfo method) {
  if (method.type == 'key') return 'Paste an API key';
  if (connectMethodNeedsServerBrowser(method)) {
    return 'Opens a browser, but sign-in completes on the computer running '
        'OpenCode. From this device choose a headless option instead.';
  }
  if (method.label.toLowerCase().contains('headless')) {
    return 'Sign in on any device, then paste the code here. Works from a phone.';
  }
  return 'Sign in with your account';
}
