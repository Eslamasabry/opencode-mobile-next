import 'byo_host.dart';

/// Display-only preparation. These commands are NEVER passed to the SSH runner.
class ByoHostSetupStep {
  const ByoHostSetupStep({
    required this.id,
    required this.title,
    required this.copy,
    required this.commands,
    required this.verification,
  });
  final String id;
  final String title;
  final String copy;
  final List<String> commands;
  final String verification;
}

/// English copy intent for kit UI/l10n. Commands use the validated host account;
/// no password, key, device token or output from a server is interpolated.
List<ByoHostSetupStep> byoHostOwnerSetup(ByoHostTarget target) {
  final user = target.user;
  // ByoHostTarget limits user to letters/digits/underscore/dot/hyphen. Quotes
  // additionally make shell arguments explicit; no shell substitution is used.
  return List.unmodifiable([
    const ByoHostSetupStep(
      id: 'tailnet',
      title: 'Connect both devices to your Tailscale',
      copy:
          'Sign in to your Tailscale on the phone and machine. Use the machine’s Tailscale address. Public addresses are not supported.',
      commands: ['tailscale status', 'tailscale ip -4', 'tailscale ip -6'],
      verification:
          'The app resolves the entered address once and accepts only machine addresses in the Tailscale ranges. It then connects to that numeric address and verifies the pinned SSH identity. Address syntax or a Tailscale app being installed does not prove VPN membership.',
    ),
    const ByoHostSetupStep(
      id: 'account',
      title: 'Use a separate machine account',
      copy:
          'Use an Ubuntu 24.04 account without root access. This account owns the host’s files and sessions.',
      commands: [
        'cat /etc/os-release',
        'id -u',
        'command -v python3 curl tar ss sshd ssh-keygen loginctl systemctl',
      ],
      verification:
          'The installer checks Ubuntu 24.04, a nonroot UID, architecture, Python and the user service manager before pairing. Missing tools require owner preparation; setup does not install OS packages with sudo.',
    ),
    ByoHostSetupStep(
      id: 'sshNetwork',
      title: 'Keep SSH on your private network',
      copy:
          'Do this from a rescue session before setup. Replace MACHINE_TAILSCALE_IP with the machine’s numeric Tailscale address. Remove other ListenAddress directives that allow public or wildcard listeners. This changes access for the whole SSH daemon.',
      commands: [
        "sudo tee /etc/ssh/sshd_config.d/00-oc-byo-listen.conf >/dev/null <<'OC_BYO_LISTEN'\nListenAddress MACHINE_TAILSCALE_IP\nOC_BYO_LISTEN",
        'sudo /usr/sbin/sshd -t',
        "sudo /usr/sbin/sshd -T | sed -n '/^listenaddress /p'",
        'sudo systemctl disable --now ssh.socket',
        'sudo systemctl enable ssh.service',
        'sudo systemctl restart ssh.service',
        "sudo ss -ltnp 'sport = :${target.port}'",
      ],
      verification:
          'The installer reads actual listening sockets on the selected SSH port using ss -H -ltn. Any wildcard, public or ordinary LAN listener is refused. The app never changes daemon bindings, socket activation or firewall rules. Keep rescue access and verify every SSH port separately; the app checks its selected port only.',
    ),
    ByoHostSetupStep(
      id: 'linger',
      title: 'Let sessions keep running',
      copy:
          'Run this once on the machine with administrator access. Your sessions can then keep running after you close the phone.',
      commands: [
        "sudo loginctl enable-linger '$user'",
        "loginctl show-user '$user' --property=Linger --value",
        'systemctl --user show-environment >/dev/null',
      ],
      verification:
          'The installer reads loginctl show-user --property=Linger --value and requires yes, then verifies that systemctl --user is usable. It never enables linger or asks for a sudo password.',
    ),
    ByoHostSetupStep(
      id: 'sshPolicy',
      title: 'Allow the private connection',
      copy:
          'Keep an administrator session open while changing SSH settings. Apply these settings for this dedicated account, check them, and reload SSH only after the check passes.',
      commands: [
        "sudo tee /etc/ssh/sshd_config.d/00-oc-byo-host.conf >/dev/null <<'OC_BYO_SSH'\nUseDNS no\nMatch User $user\n    AllowTcpForwarding local\n    AllowStreamLocalForwarding no\n    PermitTunnel no\n    DisableForwarding no\n    PubkeyAuthentication yes\n    AuthorizedKeysFile .ssh/authorized_keys\n    PermitListen none\nMatch all\nOC_BYO_SSH",
        'sudo /usr/sbin/sshd -t',
        "sudo /usr/sbin/sshd -T -C 'user=$user,host=PHONE_TAILSCALE_IP,addr=PHONE_TAILSCALE_IP,laddr=MACHINE_TAILSCALE_IP,lport=${target.port}'",
        'sudo systemctl reload ssh',
      ],
      verification:
          'Replace PHONE_TAILSCALE_IP and MACHINE_TAILSCALE_IP with the actual numeric addresses. The app checks the effective sshd -T settings for the connection: local TCP forwarding allowed; reverse and Unix-socket forwarding denied; tunnel devices denied; UseDNS no; DisableForwarding no; public-key login enabled; standard authorized_keys path. Existing earlier directives can override the drop-in. The owner must verify the reload and keep rescue access; parsing settings does not prove the running daemon loaded them.',
    ),
    const ByoHostSetupStep(
      id: 'identity',
      title: 'Verify the machine',
      copy:
          'Compare this fingerprint with the app before signing in. Use an independent session on the machine.',
      commands: ['ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256'],
      verification:
          'The app computes the SHA256 fingerprint of the offered Ed25519 host key and requires your confirmation. Later changes are blocked; there is no automatic replacement.',
    ),
  ]);
}

/// Optional one-time public-key enrollment for machines without password login.
/// The exact marker is removed/replaced by the installer and by revoke.
ByoHostSetupStep byoHostKeyEnrollment(
  String profileId,
  ByoHostIdentity identity,
) {
  if (!byoHostSafeId(profileId) ||
      identity.keyAlias != 'oc.byoHostSsh.$profileId' ||
      !RegExp(
        r'^ecdsa-sha2-nistp256 [A-Za-z0-9+/]+={0,2}$',
      ).hasMatch(identity.publicKey)) {
    throw const ByoHostFailure(ByoHostFailureCode.protocol);
  }
  return ByoHostSetupStep(
    id: 'phoneKey',
    title: 'Allow this phone to sign in',
    copy:
        'Run this as the dedicated machine user if password sign-in is unavailable. This is a public key. Setup will restrict it to the private connection.',
    commands: [
      "install -d -m 700 ~/.ssh\ntouch ~/.ssh/authorized_keys\nchmod 600 ~/.ssh/authorized_keys\nprintf '%s\\n' '${identity.publicKey} oc-byo-$profileId' >> ~/.ssh/authorized_keys",
    ],
    verification:
        'Connect with the native signer and the independently pinned SSH host key. The installer replaces this exact marker with a forwarding-only key; do not install this key with a different comment.',
  );
}
