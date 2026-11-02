package io.github.eslamasabry.oc.engine.agents.claudeagent

// GENERATED from lib/builtin/claude_agent_link.dart `ClaudeAgentLink.script` (the Dart template, byte for byte).
// In the template `§` stands for a literal dollar sign, ⟨HOME⟩ and ⟨MARKER⟩ for the two Dart interpolations.
// ClaudeAgentLinkScriptParityTest renders the Dart source independently and compares.
internal const val CLAUDE_AGENT_LINK_TEMPLATE: String = """
set -u
home='⟨HOME⟩'
claude=/home/oc/.local/bin/claude
shim=/usr/local/sbin/claude
if [ ! -x "§claude" ] || [ ! -d "§home" ]; then
  echo oc-claude-agent:not-installed
  exit 0
fi
if [ -e "§shim" ] && ! grep -qx '⟨MARKER⟩' "§shim" 2>/dev/null; then
  echo oc-claude-agent:blocked
  exit 0
fi
mkdir -p /usr/local/sbin || { echo oc-claude-agent:failed; exit 0; }
cat > "§shim.new" <<'OC_SHIM' || { rm -f "§shim.new"; echo oc-claude-agent:failed; exit 0; }
#!/bin/sh
⟨MARKER⟩
# OpenCode's Claude Agent on this phone finds this first on root's PATH.
# Typed in the Terminal, the launcher in /usr/local/bin runs Claude as oc.
if [ -t 0 ] && [ -x /usr/local/bin/claude ]; then exec /usr/local/bin/claude "§@"; fi
export CLAUDE_CONFIG_DIR='⟨HOME⟩'
export DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
exec /home/oc/.local/bin/claude "§@"
OC_SHIM
chmod 755 "§shim.new" && mv -f "§shim.new" "§shim" || { rm -f "§shim.new"; echo oc-claude-agent:failed; exit 0; }
link=/root/.claude
if [ -L "§link" ]; then
  ln -sfn "§home" "§link.new" && mv -Tf "§link.new" "§link" || { rm -f "§link.new"; echo oc-claude-agent:failed; exit 0; }
elif [ -d "§link" ] && [ -z "§(ls -A "§link" 2>/dev/null)" ]; then
  rmdir "§link" && ln -s "§home" "§link" || { echo oc-claude-agent:failed; exit 0; }
elif [ -e "§link" ]; then
  echo oc-claude-agent:linked-without-resume
  exit 0
else
  ln -s "§home" "§link" || { echo oc-claude-agent:failed; exit 0; }
fi
echo oc-claude-agent:linked
"""
