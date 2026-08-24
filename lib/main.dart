import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'state/connection.dart';
import 'ui/screens/guide_screen.dart';
import 'ui/screens/about_screen.dart';
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
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
        ),
        textButtonTheme: TextButtonThemeData(
          style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
        ),
      ),
      initialRoute: '/',
      routes: {
        '/': (_) => _Root(),
        '/servers': (_) => const ServersScreen(),
        '/home': (_) => const HomeScreen(),
        '/guide': (_) => GuideScreen(embedded: false),
        '/about': (_) => const AboutScreen(),
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
class _Root extends ConsumerStatefulWidget {
  @override
  ConsumerState<_Root> createState() => _RootState();
}

class _RootState extends ConsumerState<_Root> {
  bool _started = false;
  late final ConnectionController _controller;

  @override
  void initState() {
    super.initState();
    _controller = ref.read(connProvider)..addListener(_changed);
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  void _connectSaved() {
    if (_started) return;
    final conn = _controller;
    final profile = conn.profile;
    if (profile == null) return;
    _started = true;
    WidgetsBinding.instance.addPostFrameCallback((_) => conn.connect(profile));
  }

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    if (conn.profile == null) return const ServersScreen();
    if (conn.api != null && conn.repository != null && conn.version != null) {
      return const HomeScreen();
    }
    _connectSaved();
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 360),
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (conn.lastError == null)
                    const CircularProgressIndicator()
                  else
                    Icon(
                      Icons.cloud_off_outlined,
                      size: 42,
                      color: Theme.of(context).colorScheme.error,
                    ),
                  const SizedBox(height: 20),
                  Text(
                    conn.lastError == null
                        ? 'Connecting to ${conn.profile!.name}'
                        : 'Could not connect',
                    style: Theme.of(context).textTheme.titleMedium,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    conn.lastError ?? conn.profile!.baseUrl,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 20),
                  if (conn.lastError != null)
                    Wrap(
                      alignment: WrapAlignment.center,
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        OutlinedButton(
                          onPressed: () => Navigator.of(
                            context,
                          ).pushNamedAndRemoveUntil('/servers', (_) => false),
                          child: const Text('Change server'),
                        ),
                        FilledButton(
                          onPressed: () {
                            _started = false;
                            _connectSaved();
                          },
                          child: const Text('Try again'),
                        ),
                      ],
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    super.dispose();
  }
}
