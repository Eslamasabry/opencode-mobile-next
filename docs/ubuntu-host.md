# Running OpenCode on an Ubuntu host for this app

The phone app talks to any reachable OpenCode server. This guide sets one up
on Ubuntu (or any systemd Linux) as a per-user service, so it keeps running
after you close the terminal and comes back after reboots — no root required.

The app cannot run commands on your computer. Settings → **Ubuntu host
management** shows these same commands with copy buttons, filled in with your
server's actual port.

## One-time setup (on the Ubuntu machine)

```sh
curl -fsSL https://raw.githubusercontent.com/Eslamasabry/oc_app/master/scripts/host/ubuntu-opencode.sh -o ubuntu-opencode.sh
bash ubuntu-opencode.sh install
```

This installs OpenCode with the official installer if it is missing, writes a
`systemd --user` unit that runs `opencode serve --hostname 127.0.0.1 --port
4096`, enables it, and starts it. Re-running `install` is safe; it refreshes
the unit in place.

Want a different port or bind address?

```sh
OPENCODE_PORT=5000 OPENCODE_HOSTNAME=127.0.0.1 bash ubuntu-opencode.sh install
```

### Keep it running after logout

```sh
loginctl enable-linger "$USER"
```

Without linger, systemd stops user services when your last session ends.

### Firewall

Leave it closed. The service listens on `127.0.0.1` only, so opening a
port does nothing for you and everything for anyone else on the network:
an OpenCode server runs shell commands as your user.

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

The server is loopback-only and the app refuses plain HTTP to anything but
the device's own loopback, so the phone reaches it through a tunnel that
ends at `127.0.0.1` on the phone. Read the password first:

```sh
bash ubuntu-opencode.sh password
```

**USB (simplest).** With the phone plugged in and USB debugging on:

```sh
adb reverse tcp:4096 tcp:4096
```

Then use `http://127.0.0.1:4096` in the app.

**SSH.** Any SSH client on the phone that forwards a local port works;
forward phone-local `4096` to `127.0.0.1:4096` on this machine, then use
`http://127.0.0.1:4096`.

**Tailscale Serve or another HTTPS reverse proxy.** Terminate TLS in front
of the server and use the `https://` address. A plain
`http://<tailscale-name>:4096` will be rejected by the app — it is
unencrypted, and the password would cross the network in clear text.

**Binding to the network directly is an advanced path.** It requires
`OPENCODE_ALLOW_REMOTE_BIND=1`, and you should only take it behind TLS.

## Uninstall

```sh
systemctl --user disable --now opencode-serve
rm ~/.config/systemd/user/opencode-serve.service
systemctl --user daemon-reload
```
