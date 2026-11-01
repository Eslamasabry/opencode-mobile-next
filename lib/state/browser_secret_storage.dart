/// Whether a browser page can keep a profile secret at all.
///
/// flutter_secure_storage's web implementation encrypts with WebCrypto,
/// which only exists in a secure context (https, or localhost), and keeps
/// the ciphertext in the origin's localStorage, which a sandboxed frame or a
/// browser that blocks site data refuses. Both are decided by where the
/// page runs, not by anything the app can change, so the answer is probed
/// before a password is typed and reported in product words rather than
/// discovered as a failed save. Native builds always answer [available]:
/// their keyring problems surface through the platform exception instead.
enum BrowserSecretStorageState {
  available,

  /// Not https and not localhost: WebCrypto is absent, so nothing can be
  /// encrypted. Passwords are refused outright — never kept in the clear.
  insecureContext,

  /// The page cannot use localStorage (blocked site data, a sandboxed frame,
  /// or a private mode that refuses writes).
  storageBlocked,
}
