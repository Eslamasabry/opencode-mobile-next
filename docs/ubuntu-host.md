# Running OpenCode on an Ubuntu host for this app

The app supports OpenCode 2. On a computer with Node.js and npm installed,
install the pinned official CLI and start a loopback-only server:

```sh
npm install -g @opencode/cli@2.1.1 &&
opencode2 serve --hostname 127.0.0.1 --port 4096
```

The app's Ubuntu host setup shows this same command with the chosen port.
Keep this terminal open while using the server. The server prints a per-run
password; pairing below transfers it to the app. This command does not install
a background service.

For a different port, change `--port 4096` in the command. Keep
`--hostname 127.0.0.1` and use a tunnel; the server can run commands as your
computer account.

## Existing OpenCode 1 installations

OpenCode 1 is no longer supported. Preserve your existing data before upgrading
with the official OpenCode 2 CLI. The repository's Ubuntu setup script refuses
to modify an existing OpenCode 1 installation. Its OpenCode 2 service installer
has not been published as a reviewed downloadable script, so the app does not
link to a historical installer.

## Updating OpenCode 2

Stop your server, install the pinned version with the npm command above, and
start it again. Pair again if its password changed.

## Connecting the phone

The server is loopback-only and the app refuses plain HTTP to anything but
the device's own loopback, so the phone reaches it through a tunnel that
ends at `127.0.0.1` on the phone.

### 1. Bring the server within reach

Do this first. Pairing hands the app an address and a password; it cannot
conjure a route to a server the phone cannot reach.

**USB (simplest).** With the phone plugged in and USB debugging on:

```sh
adb reverse tcp:4096 tcp:4096
```

The phone can then reach the server at `http://127.0.0.1:4096`.

**SSH.** Any SSH client on the phone that forwards a local port works;
forward phone-local `4096` to `127.0.0.1:4096` on this machine, which gives
the same `http://127.0.0.1:4096`.

**Tailscale Serve or another HTTPS reverse proxy.** Terminate TLS in front
of the server and use the `https://` address. A plain
`http://<tailscale-name>:4096` will be rejected by the app — it is
unencrypted, and the password would cross the network in clear text.

**Binding to the network directly is an advanced path.** It requires
`OPENCODE_ALLOW_REMOTE_BIND=1`, and you should only take it behind TLS.

### 2. Pair (OpenCode 2 servers)

Don't copy the password by hand:

```sh
opencode2 pair
```

It prints the server's addresses, the username (always `opencode`), and the
current serve password, plus a QR encoding all three. In the app's server
editor, either tap **Scan** and point the camera at that QR (Android), or
copy the printed code and tap **Paste pairing code** (anywhere). Either way
the app fills the address, username and password in one step, tries each
address the code carries, and names the one it connected to.

The code contains the serve password, so treat it like the password itself:
it is as good as shell access to this machine. It also goes stale — the
password rotates on every restart unless `OPENCODE_SERVER_PASSWORD` is set,
and a stale code produces "Password rejected" rather than a mystery failure.

`opencode2 pair` reports `http://127.0.0.1:4096` while the service is
loopback-only, which is exactly the address to use once the tunnel above is
up. If you have run `opencode service set hostname 0.0.0.0`, the code will
also carry a plain-HTTP LAN address; the app lists that one as skipped and
says why, because sending the serve password across a network in the clear
is not something it will do. Put TLS in front of it instead.

## Existing systemd services

If you already manage OpenCode 2 through a systemd user service, keep using
that service. The CLI setup above does not change its files or data. To stop
that service, use `systemctl --user stop opencode-serve`.
