import '../builtin/builtin_server.dart';
import 'profiles.dart';

/// Whether [profile] is OpenCode on this phone (the built-in Linux). The MCP
/// catalogue (P2.5) uses it to offer phone setup's Add tools for a server
/// that needs Node there, instead of a command that would fail. Shape only
/// (the built-in server's loopback address on Android), the same test This
/// phone uses.
bool isPhoneHostedProfile(ServerProfile? profile) =>
    looksLikeInAppServer(profile);
