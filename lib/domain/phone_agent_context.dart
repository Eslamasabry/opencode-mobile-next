/// What an agent running on the person's phone is told about where it is.
///
/// OpenCode loads a global `AGENTS.md` into every conversation. Without it an
/// agent on the phone assumed a desktop: it sent the person to "the OpenCode
/// desktop app" and said "no phone ADB connection needed" (owner's phone,
/// 2026-09-24). The phone's server (the built-in Ubuntu) writes this
/// block into the global file of both OpenCode generations before each
/// start. Only the text between the markers is ours; anything the person
/// wrote in the file stays.
abstract final class PhoneAgentContext {
  static const begin = '<!-- opencode-mobile:phone-context:begin -->';
  static const end = '<!-- opencode-mobile:phone-context:end -->';

  /// OpenCode 1 reads `~/.config/opencode/AGENTS.md`; OpenCode 2 runs with
  /// its config isolated under `/root/.oc-opencode2/config` (see
  /// BuiltinLinux.serverScript).
  static const files = [
    '/root/.config/opencode/AGENTS.md',
    '/root/.oc-opencode2/config/opencode/AGENTS.md',
  ];

  static const builtinHost = "the OpenCode Mobile app's built-in Ubuntu";

  static const _intro =
      '## Where you are running\n'
      '\n'
      'Written by OpenCode Mobile; the text between these markers is '
      'replaced on each server start.\n'
      '\n'
      "- You run on the person's Android phone, inside ";

  static const _rest =
      '. The person talks to you through the OpenCode Mobile app on this '
      'same phone. There is no desktop app, no display, no window you can '
      'open, and no adb connection to this phone from here.\n'
      '- Projects live in /root/projects. The person sees your replies, '
      'changes and files in the app.\n'
      '- A server you start on 127.0.0.1:PORT opens in the phone\'s own '
      'browser at http://127.0.0.1:PORT; give the person that link to try a '
      'web app. Bind servers to 127.0.0.1 only, never to every network '
      'interface, unless the person asks for more.\n'
      '- There is no visible browser here. Headless browsers (Playwright and '
      'similar) work but are slow and use a lot of memory: prefer tests, '
      'curl and logs, and close any browser you start as soon as you are '
      'done.\n'
      "- The phone's memory and CPU are limited and shared with the person's "
      'apps. Android stops background processes when an app runs more than '
      'about 32 of them, and can close this whole environment when memory '
      'runs low. Avoid many parallel processes, file watchers and leftover '
      'servers; stop what you start once it is not needed.\n'
      '- Long builds (Gradle, the Android SDK, large installs) are slow here '
      'and may be stopped by Android: say so before starting one.\n';

  /// The block for a server hosted in [host] ([builtinHost]).
  static String block(String host) => '$begin\n$_intro$host$_rest$end\n';

  static const _builtinBlock = '$begin\n$_intro$builtinHost$_rest$end\n';

  /// POSIX sh, run inside the Ubuntu: writes the block into every file in
  /// [files], replacing an earlier block and keeping everything else. Set
  /// `OC_CTX_ROOT` to write under another root (tests).
  static const builtinScript =
      '${_scriptHead}cat <<\'OC_CTX_BODY\'\n${_builtinBlock}OC_CTX_BODY\n'
      '$_scriptTail';

  static const _scriptHead = 'oc_ctx_body() {\n';

  static const _scriptTail =
      '}\n'
      'oc_ctx() {\n'
      '  f="\${OC_CTX_ROOT:-}\$1"\n'
      '  mkdir -p "\$(dirname "\$f")"\n'
      '  tmp="\$f.oc-tmp.\$\$"\n'
      '  if [ -f "\$f" ]; then\n'
      '    awk -v b=\'$begin\' -v e=\'$end\' \'\n'
      '      \$0 == b { skip = 1; next }\n'
      '      \$0 == e { skip = 0; next }\n'
      '      skip { next }\n'
      '      \$0 == "" { if (seen) blank++; next }\n'
      '      { while (blank > 0) { print ""; blank-- } print; seen = 1 }\n'
      '    \' "\$f" > "\$tmp"\n'
      '  else\n'
      '    : > "\$tmp"\n'
      '  fi\n'
      '  if [ -s "\$tmp" ]; then printf \'\\n\' >> "\$tmp"; fi\n'
      '  oc_ctx_body >> "\$tmp"\n'
      '  mv "\$tmp" "\$f"\n'
      '}\n'
      'oc_ctx /root/.config/opencode/AGENTS.md\n'
      'oc_ctx /root/.oc-opencode2/config/opencode/AGENTS.md\n';
}
