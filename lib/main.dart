import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'state/connection.dart';
import 'ui/screens/guide_screen.dart';
import 'ui/screens/home_screen.dart';
import 'ui/screens/servers_screen.dart';
import 'ui/screens/chat_screen.dart';
import 'ui/screens/termux_setup_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final bootstrap = await AppBootstrap.create();
  final conn = ConnectionController(bootstrap.store);

  runApp(
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(bootstrap),
        connProvider.overrideWithValue(conn),
      ],
      child: const OcApp(),
    ),
  );
}

class OcApp extends StatelessWidget {
  const OcApp({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFF34D399),
      brightness: Brightness.dark,
    );
    return MaterialApp(
      title: 'OpenCode',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: const Color(0xFF0F1115),
        appBarTheme: AppBarTheme(
          backgroundColor: const Color(0xFF0F1115),
          foregroundColor: Colors.grey.shade100,
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
      ),
      initialRoute: '/',
      routes: {
        '/': (_) => _Root(),
        '/servers': (_) => const ServersScreen(),
        '/home': (_) => const HomeScreen(),
        '/guide': (_) => GuideScreen(embedded: false),
        '/termux-setup': (_) => const TermuxSetupScreen(),
      },
      onGenerateRoute: (settings) {
        if (settings.name?.startsWith('/chat/') == true) {
          final id = settings.name!.substring('/chat/'.length);
          return MaterialPageRoute(builder: (_) => ChatScreen(sessionID: id));
        }
        return null;
      },
    );
  }
}

/// Decides the start destination from persisted state.
class _Root extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final conn = ref.watch(connProvider);
    if (conn.profile != null) {
      // Reconnect silently to the saved server.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final p = conn.profile;
        if (p != null && conn.api == null) conn.connect(p);
      });
      return const HomeScreen();
    }
    return const ServersScreen();
  }
}
