#!/usr/bin/env python3
"""Fetch one agent download and print what a pin needs. It NEVER executes anything it downloads.

Run as `python3 -I native/tools/agent-pins/agent_artifact.py --workdir <new empty folder> --url URL
[--format executable|tarGz|npmTarGz] [--member NAME] [--expect-sha256 HEX] [--expect-sha512-sri SRI]`.

Prints JSON: sha256 and size of the downloaded bytes (downloadBytes), and for a tar.gz the member's uncompressed size
(installedBytes for tarGz; for npm packages the registry's unpackedSize is the pin's number, read separately) and the
member's ELF machine read from its first bytes (183 = arm64, 62 = x86-64). The download is kept in the work folder
only; the archive is read in memory (no extraction, so no path or link can be followed).
Cross-checks: --expect-sha256 for a GitHub asset digest, --expect-sha512-sri for an npm tarball's dist.integrity.
"""
import argparse, base64, hashlib, json, os, sys, tarfile, urllib.request


def elf_machine(head):
    if head[:4] != b"\x7fELF":
        return None
    return int.from_bytes(head[18:20], "little")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--workdir", required=True)
    ap.add_argument("--url", required=True)
    ap.add_argument("--member")
    ap.add_argument("--expect-sha256")
    ap.add_argument("--expect-sha512-sri")
    a = ap.parse_args()
    os.makedirs(a.workdir)  # must be new
    path = os.path.join(a.workdir, "download")
    s256, s512, size = hashlib.sha256(), hashlib.sha512(), 0
    req = urllib.request.Request(a.url)
    with urllib.request.urlopen(req) as r, open(path, "wb") as f:
        while True:
            b = r.read(1 << 20)
            if not b:
                break
            s256.update(b); s512.update(b); size += len(b); f.write(b)
    out = {"url": a.url, "sha256": s256.hexdigest(), "downloadBytes": size}
    if a.expect_sha256 and a.expect_sha256 != out["sha256"]:
        sys.exit("STOP: sha256 differs from the source's own digest")
    sri = "sha512-" + base64.b64encode(s512.digest()).decode()
    out["sha512Sri"] = sri
    if a.expect_sha512_sri and a.expect_sha512_sri != sri:
        sys.exit("STOP: sha512 differs from the registry's integrity")
    with open(path, "rb") as f:
        head = f.read(4)
    if head[:2] == b"\x1f\x8b" and a.member:
        with tarfile.open(path) as t:
            names = [m.name for m in t.getmembers()]
            m = t.getmember(a.member) if a.member in names else None
            if m is None:
                sys.exit(f"STOP: member {a.member} not in archive; first entries {names[:8]}")
            if not m.isfile():
                sys.exit("STOP: member is not a regular file")
            out["memberBytes"] = m.size
            out["elfMachine"] = elf_machine(t.extractfile(m).read(32))
    else:
        with open(path, "rb") as f:
            out["elfMachine"] = elf_machine(f.read(32))
    print(json.dumps(out))


if __name__ == "__main__":
    main()
