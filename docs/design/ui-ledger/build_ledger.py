#!/usr/bin/env python3
"""Merge docs/design/ui-ledger/parts/*.json into ledger.json, pages.md and
navigation.md, and print the statistics used by findings.md.

Usage (from the repository root):

    python3 docs/design/ui-ledger/build_ledger.py            # rebuild outputs
    python3 docs/design/ui-ledger/build_ledger.py --stats    # also print stats

The part files are the hand/agent-authored source; this script only resolves
cross-references, derives inbound edges and renders the Markdown views.
"""
from __future__ import annotations

import collections
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
PARTS = os.path.join(HERE, "parts")

KINDS = {"screen", "sheet", "dialog", "tab", "overlay", "onboarding-step"}
TYPES = {
    "button", "icon-button", "fab", "list-tile", "menu-item", "chip", "toggle",
    "text-field", "slider", "dropdown", "tab", "link", "gesture", "card", "banner",
}
ACTIONS = {
    "navigate", "open-sheet", "open-dialog", "mutate", "toggle-setting", "submit",
    "copy", "external-link", "dismiss", "other",
}
OPEN_ACTIONS = {"navigate", "open-sheet", "open-dialog"}

# part file -> area shown in pages.md / navigation.md
AREA_OF_PART = {
    "a-shell": "shell",
    "b1-chat-screen": "chat",
    "b2-chat-screen": "chat",
    "c-chat-compose": "chat",
    "d-chat-sheets": "chat",
    "e-workspace": "workspace",
    "f-files-review-terminal": "files",
    "g-servers": "servers",
    "h-termux": "termux",
    "i1-team-core": "team",
    "i2-team-sheets": "team",
    "j1-settings-more": "settings",
    "j2-library": "settings",
    "k-session-misc": "chat",
    "l-chats": "workspace",
}
AREA_TITLES = collections.OrderedDict([
    ("shell", "Shell and navigation"),
    ("chat", "Chat"),
    ("workspace", "Workspace and projects"),
    ("files", "Files, review and terminal"),
    ("servers", "Servers and connection"),
    ("termux", "Termux / on-device setup"),
    ("team", "AI Team / orchestration"),
    ("settings", "Settings / More"),
    ("onboarding", "Onboarding"),
    ("misc", "Misc dialogs and sheets"),
])


def load_json(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def slug(text):
    return re.sub(r"[^a-z0-9]+", "-", str(text).lower()).strip("-") or "x"


def main():
    overrides = {}
    opath = os.path.join(PARTS, "_overrides.json")
    if os.path.exists(opath):
        overrides = load_json(opath)
    widget_to_page = dict(overrides.get("widgetToPage", {}))
    page_area = overrides.get("pageArea", {})
    drop_targets = set(overrides.get("nullTargets", []))
    file_to_page = overrides.get("fileToPage", {})
    page_alias = overrides.get("pageAlias", {})      # merge page into another id (elements kept)
    drop_pages = overrides.get("dropPages", {})      # duplicate description: keep only its inbound edges
    wiring_prefixes = tuple(overrides.get("hostWiringPrefixes", []))
    element_file_by_prefix = overrides.get("elementFileByPrefix", {})
    renamed = {**page_alias, **drop_pages}

    problems = []
    pages = collections.OrderedDict()
    not_pages = []
    notes = []

    for name in sorted(os.listdir(PARTS)):
        if name.startswith("_") or not name.endswith(".json"):
            continue
        part = name[:-5]
        data = load_json(os.path.join(PARTS, name))
        for np_ in data.get("notPages", []):
            np_["part"] = part
            not_pages.append(np_)
        for n in data.get("notes", []):
            notes.append({"part": part, "note": n})
        for p in data.get("pages", []):
            if p["id"] in drop_pages:
                p["id"] = drop_pages[p["id"]]
                p["elements"] = []
                p["reachedFrom"] = [r for r in p.get("reachedFrom", []) if r.get("page") != "?"]
                p["presentedBy"] = []
                p["gates"] = []
            elif p["id"] in page_alias:
                p["id"] = page_alias[p["id"]]
            p.setdefault("elements", [])
            p.setdefault("reachedFrom", [])
            p.setdefault("gates", [])
            p.setdefault("presentedBy", [])
            p["area"] = page_area.get(p["id"], AREA_OF_PART.get(part, "misc"))
            for e in p["elements"]:
                e.setdefault("file", p.get("file"))
            if p["id"] in pages:  # same page described by two parts: merge
                q = pages[p["id"]]
                q["elements"].extend(p["elements"])
                q["reachedFrom"].extend(p["reachedFrom"])
                for g in p["gates"]:
                    if g not in q["gates"]:
                        q["gates"].append(g)
                for f in p["presentedBy"]:
                    if f not in q["presentedBy"]:
                        q["presentedBy"].append(f)
                for k in ("title", "purpose", "line", "widget", "file"):
                    if not q.get(k) and p.get(k):
                        q[k] = p[k]
            else:
                pages[p["id"]] = p

    # ---- normalise enums ---------------------------------------------------
    for p in pages.values():
        kind = p.get("kind")
        if kind == "menu":
            kind = "overlay"
        if kind not in KINDS:
            problems.append(f"page {p['id']}: unknown kind {kind!r} -> overlay")
            kind = "overlay"
        p["kind"] = kind
        if p["kind"] == "onboarding-step" and p["id"] not in page_area:
            pass  # stays in its feature area; onboarding area is assigned via overrides
        for e in p["elements"]:
            if e.get("type") not in TYPES:
                problems.append(f"element {e.get('id')}: unknown type {e.get('type')!r} -> button")
                e["type"] = "button"
            if e.get("action") not in ACTIONS:
                problems.append(f"element {e.get('id')}: unknown action {e.get('action')!r} -> other")
                e["action"] = "other"
            e.setdefault("label", "")
            for pref, f in element_file_by_prefix.items():
                if str(e.get("id", "")).startswith(pref):
                    e["file"] = f
            if wiring_prefixes and str(e.get("id", "")).startswith(wiring_prefixes):
                e["hostWiring"] = True
            if e.get("target") in renamed:
                e["target"] = renamed[e["target"]]
            e.setdefault("key", None)
            e.setdefault("target", None)
            e.setdefault("effect", "")
            e.setdefault("gates", [])
            e.setdefault("destructive", False)

    # ---- page field overrides and use-site line repair -------------------------
    for pid, fields in overrides.get("pageFields", {}).items():
        if pid in pages:
            pages[pid].update(fields)
    nlines = {}

    def line_count(f):
        if f not in nlines:
            try:
                with open(os.path.join(ROOT, f), encoding="utf-8", errors="replace") as fh:
                    nlines[f] = sum(1 for _ in fh)
            except OSError:
                nlines[f] = 0
        return nlines[f]

    for p in pages.values():
        for e in p["elements"]:
            f, ln = e.get("file"), e.get("line")
            if f and isinstance(ln, int) and f != p.get("file") and ln > line_count(f) and ln <= line_count(p.get("file")):
                # the agent recorded the widget's file but the host's use-site line
                e["widgetFile"] = f
                e["file"] = p["file"]
            elif f and isinstance(ln, int) and ln > line_count(f) > 0:
                problems.append(f"element {e.get('id')}: line {ln} outside {f}")

    # ---- unique element ids ------------------------------------------------
    seen = set()
    for p in pages.values():
        for e in p["elements"]:
            base = e.get("id") or f"{p['id']}-{slug(e.get('label'))}"
            eid, n = base, 1
            while eid in seen:
                n += 1
                eid = f"{base}-{n}"
            seen.add(eid)
            e["id"] = eid

    # ---- widget / function name -> page id ----------------------------------
    for p in pages.values():
        names = [p.get("widget")] + list(p.get("presentedBy") or [])
        for nme in names:
            if not nme:
                continue
            for variant in {nme, nme.lstrip("_"), nme.replace("State", "")}:
                widget_to_page.setdefault(variant, p["id"])
    primary_of_file = dict(file_to_page)
    for rank in (("screen", "tab"), ("overlay",), ("sheet", "dialog", "onboarding-step")):
        for p in pages.values():
            f = p.get("file")
            if f and f not in primary_of_file and p["kind"] in rank:
                primary_of_file[f] = p["id"]
    by_basename = {}
    for f, pid in primary_of_file.items():
        by_basename.setdefault(os.path.basename(f), pid)

    def resolve_many(text):
        """All page ids named in a free-text reference (class names, show* functions, file names)."""
        if not text:
            return []
        text = str(text).strip()
        if text in pages:
            return [text]
        if text in widget_to_page:
            return [widget_to_page[text]]
        found = []
        for tok in re.findall(r"[A-Za-z_][A-Za-z0-9_]*\.dart|[A-Za-z_][A-Za-z0-9_]*", text):
            pid = None
            if tok.endswith(".dart"):
                pid = by_basename.get(tok)
            else:
                for variant in (tok, tok.lstrip("_"), re.sub(r"State$", "", tok), re.sub(r"State$", "", tok.lstrip("_"))):
                    if variant in widget_to_page:
                        pid = widget_to_page[variant]
                        break
            if pid and pid not in found:
                found.append(pid)
        return found

    def resolve_widget(name):
        r = resolve_many(name)
        return r[0] if r else None

    # ---- embedded overlays -> hosts ------------------------------------------
    for p in pages.values():
        hosts = []
        for h in p.get("embeddedIn", []) or []:
            pids = [x for x in resolve_many(h) if x != p["id"]]
            hosts.extend(pids)
            if not pids:
                problems.append(f"page {p['id']}: embeddedIn host {h!r} unresolved")
        if "embeddedIn" in p:
            p["hostPages"] = sorted(set(hosts))

    # ---- resolve element targets ----------------------------------------------
    unresolved_targets = []
    for p in pages.values():
        for e in p["elements"]:
            tw = e.get("targetWidget")
            t = e.get("target")
            if e["id"] in drop_targets or (t and f"{p['id']}>{t}" in drop_targets):
                e["target"] = None
                continue
            if t in pages:
                continue
            r = resolve_widget(tw) or resolve_widget(t)
            if r:
                e["target"] = r
            elif t or (tw and e["action"] in OPEN_ACTIONS):
                unresolved_targets.append((p["id"], e["id"], t, tw, e.get("file"), e.get("line")))
                e["unresolvedTarget"] = t or tw
                e["target"] = None

    # ---- reachedFrom -------------------------------------------------------------
    system_elements = collections.OrderedDict()
    unresolved_inbound = []
    for p in pages.values():
        resolved = []
        for r in p["reachedFrom"]:
            src = renamed.get(r.get("page"), r.get("page"))
            if src == "system-entry":
                src = "system"
            if src == "system" and p["id"] == "system":
                continue
            if src == "system":
                trig = r.get("trigger") or "system trigger"
                eid = f"system-{slug(trig)[:60]}-to-{p['id']}"
                system_elements.setdefault(eid, {
                    "id": eid, "type": "gesture", "label": trig, "key": None,
                    "file": r.get("file") or "lib/main.dart", "line": r.get("line"),
                    "action": "navigate" if p["kind"] in ("screen", "tab") else (
                        "open-dialog" if p["kind"] == "dialog" else "open-sheet"),
                    "target": p["id"], "effect": trig, "gates": [], "destructive": False,
                })
                continue
            if src in pages:
                resolved.append({"page": src, "element": r.get("element")})
                continue
            cand = primary_of_file.get(r.get("file")) if r.get("file") in file_to_page else None
            cand = cand or resolve_widget(r.get("widget")) or primary_of_file.get(r.get("file"))
            if cand and cand != p["id"]:
                resolved.append({"page": cand, "element": r.get("element"),
                                 "evidence": f"{r.get('file')}:{r.get('line')}"})
            elif cand == p["id"]:
                continue
            else:
                unresolved_inbound.append((p["id"], r))
        p["reachedFrom"] = resolved

    covered = {e.get("target") for e in pages.get("system", {}).get("elements", [])}
    extra = [e for e in system_elements.values()
             if not (e["target"] in covered and "main.dart" in (e["label"] or ""))]
    if "system" in pages:
        pages["system"]["elements"].extend(extra)
        pages["system"]["reachedFrom"] = []
    elif system_elements:
        pages["system"] = {
            "id": "system", "title": "[System triggers]", "kind": "overlay",
            "file": "lib/main.dart", "widget": "OcApp", "line": 1, "area": "shell",
            "purpose": "Pseudo page holding non-UI entry points: notification taps, deep links, launch intents and automatic state-driven navigation.",
            "presentedBy": [], "reachedFrom": [], "gates": [],
            "elements": list(system_elements.values()),
        }

    # element ids per page for validation of reachedFrom.element
    el_page = {e["id"]: p["id"] for p in pages.values() for e in p["elements"]}

    # derive inbound edges from element targets
    edges = []
    for p in pages.values():
        for e in p["elements"]:
            if e.get("target"):
                edges.append({"from": p["id"], "element": e["id"], "to": e["target"]})
    for ed in edges:
        tgt = pages[ed["to"]]
        if not any(r["page"] == ed["from"] and r.get("element") == ed["element"] for r in tgt["reachedFrom"]):
            tgt["reachedFrom"].append({"page": ed["from"], "element": ed["element"]})
    have = {(ed["from"], ed["element"], ed["to"]) for ed in edges}
    for p in pages.values():
        for r in p["reachedFrom"]:
            el = r.get("element")
            if el and el_page.get(el) == r["page"] and (r["page"], el, p["id"]) not in have:
                edges.append({"from": r["page"], "element": el, "to": p["id"], "secondary": True})
                have.add((r["page"], el, p["id"]))
    import fnmatch
    for se in overrides.get("structuralEdges", []):
        for p in pages.values():
            if fnmatch.fnmatch(p["id"], se["to"]) and p["id"] != se["from"] and se["from"] in pages:
                if not any(r["page"] == se["from"] and r.get("via") == "state" for r in p["reachedFrom"]):
                    p["reachedFrom"].append({"page": se["from"], "element": None, "via": "state",
                                             "note": se.get("note", "shown automatically by the parent's state")})
    for p in pages.values():
        for h in p.get("hostPages", []):
            if not any(r["page"] == h and r.get("via") == "embedded" for r in p["reachedFrom"]):
                p["reachedFrom"].append({"page": h, "element": None, "via": "embedded"})
        # drop element-less duplicates when an element-bearing edge from the same page exists
        with_el = {r["page"] for r in p["reachedFrom"] if r.get("element") in el_page}
        cleaned, seen_r = [], set()
        for r in p["reachedFrom"]:
            if r.get("element") and r["element"] not in el_page:
                r = dict(r, element=None, note=f"opener id {r['element']!r} not found; page-level edge")
            if r.get("element") is None and not r.get("via") and r["page"] in with_el:
                continue
            k = (r["page"], r.get("element"), r.get("via"))
            if k in seen_r:
                continue
            seen_r.add(k)
            cleaned.append(r)
        p["reachedFrom"] = cleaned

    # page-level edges (no element known) are still navigation edges
    page_edges = []
    for p in pages.values():
        for r in p["reachedFrom"]:
            if r.get("element") is None:
                page_edges.append({"from": r["page"], "element": None, "to": p["id"],
                                   **({"via": r["via"]} if r.get("via") else {})})

    roots = [r for r in overrides.get("roots", []) if r in pages]
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True).stdout.strip()
    generated_from = overrides.get("generatedFrom") or head

    ordered_pages = sorted(pages.values(), key=lambda p: (list(AREA_TITLES).index(p["area"]) if p["area"] in AREA_TITLES else 99, p.get("file") or "", p.get("line") or 0, p["id"]))
    for p in ordered_pages:
        p["elements"].sort(key=lambda e: (e.get("file") or "", e.get("line") or 0))

    key_order = ["id", "title", "kind", "area", "file", "widget", "presentedBy", "line", "purpose",
                 "reachedFrom", "gates", "embeddedIn", "hostPages", "elements"]
    el_order = ["id", "type", "label", "key", "file", "line", "action", "target", "targetWidget",
                "unresolvedTarget", "effect", "gates", "destructive"]

    def ordered(d, order):
        out = collections.OrderedDict((k, d[k]) for k in order if k in d)
        for k in d:
            if k not in out:
                out[k] = d[k]
        return out

    ledger = collections.OrderedDict([
        ("generatedFrom", generated_from),
        ("pages", [ordered({**p, "elements": [ordered(e, el_order) for e in p["elements"]]}, key_order) for p in ordered_pages]),
        ("navigation", {"roots": roots, "edges": edges + page_edges}),
        ("notPages", sorted(not_pages, key=lambda n: n["file"])),
        ("notes", notes),
    ])
    with open(os.path.join(HERE, "ledger.json"), "w", encoding="utf-8") as fh:
        json.dump(ledger, fh, indent=1, ensure_ascii=False)
        fh.write("\n")

    render_pages_md(ordered_pages, pages)
    depth = render_navigation_md(ordered_pages, pages, roots, edges + page_edges)

    n_el = sum(len(p["elements"]) for p in pages.values())
    print(f"pages={len(pages)} elements={n_el} edges={len(edges)} page-level-edges={len(page_edges)}")
    print(f"unresolved targets={len(unresolved_targets)} unresolved inbound={len(unresolved_inbound)} problems={len(problems)}")
    if "--stats" in sys.argv or "--verbose" in sys.argv:
        for u in unresolved_targets:
            print("UNRESOLVED TARGET", u)
        for u in unresolved_inbound:
            print("UNRESOLVED INBOUND", u[0], json.dumps(u[1]))
        for pr in problems:
            print("PROBLEM", pr)
    if "--stats" in sys.argv:
        print_stats(ordered_pages, pages, edges + page_edges, depth, roots)


def esc(text):
    return str(text if text is not None else "").replace("|", "\\|").replace("\n", " ").strip()


def render_pages_md(ordered_pages, pages):
    out = ["# UI ledger: pages and elements", "",
           "Generated by `build_ledger.py` from `parts/*.json`. Do not edit by hand; edit the part files and rebuild.",
           "", f"{len(pages)} pages, {sum(len(p['elements']) for p in pages.values())} interactive elements.", "",
           "Legend: **Action -> target** uses the ledger action vocabulary; a target in `code` is a page id in this file. `!` marks destructive elements, `~` marks host wiring of a control that is also listed on an `embedded-*` page (see README).", ""]
    out += ["## Index", ""]
    by_area = collections.OrderedDict((a, []) for a in AREA_TITLES)
    for p in ordered_pages:
        by_area.setdefault(p["area"], []).append(p)
    for a, ps in by_area.items():
        if not ps:
            continue
        out.append(f"- **{AREA_TITLES.get(a, a)}** ({len(ps)}): " + ", ".join(f"[{p['id']}](#{p['id']})" for p in ps))
    out.append("")
    for a, ps in by_area.items():
        if not ps:
            continue
        out += [f"## {AREA_TITLES.get(a, a)}", ""]
        for p in ps:
            out += [f"### {p['id']}", "",
                    f"**{esc(p.get('title'))}** · {p['kind']} · `{p.get('widget')}` · `{p.get('file')}:{p.get('line')}`", "",
                    esc(p.get("purpose")), ""]
            if p.get("gates"):
                out.append("Gates: " + "; ".join(f"`{esc(g)}`" for g in p["gates"]))
                out.append("")
            if p.get("hostPages"):
                out.append("Embedded in: " + ", ".join(f"`{h}`" for h in p["hostPages"]))
                out.append("")
            inbound = sorted({r["page"] for r in p["reachedFrom"]})
            out.append("Reached from: " + (", ".join(f"`{i}`" for i in inbound) if inbound else "_no inbound edge found_"))
            out.append("")
            if p["elements"]:
                out += ["| Label | Type | Action -> target | Effect | Gates | Line |", "|---|---|---|---|---|---|"]
                for e in p["elements"]:
                    tgt = f" -> `{e['target']}`" if e.get("target") else (
                        f" -> ?{esc(e.get('unresolvedTarget'))}" if e.get("unresolvedTarget") else "")
                    mark = (" !" if e.get("destructive") else "") + (" ~" if e.get("hostWiring") else "")
                    loc = str(e.get("line") or "")
                    if e.get("file") and e.get("file") != p.get("file"):
                        loc = f"{os.path.basename(e['file'])}:{loc}"
                    out.append(f"| {esc(e.get('label'))}{mark} | {e['type']} | {e['action']}{tgt} | {esc(e.get('effect'))} | {esc('; '.join(e.get('gates') or []))} | {loc} |")
            else:
                out.append("_No interactive elements._")
            out.append("")
    with open(os.path.join(HERE, "pages.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


def render_navigation_md(ordered_pages, pages, roots, all_edges):
    # taps: embedded edges cost 0 taps
    adj = collections.defaultdict(list)
    for ed in all_edges:
        adj[ed["from"]].append((ed["to"], 0 if ed.get("via") else 1))
    def bfs(start):
        dist = {r: 0 for r in start}
        dq = collections.deque(start)
        while dq:
            cur = dq.popleft()
            for nxt, w in adj[cur]:
                nd = dist[cur] + w
                if nxt not in dist or nd < dist[nxt]:
                    dist[nxt] = nd
                    (dq.appendleft if w == 0 else dq.append)(nxt)
        return dist

    pre_roots = [r for r in roots if r in ("servers", "root-connecting")]
    app_roots = [r for r in roots if r not in pre_roots]
    depth = bfs(app_roots)          # taps from the connected shell
    pre_depth = bfs(pre_roots)      # taps from the pre-connection Servers root
    for k, v in pre_depth.items():
        depth.setdefault(k, None)
    depth = {k: v for k, v in depth.items()}
    render_navigation_md.pre_depth = pre_depth

    sys_only = {}
    if "system" in pages:
        dq = collections.deque(["system"])
        seen_s = {"system"}
        while dq:
            cur = dq.popleft()
            for nxt, _w in adj[cur]:
                if nxt not in seen_s:
                    seen_s.add(nxt)
                    dq.append(nxt)
                    if nxt not in depth:
                        sys_only[nxt] = True
    render_navigation_md.sys_only = sys_only

    out = ["# UI ledger: navigation map", "",
           "Generated by `build_ledger.py`. Depth = minimum number of taps from the connected shell "
           "(`home-shell` and its four tabs); the second number, after the slash, is the depth from the pre-connection root (`servers` / `root-connecting`), `-` when not reachable from there. "
           "(edges marked `embedded` or `state` cost no tap; the `system` pseudo page is not a root, pages reachable only through it show `system only`).", "",
           "## Roots", ""]
    for r in roots:
        p = pages[r]
        out.append(f"- `{r}`: {esc(p.get('title'))} ({p['kind']}, `{p.get('file')}`)" + (f"; gates: {esc('; '.join(p['gates']))}" if p.get("gates") else ""))
    out.append("")

    by_area = collections.OrderedDict((a, []) for a in AREA_TITLES)
    for p in ordered_pages:
        by_area.setdefault(p["area"], []).append(p)

    out += ["## Diagrams", "",
            "One diagram per area. Dialog pages and the `system` pseudo page are left out of the diagrams for readability "
            "(they are in the tables below). Dashed nodes belong to another area; dotted arrows mean \"embeds\".", ""]
    for a, ps in by_area.items():
        ids = {p["id"] for p in ps if p["kind"] != "dialog" and p["id"] != "system"}
        if not ids:
            continue
        pairs = collections.OrderedDict()
        for ed in all_edges:
            f, t = ed["from"], ed["to"]
            if f == "system" or pages[t]["kind"] == "dialog" or pages[f]["kind"] == "dialog":
                continue
            if f in ids or t in ids:
                pairs.setdefault((f, t), bool(ed.get("via")))
        if not pairs:
            continue
        out += [f"### {AREA_TITLES.get(a, a)}", "", "```mermaid", "graph LR"]
        nodes = set()
        for f, t in pairs:
            nodes.update((f, t))
        for n in sorted(nodes):
            nid = n.replace("-", "_")
            if n in ids:
                shape = f'{nid}(["{n}"])' if pages[n]["kind"] in ("sheet", "overlay") else f'{nid}["{n}"]'
                out.append(f"  {shape}")
            else:
                out.append(f'  {nid}["{n}"]:::ext')
        for (f, t), emb in pairs.items():
            arrow = "-.->" if emb else "-->"
            out.append(f"  {f.replace('-', '_')} {arrow} {t.replace('-', '_')}")
        out += ["  classDef ext stroke-dasharray: 4 3,opacity:0.7", "```", ""]

    out += ["## Per-page edges", ""]
    outbound = collections.defaultdict(list)
    for ed in all_edges:
        outbound[ed["from"]].append(ed)
    for a, ps in by_area.items():
        if not ps:
            continue
        out += [f"### {AREA_TITLES.get(a, a)}", "", "| Page | Kind | Depth | Inbound (page / element) | Outbound (element -> page) |", "|---|---|---|---|---|"]
        for p in ps:
            inn = "<br>".join(f"`{r['page']}`" + (f" / {r['element']}" if r.get("element") else (f" / ({r['via']})" if r.get("via") else "")) for r in p["reachedFrom"]) or "_none_"
            outs = "<br>".join((f"{ed['element']} -> " if ed.get("element") else (f"({ed['via']}) -> " if ed.get("via") else "-> ")) + f"`{ed['to']}`" for ed in outbound.get(p["id"], [])) or "_none_"
            d = depth.get(p["id"])
            pd = pre_depth.get(p["id"])
            if d is None and pd is None:
                dtxt = "system only" if p["id"] in sys_only or p["id"] == "system" else "unreachable"
            else:
                dtxt = f"{d if d is not None else '-'} / {pd if pd is not None else '-'}"
            out.append(f"| `{p['id']}` | {p['kind']} | {dtxt} | {inn} | {outs} |")
        out.append("")
    with open(os.path.join(HERE, "navigation.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    return depth


def print_stats(ordered_pages, pages, all_edges, depth, roots):
    print("\n== element count per page (top 25)")
    cnt = lambda p: sum(1 for e in p["elements"] if not e.get("hostWiring"))
    for p in sorted(pages.values(), key=lambda p: -cnt(p))[:30]:
        print(f"{cnt(p):4d} (+{len(p['elements']) - cnt(p)} wiring) {p['id']} ({p['kind']}) {p.get('file')}")
    print("\n== pages by kind", collections.Counter(p["kind"] for p in pages.values()))
    print("== pages by area", collections.Counter(p["area"] for p in pages.values()))
    print("\n== targets with most distinct inbound elements")
    inbound = collections.defaultdict(list)
    for ed in all_edges:
        if ed.get("element"):
            inbound[ed["to"]].append((ed["from"], ed["element"]))
    for t, lst in sorted(inbound.items(), key=lambda kv: -len(kv[1]))[:40]:
        srcs = sorted({f for f, _ in lst})
        print(f"{len(lst):3d} elements from {len(srcs):2d} pages -> {t}: {', '.join(srcs)}")
    print("\n== no inbound edge")
    for p in ordered_pages:
        if not p["reachedFrom"] and p["id"] not in roots and p["id"] != "system":
            print(f"  {p['id']} ({p['kind']}) {p.get('file')}:{p.get('line')}")
    print("\n== unreachable from roots")
    for p in ordered_pages:
        if p["id"] not in depth and p["id"] not in render_navigation_md.pre_depth and p["id"] != "system":
            tag = "system-only" if p["id"] in render_navigation_md.sys_only else "UNREACHABLE"
            print(f"  {tag} {p['id']} ({p['kind']})")
    print("\n== deepest pages")
    depth = {k: v for k, v in depth.items() if v is not None}
    for pid, d in sorted(depth.items(), key=lambda kv: -kv[1])[:60]:
        print(f"  {d} {pid} ({pages[pid]['kind']})")
    print("\n== depth histogram", sorted(collections.Counter(depth.values()).items()))
    print("\n== toggle-setting elements per page")
    c = collections.Counter()
    for p in pages.values():
        for e in p["elements"]:
            if e["action"] == "toggle-setting":
                c[p["id"]] += 1
    for k, v in c.most_common():
        print(f"  {v:3d} {k}")
    print("\n== same label on many pages (navigate/open actions)")
    lab = collections.defaultdict(set)
    for p in pages.values():
        for e in p["elements"]:
            if e.get("target"):
                lab[e["target"]].add(str(e.get("label")).strip())
    for t, labels in sorted(lab.items(), key=lambda kv: -len(kv[1]))[:40]:
        if len(labels) > 1:
            print(f"  {t}: {sorted(labels)}")
    print("\n== destructive elements:", sum(1 for p in pages.values() for e in p["elements"] if e.get("destructive")))


if __name__ == "__main__":
    main()
