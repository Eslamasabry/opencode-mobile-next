/// Fallback for platforms without `dart:io`: no resolver detail exists.
library;

bool isHostLookupFailure(Object? cause) => false;
