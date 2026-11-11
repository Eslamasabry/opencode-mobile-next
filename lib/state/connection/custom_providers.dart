part of '../connection.dart';

// Declaring a provider the server's catalog does not have.
//
// OpenCode 2 has no endpoint that adds a provider: providers are declared in
// the server's own `opencode.json` (`docs/opencode2-protocol-notes.md`). Only
// the in-app engine lets the app own that file, so this path is gated on
// [ServerCapabilities.customProviders] and never claims a remote server can.

/// [ConnectionController]'s custom-provider declaration.
mixin _ConnectionControllerCustomProviders on ChangeNotifier
    implements CustomProviderGateway {
  ConnectionController get _self;

  /// Injected by tests and by a side connection of the same phone, so a widget
  /// test never has to answer the Android channel.
  CustomProviderWriter? _customProviderWriter;

  /// Whether the selected server's config file is one the app can write.
  bool get _customProvidersEffective {
    final owner = _self._connectedProfile ?? _self.profile;
    if (owner == null) return false;
    return BuiltinLinux.managesServerUrl(owner.baseUrl) &&
        (BuiltinLinux.supported || _customProviderWriter != null);
  }

  @override
  Future<CustomProviderSaveResult> addCustomProvider(
    CustomProviderDraft draft,
  ) async {
    if (!_self.capabilities.customProviders) {
      throw const ProductException(
        'This server keeps its own configuration, so a provider can only be '
        'declared on the server itself.',
      );
    }
    // Validate before touching the phone's file: a rejected field must not
    // leave a half-written config behind.
    final block = draft.toConfigJson();
    final providerID = draft.normalizedID;
    final writer = _customProviderWriter ?? BuiltinCustomProviderWriter();
    final outcome = await writer.write(providerID: providerID, block: block);
    if (outcome != CustomProviderWriteOutcome.saved) {
      throw customProviderWriteFailure(outcome);
    }
    // The declaration is on disk; the running server read its config at
    // startup, so nothing about the provider is usable until it restarts.
    // The caller restarts and reconnects, then the provider is listed.
    return const CustomProviderSaveResult(restartRequired: true);
  }
}
