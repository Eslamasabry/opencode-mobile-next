Released Flutter twins (OpenCode Mobile 2.1.4+62, `origin/master` 2dec68a250fec08909593fd3b1a8125de908f0c2),
copied verbatim with a `.txt` suffix so Dart tooling never analyzes them.

The repository's own `lib/` on the native branches is an older snapshot that predates the #117 setup fix
(app download/unpack when curl or tar crash). The engine's scripts follow the released app, so the parity
tests for those scripts read these copies. Refresh them from the released tag when the app ships a change to
these files: `git show <release>:lib/builtin/setup/setup_scripts.dart > .../setup_scripts.dart.txt`.
