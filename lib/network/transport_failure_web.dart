/// Browser side of `transport_failure.dart`: the browser never reports
/// resolver outcomes, so no failure can be attributed to DNS.
library;

bool isHostLookupFailure(Object? cause) => false;
