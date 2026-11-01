/// Fallback for platforms with neither `dart:io` nor `dart:js_interop`.
/// Dio's own adapter selection applies; nothing to configure.
library;

import 'package:dio/dio.dart';

const bool usesBrowserTransport = false;

void configurePlatformDio(Dio dio) {}
