// Single source of truth for brand, timing and footage wiring.
import { Easing } from "remotion";

export const FPS = 30;
export type Layout = "landscape" | "vertical";

export const colors = {
  bg: "#101310",
  surface: "#151A17",
  primary: "#83CDAA",
  text: "#E3E8E4",
  muted: "#BCC5BF",
  dim: "#7F8A83",
  line: "rgba(227,232,228,0.10)",
  glow: "rgba(131,205,170,0.35)",
};

export const fonts = {
  display: "'Space Grotesk', 'Inter', system-ui, sans-serif",
  mono: "'JetBrains Mono', ui-monospace, Menlo, monospace",
};

export const ease = {
  std: Easing.bezier(0.4, 0, 0.2, 1),
  emph: Easing.bezier(0.05, 0.7, 0.1, 1),
  exit: Easing.bezier(0.3, 0, 1, 1),
  inOut: Easing.bezier(0.83, 0, 0.17, 1),
};

/** Cross-fade length between top-level scenes (frames). */
export const XFADE = 8;

// ---------------------------------------------------------------------------
// Phone footage: 756 PNG frames, 585x1266 @30fps, caption strip in the bottom
// 60 px (cropped off; we caption ourselves). public/phone-frames -> frames dir.
export const PHONE = {
  dir: "phone-frames",
  count: 756,
  w: 585,
  h: 1266,
  cropBottom: 60,
  statusBar: 30,
};

/** Playback script: play source ranges (inclusive) and freeze-frame holds. */
export type Piece = { play: [number, number] } | { hold: number };
export const PHONE_SCRIPT: Piece[] = [
  { play: [0, 62] }, { hold: 27 }, //   0– 89 welcome
  { play: [63, 123] }, { hold: 29 }, //  90–179 workspace
  { play: [124, 249] }, //             180–305 empty chat -> typed prompt
  { play: [250, 376] }, { hold: 23 }, // 306–455 streaming + tool ticker
  { play: [377, 418] }, { hold: 36 }, { play: [419, 448] }, // 456–563 permission
  { play: [449, 589] }, { hold: 19 }, // 564–723 choices, one-tap answer
  { play: [590, 695] }, { hold: 44 }, // 724–873 diff
];

export const phoneScriptLength = (): number =>
  PHONE_SCRIPT.reduce(
    (n, p) => n + ("hold" in p ? p.hold : p.play[1] - p.play[0] + 1),
    0,
  );

/** Map a PhoneAct-local frame to a source frame index. */
export const phoneSourceFrame = (local: number): number => {
  let t = local;
  let last = 0;
  for (const p of PHONE_SCRIPT) {
    if ("hold" in p) {
      if (t < p.hold) return last;
      t -= p.hold;
    } else {
      const len = p.play[1] - p.play[0] + 1;
      if (t < len) return p.play[0] + t;
      t -= len;
      last = p.play[1];
    }
  }
  return last;
};

export type Beat = { at: number; text: string; sub?: string };
/** Captions, in PhoneAct-local frames. Each runs until the next one. */
export const PHONE_BEATS: Beat[] = [
  { at: 0, text: "Pair in one command.", sub: "$ opencode2 pair" },
  { at: 90, text: "Sessions, with what they cost.", sub: "shopfront · $1.18 · +212 −4" },
  { at: 180, text: "Just ask.", sub: "> Fix the flaky checkout test" },
  { at: 306, text: "Watch it work.", sub: "Read · Edit · Shell — as it happens" },
  { at: 456, text: "Approve without leaving the keyboard.", sub: "flutter test test/checkout_test.dart" },
  { at: 564, text: "Choices come as buttons.", sub: "no typing required" },
  { at: 660, text: "One tap to answer.", sub: "Run the full test suite →" },
  { at: 724, text: "Diffs that fit a phone.", sub: "checkout_test.dart · +5 −2" },
];

/** Camera pushes (zoom-ins) on the phone, PhoneAct-local frames. y = screen fraction. */
export type Focus = { y: number; zoom: number; in: [number, number]; out: [number, number] };
export const PHONE_FOCUS: Focus[] = [
  { y: 0.73, zoom: 1.45, in: [464, 488], out: [546, 566] }, // permission card
  { y: 0.64, zoom: 1.4, in: [576, 600], out: [682, 706] }, // choices block
];

/** Tap moments (for sfx), PhoneAct-local frames. */
export const PHONE_TAPS = [547, 667];

// ---------------------------------------------------------------------------
// Desktop footage: 1440x900 PNGs captured live on Linux at a MEASURED 4.12 fps
// (public/desktop-frames -> the frames dir). Played back at capture speed
// (each source frame ~7.3 video frames), trimmed to three excerpts.
// Set `enabled: false` to fall back to the placeholder card.
export const DESKTOP = {
  enabled: true,
  dir: "desktop-frames",
  fps: 4.12,
  w: 1440,
  h: 900,
  /** Source frame ranges (inclusive, 1-based file numbers). */
  segments: [
    [28, 40], // prompt sent, permission card slides in
    [91, 106], // "Allow once" -> shell runs -> summary + choices
    [212, 230], // Files -> Review workspace with the diff
  ] as [number, number][],
  dissolve: 6,
};

export const desktopSegmentLengths = (): number[] =>
  DESKTOP.segments.map(([a, b]) => Math.round(((b - a + 1) * FPS) / DESKTOP.fps));

export const desktopLength = (): number =>
  DESKTOP.enabled ? desktopSegmentLengths().reduce((a, b) => a + b, 0) : 210;

/** Map a Desktop-local frame to {index, prev, mix}: prev/mix drive a short dissolve at segment starts. */
export const desktopSourceFrame = (local: number): { index: number; prev: number | null; mix: number } => {
  const lens = desktopSegmentLengths();
  let t = Math.max(0, local);
  for (let i = 0; i < DESKTOP.segments.length; i++) {
    const [a, b] = DESKTOP.segments[i];
    if (t < lens[i] || i === DESKTOP.segments.length - 1) {
      const idx = Math.min(b, a + Math.floor((Math.min(t, lens[i] - 1) * DESKTOP.fps) / FPS));
      const prev = i > 0 && t < DESKTOP.dissolve ? DESKTOP.segments[i - 1][1] : null;
      const mix = prev === null ? 0 : 1 - t / DESKTOP.dissolve;
      return { index: idx, prev, mix };
    }
    t -= lens[i];
  }
  return { index: DESKTOP.segments[0][0], prev: null, mix: 0 };
};

// ---------------------------------------------------------------------------
export const SCENES = {
  intro: 90,
  phone: phoneScriptLength(), // 874
  inbox: 120,
  desktop: desktopLength(), // ~350 with live footage
  outro: 135,
};

export const totalDuration = (): number =>
  SCENES.intro + SCENES.phone + SCENES.inbox + SCENES.desktop + SCENES.outro - 4 * XFADE;

/** Absolute start frame of each scene (accounting for cross-fades). */
export const sceneStarts = () => {
  const intro = 0;
  const phone = intro + SCENES.intro - XFADE;
  const inbox = phone + SCENES.phone - XFADE;
  const desktop = inbox + SCENES.inbox - XFADE;
  const outro = desktop + SCENES.desktop - XFADE;
  return { intro, phone, inbox, desktop, outro };
};

export const pad4 = (n: number) => String(n).padStart(4, "0");
