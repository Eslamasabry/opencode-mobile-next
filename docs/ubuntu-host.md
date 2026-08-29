# Running OpenCode on an Ubuntu host for this app

The phone app talks to any reachable OpenCode server. This guide sets one up
on Ubuntu (or any systemd Linux) as a per-user service, so it keeps running
after you close the terminal and comes back after reboots — no root required.

The app cannot run commands on your computer. Settings → **Ubuntu host
management** shows these same commands with copy buttons, filled in with your
server's actual port.

## One-time setup (on the Ubuntu machine)

```sh
curl -fsSL https://raw.githubusercontent.com/Eslamasabry/oc_app/production/android-release-hardening/scripts/host/ubuntu-opencode.sh -o ubuntu-opencode.sh
bash ubuntu-opencode.sh install
```

This installs OpenCode with the official installer if it is missing, writes a
`systemd --user` unit that runs `opencode serve --hostname 0.0.0.0 --port
4096`, enables it, and starts it. Re-running `install` is safe; it refreshes
the unit in place.

Want a different port or bind address?

```sh
OPENCODE_PORT=5000 OPENCODE_HOSTNAME=0.0.0.0 bash ubuntu-opencode.sh install
```

### Keep it running after logout

```sh
loginctl enable-linger "$USER"
```

Without linger, systemd stops user services when your last session ends.

### Firewall

If `ufw` is active, allow the port (ideally restricted to your LAN):

```sh
sudo ufw allow 4096/tcp
```

## Day-to-day

```sh
bash ubuntu-opencode.sh status    # service state + listening check
bash ubuntu-opencode.sh restart   # restart the server process
bash ubuntu-opencode.sh logs      # follow the server log (Ctrl-C to stop)
bash ubuntu-opencode.sh update    # upgrade OpenCode, refresh unit, restart
```

The app's Settings screen remains the primary upgrade path when the server
itself reports an available update; `update` here is the host-side
equivalent for servers that don't.

## Connecting the phone

Use `http://<the-machine's-LAN-IP>:4096` in the app's server profile. Find
the IP with `ip -4 addr show scope global`. For networks where the phone
cannot reach the machine directly, any TCP tunnel (Tailscale, WireGuard,
`adb reverse` for emulators) works — the app only needs the URL.

## Uninstall

```sh
systemctl --user disable --now opencode-serve
rm ~/.config/systemd/user/opencode-serve.service
systemctl --user daemon-reload
```
