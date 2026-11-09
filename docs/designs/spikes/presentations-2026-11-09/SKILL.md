---
name: make-presentation
description: Make a slide presentation (PDF and PowerPoint) from a short brief. Use when the person asks for slides, a deck or a presentation, or the message contains an oc-brief block.
---

# Make a presentation

The first message has a fenced `oc-brief` JSON block. It is authoritative: `topic`, `slides` (count), `audience`, `lang` (write ALL slide text in this language), `dir`, `theme`, `slug`.

All files go in `/root/projects/presentations/<slug>/` (create it). Never write elsewhere.

## Step 1: outline, then STOP

Write `deck.json` with `"stage": "outline"`: one slide per entry with only `layout` and `title` (plus `subtitle` on the title slide). Then ask the person with the `question` tool, with exactly two options: "Make the slides" and "Change the outline". Do nothing else until they answer. If they choose "Change the outline", ask what to change, update the outline, and ask again.

## Step 2: slides

When the answer is "Make the slides", rewrite `deck.json` with `"stage": "slides"` and fill every slide, then run exactly:

`node /opt/oc-packs/presentations/build.js <slug>`

If it prints errors or warnings, fix `deck.json` and run it again. When it prints an `OC_DECK` line, reply with one short sentence in the person's language. Do not describe the files.

## deck.json

```json
{"schema":1,"stage":"slides","title":"...","lang":"pt-BR","dir":"ltr","theme":"clean","version":1,
 "slides":[
  {"layout":"title","title":"max 80 chars","subtitle":"max 120, optional"},
  {"layout":"bullets","title":"...","bullets":["max 120 chars each","max 6 items"],"notes":"speaker notes, optional"},
  {"layout":"two_column","title":"...","left":["..."],"right":["..."]},
  {"layout":"quote","title":"the quote","subtitle":"who said it"},
  {"layout":"section","title":"..."},
  {"layout":"closing","title":"...","subtitle":"..."}
 ]}
```

Layouts: title, bullets, two_column, quote, section, closing. The first slide is `title`, the last is `closing`. Match the `slides` count in the brief. Keep bullets short (a phrase, not a paragraph). Write `dir` as in the brief (`rtl` for Arabic). Plain text only: no markdown, no emoji.
