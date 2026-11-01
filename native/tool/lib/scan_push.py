"""Scanner behind native/tool/scan_push.sh (plan 15 §2.1.1 step 6). Standard library only; run with python3 -I.

Input on stdin:
  --commits  the NUL-delimited stream scan_push.sh writes (metadata + `git log -p` patch per commit)
  --patch    a plain unified diff
Prints one masked line per hit and exits 1 on any hit, 0 when clean.
"""

import fnmatch
import ipaddress
import os
import re
import sys

NOREPLY = re.compile(r"(@users\.noreply\.github\.com|^noreply@github\.com|^noreply@anthropic\.com)$", re.I)

TOKEN_RULES = [
    ("private-key", re.compile(r"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY(?: BLOCK)?-----")),
    ("aws-key", re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b")),
    ("github-token", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{50,})\b")),
    ("slack-token", re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}")),
    ("google-api-key", re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b")),
    ("anthropic-key", re.compile(r"\bsk-ant-[A-Za-z0-9_\-]{20,}")),
    ("openai-key", re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9]{32,}\b")),
    ("jwt", re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}")),
]
URL_CRED = re.compile(r"\b[a-z][a-z0-9+.-]*://([^/\s:@'\"]+):([^/\s@'\"]+)@")
ASSIGN = re.compile(
    r"(?i)\b(?:password|passwd|secret|api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|private[_-]?key)"
    r"[\"']?\s*[:=]\s*[\"']([^\"'\s]{12,})[\"']"
)
PLACEHOLDER = re.compile(
    r"(?i)example|fake|dummy|test|sample|placeholder|changeme|change-me|redacted|secret|password|xxxx|\*\*\*|\$\{|\$\(|<|%s|\{\{"
)
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}")
PERSONAL_DOMAINS = re.compile(
    r"@(?:gmail|googlemail|outlook|hotmail|live|yahoo|icloud|me|proton|protonmail|pm)\.(?:com|me|net|co\.uk)$", re.I
)
IPV4 = re.compile(r"(?<![\w.])(\d{1,3}(?:\.\d{1,3}){3})(?![\w]|\.\d)(/\d{1,2})?")
IPV6 = re.compile(r"(?<![\w:])((?:f[cd][0-9a-f]{2}|fe[89ab][0-9a-f]):[0-9a-f:]*:[0-9a-f:]*)(/\d{1,3})?", re.I)
# Tailnet and home.arpa names are private anywhere; .lan and .internal only as a URL or user@ host, because
# "x.y.internal" is also a Kotlin package path.
PRIVATE_HOST = re.compile(
    r"\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:ts\.net|home\.arpa)\b"
    r"|(?:://|@)[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:lan|internal)(?![\w.-])",
    re.I,
)
CGNAT = ipaddress.ip_network("100.64.0.0/10")
BAD_FILE = re.compile(
    r"(?:^|/)(?:[^/]+\.(?:jks|keystore|p12|pfx|key)|key\.properties|local\.properties|google-services\.json|\.env|"
    r"id_rsa|id_ed25519|id_ecdsa)$"
)


def load_allow():
    """Two line forms: "<rule> <exact value>" and "@path <fnmatch glob> <rule or *>" (a reviewed fixture file)."""
    allow, paths = set(), []
    path = os.environ.get("OC_SCAN_ALLOW_FILE", "")
    if path and os.path.isfile(path):
        for line in open(path, encoding="utf-8"):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            if line.startswith("@path "):
                parts = line.split()
                if len(parts) == 3:
                    paths.append((parts[1], parts[2]))
                continue
            rule, _, value = line.partition(" ")
            allow.add((rule.strip(), value.strip()))
    return allow, paths


def load_extra():
    path = os.environ.get("OC_SCAN_EXTRA", "")
    out = []
    if path and os.path.isfile(path):
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if line.strip() and not line.lstrip().startswith("#"):
                out.append(re.compile(line))
    return out


ALLOW, ALLOW_PATHS = load_allow()
EXTRA = load_extra()
WATCH_EMAILS = {e.lower() for e in os.environ.get("OC_SCAN_EMAILS", "").split() if e}
own = os.environ.get("OC_SCAN_OWN_EMAIL", "").strip().lower()
if own and not NOREPLY.search(own):
    WATCH_EMAILS.add(own)


def mask(value, rule):
    if rule.startswith("email"):
        local, _, domain = value.partition("@")
        return local[:2] + "…@" + domain
    if rule in ("private-ip", "private-ipv6"):
        sep = "." if "." in value else ":"
        return value.split(sep)[0] + sep + "…"
    if rule == "private-host":
        return "…" + value[-12:]
    return value[:4] + "…(" + str(len(value)) + " chars)"


def private_ip(text):
    for m in IPV4.finditer(text):
        if m.group(2):  # CIDR notation names a range, not a host.
            continue
        try:
            ip = ipaddress.IPv4Address(m.group(1))
        except ValueError:
            continue
        s = str(ip)
        if s.endswith(".0") or s.endswith(".255"):
            continue  # a network or broadcast address names a range boundary, not a host
        # RFC 1918, link-local and the tailnet range; not loopback, documentation or benchmark ranges.
        if (
            ip in ipaddress.ip_network("10.0.0.0/8")
            or ip in ipaddress.ip_network("172.16.0.0/12")
            or ip in ipaddress.ip_network("192.168.0.0/16")
            or ip in ipaddress.ip_network("169.254.0.0/16")
            or ip in CGNAT
        ):
            yield "private-ip", s
    for m in IPV6.finditer(text):
        if m.group(2):
            continue
        try:
            ipaddress.IPv6Address(m.group(1))
        except ValueError:
            continue
        yield "private-ipv6", m.group(1)


def scan_text(text):
    for rule, rx in TOKEN_RULES:
        for m in rx.finditer(text):
            yield rule, m.group(0)
    for m in URL_CRED.finditer(text):
        if not PLACEHOLDER.search(m.group(2)) and m.group(2) not in ("pass", "pw", "token"):
            yield "url-credential", m.group(1) + ":" + m.group(2)
    for m in ASSIGN.finditer(text):
        v = m.group(1)
        if not PLACEHOLDER.search(v) and re.search(r"\d", v) and re.search(r"[A-Za-z]", v):
            yield "secret-assignment", v
    for m in EMAIL.finditer(text):
        e = m.group(0)
        if e.lower() in WATCH_EMAILS:
            yield "email-owner", e
        elif PERSONAL_DOMAINS.search(e) and not NOREPLY.search(e):
            yield "email-personal", e
    yield from private_ip(text)
    for m in PRIVATE_HOST.finditer(text):
        yield "private-host", m.group(0)
    for rx in EXTRA:
        for m in rx.finditer(text):
            yield "extra-pattern", m.group(0)


hits = 0


def report(where, rule, value, path=None):
    global hits
    if (rule, value) in ALLOW:
        return
    if path and any(fnmatch.fnmatchcase(path, g) and r in ("*", rule) for g, r in ALLOW_PATHS):
        return
    hits += 1
    print("HIT %-17s %s  %s" % (rule, where, mask(value, rule)))


def scan_patch(lines, label):
    path, lineno, cols = "?", 0, 1
    for raw in lines:
        if raw.startswith("diff --git ") or raw.startswith("diff --cc "):
            cols = 2 if raw.startswith("diff --cc ") else 1
            path = raw.split(" b/", 1)[-1] if " b/" in raw else raw.split()[-1]
            lineno = 0
            continue
        if raw.startswith("+++ "):
            p = raw[4:]
            if p != "/dev/null":
                path = p[2:] if p.startswith("b/") else p
                if BAD_FILE.search(path):
                    report("%s%s" % (label, path), "forbidden-file", path)
            continue
        if raw.startswith("--- ") or raw.startswith("index ") or raw.startswith("new file") or raw.startswith("deleted file"):
            continue
        if raw.startswith("@@"):
            m = re.search(r" \+(\d+)", raw)
            lineno = int(m.group(1)) - 1 if m else 0
            continue
        if raw.startswith("Binary files ") and " and b/" in raw:
            p = raw.rsplit(" and b/", 1)[1].rsplit(" differ", 1)[0]
            if BAD_FILE.search(p):
                report("%s%s" % (label, p), "forbidden-file", p)
            continue
        prefix, body = raw[:cols], raw[cols:]
        if "-" in prefix:
            continue  # a removed line is not published by this commit
        if "+" in prefix:
            lineno += 1
            for rule, value in scan_text(body):
                report("%s%s:%d" % (label, path, lineno), rule, value, path)
        elif prefix.strip() == "":
            lineno += 1


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "--patch"
    data = sys.stdin.buffer.read().decode("utf-8", "replace")
    commits = 0
    if mode == "--patch":
        scan_patch(data.split("\n"), "")
    else:
        for chunk in data.split("\x00\x01")[1:]:
            commits += 1
            head, _, patch = chunk.partition("\x00\x02")
            sha, author, committer, message = (head.split("\x1f") + ["", "", "", ""])[:4]
            label = sha[:8] + " "
            for who, email in (("author", author), ("committer", committer)):
                if not NOREPLY.search(email.strip()):
                    report(label + who, "non-noreply-" + who, email.strip())
            for i, line in enumerate(message.split("\n"), 1):
                for rule, value in scan_text(line):
                    report("%smessage:%d" % (label, i), rule, value)
            scan_patch(patch.split("\n"), label)
    scope = ("%d commit(s)" % commits) if mode == "--commits" else "the patch"
    if hits:
        print("scan_push: %d hit(s) in %s; push blocked (allow-list: native/tool/scan_push.allow)" % (hits, scope))
        return 1
    print("scan_push: clean, %s scanned" % scope)
    return 0


sys.exit(main())
