import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../platform/platform_capabilities.dart';
import '../app_theme.dart';
import '../widgets/product_states.dart';

/// Setup guide. Leads with the one story a first-time user needs — run
/// `opencode2 pair`, scan or paste, start talking — and folds every other
/// route (HTTPS proxies, SSH tunnels, older `opencode serve` servers, Termux
/// internals) behind an "Advanced" disclosure so nobody has to pick a path
/// before they know what the app does.
class GuideScreen extends StatelessWidget {
  final bool embedded;
  const GuideScreen({super.key, this.embedded = false});

  Widget _body(BuildContext context) {
    final theme = Theme.of(context);
    // The on-device path is Termux, which is Android-only. A desktop reader
    // is told about the one path that exists for them rather than a second
    // one they cannot take.
    final onDevice = platformCapabilities.supportsTermux;
    final canScan = platformCapabilities.supportsQrPairing;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
      children: [
        Text(
          'Three steps to your first session',
          style: theme.textTheme.titleLarge,
        ),
        const SizedBox(height: 6),
        Text(
          'OpenCode runs on your computer. This app is the remote. Pairing '
          'connects the two with one command — no addresses or passwords to '
          'type.',
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            height: 1.4,
          ),
        ),
        const SizedBox(height: 18),
        _Step(
          n: 1,
          title: 'On your computer, run one command',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'In a terminal on the computer where OpenCode is installed:',
              ),
              const Cmd('opencode2 pair', key: ValueKey('guide-pair-command')),
              const Text(
                'It starts the server and prints a pairing code'
                ' — and a QR code you can scan.',
              ),
            ],
          ),
        ),
        _Step(
          n: 2,
          title: canScan
              ? 'Scan the QR or paste the code in this app'
              : 'Paste the code in this app',
          child: Text(
            canScan
                ? 'Open Servers, tap Scan and point the camera at the QR — '
                      'or copy the code and tap Paste pairing code. The '
                      'address, username and password fill in together.'
                : 'Copy the printed code, open Servers and tap Paste pairing '
                      'code. The address, username and password fill in '
                      'together.',
          ),
        ),
        const _Step(
          n: 3,
          title: 'Start talking',
          child: Text(
            'Pick a project and send your first message. The work happens on '
            'your computer; this app shows it and lets you steer.',
          ),
        ),
        const SizedBox(height: 8),
        Theme(
          data: theme.copyWith(dividerColor: Colors.transparent),
          child: ExpansionTile(
            key: const ValueKey('guide-advanced'),
            tilePadding: EdgeInsets.zero,
            childrenPadding: const EdgeInsets.only(top: 4),
            expandedCrossAxisAlignment: CrossAxisAlignment.start,
            leading: const Icon(Icons.tune_rounded),
            title: const Text('Advanced'),
            subtitle: Text(
              onDevice
                  ? 'HTTPS, SSH tunnels, older servers, Termux internals'
                  : 'HTTPS, SSH tunnels, older servers',
            ),
            children: [
              _Section(
                title: 'Reach a server over HTTPS or a tunnel',
                children: [
                  const Text(
                    'Pairing works when the address the server prints is one '
                    'this device can reach. If it is not, expose the server '
                    'through an HTTPS reverse proxy or an encrypted tunnel and '
                    'add the resulting https:// URL by hand. Remote HTTP is '
                    'intentionally blocked.',
                  ),
                  _tip(
                    context,
                    'Keep it off the public internet. Use HTTPS through a '
                    'private network such as Tailscale, or an SSH tunnel:\n'
                    'ssh -L 4096:127.0.0.1:4096 user@host\n…then connect to '
                    'http://127.0.0.1:4096 from a port-forward app.',
                  ),
                ],
              ),
              _Section(
                title: 'Older servers without pairing',
                children: [
                  const Text(
                    'Servers started with `opencode serve` do not print a '
                    'pairing code. Start them on loopback with a password:',
                  ),
                  const Cmd(
                    'OPENCODE_SERVER_PASSWORD=your-secret \\\n  opencode serve --hostname 127.0.0.1 --port 4096',
                  ),
                  const Text(
                    'Then add the server manually with username opencode and '
                    'that password.',
                  ),
                ],
              ),
              if (onDevice)
                _Section(
                  key: const ValueKey('guide-termux-section'),
                  title: 'On-device via Termux (automated)',
                  children: [
                    const Text(
                      'Use the “On-device (Termux)” card on the Servers '
                      'screen. The app installs Termux, unlocks the bridge, '
                      'sets up opencode, starts the server and connects — '
                      'all guided.',
                    ),
                    _tip(
                      context,
                      'Only two taps need you personally: downloading the '
                      'Termux APK and pasting one unlock line inside Termux '
                      'once — both required by Android’s security model, '
                      'not by this app.',
                    ),
                    const Text('Prefer manual? Inside Termux run:'),
                    const Cmd(
                      '# plain-Termux npm installs are broken upstream\n'
                      '# (npm os=android -> no opencode-android-arm64 package),\n'
                      '# so we use an Ubuntu chroot:\n'
                      'pkg install proot-distro\n'
                      'proot-distro install ubuntu\n'
                      'proot-distro login ubuntu\n'
                      '  apt update && apt install -y nodejs npm\n'
                      '  npm i -g opencode-ai\n'
                      '  opencode serve --hostname 127.0.0.1 --port 4096 &\n'
                      'exit',
                    ),
                    const Text(
                      'The chroot shares the network stack, so '
                      'http://127.0.0.1:4096 works from this app. Run '
                      '`termux-wake-lock` to keep it alive.',
                    ),
                  ],
                ),
              _Section(
                title: 'Security notes',
                children: [
                  const Bullet(
                    'Always set OPENCODE_SERVER_PASSWORD when binding beyond localhost.',
                  ),
                  Bullet(
                    onDevice
                        ? 'Passwords are stored in the Android Keystore on '
                              'this device only.'
                        : 'Passwords are stored in this desktop’s secret '
                              'service (libsecret) on this machine only.',
                  ),
                  const Bullet(
                    'The server can execute commands on its host — treat access like SSH access.',
                  ),
                ],
              ),
            ],
          ),
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
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            Icons.lightbulb_outline_rounded,
            size: 16,
            color: theme.colorScheme.primary,
          ),
          const SizedBox(width: 8),
          Expanded(child: Text(text, style: theme.textTheme.bodySmall)),
        ],
      ),
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

/// One numbered step of the three-step story. The number is decorative for
/// sighted readers and spoken as "Step 1 of 3" for everyone else.
class _Step extends StatelessWidget {
  final int n;
  final String title;
  final Widget child;
  const _Step({required this.n, required this.title, required this.child});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 18),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            label: 'Step $n of 3',
            excludeSemantics: true,
            child: CircleAvatar(
              radius: 14,
              backgroundColor: theme.colorScheme.primary,
              child: Text(
                '$n',
                style: theme.textTheme.labelLarge?.copyWith(
                  color: theme.colorScheme.onPrimary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 4, bottom: 6),
                  child: Text(title, style: theme.textTheme.titleMedium),
                ),
                DefaultTextStyle.merge(
                  style: theme.textTheme.bodyMedium?.copyWith(height: 1.4),
                  child: child,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  final String title;
  final List<Widget> children;
  const _Section({super.key, required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [SectionLabel.inline(title), ...children],
      ),
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
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? Colors.black.withValues(alpha: .45)
            : Colors.black.withValues(alpha: .05),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppTheme.hairline(theme)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 4, 12),
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: SelectableText(
                  text,
                  style: theme.textTheme.bodySmall!.copyWith(
                    fontFamily: AppTheme.monoFamily,
                    fontSize: AppTheme.codeFontSize,
                  ),
                ),
              ),
            ),
          ),
          // A full-size target: the command is the thing the reader came
          // here to take away, so its copy button is not a 15px afterthought.
          IconButton(
            tooltip: 'Copy command',
            icon: Icon(AppIcons.copy, color: AppTheme.mutedOf(theme)),
            onPressed: () async {
              await Clipboard.setData(ClipboardData(text: text));
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Copied'),
                    duration: Duration(seconds: 1),
                  ),
                );
              }
            },
          ),
        ],
      ),
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
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '•  ',
            style: TextStyle(color: Theme.of(context).colorScheme.primary),
          ),
          Expanded(child: Text(text)),
        ],
      ),
    );
  }
}
