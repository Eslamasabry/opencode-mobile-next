#!/usr/bin/env python3
"""P-N4 route diff: compare two recorded OpenCode 2 openapi.json files.

Usage: python3 -I route_diff.py OLD.json NEW.json  (prints Markdown)
Reads only the two files; no network.
"""
import json
import sys

METHODS = ("get", "post", "put", "patch", "delete")


def ops(spec):
    out = {}
    for path, item in spec["paths"].items():
        for m in METHODS:
            if m in item:
                out[f"{m.upper()} {path}"] = item[m]
    return out


def main(old_path, new_path):
    old, new = (json.load(open(p)) for p in (old_path, new_path))
    a, b = ops(old), ops(new)
    sa = old.get("components", {}).get("schemas", {})
    sb = new.get("components", {}).get("schemas", {})
    print(f"Operations: {len(a)} -> {len(b)}; schemas: {len(sa)} -> {len(sb)}\n")
    print("### Added operations\n")
    for k in sorted(set(b) - set(a)):
        print(f"- `{k}`")
    print("\n### Removed operations\n")
    removed = sorted(set(a) - set(b))
    print("\n".join(f"- `{k}`" for k in removed) or "- none")
    print("\n### Changed operations (same method + path, different definition)\n")
    changed = [k for k in sorted(set(a) & set(b))
               if json.dumps(a[k], sort_keys=True) != json.dumps(b[k], sort_keys=True)]
    print("\n".join(f"- `{k}`" for k in changed) or "- none")
    print("\n### Removed schemas\n")
    print("\n".join(f"- `{k}`" for k in sorted(set(sa) - set(sb))) or "- none")
    print("\n### Changed schemas\n")
    ch = [k for k in sorted(set(sa) & set(sb))
          if json.dumps(sa[k], sort_keys=True) != json.dumps(sb[k], sort_keys=True)]
    print("\n".join(f"- `{k}`" for k in ch) or "- none")
    print(f"\n### Added schemas ({len(set(sb) - set(sa))})\n")
    print(", ".join(f"`{k}`" for k in sorted(set(sb) - set(sa))))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
