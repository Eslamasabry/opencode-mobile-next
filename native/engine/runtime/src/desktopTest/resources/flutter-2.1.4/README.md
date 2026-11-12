Released Flutter twins (OpenCode Mobile 2.1.4+62, `origin/master` 2dec68a250fec08909593fd3b1a8125de908f0c2),
copied verbatim with a `.txt` suffix so Dart tooling never analyzes them (the same pattern as `:engine:agents` and
`:engine:setupscripts` `resources/flutter-2.1.4`).

`VerbatimScriptsTest` compares the engine's install preludes and the in-Ubuntu setup text with these copies through
`Sh.releasedText`. CI checks out a single commit, so the release is not in its git history; where it is, the copy must
equal `git show 2dec68a250fec08909593fd3b1a8125de908f0c2:<path>`. Refresh them from the released tag when the engine
follows a later release: `git show <release>:<path> > <path>.txt`.
