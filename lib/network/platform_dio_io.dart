/// Native (`dart:io`) side of `platform_dio.dart`.
///
/// The default `IOHttpClientAdapter` already streams response bodies chunk
/// by chunk, applies connect/receive timeouts and aborts on cancel, so the
/// native transport is left exactly as Dio created it.
library;

import 'package:dio/dio.dart';

const bool usesBrowserTransport = false;

void configurePlatformDio(Dio dio) {}
