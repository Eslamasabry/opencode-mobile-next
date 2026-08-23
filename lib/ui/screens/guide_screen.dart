import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Setup guide for both connection modes:
/// remote machine and on-device (Termux).
class GuideScreen extends StatelessWidget {
  final bool embedded;
  const GuideScreen({super.key, this.embedded = false});

  Widget _body(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
      children: [
        _Section(
          title: 'How this app works',
          children: [
            const Text(
              'OpenCode runs as a headless HTTP server (`opencode serve`). '
              'This app is a client that talks to it — either on your computer '
              '(remote) or right here on the phone via Termux (on-device).',
            ),
          ],
        ),
        _Section(
          title: '1 · Remote machine',
          children: [
            const Text('On your dev box, expose the server:'),
            const Cmd('OPENCODE_SERVER_PASSWORD=your-secret \\\n  opencode serve --hostname 0.0.0.0 --port 4096'),
            const Text(
                'Then add a server in the app pointing to http://<machine-ip>:4096 '
                'with username opencode and your password.'),
            _tip(context,
                'Keep it off the public internet. Use a VPN like Tailscale, or an SSH tunnel:\n'
                'ssh -L 4096:127.0.0.1:4096 user@host\n…then connect to http://127.0.0.1:4096 from a port-forward app.'),
          ],
        ),
        _Section(
          title: '2 · On-device via Termux (automated)',
          children: [
            const Text(
                'Use the “On-device (Termux)” card on the Servers screen. '
                'The app installs Termux, unlocks the bridge, sets up opencode, '
                'starts the server and connects — all guided.'),
            _tip(context,
                'Only two taps need you personally: downloading the Termux APK and '
                'pasting one unlock line inside Termux once — both required by '
                'Android\u2019s security model, not by this app.'),
            const Text('Prefer manual? Inside Termux run:'),
            const Cmd('# plain-Termux npm installs are broken upstream\n'
                '# (npm os=android -> no opencode-android-arm64 package),\n'
                '# so we use an Ubuntu chroot:\n'
                'pkg install proot-distro\n'
                'proot-distro install ubuntu\n'
                'proot-distro login ubuntu\n'
                '  apt update && apt install -y nodejs npm\n'
                '  npm i -g opencode-ai\n'
                '  opencode serve --hostname 127.0.0.1 --port 4096 &\n'
                'exit'),
            const Text(
                'The chroot shares the network stack, so http://127.0.0.1:4096 '
                'works from this app. Run `termux-wake-lock` to keep it alive.'),
          ],
        ),
        _Section(
          title: 'Security notes',
          children: [
            const Bullet('Always set OPENCODE_SERVER_PASSWORD when binding beyond localhost.'),
            const Bullet('Passwords are stored in the Android Keystore on this device only.'),
            const Bullet(
                'The server can execute commands on its host — treat access like SSH access.'),
          ],
        ),
      ],
    );
  }

  static Widget _tip(BuildContext context, String text) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.primaryContainer.withValues(alpha: .35),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(Icons.lightbulb_outline_rounded,
            size: 16, color: theme.colorScheme.primary),
        const SizedBox(width: 8),
        Expanded(child: Text(text, style: theme.textTheme.bodySmall)),
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (embedded) return _body(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Setup guide')),
      body: _body(context),
    );
  }
}

class _Section extends StatelessWidget {
  final String title;
  final List<Widget> children;
  const _Section({required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 22),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title.toUpperCase(),
            style: Theme.of(context).textTheme.labelMedium!.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.w700,
                letterSpacing: .8)),
        const SizedBox(height: 8),
        ...children,
      ]),
    );
  }
}

class Cmd extends StatelessWidget {
  final String text;
  const Cmd(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(vertical: 8),
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? Colors.black.withValues(alpha: .45)
            : Colors.black.withValues(alpha: .05),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: theme.dividerColor.withValues(alpha: .5)),
      ),
      child: Stack(children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: SelectableText(
            text,
            style: theme.textTheme.bodySmall!.copyWith(fontFamily: 'monospace', fontSize: 12.5),
          ),
        ),
        Positioned(
          top: -4,
          right: -4,
          child: IconButton(
            visualDensity: VisualDensity.compact,
            iconSize: 15,
            icon: Icon(Icons.copy_rounded, color: theme.hintColor),
            onPressed: () async {
              await Clipboard.setData(ClipboardData(text: text));
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                    content: Text('Copied'), duration: Duration(seconds: 1)));
              }
            },
          ),
        ),
      ]),
    );
  }
}

class Bullet extends StatelessWidget {
  final String text;
  const Bullet(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('•  ', style: TextStyle(color: Theme.of(context).colorScheme.primary)),
        Expanded(child: Text(text)),
      ]),
    );
  }
}
