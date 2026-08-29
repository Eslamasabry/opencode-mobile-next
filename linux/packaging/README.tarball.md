# OpenCode for Linux — @VERSION@ (x86_64)

**Experimental.** The Linux build shares all of its Dart code with the
Android app and is used daily by its authors, but it has had far less
testing, and it is not signed or notarised in any way. Treat it as a
preview.

## What's in here

```
lib/opencode/            the application (binary, Flutter assets, plugins)
share/applications/      the .desktop entry
share/icons/hicolor/     icons, 16px to 512px plus scalable SVG
share/metainfo/          AppStream metadata for GNOME Software / Discover
install.sh               installs the above
uninstall.sh             removes it again
LICENSE                  MIT
```

## Install

```sh
./install.sh                  # into ~/.local, no root needed
sudo ./install.sh             # into /usr/local, system-wide
./install.sh --prefix /opt/oc # anywhere else
```

Afterwards `opencode` is on your PATH (if `<prefix>/bin` is) and OpenCode
appears in your applications menu with its own icon.

To uninstall: `./uninstall.sh --prefix <the prefix you used>`.

You can also skip the installer entirely and just run the binary in place:

```sh
./lib/opencode/@BINARY@
```

It resolves its assets relative to itself, so the whole `lib/opencode`
directory has to stay together.

## Runtime requirements

- GTK 3 (`libgtk-3-0`), glib, and the usual X11/Wayland client libraries —
  present on every mainstream desktop install.
- `libsecret-1-0` **and a running keyring daemon** (gnome-keyring,
  KWallet with the libsecret bridge, or similar). Server passwords and
  tokens are stored there; without it, saved credentials will not persist.
- `zenity` or `qarma` for the file picker dialogs.

If the app starts but cannot save a server password, the keyring is the
first thing to check.

## Multi-monitor and window size

OpenCode remembers where you left its window — position, size, and whether
it was maximised — and restores it on the next launch. If that window was
on a monitor you have since unplugged, it comes back centred on a display
that still exists rather than opening off-screen.

## What is not here

- **Termux hosting.** Android-only; the setup screens do nothing on desktop.
- **Code-push updates.** Shorebird patches Android only. The desktop build
  checks the GitHub releases feed instead and points you at the release
  page — nothing downloads or installs itself.
- **Background-live notifications.** Android foreground-service feature;
  inert here.

## Updating

Download the newer tarball and run `./install.sh` again with the same
prefix. It replaces the runtime directory wholesale, so no stale plugin
libraries are left behind.

## Reporting problems

https://github.com/Eslamasabry/oc_app/issues — please say which
distribution, which desktop, and X11 or Wayland.
