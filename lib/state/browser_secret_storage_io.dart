import 'browser_secret_storage.dart';

/// Native builds keep secrets in the platform keyring; there is no page
/// context to judge. The default of the conditional import in
/// `profiles.dart`, so it is also what the analyzer sees.
BrowserSecretStorageState browserSecretStorageState() =>
    BrowserSecretStorageState.available;
