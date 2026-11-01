import 'package:web/web.dart' as web;

/// The browser is authoritative, including embedding and origin restrictions.
bool get isSecureContext => web.window.isSecureContext;
